package dev.gkissel.forgeweave.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import dev.gkissel.forgeweave.menu.ChestMenu;

/**
 * Holds a Pattern Chest or Part Chest's persistent, filtered inventory (docs/SCOPE.md M1 issue
 * #66) and opens its menu; which {@link ChestKind} it is decides the filter ({@link
 * ChestKind#accepts}) and which of the two registered {@code BlockEntityType}s it reports as (see
 * {@link ChestKind#blockEntityType}).
 *
 * <p><b>Capacity (upstream deviation, no NOTICE.md row -- ported semantics, not copied code):</b>
 * upstream's {@code TileTinkerChest} is a virtual list up to 256 items with a scaling GUI window
 * that grows/shrinks with content (upstream {@code GuiScalingChest}). Reproducing that dynamic
 * window is a lot of bespoke GUI machinery for what is fundamentally still a storage box; this
 * ships a fixed {@value #SLOTS}-slot grid (double-chest-sized: 6 rows of 9) instead -- comfortably
 * more than the handful of pattern/part item types that exist, and simple enough to reuse a plain
 * {@code AbstractContainerScreen} over vanilla's own double-chest background (see {@code
 * ChestScreen}). Expand toward the real dynamic window if 54 slots ever proves too small.
 *
 * <p>Exposes its inventory as an {@link IItemHandler} capability ({@link #registerCapabilities})
 * so any adjacent station's side-inventory panel ({@link SideInventory#find}) picks it up
 * automatically, the same as a vanilla chest already does.
 */
public class ChestBlockEntity extends BlockEntity implements MenuProvider {
    /** Double-chest-sized fixed grid; see the class javadoc's capacity note. */
    public static final int SLOTS = 54;

    private static final String TAG_INVENTORY = "inventory";

    private final ChestKind kind;
    private final FilteredContainer container;
    private final IItemHandler itemHandler;

    public ChestBlockEntity(BlockPos pos, BlockState state, ChestKind kind) {
        super(kind.blockEntityType().get(), pos, state);
        this.kind = kind;
        this.container = new FilteredContainer(kind);
        this.itemHandler = new InvWrapper(container);
        container.addListener(c -> setChanged());
    }

    public ChestKind kind() {
        return kind;
    }

    public Container container() {
        return container;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, container.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList(TAG_INVENTORY, Tag.TAG_COMPOUND), registries);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChestMenu(kind, containerId, playerInventory, container, ContainerLevelAccess.create(level, worldPosition));
    }

    /** Wires {@link Capabilities.ItemHandler#BLOCK} for both chest types; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ForgeweaveBlockEntities.PATTERN_CHEST.get(),
                (blockEntity, side) -> blockEntity.itemHandler);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ForgeweaveBlockEntities.PART_CHEST.get(),
                (blockEntity, side) -> blockEntity.itemHandler);
    }

    /**
     * A {@link SimpleContainer} that only accepts items {@link ChestKind#accepts} -- enforced here
     * so both the {@link IItemHandler} capability (automation/side-panel insertion, via {@link
     * InvWrapper#isItemValid} delegating to {@link #canPlaceItem}) and the GUI slots ({@code
     * ChestMenu}'s {@code FilteredSlot}) share one source of truth, matching upstream's single
     * {@code isItemValidForSlot} check (NOTICE.md, {@link ChestKind}).
     */
    private static final class FilteredContainer extends SimpleContainer {
        private final ChestKind kind;

        FilteredContainer(ChestKind kind) {
            super(SLOTS);
            this.kind = kind;
        }

        @Override
        public boolean canPlaceItem(int slot, @Nonnull ItemStack stack) {
            return kind.accepts(stack);
        }
    }
}
