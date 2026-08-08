package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The horizontal-neighbor item-handler scan every station's side-inventory GUI panel is built from
 * (docs/SCOPE.md issue #40, extended to the Part Builder and Tool Station in the same issue's
 * follow-up). Ports upstream 1.12's {@code ContainerCraftingStation} neighbor scan (NOTICE.md,
 * originally on {@code CraftingStationBlockEntity} before this issue reused it for the other two
 * stations) -- the first of the four horizontal neighbors exposing an item-handler capability wins;
 * there is no "is this neighbor also part of the station" exclusion to port since every Forgeweave
 * station is always a single block.
 */
public final class SideInventory {
    private SideInventory() {}

    /** The adjacent block's item handler to expose in a station GUI's side panel, or {@code null} if none qualifies. */
    @Nullable
    public static IItemHandler find(BlockEntity blockEntity) {
        Direction direction = findDirection(blockEntity);
        return direction == null ? null : handlerAt(blockEntity, direction);
    }

    /**
     * Where {@link #find}'s handler came from, or {@code null} if there is none -- so a caller can ask
     * what kind of block it actually is (the Part Builder's pattern-chest sidebar, issue #78, only
     * appears when the neighbour feeding the side panel is a Pattern Chest).
     */
    @Nullable
    public static BlockPos findPos(BlockEntity blockEntity) {
        Direction direction = findDirection(blockEntity);
        return direction == null ? null : blockEntity.getBlockPos().relative(direction);
    }

    @Nullable
    private static Direction findDirection(BlockEntity blockEntity) {
        if (blockEntity.getLevel() == null) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (handlerAt(blockEntity, direction) != null) {
                return direction;
            }
        }
        return null;
    }

    @Nullable
    private static IItemHandler handlerAt(BlockEntity blockEntity, Direction direction) {
        Level level = blockEntity.getLevel();
        BlockPos neighborPos = blockEntity.getBlockPos().relative(direction);
        return level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());
    }
}
