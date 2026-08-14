package dev.gkissel.forgeweave.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ChestKind;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * The Pattern Chest/Part Chest menu (docs/SCOPE.md M1 issue #66; paging added by issue #305): a
 * {@value ChestBlockEntity#PAGE_SIZE}-slot grid (6 rows of 9, same layout math as vanilla's own
 * double-chest {@code ChestMenu}) whose slots only accept what {@link ChestKind#accepts} allows,
 * plus the player's inventory. One class for both chest kinds -- see {@link ChestKind}'s javadoc.
 *
 * <h2>Paging (issue #305)</h2>
 *
 * <p>The container can grow past one page (up to {@link ChestBlockEntity#MAX_SLOTS}, see {@link
 * ChestBlockEntity}'s capacity note), but the menu only ever holds {@value ChestBlockEntity#PAGE_SIZE}
 * real {@link Slot} objects at the fixed grid coordinates the screen has always drawn. A page change
 * keeps their coordinates and only substitutes a fresh {@link FilteredSlot} at a different
 * container-relative index -- {@link Slot}'s own index is a {@code private final} field with no
 * setter, so unlike {@code ToolStationMenu#applyLayout}'s "same index, new coordinates" swap (its
 * tabs never change which container the slots read from, only where they're drawn), this is "same
 * coordinates, new index"; {@link #applyPage} explains the mechanics. Which page is selected is a
 * server-authoritative {@link DataSlot} set from {@link #clickMenuButton} -- the vanilla
 * stonecutter/loom mechanism {@code StencilTableMenu} and {@code ToolStationMenu} already use for an
 * equivalent "which of several things is this menu showing" choice -- so a dedicated server stays
 * authoritative and no custom network payload is needed.
 */
public class ChestMenu extends StationMenu {
    private static final int COLUMNS = 9;
    private static final int ROWS = ChestBlockEntity.PAGE_SIZE / COLUMNS;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_Y = 103 + (ROWS - 4) * SLOT_SIZE;

    public static final int BUTTON_PREVIOUS_PAGE = 0;
    public static final int BUTTON_NEXT_PAGE = 1;

    public final ChestKind kind;
    private final Container container;
    private final ContainerLevelAccess access;
    private final DataSlot currentPage = DataSlot.standalone();
    private final DataSlot capacity = DataSlot.standalone();

    /** Client-side: constructed from the open-menu packet, with a throwaway local container. */
    public ChestMenu(ChestKind kind, int containerId, Inventory playerInventory, StationGroup stationGroup) {
        // Sized to the full backing array, not just one page: a page change reassigns each Slot's
        // index to wherever the real (server-side) container's window has moved to, and the client's
        // own slot-sync packets write through that same index into this placeholder container.
        this(kind, containerId, playerInventory, new SimpleContainer(ChestBlockEntity.BACKING_SLOTS),
                ContainerLevelAccess.NULL, stationGroup);
    }

    /** Server-side: constructed by {@link ChestBlockEntity} with the block's real inventory. */
    public ChestMenu(ChestKind kind, int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        this(kind, containerId, playerInventory, container, access, groupAt(access));
    }

    private ChestMenu(ChestKind kind, int containerId, Inventory playerInventory, Container container,
            ContainerLevelAccess access, StationGroup stationGroup) {
        super(kind == ChestKind.PATTERN ? ForgeweaveMenus.PATTERN_CHEST.get() : ForgeweaveMenus.PART_CHEST.get(),
                containerId, stationGroup);
        // Capacity only ever shrinks to ChestBlockEntity.PAGE_SIZE, never below it, so this stays a
        // valid sanity check regardless of how much the chest has since grown.
        checkContainerSize(container, ChestBlockEntity.PAGE_SIZE);
        this.kind = kind;
        this.container = container;
        this.access = access;
        container.startOpen(playerInventory.player);

        addDataSlot(currentPage);
        addDataSlot(capacity);
        capacity.set(container.getContainerSize());

        for (int i = 0; i < ChestBlockEntity.PAGE_SIZE; i++) {
            addSlot(new FilteredSlot(container, i, chestSlotX(i), chestSlotY(i)));
        }
        layoutPlayerInventorySlots(playerInventory);
    }

    private static int chestSlotX(int localIndex) {
        return 8 + (localIndex % COLUMNS) * SLOT_SIZE;
    }

    private static int chestSlotY(int localIndex) {
        return 18 + (localIndex / COLUMNS) * SLOT_SIZE;
    }

    /** Which page (0-based) is currently displayed -- the screen reads this for its page label. */
    public int currentPage() {
        return currentPage.get();
    }

    /** The chest's current capacity, synced for the screen's page label and "next" button. */
    public int capacity() {
        return capacity.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (super.clickMenuButton(player, id)) {
            return true; // a station-group tab (issue #78), handled by StationMenu
        }
        if (id == BUTTON_PREVIOUS_PAGE && currentPage.get() > 0) {
            currentPage.set(currentPage.get() - 1);
            applyPage();
            return true;
        }
        if (id == BUTTON_NEXT_PAGE && (currentPage.get() + 1) * ChestBlockEntity.PAGE_SIZE < container.getContainerSize()) {
            currentPage.set(currentPage.get() + 1);
            applyPage();
            return true;
        }
        return false;
    }

    /** Client side: a page (or capacity) data-slot update arrived, so the window follows it. */
    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        applyPage();
    }

    /**
     * Points every chest slot at the current page's window of the container (see the class javadoc).
     *
     * <p>{@link Slot}'s container-relative index ({@code Slot#getContainerSlot}) is set once, at
     * construction, from a {@code private final} field -- unlike {@link Slot#x}/{@code y}, there is
     * no field to mutate in place. So a page change substitutes a fresh {@link FilteredSlot} at the
     * same {@code slots} list position instead, exactly {@code SideInventorySlots#layout}'s
     * "the slot moved, the object doesn't have to" comment already documents for repositioning --
     * here it's the container index rather than the coordinates that changes, but the substitution
     * technique is the same.
     */
    private void applyPage() {
        int base = currentPage.get() * ChestBlockEntity.PAGE_SIZE;
        for (int i = 0; i < ChestBlockEntity.PAGE_SIZE; i++) {
            FilteredSlot slot = new FilteredSlot(container, base + i, chestSlotX(i), chestSlotY(i));
            slot.index = i;
            slots.set(i, slot);
        }
    }

    @Override
    public void broadcastChanges() {
        capacity.set(container.getContainerSize());
        super.broadcastChanges();
    }

    private void layoutPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * SLOT_SIZE, PLAYER_INVENTORY_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * SLOT_SIZE, PLAYER_INVENTORY_Y + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();
        // The current page's window only -- a shift-click can't reach a page that isn't displayed,
        // same limitation upstream's own dynamic-window GUI has (only the visible slots are ever
        // menu slots there too).
        int chestSlots = ChestBlockEntity.PAGE_SIZE;

        if (index < chestSlots) { // chest -> player inventory
            if (!moveItemStackTo(stackInSlot, chestSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, 0, chestSlots, false)) { // player inventory -> chest (filtered by FilteredSlot#mayPlace)
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stackInSlot.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, kind == ChestKind.PATTERN ? ForgeweaveBlocks.PATTERN_CHEST.get() : ForgeweaveBlocks.PART_CHEST.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private final class FilteredSlot extends Slot {
        FilteredSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return kind.accepts(stack);
        }

        /**
         * The last page can be short ({@value ChestBlockEntity#MAX_SLOTS} isn't a multiple of
         * {@value ChestBlockEntity#PAGE_SIZE}): once a page change substitutes this slot for one
         * whose container-relative index ({@link Slot#getContainerSlot}, not {@link Slot#index} --
         * that field is only this slot's position in the menu's own list) sits past the real cap,
         * it's neither drawn nor clickable -- same "the tab doesn't use this one" pattern {@code
         * ToolStationMenu#inputSlot} already uses for its own always-present-but-not-always-active
         * slots.
         */
        @Override
        public boolean isActive() {
            return getContainerSlot() < ChestBlockEntity.MAX_SLOTS;
        }
    }
}
