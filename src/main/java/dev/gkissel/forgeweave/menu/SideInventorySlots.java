package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;

import net.neoforged.neoforge.items.IItemHandler;
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
 * existing layout.
 */
public final class SideInventorySlots {
    public static final int SLOT_SIZE = 18;
    public static final int MAX_COLUMNS = 9;

    private SideInventorySlots() {}

    /**
     * @param sideInventory the neighbor's item handler, or {@code null} when constructing the
     *     client-side placeholder (its contents sync down via the normal container-slot-sync
     *     packets, same as every other slot -- the real {@link IItemHandler} only exists server-side)
     * @param slotCount how many slots to build -- 0 if there is no qualifying neighbor
     * @param x the panel's left edge, in GUI pixels from the screen's top-left
     * @param y the panel's top edge, in GUI pixels from the screen's top-left
     */
    public static List<Slot> create(@Nullable IItemHandler sideInventory, int slotCount, int x, int y) {
        List<Slot> slots = new ArrayList<>(slotCount);
        Container placeholder = sideInventory == null ? new SimpleContainer(slotCount) : null;
        for (int i = 0; i < slotCount; i++) {
            int slotX = x + (i % MAX_COLUMNS) * SLOT_SIZE;
            int slotY = y + (i / MAX_COLUMNS) * SLOT_SIZE;
            slots.add(sideInventory != null
                    ? new SlotItemHandler(sideInventory, i, slotX, slotY)
                    : new Slot(placeholder, i, slotX, slotY));
        }
        return slots;
    }

    public static int columns(int slotCount) {
        return Math.min(slotCount, MAX_COLUMNS);
    }

    public static int rows(int slotCount) {
        return slotCount == 0 ? 0 : (slotCount + MAX_COLUMNS - 1) / MAX_COLUMNS;
    }
}
