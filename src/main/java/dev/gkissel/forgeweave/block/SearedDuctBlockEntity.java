package dev.gkissel.forgeweave.block;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Containers;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.SearedDuctMenu;

/**
 * A duct's filter slot and its filtered view of the smeltery's tank (docs/SCOPE.md M3.4 issue #277),
 * ported from the 1.20 clone's {@code DuctBlockEntity}, {@code DuctItemHandler} and {@code
 * DuctTankWrapper} (NOTICE.md) -- a maintainer-approved deviation from the 1.12 parity default,
 * recorded on issue #277: the 1.12 generation has only the plain drain.
 *
 * <p>A duct is a drain that only lets one fluid through. The fluid is named by putting a <em>filled
 * fluid container</em> -- a bucket, or anything whose empty form is one -- into its one-slot
 * inventory; whatever that container holds is the only thing the duct will fill or drain. With the
 * slot empty nothing passes at all, which is upstream's behaviour too (its wrapper's {@code fill} and
 * {@code drain} both compare against an empty filter and fail).
 *
 * <p>The filter slot is itself exposed as an item-handler capability, so a hopper can set the filter
 * the same way a player can -- again upstream's own arrangement.
 */
public class SearedDuctBlockEntity extends SmelteryIoBlockEntity implements MenuProvider {
    /**
     * The empty fluid containers a duct accepts as a filter, the 1.20 clone's {@code duct_containers}
     * (NOTICE.md). A <em>filled</em> container qualifies through its empty form, which is how a lava
     * bucket gets in when only the plain bucket is listed. Upstream also lists its copper can and
     * seared/scorched lanterns; Forgeweave has none of those, so the vanilla bucket is the whole list
     * -- filled by {@code ForgeweaveItemTagsProvider}.
     */
    public static final TagKey<Item> DUCT_CONTAINERS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Forgeweave.MODID, "duct_containers"));

    private static final String TAG_FILTER = "filter";

    private final FilterSlot filter = new FilterSlot();
    private final FilteredTank filteredTank = new FilteredTank();

    public SearedDuctBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_DUCT.get(), pos, state);
    }

    /** The one-slot filter inventory, exposed to hoppers and to {@link SearedDuctMenu}. */
    public TransferBridge.ItemStore filterSlot() {
        return filter;
    }

    /** What the filter container holds, or {@link FluidStack#EMPTY} when the slot is empty. */
    public FluidStack filterFluid() {
        return filter.fluid();
    }

    /**
     * The smeltery tank as this duct's filter sees it, or {@code null} while the duct is not part of a
     * formed structure (same as a drain).
     */
    @Nullable
    public TransferBridge.FluidStore fluidHandler() {
        return formedCore() == null ? null : filteredTank;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SearedDuctMenu(containerId, playerInventory,
                ContainerLevelAccess.create(level, worldPosition), filter);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putChild(TAG_FILTER, filter);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.readChild(TAG_FILTER, filter);
    }

    /** Wires both capabilities; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ForgeweaveBlockEntities.SEARED_DUCT.get(),
                (blockEntity, side) -> blockEntity.fluidCapability());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ForgeweaveBlockEntities.SEARED_DUCT.get(),
                (blockEntity, side) -> blockEntity.filter);
    }

    /** {@link #fluidHandler()} as the transfer API sees it, journalled on the smeltery's own tank. */
    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        return fluidHandler();
    }

    /** {@link #filterSlot()} as the transfer API sees it; what the item capability hands out. */
    public ResourceHandler<ItemResource> filterCapability() {
        return filter;
    }

    /**
     * The filter slot, upstream's {@code DuctItemHandler}: one item, only a filled fluid container,
     * with the container's fluid cached until the slot changes.
     */
    private final class FilterSlot extends TransferBridge.ItemStore implements ValueIOSerializable {
        /** NeoForge's removed {@code ItemStackHandler} saved its slots under these two keys; so does this. */
        private static final String TAG_ITEMS = "Items";
        private static final String TAG_SIZE = "Size";

        private ItemStack stack = ItemStack.EMPTY;
        @Nullable
        private FluidStack cached;
        private final TransferBridge.State<ItemStack> state = TransferBridge.State.of(() -> stack.copy(), this::setStackInSlot0);

        @Override
        protected TransferBridge.State<?> state() {
            return state;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public boolean setStackInSlot(int slot, ItemStack stack) {
            setStackInSlot0(stack);
            return true;
        }

        private void setStackInSlot0(ItemStack stack) {
            this.stack = stack;
            onContentsChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        /**
         * Upstream's own two-part test: the item is a known fluid container (or its empty form is --
         * that is what lets a <em>filled</em> bucket in when only the empty bucket is tagged), and it
         * actually holds something. An empty container names no fluid, so it is no filter.
         */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!stack.is(DUCT_CONTAINERS)) {
                ItemStackTemplate remainder = stack.getCraftingRemainder();
                ItemStack empty = remainder == null ? ItemStack.EMPTY : remainder.create();
                if (empty.isEmpty() || !empty.is(DUCT_CONTAINERS)) {
                    return false;
                }
            }
            return !FluidUtil.getFirstStackContained(stack).isEmpty();
        }

        /** {@code ItemStackHandler#insertItem}'s rules, for its one slot. */
        @Override
        public ItemStack insertItem(int slot, ItemStack offered, boolean simulate) {
            if (offered.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!isItemValid(slot, offered)) {
                return offered;
            }
            int limit = Math.min(getSlotLimit(slot), offered.getMaxStackSize());
            if (!stack.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(offered, stack)) {
                    return offered;
                }
                limit -= stack.getCount();
            }
            if (limit <= 0) {
                return offered;
            }
            boolean reachedLimit = offered.getCount() > limit;
            if (!simulate) {
                if (stack.isEmpty()) {
                    stack = reachedLimit ? offered.copyWithCount(limit) : offered;
                } else {
                    stack.grow(reachedLimit ? limit : offered.getCount());
                }
                onContentsChanged();
            }
            return reachedLimit ? offered.copyWithCount(offered.getCount() - limit) : ItemStack.EMPTY;
        }

        /** {@code ItemStackHandler#extractItem}'s rules, for its one slot. */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount == 0 || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int toExtract = Math.min(amount, stack.getMaxStackSize());
            if (stack.getCount() <= toExtract) {
                ItemStack existing = stack;
                if (simulate) {
                    return existing.copy();
                }
                stack = ItemStack.EMPTY;
                onContentsChanged();
                return existing;
            }
            if (!simulate) {
                stack = stack.copyWithCount(stack.getCount() - toExtract);
                onContentsChanged();
            }
            return stack.copyWithCount(toExtract);
        }

        private void onContentsChanged() {
            cached = null;
            setChanged();
            if (level != null) {
                // The fluid handler this duct hands out answers differently now, and a pipe next to it
                // may be holding a BlockCapabilityCache over the old answer.
                level.invalidateCapabilities(worldPosition);
            }
        }

        @Override
        public void serialize(ValueOutput output) {
            ValueOutput.TypedOutputList<ItemStackWithSlot> items = output.list(TAG_ITEMS, ItemStackWithSlot.CODEC);
            if (!stack.isEmpty()) {
                items.add(new ItemStackWithSlot(0, stack));
            }
            output.putInt(TAG_SIZE, 1);
        }

        @Override
        public void deserialize(ValueInput input) {
            stack = ItemStack.EMPTY;
            input.listOrEmpty(TAG_ITEMS, ItemStackWithSlot.CODEC).forEach(saved -> {
                if (saved.isValidInContainer(1)) {
                    stack = saved.stack();
                }
            });
            cached = null;
        }

        FluidStack fluid() {
            if (cached == null) {
                cached = FluidUtil.getFirstStackContained(getStackInSlot(0));
            }
            return cached;
        }
    }

    /**
     * The smeltery's tank seen through the filter, upstream's {@code DuctTankWrapper}: a fill or drain
     * only goes through for the filtered fluid, and an unset filter matches nothing.
     *
     * <p>ponytail: upstream additionally re-indexes the smeltery's tank <em>list</em> down to the
     * matching entries, so a gauge reading the duct sees only those. Forgeweave shows one tank -- how
     * much of the filtered fluid the smeltery holds -- because that is the whole of what a filter can
     * usefully report, and the fill/drain gating (which is what "only the filtered fluid passes"
     * means) is upstream's verbatim. Re-index here if something ever wants to enumerate a duct.
     */
    private final class FilteredTank extends TransferBridge.FluidStore {
        /** The smeltery tank's own journal: a filtered fill or drain changes nothing else. */
        @Nullable
        @Override
        protected TransferBridge.State<?> state() {
            SmelteryTank smeltery = tank();
            return smeltery == null ? null : smeltery.transferState();
        }

        @Nullable
        private SmelteryTank tank() {
            SmelteryControllerBlockEntity core = formedCore();
            return core == null ? null : core.tank();
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            SmelteryTank smeltery = tank();
            FluidStack wanted = filterFluid();
            if (smeltery == null || wanted.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return smeltery.fluids().stream()
                    .filter(fluid -> FluidStack.isSameFluidSameComponents(fluid, wanted))
                    .findFirst()
                    .map(FluidStack::copy)
                    .orElse(FluidStack.EMPTY);
        }

        @Override
        public int getTankCapacity(int tank) {
            SmelteryTank smeltery = tank();
            return smeltery == null ? 0 : smeltery.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            FluidStack wanted = filterFluid();
            return !wanted.isEmpty() && FluidStack.isSameFluidSameComponents(wanted, stack);
        }

        @Override
        public int fill(FluidStack resource, boolean simulate) {
            SmelteryTank smeltery = tank();
            if (smeltery == null || resource.isEmpty() || !isFluidValid(0, resource)) {
                return 0;
            }
            return smeltery.fill(resource, simulate);
        }

        @Override
        public FluidStack drain(int maxDrain, boolean simulate) {
            FluidStack wanted = filterFluid();
            if (wanted.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return drain(wanted.copyWithAmount(maxDrain), simulate);
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean simulate) {
            SmelteryTank smeltery = tank();
            if (smeltery == null || resource.isEmpty() || !isFluidValid(0, resource)) {
                return FluidStack.EMPTY;
            }
            return smeltery.drain(resource, simulate);
        }
    }

    /** Drops the filter item when the block is broken (was {@code SearedDuctBlock#onRemove}). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, new SimpleContainer(filterSlot().getStackInSlot(0)));
        }
    }
}
