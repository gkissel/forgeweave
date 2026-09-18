package dev.gkissel.forgeweave.menu;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.block.EnergizedTankBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The energized tank's menu (docs/SCOPE.md M8, D-M8-11; issue #1018). The tank is a GUI block by
 * maintainer decision of 2026-09-18: the overdrive button lives on a screen rather than on the block
 * face, which is what {@link #OVERDRIVE_BUTTON} carries.
 *
 * <p>The only slots here are the player's own inventory, which the panel art draws anyway. A tank
 * holds a fluid and a charge, so there is nothing to put an item into, and nothing can be shift
 * clicked anywhere -- see {@link #quickMoveStack}.
 *
 * <p>Where the displayed state comes from is {@link SearedReservoirMenu}'s answer: not from the
 * menu. The sample, the buffer and the overdrive flag all ride {@link EnergizedTankBlockEntity}'s
 * own block-entity sync, and both sides resolve the block entity from the position this menu
 * carries. So there is no data slot and no extra packet; the screen reads live values off the same
 * block entity the server mutates.
 */
public class EnergizedTankMenu extends AbstractContainerMenu {
    /** The one button id this menu accepts: press overdrive. */
    public static final int OVERDRIVE_BUTTON = 0;

    /** The plain station panel this screen is drawn on, player inventory and all. */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    /** Vanilla's own layout for a 166px-tall container panel. */
    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    /** How far a player may stray from the tank before the screen closes; vanilla's own container reach. */
    private static final double MAX_DISTANCE_SQR = 64.0D;

    private final ContainerLevelAccess access;
    private final BlockPos tankPos;

    /** Client-side: built from the open-menu packet, which carries only the tank's position. */
    public EnergizedTankMenu(int containerId, Inventory playerInventory, BlockPos tankPos) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, tankPos);
    }

    /** Server-side: built by {@link EnergizedTankBlockEntity#createMenu}. */
    public EnergizedTankMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access,
            BlockPos tankPos) {
        super(ForgeweaveMenus.ENERGIZED_TANK.get(), containerId);
        this.access = access;
        this.tankPos = tankPos;
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

    /** The tank this screen belongs to. */
    public BlockPos tankPos() {
        return tankPos;
    }

    /**
     * The tank's block entity, resolved from {@code level}: the server against its own world, the
     * screen against the client copy the block-entity sync maintains.
     */
    @Nullable
    public EnergizedTankBlockEntity tank(@Nullable Level level) {
        return level != null && level.getBlockEntity(tankPos) instanceof EnergizedTankBlockEntity tank
                ? tank
                : null;
    }

    /** The fuel sample, or empty when the tank holds none or is already gone. */
    public FluidStack sample(@Nullable Level level) {
        EnergizedTankBlockEntity tank = tank(level);
        return tank == null ? FluidStack.EMPTY : tank.sample().getFluid();
    }

    /** What the buffer holds right now. */
    public int energy(@Nullable Level level) {
        EnergizedTankBlockEntity tank = tank(level);
        return tank == null ? 0 : tank.buffer().getEnergyStored();
    }

    /** What the buffer holds at most, which is a live config value -- see the buffer's own javadoc. */
    public int energyCapacity(@Nullable Level level) {
        EnergizedTankBlockEntity tank = tank(level);
        return tank == null ? ForgeweaveConfig.energizedTankBuffer() : tank.buffer().getMaxEnergyStored();
    }

    /** The heat the sample gives this smeltery, on the same scale every other display shows (#933). */
    public int temperature(@Nullable Level level) {
        EnergizedTankBlockEntity tank = tank(level);
        return tank == null ? 0 : tank.temperature();
    }

    /** What one melt tick costs at the current sample and overdrive setting. */
    public int costPerMeltTick(@Nullable Level level) {
        EnergizedTankBlockEntity tank = tank(level);
        return tank == null ? 0 : tank.costPerMeltTick();
    }

    /** Whether the overdrive button reads as pressed. */
    public boolean overdrive(@Nullable Level level) {
        EnergizedTankBlockEntity tank = tank(level);
        return tank != null && tank.overdrive();
    }

    /**
     * Whether energized tanks are switched on at all. A dormant tank still opens its screen and
     * still shows its preserved sample and buffer (D-M8-5's inert-not-destructive contract); only the
     * button goes dead, here and on the server both.
     */
    public boolean active() {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.ENERGIZED_TANK);
    }

    /** The overdrive button, and the only mutation this menu has. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != OVERDRIVE_BUTTON || !active()) {
            return false;
        }
        access.execute((level, pos) -> {
            EnergizedTankBlockEntity tank = tank(level);
            if (tank != null) {
                tank.toggleOverdrive();
            }
        });
        return true;
    }

    /** The player's own inventory is the only inventory here, so there is nowhere to move a stack to. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(ForgeweaveBlocks.ENERGIZED_TANK.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_DISTANCE_SQR,
                true);
    }
}
