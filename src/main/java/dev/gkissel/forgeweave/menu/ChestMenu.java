package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;

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
 * The Pattern Chest/Part Chest menu (docs/SCOPE.md M1 issue #66): every one of the chest's {@value
 * ChestBlockEntity#MAX_SLOTS} slots, only {@value #VISIBLE_SLOTS} of them on screen at a time, plus
 * the player's inventory. Slots only accept what {@link ChestKind#accepts} allows. One class for
 * both chest kinds -- see {@link ChestKind}'s javadoc.
 *
 * <h2>Upstream's scaling chest (parity audit T45, issue #476)</h2>
 *
 * <p>Upstream {@code ContainerPatternChest} adds one slot per {@code TileTinkerChest.MAX_INVENTORY}
 * slot and lets its GUI module ({@code GuiScalingChest} over {@code GuiDynInventory}) decide which
 * of them are on screen, scrolling a {@value #COLUMNS}x{@value #ROWS} window down the chest with a
 * slider. This does the same: all {@value ChestBlockEntity#MAX_SLOTS} slots exist for the whole life
 * of the menu -- so a shift-click reaches the whole chest, not just what is drawn -- and {@link
 * #scrollTo} moves the visible window, hiding the rest.
 *
 * <p>{@link Slot#x}/{@link Slot#y} are final, so "moving" a slot means substituting a new one at the
 * same {@code slots} index, exactly as {@link SideInventorySlots#layout} already does for the side
 * panels (upstream's own {@code GuiDynInventory#updateSlots} assigns {@code slot.xPos} in place,
 * which modern Minecraft does not allow). {@link Slot#isActive} hides what is scrolled away, which
 * is upstream's client-only {@code shouldDrawSlot} -- the server never consults it, so transfers
 * still reach every slot.
 *
 * <p>Scrolling is client-side state ({@code ChestScreen}), like every other panel in this codebase:
 * nothing about which rows are drawn changes what the server will accept, so it needs no data slot
 * and no packet. The one thing the client cannot work out for itself is how far the chest has grown
 * ({@link ChestBlockEntity}'s self-expanding capacity), so that rides down as a {@link DataSlot}.
 *
 * <p>This replaces the fixed 54-slot page and the {@code &lt;}/{@code &gt;} page buttons issue #305
 * shipped, which existed only because a scrolling window looked expensive on the 1.21 slot API.
 */
public class ChestMenu extends StationMenu {
    /** Upstream {@code GuiDynInventory}: {@code (xSize 162 - slider.width 12) / slot.w 18}. */
    public static final int COLUMNS = 8;
    /** Upstream {@code GuiDynInventory}: {@code ySize 54 / slot.h 18}. */
    public static final int ROWS = 3;
    /** How many of the chest's slots are on screen at once. */
    public static final int VISIBLE_SLOTS = COLUMNS * ROWS;

    private static final int SLOT_SIZE = 18;
    /**
     * Upstream {@code GuiDynInventory#updateSlots}: {@code xOffset/yOffset} plus the 1px bevel
     * {@code generic.png}'s slot tile draws around its 16x16 socket (see {@link
     * SideInventorySlots#SLOT_INSET}).
     */
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;

    /** Upstream {@code ContainerPatternChest}: {@code addPlayerInventory(playerInventory, 8, 84)}. */
    public static final int PLAYER_INVENTORY_Y = 84;

    /** Where a scrolled-away slot parks -- upstream {@code GuiDynInventory} sends them to (0, 0) too. */
    private static final int OFF_SCREEN = 0;

    public final ChestKind kind;
    private final Container container;
    private final ContainerLevelAccess access;
    private final DataSlot capacity = DataSlot.standalone();
    private final List<ChestSlot> chestSlots = new ArrayList<>(ChestBlockEntity.MAX_SLOTS);

    /** Client-side: constructed from the open-menu packet, with a throwaway local container. */
    public ChestMenu(ChestKind kind, int containerId, Inventory playerInventory, StationGroup stationGroup) {
        this(kind, containerId, playerInventory, new SimpleContainer(ChestBlockEntity.MAX_SLOTS),
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
        this.kind = kind;
        this.container = container;
        this.access = access;
        container.startOpen(playerInventory.player);

        addDataSlot(capacity);
        capacity.set(container.getContainerSize());

        for (int i = 0; i < ChestBlockEntity.MAX_SLOTS; i++) {
            ChestSlot slot = new ChestSlot(i, slotX(i), slotY(i), i < VISIBLE_SLOTS);
            chestSlots.add(slot);
            addSlot(slot);
        }
        layoutPlayerInventorySlots(playerInventory);
    }

    /** Where the {@code index}'th slot of the visible window sits, relative to the screen's top-left. */
    public static int slotX(int windowIndex) {
        return GRID_X + (windowIndex % COLUMNS) * SLOT_SIZE;
    }

    public static int slotY(int windowIndex) {
        return GRID_Y + (windowIndex / COLUMNS) * SLOT_SIZE;
    }

    /**
     * The last row the window can be scrolled to for a chest of this capacity -- 0 while everything
     * fits. Upstream {@code GuiDynInventory#updateSlider} writes the same thing as {@code slotCount /
     * columns - rows + 1}, guarded by its {@code sliderActive} check for the divisible case.
     */
    public static int maxScrollRow(int capacity) {
        return Math.max(0, (capacity + COLUMNS - 1) / COLUMNS - ROWS);
    }

    /** The chest's current capacity ({@link ChestBlockEntity}'s self-expanding size), synced for the screen. */
    public int capacity() {
        return capacity.get();
    }

    /**
     * Client-side: show the {@value #ROWS} rows starting at {@code firstRow}, hiding the rest (see
     * the class javadoc). Called every frame by {@code ChestScreen}; slots already where they belong
     * are left alone, so a chest that is not scrolling allocates nothing.
     */
    public void scrollTo(int firstRow) {
        int first = Math.max(0, firstRow) * COLUMNS;
        int last = first + VISIBLE_SLOTS;
        int size = capacity.get();
        for (int i = 0; i < chestSlots.size(); i++) {
            ChestSlot slot = chestSlots.get(i);
            boolean visible = i >= first && i < last && i < size;
            int x = visible ? slotX(i - first) : OFF_SCREEN;
            int y = visible ? slotY(i - first) : OFF_SCREEN;
            if (slot.visible == visible && slot.x == x && slot.y == y) {
                continue;
            }
            ChestSlot replacement = new ChestSlot(i, x, y, visible);
            replacement.index = slot.index;
            slots.set(slot.index, replacement);
            chestSlots.set(i, replacement);
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
        int chestSlots = ChestBlockEntity.MAX_SLOTS;

        if (index < chestSlots) { // chest -> player inventory
            if (!moveItemStackTo(stackInSlot, chestSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, 0, chestSlots, false)) { // player inventory -> chest (filtered by ChestSlot#mayPlace)
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

    private final class ChestSlot extends Slot {
        private final boolean visible;

        ChestSlot(int index, int x, int y, boolean visible) {
            super(ChestMenu.this.container, index, x, y);
            this.visible = visible;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }

        /** Client render/click only, upstream's {@code shouldDrawSlot} -- see the class javadoc. */
        @Override
        public boolean isActive() {
            return visible;
        }
    }
}
