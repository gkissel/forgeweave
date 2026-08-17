package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * The smeltery controller's menu (docs/SCOPE.md M2 issue #101), ported from upstream 1.12's
 * {@code ContainerSmeltery} (NOTICE.md).
 *
 * <h2>Where the displayed state comes from</h2>
 *
 * <p>Not from this menu. The smeltery's tank is a list of fluids with amounts, and vanilla's menu
 * sync carries item stacks and {@code int} data slots -- neither can express it. Upstream's own
 * screen has the same problem and solves it the same way: it holds the tile entity and reads
 * {@code smeltery.getTank()} straight out of it, letting the block entity's own sync carry the
 * contents. {@link SmelteryControllerBlockEntity#getUpdateTag} already ships tank and structure to
 * every watching client (issue #95 wired that for this issue), so this menu carries only the
 * controller's {@link BlockPos} and resolves the block entity on both sides from it.
 *
 * <p>That keeps the client strictly a reader. The one thing a player can change from this screen --
 * which fluid sits at the bottom and therefore drains -- goes back as a vanilla container button
 * click ({@link #clickMenuButton}), is range-checked against the server's own tank, and comes back
 * as a block update. Nothing is applied client-side first.
 *
 * <h2>The melting slots</h2>
 *
 * <p>The melting slots (issue #96) are one per interior block, laid out the way upstream lays them
 * out: a three-column grid in a connected module hanging off the <em>left</em> edge of the panel,
 * sized to the smeltery ({@link #visibleMeltRows}, issue #408). The geometry lives here rather than
 * in the screen because the server lays these slots out too, and both sides have to agree to the
 * pixel.
 */
public class SmelteryMenu extends StationMenu {
    /** Player inventory origin, upstream {@code ContainerSmeltery}'s {@code addPlayerInventory(inv, 8, 84)}. */
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    /** The panel art's own height, upstream's 176x166 {@code smeltery.png}. */
    public static final int PANEL_HEIGHT = 166;
    /** Upstream {@code GuiSideInventory#updatePosition}'s {@code calcCappedYSize(parentSizeY - 10)}. */
    public static final int PANEL_MARGIN = 10;

    /** How far a player may stray from the core before the screen closes; vanilla's own container reach. */
    private static final double MAX_DISTANCE_SQR = 64.0D;

    /**
     * Melt-grid geometry, upstream {@code GuiSmelterySideInventory} over {@code GuiSideInventory}: a
     * three-column grid of 22px cells -- a 4px heat bar then an 18px slot -- inside a 7px border,
     * hanging off the left edge of the panel (see {@link #gridX}).
     */
    public static final int MELT_COLUMNS = 3;
    public static final int CELL_WIDTH = 22;
    public static final int HEAT_BAR_WIDTH = 4;
    public static final int SLOT_SIZE = 18;
    public static final int GRID_BORDER = 7;
    /**
     * Upstream sets {@code yOffset = 0} for the smeltery's side inventory, so the grid's frame starts
     * level with the panel's own top edge.
     */
    public static final int GRID_Y = 0;
    /**
     * How far a {@code connected} module laps over its parent's edge, upstream
     * {@code GuiSideInventory#updatePosition}: {@code xOffset = (border.w - 1) * (right ? -1 : 1)},
     * i.e. the two frames share a pixel column so they read as one window.
     */
    public static final int GRID_OVERLAP = GRID_BORDER - 1;
    /**
     * The most rows the grid ever draws, upstream {@code GuiSideInventory#calcCappedYSize}: whole
     * rows are shed until the framed grid fits in the parent's height less {@value #PANEL_MARGIN}px.
     * Seven, for the 166px panel.
     */
    public static final int MELT_MAX_ROWS = (PANEL_HEIGHT - PANEL_MARGIN - GRID_BORDER * 2) / SLOT_SIZE;

    private final ContainerLevelAccess access;
    private final BlockPos corePos;
    private final Container meltingInventory;
    /** Index of this menu's first melt slot; the grid is added before the player inventory. */
    private final int firstMeltSlot;
    /** Client-only scroll offset in whole rows; see {@link MeltSlot}. */
    private int scrollRow;

    /**
     * Client-side: built from the open-menu packet, which carries the controller's position and how
     * many melting slots to build -- the client must add exactly as many {@link Slot}s as the server
     * did, in the same order, or every index after the grid is off by the difference. The throwaway
     * container behind them is filled by vanilla's own slot sync.
     */
    public SmelteryMenu(int containerId, Inventory playerInventory, BlockPos corePos, int meltSlots) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, corePos, new SimpleContainer(meltSlots));
    }

    /** Server-side: built by {@link SmelteryControllerBlockEntity#createMenu} over the real melt inventory. */
    public SmelteryMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos corePos,
            Container meltingInventory) {
        // A smeltery core is not a station block, so it never has a station-group tab row; upstream's
        // smeltery has none either. Passing EMPTY rather than resolving one keeps the base class's
        // tab-button range reserved but unused.
        super(ForgeweaveMenus.SMELTERY.get(), containerId, StationGroup.EMPTY);
        this.access = access;
        this.corePos = corePos;
        this.meltingInventory = meltingInventory;
        this.firstMeltSlot = 0;
        layoutMeltSlots();
        layoutPlayerInventorySlots(playerInventory);
    }

    /**
     * The line the screen draws over the melt grid while the smeltery family is switched off (the
     * content-family toggles ticket), or {@code null} while it is on.
     *
     * <p>On the menu rather than in the screen because the answer is the server's: {@code SERVER}
     * configs are synced to the client on login, so both sides read the same value, and asking it
     * here is what lets a GameTest assert on it without a client.
     */
    @Nullable
    public Component disabledNotice() {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.SMELTERY)
                ? null
                : Component.translatable("gui.forgeweave.smeltery.disabled");
    }

    /**
     * One slot per interior block, in the same order the controller stores them.
     *
     * <p>All of them are added, not just the visible three rows: the server validates clicks against
     * slot indices, so a scrolled-out slot still has to exist. {@link MeltSlot#isActive} is what
     * hides the ones outside the scroll window, which is vanilla's own hook for exactly this -- an
     * inactive slot is neither drawn nor hit-tested ({@code AbstractContainerScreen#findSlot}).
     */
    private void layoutMeltSlots() {
        for (int index = 0; index < meltingInventory.getContainerSize(); index++) {
            addSlot(new MeltSlot(meltingInventory, index, meltSlotX(index, meltSlotCount()), meltSlotY(index)));
        }
    }

    /** Slot {@code index}'s x, relative to the panel: the cell's heat bar, then a 1px socket bevel. */
    public static int meltSlotX(int index, int slotCount) {
        return gridX(slotCount) + GRID_BORDER + (index % MELT_COLUMNS) * CELL_WIDTH + HEAT_BAR_WIDTH + 1;
    }

    /** Slot {@code index}'s y before scrolling; the screen shifts by whole rows. */
    public static int meltSlotY(int index) {
        return GRID_Y + GRID_BORDER + (index / MELT_COLUMNS) * SLOT_SIZE + 1;
    }

    /** Rows the smeltery's slots need, upstream {@code GuiSideInventory#getTotalRows}. */
    public static int meltRows(int slotCount) {
        return (slotCount + MELT_COLUMNS - 1) / MELT_COLUMNS;
    }

    /**
     * Rows the grid actually draws (issue #408): {@link #meltRows} capped by upstream's
     * {@code calcCappedYSize}, so the grid is sized to the smeltery exactly as upstream's side
     * inventory is -- one row for a one-block smeltery, seven and a slider for a big one.
     */
    public static int visibleMeltRows(int slotCount) {
        return Math.clamp(meltRows(slotCount), 1, MELT_MAX_ROWS);
    }

    /**
     * Rows that do not fit in the drawn grid, i.e. the slider's range -- upstream enables its slider
     * exactly when this is non-zero ({@code GuiSideInventory#updatePosition}).
     */
    public static int meltScrollRows(int slotCount) {
        return Math.max(0, meltRows(slotCount) - visibleMeltRows(slotCount));
    }

    /** Upstream's {@code xSize = columns * slot.w + border.w * 2}, plus the slider's width when one is shown. */
    public static int gridWidth(int slotCount) {
        return MELT_COLUMNS * CELL_WIDTH + GRID_BORDER * 2
                + (meltScrollRows(slotCount) > 0 ? SideInventorySlots.SLIDER_WIDTH : 0);
    }

    /** Upstream's {@code calcCappedYSize}: the drawn rows inside {@code GuiWidgetBorder}'s frame. */
    public static int gridHeight(int slotCount) {
        return visibleMeltRows(slotCount) * SLOT_SIZE + GRID_BORDER * 2;
    }

    /**
     * Where the grid's frame starts, relative to the smeltery panel's own top-left -- negative,
     * because the grid hangs off the panel's <em>left</em> edge.
     *
     * <p>Upstream {@code GuiSmelterySideInventory} builds itself {@code (rightSide = false,
     * connected = true)}, and {@code GuiSideInventory}'s non-{@code right} branch is
     * {@code guiLeft = parentX - xSize} followed by {@code guiLeft += xOffset} with
     * {@code xOffset = border.w - 1}. So the frame's right edge laps {@value #GRID_OVERLAP}px over
     * the panel, and when the slider appears the whole module -- slots included -- shifts a slider's
     * width further left, which is what puts the slider in the column next to the panel.
     */
    public static int gridX(int slotCount) {
        return GRID_OVERLAP - gridWidth(slotCount);
    }

    /**
     * A melting slot: holds one item, only something the smeltery can melt, and only while its row is
     * inside the scroll window.
     *
     * <p>{@code scrollRow} is client-only state -- the server's copy of the menu never scrolls and
     * never consults it when validating a click, which is deliberate: hiding a slot is a rendering
     * concern, and every melt slot is one the player is legitimately allowed to use either way.
     */
    private final class MeltSlot extends Slot {
        MeltSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return meltingInventory.canPlaceItem(getSlotIndex(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1; // one item per interior block, upstream's own rule
        }

        @Override
        public boolean isActive() {
            int row = getSlotIndex() / MELT_COLUMNS;
            return row >= scrollRow && row < scrollRow + visibleMeltRows(meltSlotCount());
        }
    }

    /**
     * Scrolls the melt grid by whole rows (client-side only; see {@link MeltSlot}).
     *
     * <p>{@link Slot#x}/{@link Slot#y} are final, so "moving" a slot means substituting a new one at
     * the same menu index -- the same substitution {@code SideInventorySlots#layout} does for the
     * station side panels, and {@code ToolStationMenu#applyLayout} for its tab changes.
     */
    public void setScrollRow(int row) {
        int clamped = Math.clamp(row, 0, meltScrollRows(meltSlotCount()));
        if (clamped == scrollRow) {
            return;
        }
        scrollRow = clamped;
        for (int index = 0; index < meltSlotCount(); index++) {
            int menuIndex = firstMeltSlot + index;
            MeltSlot replacement = new MeltSlot(meltingInventory, index,
                    meltSlotX(index, meltSlotCount()), meltSlotY(index) - scrollRow * SLOT_SIZE);
            replacement.index = menuIndex;
            slots.set(menuIndex, replacement);
        }
    }

    public int scrollRow() {
        return scrollRow;
    }

    /** How many melting slots this smeltery has -- one per interior block, upstream's own rule. */
    public int meltSlotCount() {
        return meltingInventory.getContainerSize();
    }

    /** The menu index of melt slot {@code index}, for the screen's heat-bar and hit-test maths. */
    public Slot meltSlot(int index) {
        return slots.get(firstMeltSlot + index);
    }

    private void layoutPlayerInventorySlots(Inventory playerInventory) {
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

    /** The controller this screen belongs to. */
    public BlockPos corePos() {
        return corePos;
    }

    /**
     * The controller block entity, resolved from {@code level}. Both sides call this: the server
     * against its own world, the screen against the client copy the block-entity sync maintains.
     *
     * @return {@code null} when the chunk is gone or the core has been broken out from under the
     *         open screen
     */
    @Nullable
    public SmelteryControllerBlockEntity core(@Nullable Level level) {
        return level != null && level.getBlockEntity(corePos) instanceof SmelteryControllerBlockEntity core
                ? core
                : null;
    }

    /** The melt, bottom fluid first; empty when the core is gone. Index 0 is what a drain pours. */
    public List<FluidStack> fluids(@Nullable Level level) {
        SmelteryControllerBlockEntity core = core(level);
        return core == null ? List.of() : core.tank().fluids();
    }

    /**
     * What is currently melting, in slot order, read off the block entity the same way the fluid
     * column is. The menu's own slots carry the items too, but their <em>progress</em> only exists on
     * the block entity, so the grid reads both from one place.
     */
    public List<ItemStack> meltingItems(@Nullable Level level) {
        SmelteryControllerBlockEntity core = core(level);
        return core == null ? List.of() : core.meltingItems();
    }

    /** How far along melt slot {@code index} is, 0 to 1; 0 when the core is gone. */
    public float meltProgress(@Nullable Level level, int index) {
        SmelteryControllerBlockEntity core = core(level);
        return core == null ? 0f : core.meltProgress(index);
    }

    /** Whether melt slot {@code index} finished but the tank had no room for the result (#290); {@code false} when the core is gone. */
    public boolean meltStalled(@Nullable Level level, int index) {
        SmelteryControllerBlockEntity core = core(level);
        return core != null && core.meltStalled(index);
    }

    /** Total capacity of the melt, which scales with the interior; 0 when the core is gone. */
    public int capacity(@Nullable Level level) {
        SmelteryControllerBlockEntity core = core(level);
        return core == null ? 0 : core.tank().getCapacity();
    }

    /**
     * The fuel liquid the gauge draws, upstream's {@code TileSmeltery#getFuelDisplay}.
     *
     * <p>#131's follow-up: reads {@code SmelteryControllerBlockEntity#fuelDisplayFluid()}, a synced
     * snapshot of the wall tank's real, live contents. This is a deliberate parity deviation from
     * upstream's own {@code getFuelDisplay}, which zeroes the displayed amount for the entire
     * duration of an active burn (its {@code hasFuel()} branch) and only shows the real tank level
     * between burns -- in practice an almost-always-empty gauge while a smeltery is actually melting
     * something, which is not the "shows the lava fill" behaviour this follow-up exists to restore.
     * Showing the tank's true level at all times is simpler and matches what a player expects to see.
     */
    public FluidStack fuel(@Nullable Level level) {
        SmelteryControllerBlockEntity core = core(level);
        return core == null ? FluidStack.EMPTY : core.fuelDisplayFluid();
    }

    /** Capacity the fuel gauge is drawn against: a wall tank's real capacity ({@link SearedTankBlockEntity#CAPACITY}); see {@link #fuel}. */
    public int fuelCapacity(@Nullable Level level) {
        return SearedTankBlockEntity.CAPACITY;
    }

    /**
     * The smeltery's working heat (#131), or 0 when nothing is burning and no fuel is in reach.
     *
     * <p>Read off the block entity's synced copy: {@code fuel_burn_ticks} and
     * {@code fuel_temperature} both ride {@code getUpdateTag}, so unlike the fuel liquid this is
     * genuinely known on the client.
     */
    public int fuelTemperature(@Nullable Level level) {
        SmelteryControllerBlockEntity core = core(level);
        return core == null ? 0 : core.fuelTemperatureForDisplay();
    }

    /**
     * The registered fuel for whatever the wall tank in {@link #fuel} holds, or empty when that fluid
     * cannot be burned -- upstream's {@code TinkerRegistry.isSmelteryFuel} check in
     * {@code GuiHeatingStructureFuelTank#drawFuelTooltip} (#377).
     *
     * <p>Answerable on the client without any new packet: {@code SmelteryFuel} is a datapack registry
     * registered with a network codec, so the server ships the whole fuel list at login (see
     * {@code Forgeweave#registerDataPackRegistries}), and {@link #fuel} is already synced.
     */
    public Optional<SmelteryFuel> loadedFuel(@Nullable Level level) {
        FluidStack fuel = fuel(level);
        return level == null || fuel.isEmpty()
                ? Optional.empty()
                : SmelteryFuel.find(level.registryAccess(), fuel.getFluid());
    }

    /**
     * The smeltery's working heat as far as the client can tell: the temperature an in-progress burn
     * locked in, else the temperature the loaded fuel would burn at, else 0 (nothing burnable in
     * reach) -- the client-visible half of {@link SmelteryControllerBlockEntity#currentTemperature()},
     * which peeks into a wall tank and so is server-only.
     */
    public int smelteryTemperature(@Nullable Level level) {
        int burning = fuelTemperature(level);
        return burning > 0 ? burning : loadedFuel(level).map(SmelteryFuel::temperature).orElse(0);
    }

    /** Melt ticks left in the current burn; 0 when nothing is burning. */
    public int fuelBurnTicks(@Nullable Level level) {
        SmelteryControllerBlockEntity core = core(level);
        return core == null ? 0 : core.fuelBurnTicksRemaining();
    }

    /**
     * Button ids are indices into {@link #fluids}: clicking a fluid in the column moves it to the
     * bottom of the melt, which is upstream's {@code SmelteryFluidClicked}. The index is untrusted
     * and range-checked by {@link dev.gkissel.forgeweave.block.SmelteryTank#moveToBottom}.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (super.clickMenuButton(player, id)) {
            return true; // a station-group tab id, though a smeltery never has one
        }
        if (id < 0) {
            return false;
        }
        access.execute((level, pos) -> {
            SmelteryControllerBlockEntity core = core(level);
            if (core != null) {
                core.selectDrainFluid(id);
            }
        });
        return true;
    }

    /**
     * Shift-click moves meltable items from the player's inventory into the melt grid, and anything
     * in the grid back out. One item per slot, so a stack of ore spreads across the free slots the
     * way {@link SmelteryControllerBlockEntity#insertForMelting} spreads a dropped stack.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        int meltEnd = firstMeltSlot + meltSlotCount();

        if (index < meltEnd) { // melt grid -> player inventory
            if (!moveItemStackTo(inSlot, meltEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoMeltGrid(inSlot, meltEnd)) { // player inventory -> melt grid
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

    /**
     * One item into each free, accepting melt slot. {@code moveItemStackTo} cannot do this: it stops
     * at the first slot when {@code getMaxStackSize()} is 1 rather than spreading across the grid.
     */
    private boolean moveIntoMeltGrid(ItemStack stack, int meltEnd) {
        boolean moved = false;
        for (int index = firstMeltSlot; index < meltEnd && !stack.isEmpty(); index++) {
            Slot target = slots.get(index);
            if (!target.hasItem() && target.mayPlace(stack)) {
                target.setByPlayer(stack.split(1));
                target.setChanged();
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Closes the screen when the player walks off, when the core is broken, and -- upstream's own
     * behaviour, {@code GuiSmeltery#updateScreen} -- when the structure stops being formed, since
     * every number on the screen is a function of an interior that no longer exists.
     */
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> {
            SmelteryControllerBlockEntity core = core(level);
            return core != null && core.isFormed()
                    && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_DISTANCE_SQR;
        }, true);
    }
}
