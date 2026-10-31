package dev.gkissel.forgeweave.block;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

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
    private final ResourceHandler<ItemResource> filterCapability = TransferBridge.items(filter);

    public SearedDuctBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_DUCT.get(), pos, state);
    }

    /** The one-slot filter inventory, exposed to hoppers and to {@link SearedDuctMenu}. */
    public IItemHandler filterSlot() {
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
    public IFluidHandler fluidHandler() {
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
                (blockEntity, side) -> blockEntity.filterCapability);
    }

    /** {@link #fluidHandler()} as the 21.9 transfer API sees it, journalled on the smeltery's own tank. */
    @Nullable
    public ResourceHandler<FluidResource> fluidCapability() {
        SmelteryControllerBlockEntity core = formedCore();
        return core == null ? null : TransferBridge.fluids(filteredTank, core.tank().transferState());
    }

    /** {@link #filterSlot()} as the 21.9 transfer API sees it; what the item capability hands out. */
    public ResourceHandler<ItemResource> filterCapability() {
        return filterCapability;
    }

    /**
     * The filter slot, upstream's {@code DuctItemHandler}: one item, only a filled fluid container,
     * with the container's fluid cached until the slot changes.
     */
    private final class FilterSlot extends ItemStackHandler {
        @Nullable
        private FluidStack cached;

        private FilterSlot() {
            super(1);
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

        @Override
        protected void onContentsChanged(int slot) {
            cached = null;
            setChanged();
            if (level != null) {
                // The fluid handler this duct hands out answers differently now, and a pipe next to it
                // may be holding a BlockCapabilityCache over the old answer.
                level.invalidateCapabilities(worldPosition);
            }
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
    private final class FilteredTank implements IFluidHandler {
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
        public int fill(FluidStack resource, FluidAction action) {
            SmelteryTank smeltery = tank();
            if (smeltery == null || resource.isEmpty() || !isFluidValid(0, resource)) {
                return 0;
            }
            return smeltery.fill(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack wanted = filterFluid();
            if (wanted.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return drain(wanted.copyWithAmount(maxDrain), action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            SmelteryTank smeltery = tank();
            if (smeltery == null || resource.isEmpty() || !isFluidValid(0, resource)) {
                return FluidStack.EMPTY;
            }
            return smeltery.drain(resource, action);
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
