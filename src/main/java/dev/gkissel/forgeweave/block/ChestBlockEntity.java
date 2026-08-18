package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.menu.ChestMenu;
import dev.gkissel.forgeweave.menu.StationGroup;

/**
 * Holds a Pattern Chest or Part Chest's persistent, filtered inventory (docs/SCOPE.md M1 issue
 * #66) and opens its menu; which {@link ChestKind} it is decides the filter ({@link
 * ChestKind#accepts}) and which of the two registered {@code BlockEntityType}s it reports as (see
 * {@link ChestKind#blockEntityType}).
 *
 * <p><b>Capacity (issue #305): self-expanding, capped at 256.</b> Upstream's {@code
 * TileTinkerChest} is a virtual list up to {@value #MAX_SLOTS} items whose <em>visible</em> size
 * ({@code actualSize}) grows one slot at a time as items are added and shrinks back as they're
 * removed, so the chest always shows exactly its contents plus one free slot; the GUI is a window
 * scrolled down that list ({@code GuiScalingChest}). Issue #305 shipped this in whole 54-slot steps
 * because the GUI paged rather than scrolled; parity audit T45 (issue #476) put the scrolling window
 * back ({@code ChestMenu}/{@code ChestScreen}), so the capacity is upstream's own one-slot step
 * again. No NOTICE.md row: ported semantics, not copied code.
 *
 * <p>Exposes its inventory as an {@link IItemHandler} capability ({@link #registerCapabilities})
 * so any adjacent station's side-inventory panel ({@link SideInventory#find}) picks it up
 * automatically, the same as a vanilla chest already does -- {@link IItemHandler#getSlots()}
 * (NeoForge's {@code InvWrapper} delegates straight to {@link Container#getContainerSize()})
 * reports the chest's *current* capacity, so a neighbor's side panel only ever sees as many slots
 * as the chest currently has, exactly like it does for a vanilla chest. {@code SideInventorySlots}
 * already scrolls a panel whose neighbor has more slots than fit on screen (issue #68), so this
 * needed no changes there.
 */
