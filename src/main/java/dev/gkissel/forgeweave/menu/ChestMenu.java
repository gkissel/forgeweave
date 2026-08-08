package dev.gkissel.forgeweave.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ChestKind;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * The Pattern Chest/Part Chest menu (docs/SCOPE.md M1 issue #66): a fixed {@value
 * ChestBlockEntity#SLOTS}-slot grid (6 rows of 9, same layout math as vanilla's own double-chest
 * {@code ChestMenu}) whose slots only accept what {@link ChestKind#accepts} allows, plus the
 * player's inventory. One class for both chest kinds -- see {@link ChestKind}'s javadoc.
 */
public class ChestMenu extends AbstractContainerMenu {
    private static final int COLUMNS = 9;
    private static final int ROWS = ChestBlockEntity.SLOTS / COLUMNS;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_Y = 103 + (ROWS - 4) * SLOT_SIZE;

    public final ChestKind kind;
    private final Container container;
    private final ContainerLevelAccess access;

    /** Client-side: constructed from the open-menu packet, with a throwaway local container. */
    public ChestMenu(ChestKind kind, int containerId, Inventory playerInventory) {
        this(kind, containerId, playerInventory, new SimpleContainer(ChestBlockEntity.SLOTS), ContainerLevelAccess.NULL);
    }

    /** Server-side: constructed by {@link ChestBlockEntity} with the block's real inventory. */
    public ChestMenu(ChestKind kind, int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        super(kind == ChestKind.PATTERN ? ForgeweaveMenus.PATTERN_CHEST.get() : ForgeweaveMenus.PART_CHEST.get(), containerId);
        checkContainerSize(container, ChestBlockEntity.SLOTS);
        this.kind = kind;
        this.container = container;
        this.access = access;
        container.startOpen(playerInventory.player);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new FilteredSlot(container, col + row * COLUMNS, 8 + col * SLOT_SIZE, 18 + row * SLOT_SIZE));
            }
        }
        layoutPlayerInventorySlots(playerInventory);
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
        int chestSlots = ChestBlockEntity.SLOTS;

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
    }
}
