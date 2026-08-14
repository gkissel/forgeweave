package dev.gkissel.forgeweave.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * The seared duct's one-slot filter GUI (docs/SCOPE.md M3.4 issue #277), ported from the 1.20 clone's
 * {@code SingleItemContainerMenu} (NOTICE.md): the filter slot at (80, 20) over a 176x133 panel, with
 * the player inventory in vanilla's own position for that height.
 *
 * <p>Not a {@link StationMenu}: a duct is a smeltery component, not a station, so there is no tab row
 * and no side inventory to carry.
 */
public class SearedDuctMenu extends AbstractContainerMenu {
    /** Upstream's {@code addSlot(new SmartItemHandlerSlot(handler, 0, 80, 20))}. */
    private static final int FILTER_X = 80;
    private static final int FILTER_Y = 20;

    /** Vanilla's own layout for a 133px-tall container: rows at height - 82, hotbar at height - 24. */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 133;
    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = PANEL_HEIGHT - 82;
    private static final int HOTBAR_Y = PANEL_HEIGHT - 24;

    /** How far a player may stray from the duct before the screen closes; vanilla's own container reach. */
    private static final double MAX_DISTANCE_SQR = 64.0D;

    private static final int FILTER_SLOT_COUNT = 1;

    private final ContainerLevelAccess access;

    /**
     * Client-side: the filter is a throwaway one-slot handler that vanilla's own slot sync fills, the
     * same arrangement {@code SmelteryMenu} uses for its melt grid.
     */
    public SearedDuctMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, new ItemStackHandler(FILTER_SLOT_COUNT));
    }

    /** Server-side: built by {@code SearedDuctBlockEntity#createMenu} over the duct's real filter slot. */
    public SearedDuctMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, IItemHandler filter) {
        super(ForgeweaveMenus.SEARED_DUCT.get(), containerId);
        this.access = access;
        addSlot(new SlotItemHandler(filter, 0, FILTER_X, FILTER_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();

        if (index < FILTER_SLOT_COUNT) { // filter -> player inventory
            if (!moveItemStackTo(inSlot, FILTER_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(inSlot, 0, FILTER_SLOT_COUNT, false)) { // player inventory -> filter
            return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (inSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, inSlot);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(ForgeweaveBlocks.SEARED_DUCT.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_DISTANCE_SQR,
                true);
    }
}