public class ChestBlockEntity extends BlockEntity implements StationMenuHost {
    /** Upstream {@code TileTinkerChest#MAX_INVENTORY}: the hard cap no chest can grow past. */
    public static final int MAX_SLOTS = 256;
    /** Upstream {@code TileTinkerChest}'s starting {@code actualSize}: one free slot and nothing else. */
    private static final int MINIMUM_SLOTS = 1;

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
        // Reset to the floor first: SimpleContainer#fromTag's list is positionless (it refills
        // through #addItem, low slot to high -- see the save-compat fixture this issue adds), so
        // capacity grows back to exactly what the reloaded contents need as #setItem's own growth
        // hook fires along the way, the same way it does during normal play.
        container.capacity = MINIMUM_SLOTS;
        // #477/T46: SimpleContainer#setItem (which #fromTag's #addItem funnels every stack through)
        // clamps to getMaxStackSize() -- fine for new placements, but a save from before this ticket's
        // Pattern/Cast Chest stack-size-1 rule existed can legally hold a bigger stack (the save-compat
        // fixture this issue adds pins exactly that), and clamping on load would silently drop the
        // rest. Lifting the cap only while this load runs preserves that old data as-is; every
        // in-game placement afterwards still goes through ChestKind#maxStackSize() as normal.
        container.loading = true;
        try {
            container.fromTag(tag.getList(TAG_INVENTORY, Tag.TAG_COMPOUND), registries);
        } finally {
            container.loading = false;
        }
    }

    /**
     * Upstream {@code BlockTable#writeDataOntoItemstack} (parity audit T47, issue #478): a harvested
     * chest carries its contents on the dropped item instead of spilling them, behind {@code
     * chestsKeepInventory}. Upstream writes its own {@code "inventory"} NBT compound onto the stack;
     * the modern equivalent is the vanilla {@link DataComponents#CONTAINER} component, which the
     * chest loot tables copy off this block entity with {@code minecraft:copy_components} (see
     * {@code ForgeweaveBlockLootSubProvider}) exactly as the retextured tables already copy their
     * {@code TEXTURE}. {@link ItemContainerContents} is slot-indexed, so items come back in the same
     * slots they were left in, and it holds {@link #MAX_SLOTS} items -- precisely this chest's cap.
     *
     * <p>Only set when there is something to carry: an empty chest must stay componentless, or every
     * broken chest would refuse to stack with a freshly crafted one.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!ForgeweaveConfig.CHESTS_KEEP_INVENTORY.get() || container.isEmpty()) {
            return;
        }
        List<ItemStack> stored = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            stored.add(container.getItem(slot));
        }
        builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stored));
    }

    /** The other half of {@link #collectImplicitComponents}: placing the item back refills the chest. */
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        ItemContainerContents stored = componentInput.get(DataComponents.CONTAINER);
        if (stored != null) {
            for (int slot = 0; slot < stored.getSlots() && slot < MAX_SLOTS; slot++) {
                container.setItem(slot, stored.getStackInSlot(slot));
            }
        }
    }

    /** Upstream {@code TilePatternChest#getName}: "Cast Chest" once the Pattern Chest holds a cast (#477/T46). */
    @Override
    public Component getDisplayName() {
        if (kind == ChestKind.PATTERN && holdsACast()) {
            return Component.translatable("gui.forgeweave.cast_chest.name");
        }
        return getBlockState().getBlock().getName();
    }

    private boolean holdsACast() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (ChestKind.isCast(container.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Upstream {@code BlockToolTable#onBlockActivated} (parity audit T75, issue #506): right-clicking
     * a chest with an item in hand inserts it directly instead of opening the GUI, as long as at
     * least some of the stack fits -- upstream's own rule is "insert succeeds if the remainder is
     * empty or smaller than what was held", i.e. even a partial insert counts. Returns whether
     * anything moved, so {@link ChestBlock#useItemOn} can fall through to opening the GUI when
     * nothing did (an empty hand, or a stack the chest's filter rejects outright).
     */
    public boolean insertHeldItem(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        ItemStack remainder = ItemHandlerHelper.insertItem(itemHandler, held, false);
        if (remainder.getCount() == held.getCount()) {
            return false;
        }
        player.setItemInHand(hand, remainder);
        return true;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChestMenu(kind, containerId, playerInventory, container, ContainerLevelAccess.create(level, worldPosition));
    }

    /** A chest has no side inventory of its own, so the tab row is the whole payload (issue #78). */
    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        StationGroup.STREAM_CODEC.encode(buf, StationGroup.tabsFor(level, worldPosition));
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
     * ChestMenu}'s {@code ChestSlot}) share one source of truth, matching upstream's single
     * {@code isItemValidForSlot} check (NOTICE.md, {@link ChestKind}).
     *
     * <p>Also owns the self-expanding capacity (class javadoc, issue #305): {@link
     * #getContainerSize()} reports the current logical {@code capacity} rather than the fixed
     * {@value #MAX_SLOTS}-slot backing array, mirroring upstream's {@code
     * getSizeInventory()}/{@code actualSize} split -- so both the {@link IItemHandler} capability
     * ({@code InvWrapper#getSlots} delegates straight to this) and {@code ChestMenu}'s scroll
     * window see the same grown-or-not number.
     */
    private static final class FilteredContainer extends SimpleContainer {
        private final ChestKind kind;
        private int capacity = MINIMUM_SLOTS;
        /** See {@link #loadAdditional}: true only while a save is being decoded. */
        private boolean loading;

        FilteredContainer(ChestKind kind) {
            super(MAX_SLOTS);
            this.kind = kind;
        }

        @Override
        public boolean canPlaceItem(int slot, @Nonnull ItemStack stack) {
            return kind.accepts(stack, this, slot);
        }

        @Override
        public int getContainerSize() {
            return capacity;
        }

        /**
         * Per-kind slot stack cap (#477/T46) -- see {@link ChestKind#maxStackSize()} -- except while
         * {@link #loading}, when it must not clamp (see {@link #loadAdditional}'s comment).
         */
        @Override
        public int getMaxStackSize() {
            return loading ? super.getMaxStackSize() : kind.maxStackSize();
        }

        /**
         * Upstream {@code TileTinkerChest#setInventorySlotContents}: the chest is always exactly as
         * big as its contents plus one free slot, capped at {@value #MAX_SLOTS}.
         */
        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (stack.isEmpty()) {
                shrinkPastTrailingEmptySlots();
            } else {
                growPastTheFilledSlot(slot);
            }
        }

        /**
         * Grows until {@code slot} is inside the capacity and there is a free slot after the last
         * filled one. The loop covers issue #478's restore path ({@link #applyImplicitComponents}),
         * the only caller that writes a slot <em>index</em> straight in rather than filling
         * sequentially -- a chest broken with a lone item at slot 200 has to reach it in one go, or
         * the item it carries would come back unreachable.
         */
        private void growPastTheFilledSlot(int slot) {
            capacity = Math.max(capacity, slot + 1);
            while (capacity < MAX_SLOTS && !getItem(capacity - 1).isEmpty()) {
                capacity++;
            }
        }

        /** The other half: trailing empty slots collapse back to the single free one. */
        private void shrinkPastTrailingEmptySlots() {
            while (capacity > MINIMUM_SLOTS && getItem(capacity - 2).isEmpty()) {
                capacity--;
            }
        }
    }
}
