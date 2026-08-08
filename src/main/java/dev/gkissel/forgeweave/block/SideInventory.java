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
        Level level = blockEntity.getLevel();
        if (level == null) {
            return null;
        }
        BlockPos pos = blockEntity.getBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }
}
