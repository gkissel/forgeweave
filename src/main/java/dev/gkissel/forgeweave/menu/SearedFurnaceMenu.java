package dev.gkissel.forgeweave.menu;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.block.SearedFurnaceBlockEntity;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * The seared furnace controller's menu (issue #442), ported from upstream 1.12's {@code
 * ContainerSearedFurnace}/{@code ContainerSearedFurnaceSideInventory} (NOTICE.md).
 *
 * <p>Same shape as {@link SmelteryMenu}: a three-column side grid hanging off the panel's left
 * edge, the player inventory at (8, 84), and everything the screen draws beyond the item stacks
 * (heat, fuel) read off the block entity's own sync rather than carried by the menu. The grid
 * geometry is {@link SmelteryMenu}'s -- upstream builds both side inventories on the same
 * {@code GuiSideInventory} with three columns -- so it is reused rather than copied. The slots
 * themselves differ: a furnace slot holds up to {@link SearedFurnaceBlockEntity#MAX_STACK} of
 * anything ({@code SearedFurnaceSlot#isItemValid} is {@code true}), and shift-click moves stacks
 * rather than spreading one item per slot.
 */
public class SearedFurnaceMenu extends StationMenu {
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final double MAX_DISTANCE_SQR = 64.0D;

    private final ContainerLevelAccess access;
    private final BlockPos corePos;
    private final Container inventory;
    private int scrollRow;

    /** Client-side, from the open-menu packet: the controller's position and how many slots to build. */
    public SearedFurnaceMenu(int containerId, Inventory playerInventory, BlockPos corePos, int slots) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, corePos, new SimpleContainer(slots));
    }

    /** Server-side, over the real inventory. */
    public SearedFurnaceMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos corePos,
            Container inventory) {
        super(ForgeweaveMenus.SEARED_FURNACE.get(), containerId, StationGroup.EMPTY);
        this.access = access;
        this.corePos = corePos;
        this.inventory = inventory;
        int slots = inventory.getContainerSize();
        for (int index = 0; index < slots; index++) {
            addSlot(new FurnaceSlot(inventory, index, SmelteryMenu.meltSlotX(index, slots), SmelteryMenu.meltSlotY(index)));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    /** Upstream {@code SearedFurnaceSlot}: anything goes in, sixteen at most, hidden while scrolled out. */
    private final class FurnaceSlot extends Slot {
        FurnaceSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return SearedFurnaceBlockEntity.MAX_STACK;
        }

        @Override
        public boolean isActive() {
            int row = getSlotIndex() / SmelteryMenu.MELT_COLUMNS;
            return row >= scrollRow && row < scrollRow + SmelteryMenu.visibleMeltRows(slotCount());
        }
    }

    /** Scrolls the grid by whole rows, client-side only -- see {@link SmelteryMenu#setScrollRow}. */
    public void setScrollRow(int row) {
        int clamped = Math.clamp(row, 0, SmelteryMenu.meltScrollRows(slotCount()));
        if (clamped == scrollRow) {
            return;
        }
        scrollRow = clamped;
        for (int index = 0; index < slotCount(); index++) {
            FurnaceSlot replacement = new FurnaceSlot(inventory, index,
                    SmelteryMenu.meltSlotX(index, slotCount()), SmelteryMenu.meltSlotY(index) - scrollRow * SmelteryMenu.SLOT_SIZE);
            replacement.index = index;
            slots.set(index, replacement);
        }
    }

    public int scrollRow() {
        return scrollRow;
    }

    /** How many furnace slots this menu has: {@code 9 + 3 * interior volume}. */
    public int slotCount() {
        return inventory.getContainerSize();
    }

    public BlockPos corePos() {
        return corePos;
    }

    @Nullable
    public SearedFurnaceBlockEntity furnace(@Nullable Level level) {
        return level != null && level.getBlockEntity(corePos) instanceof SearedFurnaceBlockEntity furnace ? furnace : null;
    }

    public SearedFurnaceBlockEntity.Progress progressState(@Nullable Level level, int index) {
        SearedFurnaceBlockEntity furnace = furnace(level);
        return furnace == null ? SearedFurnaceBlockEntity.Progress.NONE : furnace.progressState(index);
    }

    public float progress(@Nullable Level level, int index) {
        SearedFurnaceBlockEntity furnace = furnace(level);
        return furnace == null ? 0f : furnace.progress(index);
    }

    /** Upstream {@code getFuelPercentage}: the flame's fill. */
    public float fuelPercentage(@Nullable Level level) {
        SearedFurnaceBlockEntity furnace = furnace(level);
        return furnace == null ? 0f : furnace.fuelPercentage();
    }

    /** The fuel liquid the gauge draws: the feeding tank's live contents, same deviation as {@link SmelteryMenu#fuel}. */
    public FluidStack fuel(@Nullable Level level) {
        SearedFurnaceBlockEntity furnace = furnace(level);
        return furnace == null ? FluidStack.EMPTY : furnace.fuelDisplayFluid();
    }

    /** The registered fuel for what {@link #fuel} holds, or empty when it cannot burn. */
    public Optional<SmelteryFuel> loadedFuel(@Nullable Level level) {
        FluidStack fuel = fuel(level);
        return level == null || fuel.isEmpty() ? Optional.empty() : SmelteryFuel.find(level.registryAccess(), fuel.getFluid());
    }

    /** The burn's temperature, else the loaded fuel's, else 0 -- see {@link SmelteryMenu#smelteryTemperature}. */
    public int temperature(@Nullable Level level) {
        SearedFurnaceBlockEntity furnace = furnace(level);
        int burning = furnace == null ? 0 : furnace.fuelTemperatureForDisplay();
        return burning > 0 ? burning : loadedFuel(level).map(SmelteryFuel::temperature).orElse(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        int gridEnd = slotCount();
        if (index < gridEnd) {
            if (!moveItemStackTo(inSlot, gridEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(inSlot, 0, gridEnd, false)) {
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

    /** Closes with distance, a broken controller, or -- upstream {@code GuiSearedFurnace#updateScreen} -- an unformed structure. */
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> {
            SearedFurnaceBlockEntity furnace = furnace(level);
            return furnace != null && furnace.isFormed()
                    && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_DISTANCE_SQR;
        }, true);
    }
}
