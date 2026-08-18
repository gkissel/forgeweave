package dev.gkissel.forgeweave.menu;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.block.SearedReservoirBlockEntity;

/**
 * The seared reservoir's menu (parity audit T44, issue #475), ported from upstream 1.12's
 * {@code ContainerTinkerTank} (NOTICE.md).
 *
 * <p>Upstream's own comment on that class -- "no player inventory as we don't actually use slots" --
 * is the whole design: a reservoir holds fluid, so its screen has nothing to put an item in, and
 * this menu carries no slots at all. It exists only to give the screen a lifetime, a reach check,
 * and a button channel for the click that picks which fluid drains.
 *
 * <p>Where the displayed state comes from is the same answer {@link SmelteryMenu} gives at length:
 * not from the menu. The fluid list rides {@link SearedReservoirBlockEntity}'s own block-entity
 * sync, and both sides resolve the block entity from the controller position this menu carries.
 */
public class SearedReservoirMenu extends AbstractContainerMenu {
    /** How far a player may stray from the controller before the screen closes; vanilla's own container reach. */
    private static final double MAX_DISTANCE_SQR = 64.0D;

    private final ContainerLevelAccess access;
    private final BlockPos corePos;

    /** Client-side: built from the open-menu packet, which carries only the controller's position. */
    public SearedReservoirMenu(int containerId, Inventory playerInventory, BlockPos corePos) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL, corePos);
    }

    /** Server-side: built by {@link SearedReservoirBlockEntity#createMenu}. */
    public SearedReservoirMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, BlockPos corePos) {
        super(ForgeweaveMenus.SEARED_RESERVOIR.get(), containerId);
        this.access = access;
        this.corePos = corePos;
    }

    /** The controller this screen belongs to. */
    public BlockPos corePos() {
        return corePos;
    }

    /**
     * The controller block entity, resolved from {@code level}: the server against its own world, the
     * screen against the client copy the block-entity sync maintains.
     */
    @Nullable
    public SearedReservoirBlockEntity reservoir(@Nullable Level level) {
        return level != null && level.getBlockEntity(corePos) instanceof SearedReservoirBlockEntity reservoir
                ? reservoir
                : null;
    }

    /** The stored fluids, bottom first; empty when the controller is gone. Index 0 is what a drain pours. */
    public List<FluidStack> fluids(@Nullable Level level) {
        SearedReservoirBlockEntity reservoir = reservoir(level);
        return reservoir == null ? List.of() : reservoir.tank().fluids();
    }

    /** Total capacity, which scales with the whole structure; 0 when the controller is gone. */
    public int capacity(@Nullable Level level) {
        SearedReservoirBlockEntity reservoir = reservoir(level);
        return reservoir == null ? 0 : reservoir.tank().getCapacity();
    }

    /**
     * Button ids are indices into {@link #fluids}: clicking a fluid in the column moves it to the
     * bottom, upstream's {@code SmelteryFluidClicked}. The index is untrusted and range-checked by
     * {@link dev.gkissel.forgeweave.block.SmelteryTank#moveToBottom}.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0) {
            return false;
        }
        access.execute((level, pos) -> {
            SearedReservoirBlockEntity reservoir = reservoir(level);
            if (reservoir != null) {
                reservoir.selectDrainFluid(id);
            }
        });
        return true;
    }

    /** No slots, so nothing can ever be shift-clicked out of one. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Closes when the player walks off, when the controller is broken, and -- upstream's own
     * behaviour -- when the structure stops being formed, since the capacity every number on the
     * screen is drawn against no longer exists.
     */
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> {
            SearedReservoirBlockEntity reservoir = reservoir(level);
            return reservoir != null && reservoir.isFormed()
                    && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_DISTANCE_SQR;
        }, true);
    }
}
