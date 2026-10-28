package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Exposes Forgeweave's block storage, whose rules are written against the pre-21.9 {@link IFluidHandler},
 * {@link IItemHandler} and {@link IEnergyStorage} shapes, through NeoForge 21.9's transactional transfer API
 * ({@link ResourceHandler}, {@link EnergyHandler}), and reads neighbours' new-API capabilities back as the old
 * shapes. Keeping the old shapes inside means every capacity, per-side rule and filter stays the exact code
 * 1.21.1 shipped.
 *
 * <p>Transactions: an operation that would move something runs on the wrapped handler at once, after the
 * owner's {@link State} has been captured for the open transaction; aborting the transaction (a simulation
 * included) hands that capture back to {@link State#restore}. An operation that would move nothing touches
 * nothing.
 */
@SuppressWarnings("removal")
public final class TransferBridge {
    private TransferBridge() {}

    /**
     * Captures and restores everything the wrapped handlers' mutations can change. One {@code State} per
     * piece of storage: every bridge over the same storage must share it, so an aborted transaction
     * restores that storage exactly once, to the state before the first change.
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

    /** Every slot of a modifiable item handler; its own {@code setStackInSlot} hook covers the change. */
    public static State<List<ItemStack>> slotsState(IItemHandlerModifiable handler) {
        return State.of(() -> {
            List<ItemStack> copy = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                copy.add(handler.getStackInSlot(slot).copy());
            }
            return copy;
        }, copy -> {
            for (int slot = 0; slot < copy.size(); slot++) {
                handler.setStackInSlot(slot, copy.get(slot));
            }
        });
    }

    public static ResourceHandler<FluidResource> fluids(IFluidHandler handler, State<?> state) {
        return new Fluids(handler, state);
    }

    /**
     * For an insert-only handler that accepts the same amount whatever it already took, and whose fill
     * cannot be undone (the smeltery core's pour-to-transform swaps the block): during a transaction a
     * fill is only simulated, and the real fills run once the root transaction commits.
     */
    public static ResourceHandler<FluidResource> deferredFluids(IFluidHandler handler) {
        return new DeferredFluids(handler);
    }

    public static ResourceHandler<ItemResource> items(IItemHandler handler, State<?> state) {
        return new Items(handler, state);
    }

    public static ResourceHandler<ItemResource> items(IItemHandlerModifiable handler) {
        return new Items(handler, slotsState(handler));
    }

    public static EnergyHandler energy(IEnergyStorage storage, State<?> state) {
        return new Energy(storage, state);
    }

    /** A neighbour's fluid capability in the old shape, or {@code null}. */
    @Nullable
    public static IFluidHandler fluidAt(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        return handler == null ? null : IFluidHandler.of(handler);
    }

    /** A neighbour's item capability in the old shape, or {@code null}. */
    @Nullable
    public static IItemHandler itemsAt(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        return handler == null ? null : IItemHandler.of(handler);
    }

    /** Snapshot plumbing shared by the three bridges. */
    private abstract static class Journal {
        private final State<?> state;

        Journal(State<?> state) {
            this.state = state;
        }

        void updateSnapshots(TransactionContext transaction) {
            state.updateSnapshots(transaction);
        }
    }

    private static final class Fluids extends Journal implements ResourceHandler<FluidResource> {
        private final IFluidHandler handler;

        Fluids(IFluidHandler handler, State<?> state) {
            super(state);
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(handler.getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return handler.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return handler.isFluidValid(index, resource.toStack(1));
        }

        /** The old {@code fill} picks its own tank, so only index 0 takes an indexed insert. */
        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == 0 ? insert(resource, amount, transaction) : 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.SIMULATE) <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return resource.matches(handler.getFluidInTank(index)) ? extract(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }
    }

    private static final class DeferredFluids extends SnapshotJournal<List<FluidStack>> implements ResourceHandler<FluidResource> {
        private final IFluidHandler handler;
        private List<FluidStack> pending = new ArrayList<>();

        DeferredFluids(IFluidHandler handler) {
            this.handler = handler;
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
                handler.fill(fill, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(handler.getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return handler.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return handler.isFluidValid(index, resource.toStack(1));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == 0 ? insert(resource, amount, transaction) : 0;
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            int accepted = amount == 0 ? 0 : handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.SIMULATE);
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

    private static final class Items extends Journal implements ResourceHandler<ItemResource> {
        private final IItemHandler handler;

        Items(IItemHandler handler, State<?> state) {
            super(state);
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(handler.getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            int limit = handler.getSlotLimit(index);
            return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return handler.isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }
            ItemStack offered = resource.toStack(amount);
            if (handler.insertItem(index, offered.copy(), true).getCount() >= amount) {
                return 0;
            }
            updateSnapshots(transaction);
            return amount - handler.insertItem(index, offered, false).getCount();
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !resource.matches(handler.getStackInSlot(index))
                    || handler.extractItem(index, amount, true).isEmpty()) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.extractItem(index, amount, false).getCount();
        }
    }

    private static final class Energy extends Journal implements EnergyHandler {
        private final IEnergyStorage storage;

        Energy(IEnergyStorage storage, State<?> state) {
            super(state);
            this.storage = storage;
        }

        @Override
        public long getAmountAsLong() {
            return storage.getEnergyStored();
        }

        @Override
        public long getCapacityAsLong() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (storage.receiveEnergy(amount, true) <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return storage.receiveEnergy(amount, false);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (storage.extractEnergy(amount, true) <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return storage.extractEnergy(amount, false);
        }
    }
}
