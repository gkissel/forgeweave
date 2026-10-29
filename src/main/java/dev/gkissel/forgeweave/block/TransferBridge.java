package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Forgeweave's block storage on NeoForge's transactional transfer API ({@link ResourceHandler}, {@link
 * EnergyHandler}). NeoForge 26.3 removed the {@code IFluidHandler}/{@code IItemHandler} shapes Forgeweave's
 * storage rules were written against, so the rules now live in {@link FluidStore} and {@link ItemStore}:
 * every capacity, per-side rule and filter is still the 1.21.1 code, written as {@code fill}/{@code drain}
 * and {@code insertItem}/{@code extractItem} with a {@code simulate} flag, and the store <em>is</em> the
 * {@link ResourceHandler} the capability hands out.
 *
 * <p>Transactions: an operation that would move something runs on the store at once, after the owner's
 * {@link State} has been captured for the open transaction; aborting the transaction (a simulation
 * included) hands that capture back to {@link State#restore}. An operation that would move nothing touches
 * nothing. Forgeweave code that is handed a store directly (or through {@link #fill}, {@link #drain}, {@link
 * #insert} and {@link #extract}) skips the transaction when none is open, and simulates without touching
 * anything, so Forgeweave-to-Forgeweave moves run exactly the 1.21.1 code.
 */
public final class TransferBridge {
    private TransferBridge() {}

    /**
     * Captures and restores everything a store's mutations can change. One {@code State} per piece of
     * storage: every store over the same storage must share it, so an aborted transaction restores that
     * storage exactly once, to the state before the first change.
     */
    public abstract static class State<S> extends SnapshotJournal<S> {
        public static <S> State<S> of(Supplier<S> save, Consumer<S> restore) {
            return new State<>() {
                @Override
                protected S createSnapshot() {
                    return save.get();
                }

                @Override
                protected void revertToSnapshot(S snapshot) {
                    restore.accept(snapshot);
                }
            };
        }
    }

    /** A {@link FluidTank}'s contents; {@code changed} runs after a rollback, as the tank's own change hook would. */
    public static State<FluidStack> tankState(FluidTank tank, Runnable changed) {
        return State.of(() -> tank.getFluid().copy(), fluid -> {
            tank.setFluid(fluid);
            changed.run();
        });
    }

    /**
     * A transaction inside whatever transaction this thread has open, or a root one when none is. Storage
     * code can run inside another mod's transaction (a pipe filling a casting table, whose recipe check
     * opens one of its own), so opening a second root there would throw.
     */
    @SuppressWarnings("deprecation")
    public static Transaction openNested() {
        return Transaction.open(Transaction.getCurrentOpenedTransaction());
    }

    @SuppressWarnings("deprecation")
    private static boolean inTransaction() {
        return Transaction.getCurrentOpenedTransaction() != null;
    }

    /* ---------------------------------------------------------------- neighbours */

    /** A neighbour's fluid capability, or {@code null}. */
    @Nullable
    public static ResourceHandler<FluidResource> fluidAt(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
    }

    /** A neighbour's item capability, or {@code null}. */
    @Nullable
    public static ResourceHandler<ItemResource> itemsAt(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.Item.BLOCK, pos, side);
    }

    /* ---------------------------------------------------------------- one-shot moves (simulate = never committed) */

    /** Fills {@code stack} into {@code handler}; the amount accepted. */
    public static int fill(@Nullable ResourceHandler<FluidResource> handler, FluidStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return 0;
        }
        if (handler instanceof FluidStore store && (simulate || !inTransaction())) {
            return store.fill(stack, simulate);
        }
        try (Transaction transaction = openNested()) {
            int filled = handler.insert(FluidResource.of(stack), stack.getAmount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return filled;
        }
    }

    /** Drains up to {@code stack}'s amount of exactly that fluid; what came out. */
    public static FluidStack drain(@Nullable ResourceHandler<FluidResource> handler, FluidStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        if (handler instanceof FluidStore store && (simulate || !inTransaction())) {
            return store.drain(stack, simulate);
        }
        try (Transaction transaction = openNested()) {
            int drained = handler.extract(FluidResource.of(stack), stack.getAmount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return stack.copyWithAmount(drained);
        }
    }

    /** Drains up to {@code amount} of whatever fluid the handler gives first; what came out. */
    public static FluidStack drain(@Nullable ResourceHandler<FluidResource> handler, int amount, boolean simulate) {
        if (handler == null || amount <= 0) {
            return FluidStack.EMPTY;
        }
        if (handler instanceof FluidStore store && (simulate || !inTransaction())) {
            return store.drain(amount, simulate);
        }
        try (Transaction transaction = openNested()) {
            ResourceStack<FluidResource> drained = ResourceHandlerUtil.extractFirst(handler, fluid -> true, amount, transaction);
            if (drained == null) {
                return FluidStack.EMPTY;
            }
            if (!simulate) {
                transaction.commit();
            }
            return drained.resource().toStack(drained.amount());
        }
    }

    /** Inserts into one slot; the remainder that did not fit. */
    public static ItemStack insert(@Nullable ResourceHandler<ItemResource> handler, int slot, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        if (handler instanceof ItemStore store && (simulate || !inTransaction())) {
            return store.insertItem(slot, stack, simulate);
        }
        try (Transaction transaction = openNested()) {
            int inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    /** Inserts into each slot in turn, lowest first, until it is all in; the remainder that did not fit. */
    public static ItemStack insert(@Nullable ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        if (handler instanceof ItemStore store && (simulate || !inTransaction())) {
            for (int slot = 0; slot < store.getSlots() && !stack.isEmpty(); slot++) {
                stack = store.insertItem(slot, stack, simulate);
            }
            return stack;
        }
        try (Transaction transaction = openNested()) {
            ItemResource resource = ItemResource.of(stack);
            int inserted = 0;
            for (int slot = 0; slot < handler.size() && inserted < stack.getCount(); slot++) {
                inserted += handler.insert(slot, resource, stack.getCount() - inserted, transaction);
            }
            if (!simulate) {
                transaction.commit();
            }
            return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    /** Takes up to {@code amount} out of one slot (never more than a stack); what came out. */
    public static ItemStack extract(@Nullable ResourceHandler<ItemResource> handler, int slot, int amount, boolean simulate) {
        if (handler == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (handler instanceof ItemStore store && (simulate || !inTransaction())) {
            return store.extractItem(slot, amount, simulate);
        }
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction transaction = openNested()) {
            int extracted = handler.extract(slot, resource, Math.min(amount, resource.getMaxStackSize()), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return resource.toStack(extracted);
        }
    }

    /** Puts energy in; the amount accepted. */
    public static int insertEnergy(@Nullable EnergyHandler handler, int amount, boolean simulate) {
        if (handler == null || amount <= 0) {
            return 0;
        }
        try (Transaction transaction = openNested()) {
            int inserted = handler.insert(amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return inserted;
        }
    }

    /** Takes energy out; the amount removed. */
    public static int extractEnergy(@Nullable EnergyHandler handler, int amount, boolean simulate) {
        if (handler == null || amount <= 0) {
            return 0;
        }
        try (Transaction transaction = openNested()) {
            int extracted = handler.extract(amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
            return extracted;
        }
    }

    /**
     * Gives a stack to a player the way NeoForge's removed {@code ItemHandlerHelper.giveItemToPlayer} did:
     * into the main inventory, topping up matching stacks before using empty slots, with the pickup sound
     * if anything went in, and whatever is left dropped at the player's feet with the usual pickup delay.
     */
    public static void giveItemToPlayer(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Level level = player.level();
        int inserted;
        try (Transaction transaction = openNested()) {
            inserted = ResourceHandlerUtil.insertStacking(PlayerInventoryWrapper.of(player).getMainSlots(),
                    ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
        }
        if (inserted > 0) {
            level.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, 0.2F,
                    ((level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
        }
        if (inserted < stack.getCount() && !level.isClientSide()) {
            ItemEntity entity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(),
                    stack.copyWithCount(stack.getCount() - inserted));
            entity.setPickUpDelay(40);
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0, 1, 0));
            level.addFreshEntity(entity);
        }
    }

    /**
     * How a menu slot over {@code handler} overwrites a slot: in place for a store that can (a container's
     * slot, the duct filter: the stack the store holds, as the 1.21.1 {@code IItemHandlerModifiable} did) or
     * a plain stacks handler (a menu's client-side placeholder), or else by swapping the slot's contents in
     * one transaction, which does nothing unless the old stack comes out and the new one goes in whole.
     */
    public static IndexModifier<ItemResource> slotModifier(ResourceHandler<ItemResource> handler) {
        if (handler instanceof ItemStacksResourceHandler stacks) {
            return stacks::set;
        }
        return (slot, resource, amount) -> {
            if (handler instanceof ItemStore store && store.setStackInSlot(slot, resource.toStack(amount))) {
                return;
            }
            try (Transaction transaction = openNested()) {
                ItemResource current = handler.getResource(slot);
                int held = handler.getAmountAsInt(slot);
                if (!current.isEmpty() && handler.extract(slot, current, held, transaction) != held) {
                    return;
                }
                if (amount > 0 && handler.insert(slot, resource, amount, transaction) != amount) {
                    return;
                }
                transaction.commit();
            }
        };
    }

    /* ---------------------------------------------------------------- stores */

    /**
     * A Forgeweave fluid store: its rules as 1.21.1 wrote them ({@link #fill}, {@link #drain}), exposed as the
     * transfer API's {@link ResourceHandler}. {@link #state()} is the journal a transactional insert or
     * extract captures before running the rule for real.
     */
    public abstract static class FluidStore implements ResourceHandler<FluidResource> {
        /** The journal over everything {@link #fill}/{@link #drain} can change; {@code null} if they change nothing. */
        @Nullable
        protected abstract State<?> state();

        public abstract int getTanks();

        public abstract FluidStack getFluidInTank(int tank);

        public abstract int getTankCapacity(int tank);

        public abstract boolean isFluidValid(int tank, FluidStack stack);

        /** The amount of {@code resource} accepted (or, simulating, that would be). */
        public abstract int fill(FluidStack resource, boolean simulate);

        /** Up to {@code resource}'s amount of exactly that fluid. */
        public abstract FluidStack drain(FluidStack resource, boolean simulate);

        /** Up to {@code maxDrain} of whatever the store gives first: by default the first tank holding anything. */
        public FluidStack drain(int maxDrain, boolean simulate) {
            for (int tank = 0; tank < getTanks(); tank++) {
                FluidStack fluid = getFluidInTank(tank);
                if (!fluid.isEmpty()) {
                    return drain(fluid.copyWithAmount(maxDrain), simulate);
                }
            }
            return FluidStack.EMPTY;
        }

        private void updateSnapshots(TransactionContext transaction) {
            State<?> state = state();
            if (state != null) {
                state.updateSnapshots(transaction);
            }
        }

        @Override
        public int size() {
            return getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return isFluidValid(index, resource.toStack(1));
        }

        /** {@link #fill} picks its own tank, so only index 0 takes an indexed insert. */
        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == 0 ? insert(resource, amount, transaction) : 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || fill(resource.toStack(amount), true) <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return fill(resource.toStack(amount), false);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return resource.matches(getFluidInTank(index)) ? extract(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || drain(resource.toStack(amount), true).isEmpty()) {
                return 0;
            }
            updateSnapshots(transaction);
            return drain(resource.toStack(amount), false).getAmount();
        }
    }

    /**
     * For an insert-only store that accepts the same amount whatever it already took, and whose fill cannot
     * be undone (the smeltery core's pour-to-transform swaps the block): during a transaction a fill is only
     * simulated, and the real fills run once the root transaction commits.
     */
    public static ResourceHandler<FluidResource> deferredFluids(FluidStore store) {
        return new DeferredFluids(store);
    }

    private static final class DeferredFluids extends SnapshotJournal<List<FluidStack>> implements ResourceHandler<FluidResource> {
        private final FluidStore store;
        private List<FluidStack> pending = new ArrayList<>();

        DeferredFluids(FluidStore store) {
            this.store = store;
        }

        @Override
        protected List<FluidStack> createSnapshot() {
            return new ArrayList<>(pending);
        }

        @Override
        protected void revertToSnapshot(List<FluidStack> snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onRootCommit(List<FluidStack> originalState) {
            List<FluidStack> fills = pending;
            pending = new ArrayList<>();
            for (FluidStack fill : fills) {
                store.fill(fill, false);
            }
        }

        @Override
        public int size() {
            return store.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return store.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return store.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return store.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return store.isValid(index, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == 0 ? insert(resource, amount, transaction) : 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            int accepted = amount == 0 ? 0 : store.fill(resource.toStack(amount), true);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending.add(resource.toStack(accepted));
            return accepted;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }

    /**
     * A Forgeweave item store: its rules as 1.21.1 wrote them ({@link #insertItem}, {@link #extractItem}),
     * exposed as the transfer API's {@link ResourceHandler}. Stacks it returns from {@link #getStackInSlot}
     * may be live.
     */
    public abstract static class ItemStore implements ResourceHandler<ItemResource> {
        /** The journal over everything {@link #insertItem}/{@link #extractItem} can change. */
        protected abstract State<?> state();

        public abstract int getSlots();

        public abstract ItemStack getStackInSlot(int slot);

        public abstract int getSlotLimit(int slot);

        public abstract boolean isItemValid(int slot, ItemStack stack);

        /** The remainder of {@code stack} that did not go in (or, simulating, would not). */
        public abstract ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

        /** Up to {@code amount} taken out of {@code slot}. */
        public abstract ItemStack extractItem(int slot, int amount, boolean simulate);

        /** Overwrites a slot outright, for a menu slot; {@code false} where the store has no such move. */
        public boolean setStackInSlot(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public int size() {
            return getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            int limit = getSlotLimit(index);
            return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }
            ItemStack offered = resource.toStack(amount);
            if (insertItem(index, offered.copy(), true).getCount() >= amount) {
                return 0;
            }
            state().updateSnapshots(transaction);
            return amount - insertItem(index, offered, false).getCount();
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !resource.matches(getStackInSlot(index)) || extractItem(index, amount, true).isEmpty()) {
                return 0;
            }
            state().updateSnapshots(transaction);
            return extractItem(index, amount, false).getCount();
        }
    }

    /** A {@link Container} as an {@link ItemStore}, journalled slot by slot. */
    public static ContainerItems items(Container container) {
        return new ContainerItems(container, null);
    }

    /** A {@link Container} as an {@link ItemStore}, journalled by its owner's {@code state}. */
    public static ContainerItems items(Container container, State<?> state) {
        return new ContainerItems(container, state);
    }

    /**
     * NeoForge's removed {@code InvWrapper}, rule for rule: the container's own {@code canPlaceItem} and
     * {@code getMaxStackSize()}, inserts through {@code setItem} and takes through {@code removeItem}, and
     * the slot count read live from {@code getContainerSize()} (a Forgeweave chest grows while open).
     */
    public static final class ContainerItems extends ItemStore {
        private final Container container;
        private final State<?> state;

        private ContainerItems(Container container, @Nullable State<?> state) {
            this.container = container;
            this.state = state != null ? state : State.of(() -> {
                List<ItemStack> copy = new ArrayList<>(container.getContainerSize());
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    copy.add(container.getItem(slot).copy());
                }
                return copy;
            }, copy -> {
                for (int slot = 0; slot < copy.size(); slot++) {
                    container.setItem(slot, copy.get(slot));
                }
                container.setChanged();
            });
        }

        @Override
        protected State<?> state() {
            return state;
        }

        public Container container() {
            return container;
        }

        @Override
        public int getSlots() {
            return container.getContainerSize();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return container.getItem(slot);
        }

        @Override
        public boolean setStackInSlot(int slot, ItemStack stack) {
            container.setItem(slot, stack);
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return container.getMaxStackSize();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return container.canPlaceItem(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack inSlot = container.getItem(slot);
            int room;
            if (!inSlot.isEmpty()) {
                if (inSlot.getCount() >= Math.min(inSlot.getMaxStackSize(), getSlotLimit(slot))
                        || !ItemStack.isSameItemSameComponents(stack, inSlot)
                        || !container.canPlaceItem(slot, stack)) {
                    return stack;
                }
                room = Math.min(stack.getMaxStackSize(), getSlotLimit(slot)) - inSlot.getCount();
            } else {
                if (!container.canPlaceItem(slot, stack)) {
                    return stack;
                }
                room = Math.min(stack.getMaxStackSize(), getSlotLimit(slot));
            }
            int moved = Math.min(room, stack.getCount());
            if (!simulate) {
                container.setItem(slot, inSlot.isEmpty() ? stack.copyWithCount(moved) : inSlot.copyWithCount(inSlot.getCount() + moved));
                container.setChanged();
            }
            return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount == 0) {
                return ItemStack.EMPTY;
            }
            ItemStack inSlot = container.getItem(slot);
            if (inSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (simulate) {
                return inSlot.copyWithCount(Math.min(inSlot.getCount(), amount));
            }
            ItemStack removed = container.removeItem(slot, Math.min(inSlot.getCount(), amount));
            container.setChanged();
            return removed;
        }
    }

    /** A handler's slot as a stack, for code that reads it the 1.21.1 way. */
    public static ItemStack stackIn(ResourceHandler<ItemResource> handler, int slot) {
        return handler instanceof ItemStore store ? store.getStackInSlot(slot) : ItemUtil.getStack(handler, slot);
    }
}
