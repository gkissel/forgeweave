package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Detects the seared furnace multiblock around its controller (issue #442), ported from upstream
 * 1.12's {@code MultiblockCuboid} run with {@code MultiblockSearedFurnace}'s configuration --
 * {@code hasFloor = true, hasFrame = true, hasCeiling = true} -- and its block-role overrides
 * (NOTICE.md). Where {@link SmelteryScan} bakes in the smeltery's open-topped, frameless shape,
 * this bakes in the furnace's closed one:
 *
 * <ul>
 *   <li>The interior is 1x1 up to {@value SmelteryScan#MAX_SIZE}x{@value SmelteryScan#MAX_SIZE},
 *       found from the open block behind the controller exactly as the smeltery's is.
 *   <li><b>Floor:</b> the plane one below the lowest interior block. Under the interior footprint
 *       it must be plain seared blocks ({@code isFloorBlock -> isValidBlock -> searedBlock}); its
 *       outer ring, under the walls, is upstream's {@code FLOOR} frame -- <em>any</em> block passes
 *       there ({@code isFrameBlock} returns {@code true} for every non-{@code WALL} type), a tank
 *       there counting towards the tank requirement.
 *   <li><b>Walls:</b> scanned upward from the floor until a layer fails. The interior must be air;
 *       the four corner columns ({@code WALL} frame) may be a seared block <em>or a seared tank</em>;
 *       every other wall block must be a plain seared block or the controller itself. Upstream's
 *       furnace accepts no glass, drain, duct or chute anywhere in its walls, and no tank away from
 *       a corner -- {@code isWallBlock} is the plain {@code isValidBlock}.
 *   <li><b>Ceiling:</b> the plane where the wall layers stopped. Over the interior footprint it must
 *       be a seared block, or a <em>bottom-half</em> seared slab or seared stairs (issue #369's rule,
 *       {@code isCeilingBlock}); its outer ring is the {@code CEILING} frame and again takes any
 *       block, a tank counting.
 *   <li>The controller must sit in a wall layer, and at least one tank must have been seen
 *       ({@code hasTank}).
 * </ul>
 *
 * <p>Same deviation from 1.12 as {@link SmelteryScan}: upstream returns {@code null} for every
 * failure, this returns a translatable reason.
 */
public final class SearedFurnaceScan {
    private static final int WALK_LIMIT = SmelteryScan.MAX_SIZE + 1;

    private static final String PREFIX = "gui.forgeweave.seared_furnace.";
    public static final String KEY_FORMED = PREFIX + "formed";
    public static final String KEY_NOT_SCANNED = PREFIX + "not_scanned";
    public static final String KEY_NOT_LOADED = PREFIX + "not_loaded";
    public static final String KEY_BLOCKED_INTERIOR = PREFIX + "blocked_interior";
    public static final String KEY_TOO_LARGE = PREFIX + "too_large";
    public static final String KEY_INVALID_FLOOR = PREFIX + "invalid_floor";
    public static final String KEY_INVALID_WALL = PREFIX + "invalid_wall";
    public static final String KEY_INVALID_CEILING = PREFIX + "invalid_ceiling";
    public static final String KEY_NO_TANK = PREFIX + "no_tank";
    public static final String KEY_CLAIMED = PREFIX + "claimed";
    public static final String KEY_CORE_OUTSIDE = PREFIX + "core_outside";

    /**
     * Outcome of one scan: the interior when it formed, every tank position seen in the frame (the
     * furnace's fuel comes out of these; upstream's {@code tanks} list), and a player-facing message.
     */
    public record Result(@Nullable SmelteryStructure structure, List<BlockPos> tanks, Component message) {
        public boolean formed() {
            return structure != null;
        }
    }

    /** One plane's or layer's verdict, plus the tanks it contributed. */
    private record Part(@Nullable Component failure, boolean interiorBlocked, List<BlockPos> tanks) {
        static Part ok(List<BlockPos> tanks) {
            return new Part(null, false, tanks);
        }

        static Part fail(Component failure) {
            return new Part(failure, false, List.of());
        }
    }

    public static Result scan(Level level, BlockPos corePos, Direction facing) {
        BlockPos seed = corePos.relative(facing.getOpposite());
        if (!level.hasChunkAt(seed)) {
            return failure(Component.translatable(KEY_NOT_LOADED));
        }
        if (!isInterior(level, seed)) {
            return failure(at(KEY_BLOCKED_INTERIOR, seed));
        }

        BlockPos bottom = walkWhileInterior(level, seed, Direction.DOWN, 64).above();
        if (corePos.getY() < bottom.getY()) {
            return failure(Component.translatable(KEY_CORE_OUTSIDE));
        }

        int west = walkWhileInterior(level, bottom, Direction.WEST, WALK_LIMIT).getX();
        int east = walkWhileInterior(level, bottom, Direction.EAST, WALK_LIMIT).getX();
        int north = walkWhileInterior(level, bottom, Direction.NORTH, WALK_LIMIT).getZ();
        int south = walkWhileInterior(level, bottom, Direction.SOUTH, WALK_LIMIT).getZ();
        int width = east - west - 1;
        int depth = south - north - 1;
        if (width > SmelteryScan.MAX_SIZE || depth > SmelteryScan.MAX_SIZE) {
            return failure(Component.translatable(KEY_TOO_LARGE, width, depth, SmelteryScan.MAX_SIZE));
        }

        List<BlockPos> tanks = new ArrayList<>();
        Part floor = detectPlane(level, corePos, bottom.getY() - 1, west, east, north, south, false);
        if (floor.failure() != null) {
            return failure(floor.failure());
        }
        tanks.addAll(floor.tanks());

        Part layerFailure = null;
        int height = 0;
        for (int y = bottom.getY(); y < level.getMaxBuildHeight(); y++) {
            Part layer = detectLayer(level, corePos, y, west, east, north, south);
            if (layer.failure() != null) {
                layerFailure = layer;
                break;
            }
            tanks.addAll(layer.tanks());
            height++;
        }
        if (height < 1 + corePos.getY() - bottom.getY()) {
            return failure(layerFailure != null ? layerFailure.failure() : Component.translatable(KEY_CORE_OUTSIDE));
        }

        Part ceiling = detectPlane(level, corePos, bottom.getY() + height, west, east, north, south, true);
        if (ceiling.failure() != null) {
            // The layer above the last good one failed either because the ceiling blocks are sitting
            // in it (the normal case -- report the ceiling's own complaint) or because a wall block
            // there was wrong, in which case the hole in the wall is the better report.
            boolean wallHole = layerFailure != null && !layerFailure.interiorBlocked();
            return failure(wallHole ? layerFailure.failure() : ceiling.failure());
        }
        tanks.addAll(ceiling.tanks());
        if (tanks.isEmpty()) {
            return failure(Component.translatable(KEY_NO_TANK));
        }

        SmelteryStructure structure = new SmelteryStructure(
                new BlockPos(west + 1, bottom.getY(), north + 1),
                new BlockPos(east - 1, bottom.getY() + height - 1, south - 1));
        return new Result(structure, List.copyOf(tanks), Component.translatable(KEY_FORMED, width, depth, height));
    }

    /** Upstream {@code detectPlaneXZ}: the frame ring (any block, tanks noted) and the interior footprint. */
    private static Part detectPlane(Level level, BlockPos corePos, int y, int west, int east, int north, int south, boolean ceiling) {
        if (!level.hasChunksAt(new BlockPos(west, y, north), new BlockPos(east, y, south))) {
            return Part.fail(Component.translatable(KEY_NOT_LOADED));
        }
        List<BlockPos> tanks = new ArrayList<>();
        for (int x = west; x <= east; x++) {
            for (int z = north; z <= south; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (pos.equals(corePos)) {
                    continue;
                }
                if (claimedByAnotherOwner(level, pos, corePos)) {
                    return Part.fail(at(KEY_CLAIMED, pos));
                }
                BlockState state = level.getBlockState(pos);
                boolean ring = x == west || x == east || z == north || z == south;
                if (ring) {
                    if (SmelteryScan.tankBlocks().contains(state.getBlock())) {
                        tanks.add(pos);
                    }
                } else if (ceiling ? !isCeilingBlock(state) : !SmelteryScan.searedBlocks().contains(state.getBlock())) {
                    return Part.fail(at(ceiling ? KEY_INVALID_CEILING : KEY_INVALID_FLOOR, pos));
                }
            }
        }
        return Part.ok(tanks);
    }

    /**
     * Upstream {@code detectLayer}: interior air, corners (frame) and the plain walls. The interior
     * is checked first -- upstream checks the corners first, but only ever answers null -- so that a
     * layer holding the ceiling reads as {@code interiorBlocked} even when the ring around the
     * ceiling is open, which is what lets {@link #scan} tell "the ceiling is here" from "there is
     * a hole in the wall".
     */
    private static Part detectLayer(Level level, BlockPos corePos, int y, int west, int east, int north, int south) {
        if (!level.hasChunksAt(new BlockPos(west, y, north), new BlockPos(east, y, south))) {
            return Part.fail(Component.translatable(KEY_NOT_LOADED));
        }
        for (int x = west + 1; x < east; x++) {
            for (int z = north + 1; z < south; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isInterior(level, pos)) {
                    return new Part(at(KEY_BLOCKED_INTERIOR, pos), true, List.of());
                }
            }
        }
        List<BlockPos> tanks = new ArrayList<>();
        for (int x = west; x <= east; x++) {
            for (int z = north; z <= south; z++) {
                boolean xEdge = x == west || x == east;
                boolean zEdge = z == north || z == south;
                if (!xEdge && !zEdge) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                if (pos.equals(corePos)) {
                    continue;
                }
                if (claimedByAnotherOwner(level, pos, corePos)) {
                    return Part.fail(at(KEY_CLAIMED, pos));
                }
                Block block = level.getBlockState(pos).getBlock();
                if (xEdge && zEdge && SmelteryScan.tankBlocks().contains(block)) {
                    tanks.add(pos);
                } else if (!SmelteryScan.searedBlocks().contains(block)) {
                    return Part.fail(at(KEY_INVALID_WALL, pos));
                }
            }
        }
        return Part.ok(tanks);
    }

    /**
     * Upstream {@code MultiblockSearedFurnace#isCeilingBlock}: a seared block, or a seared slab /
     * seared stairs whose half is not {@code TOP} ({@code TinkerSmeltery.searedStairsSlabs}).
     */
    static boolean isCeilingBlock(BlockState state) {
        Block block = state.getBlock();
        if (SmelteryScan.searedBlocks().contains(block)) {
            return true;
        }
        if (!ForgeweaveBlocks.isSearedStairsOrSlab(block)) {
            return false;
        }
        if (block instanceof SlabBlock) {
            return state.getValue(SlabBlock.TYPE) != SlabType.TOP;
        }
        return !(block instanceof StairBlock) || state.getValue(StairBlock.HALF) != Half.TOP;
    }

    /** Upstream {@code MultiblockTinker#isValidSlave}: a tank another still-formed owner claims is refused. */
    private static boolean claimedByAnotherOwner(Level level, BlockPos pos, BlockPos corePos) {
        if (!(level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank)) {
            return false;
        }
        BlockPos owner = tank.core();
        if (owner == null || owner.equals(corePos)) {
            return false;
        }
        if (!level.isLoaded(owner)) {
            return true;
        }
        return level.getBlockEntity(owner) instanceof TankOwner other && other.isFormed();
    }

    private static BlockPos walkWhileInterior(Level level, BlockPos from, Direction dir, int limit) {
        BlockPos pos = from;
        for (int i = 0; i < limit && isInterior(level, pos); i++) {
            pos = pos.relative(dir);
        }
        return pos;
    }

    private static boolean isInterior(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos) && level.getBlockState(pos).isAir();
    }

    private static Result failure(Component message) {
        return new Result(null, List.of(), message);
    }

    private static Component at(String key, BlockPos pos) {
        return Component.translatable(key, pos.getX(), pos.getY(), pos.getZ());
    }

    private SearedFurnaceScan() {}
}
