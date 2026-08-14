package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Shared side-inventory slot wiring for the three stations that expose a neighboring block's item
 * handler in a GUI side panel (docs/SCOPE.md issue #40, extended from the Crafting Station to the
 * Part Builder and Tool Station in the same issue's follow-up). Originally {@code
 * CraftingStationMenu#addSideInventorySlots}; extracted here so the other two menus reuse the exact
 * same slot-building logic instead of a third copy.
 *
 * <p>Callers add the returned slots with their own {@code addSlot} (a menu's own inherited, protected
 * method -- this helper can't call it for them) right after their station's own container slots and
 * before the player inventory, matching {@link dev.gkissel.forgeweave.menu.CraftingStationMenu}'s
 * existing layout, and keep the list so the client-side panel can lay them out (see {@code
 * client.SideInventoryPanel}).
 *
 * <p>{@link #COLUMNS} is upstream 1.12's: both {@code ContainerCraftingStation} and {@code
 * ContainerStencilTable} build their {@code ContainerSideInventory} six columns wide, and {@code
 * GuiSideInventory} sizes the drawn panel from that same number. Issue #68 fix 3: this used to be 9,
 * which made a double chest render as a 9x6 unstyled grid sprawling across the screen.
 */
public final class SideInventorySlots {
    public static final int SLOT_SIZE = 18;
    public static final int COLUMNS = 6;

    /** Upstream {@code GuiWidgetBorder}: the panel's nine-sliced frame is 7px on every side. */
    public static final int BORDER = 7;
    /** The whole panel, frame included -- upstream's {@code columns * slot.w + border.w * 2}. */
    public static final int PANEL_WIDTH = COLUMNS * SLOT_SIZE + BORDER * 2;

    /**
     * Where the first slot sits relative to the panel's top-left corner. Upstream {@code
     * GuiSideInventory#updateSlots} is {@code slot.xPos = border.w + x + 1}: the extra pixel is
     * there because {@code generic.png}'s 18x18 slot tile is a 1px bevel drawn <em>around</em> a
     * 16x16 socket interior, so an item drawn at the tile's own corner sits one pixel up and left
     * of the hole it belongs in (issue #79). {@code client.SideInventoryPanel} draws the tiles at
     * {@code slot - SLOT_INSET + BORDER}, i.e. one pixel out from the slot, to match.
     */
    public static final int SLOT_INSET = BORDER + 1;

    /**
     * The first slot's x for a panel on the <em>left</em> of its station, which is where upstream
     * puts the Crafting Station's and Part Builder's ({@code GuiSideInventory}'s two-arg
     * constructor passes {@code rightSide = false}). Upstream's {@code slot.xPos -= this.xSize}
     * lands the panel's right edge flush on the station panel's left edge, so the offset from the
     * station's own top-left is negative.
     */
    public static final int LEFT_SLOT_X = SLOT_INSET - PANEL_WIDTH;

    /** Upstream sets {@code yOffset = 0} for a side inventory, putting its first slot row at the frame. */
    public static final int SLOT_Y = SLOT_INSET;

    /**
     * The first slot's x for a panel on the <em>right</em> of its station -- where upstream puts the
     * Stencil Table's pattern-chest side inventory ({@code ContainerStencilTable}'s {@code
     * DynamicChestInventory}, {@code GuiSideInventory}'s {@code right = true} branch adding {@code
     * parent.realWidth} to every slot's x). That lands the panel's left edge flush on the station
     * panel's right edge, so the offset is the station's own panel width plus the same {@link
     * #SLOT_INSET} {@link #LEFT_SLOT_X} uses (issue #306).
     */
    public static int rightSlotX(int stationPanelWidth) {
        return stationPanelWidth + SLOT_INSET;
    }

    private SideInventorySlots() {}

    /**
     * @param sideInventory the neighbor's item handler, or {@code null} when constructing the
     *     client-side placeholder (its contents sync down via the normal container-slot-sync
     *     packets, same as every other slot -- the real {@link IItemHandler} only exists server-side)
     * @param slotCount how many slots to build -- 0 if there is no qualifying neighbor
     * @param x the panel's first slot's left edge, in GUI pixels from the screen's top-left
     * @param y the panel's first slot's top edge, in GUI pixels from the screen's top-left
     */
    public static List<SideSlot> create(@Nullable IItemHandler sideInventory, int slotCount, int x, int y) {
        return create(sideInventory, slotCount, x, y, true);
    }

    /**
     * @param visible {@code false} to build the slots hidden: they still exist for shift-click
     *     transfers, but nothing draws or clicks them. The Part Builder's pattern-chest sidebar
     *     (issue #78) uses this -- upstream's {@code GuiPartBuilder} likewise keeps the chest's slots
     *     in its container while its {@code drawSlot}/{@code isMouseOverSlot} pair hides them behind
     *     {@code GuiButtonsPartCrafter}'s buttons.
     */
    public static List<SideSlot> create(@Nullable IItemHandler sideInventory, int slotCount, int x, int y,
            boolean visible) {
        IItemHandler handler = sideInventory != null ? sideInventory : new ItemStackHandler(slotCount);
        List<SideSlot> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new SideSlot(handler, i, x + (i % COLUMNS) * SLOT_SIZE, y + (i / COLUMNS) * SLOT_SIZE, visible));
        }
        return slots;
    }

    public static int rows(int slotCount) {
        return slotCount == 0 ? 0 : (slotCount + COLUMNS - 1) / COLUMNS;
    }

    /**
     * Moves the panel's slots to show only rows {@code [firstVisible, lastVisible)}, hiding the rest
     * -- what upstream's {@code GuiSideInventory#updateSlots}/{@code shouldDrawSlot} pair does when
     * its slider moves. Called by the client-side panel every frame; a slot that is already where it
     * should be is left alone, so a panel that isn't scrolling allocates nothing.
     *
     * <p>{@link Slot#x}/{@link Slot#y} are final, so "moving" a slot means substituting a new one at
     * the same menu index -- the same substitution {@code ToolStationMenu#applyLayout} already does
     * when a tab change moves its input slots.
     */
    public static void layout(AbstractContainerMenu menu, List<SideSlot> slots,
            int firstVisible, int lastVisible, int x, int y) {
        for (int i = 0; i < slots.size(); i++) {
            SideSlot slot = slots.get(i);
            boolean visible = i >= firstVisible && i < lastVisible;
            int offset = i - firstVisible;
            int targetX = visible ? x + (offset % COLUMNS) * SLOT_SIZE : slot.x;
            int targetY = visible ? y + (offset / COLUMNS) * SLOT_SIZE : slot.y;
            if (slot.visible == visible && slot.x == targetX && slot.y == targetY) {
                continue;
            }
            SideSlot replacement = new SideSlot(slot.getItemHandler(), slot.getSlotIndex(), targetX, targetY, visible);
            // Casts because SlotItemHandler shadows Slot's public menu-position `index` with a
            // protected field of its own holding the *item handler* index; the two are unrelated.
            int menuIndex = ((Slot) slot).index;
            ((Slot) replacement).index = menuIndex;
            menu.slots.set(menuIndex, replacement);
            slots.set(i, replacement);
        }
    }

    /**
     * A side-panel slot the client-side panel can hide when it scrolls out of view. {@link
     * Slot#isActive} is a client render/click concern only -- the server never consults it -- so
     * shift-click transfers still reach scrolled-away slots, exactly as upstream's client-only
     * {@code shouldDrawSlot} leaves its container's transfers alone.
     */
    public static final class SideSlot extends SlotItemHandler {
        private final boolean visible;

        SideSlot(IItemHandler handler, int index, int x, int y) {
            this(handler, index, x, y, true);
        }

        SideSlot(IItemHandler handler, int index, int x, int y, boolean visible) {
            super(handler, index, x, y);
            this.visible = visible;
        }

        @Override
        public boolean isActive() {
            return visible;
        }
    }
}
