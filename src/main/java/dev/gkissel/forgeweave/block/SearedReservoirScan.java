package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Detects the seared reservoir multiblock around its controller (parity audit T44, issue #475),
 * ported from upstream 1.12's {@code MultiblockCuboid} run with {@code MultiblockTinkerTank}'s
 * configuration -- {@code hasFloor = true, hasFrame = true, hasCeiling = true} -- and its
 * block-role overrides (NOTICE.md).
 *
 * <p>Same closed cuboid as {@link SearedFurnaceScan}, but every role takes a wider set of blocks,
 * because a reservoir only has to hold fluid rather than contain a fire:
 *
 * <ul>
 *   <li>The interior is 1x1 up to {@value SmelteryScan#MAX_SIZE}x{@value SmelteryScan#MAX_SIZE},
 *       found from the open block behind the controller exactly as the smeltery's is.
 *   <li><b>Floor:</b> under the interior footprint, upstream's {@code validTinkerTankFloorBlocks} --
 *       a plain seared block, seared glass, or an I/O block; notably <em>not</em> a tank. Its outer
 *       ring, under the walls, is the {@code FLOOR} frame, which upstream restricts to a plain
 *       seared block or an I/O block.
 *   <li><b>Walls:</b> scanned upward from the floor until a layer fails. The interior must be air;
 *       every wall block and every corner column ({@code WALL} frame) takes upstream's
 *       {@code validTinkerTankBlocks}, which is {@code validSmelteryBlocks} itself
 *       ({@link SmelteryScan#wallBlocks()}) -- so glass, tanks and I/O blocks are all welcome
 *       anywhere in the walls, corners included.
 *   <li><b>Ceiling:</b> over the interior footprint, any wall block <em>or</em> a bottom-half seared
 *       slab or stairs ({@code isCeilingBlock}); its outer ring is the {@code CEILING} frame, which
 *       takes a bottom-half slab or stairs, a plain seared block, or an I/O block.
 *   <li>The controller must sit in a wall layer. Unlike the smeltery and the furnace, <b>no tank is
 *       required</b>: upstream's {@code MultiblockTinkerTank} has no {@code hasTank} rule, because
 *       the structure is itself the tank.
 * </ul>
 *
 * <p>Same deviation from 1.12 as {@link SmelteryScan}: upstream returns {@code null} for every
 * failure, this returns a translatable reason.
 */
public final class SearedReservoirScan {
    private static final int WALK_LIMIT = SmelteryScan.MAX_SIZE + 1;

    private static final String PREFIX = "gui.forgeweave.seared_reservoir.";
    public static final String KEY_FORMED = PREFIX + "formed";
    public static final String KEY_NOT_SCANNED = PREFIX + "not_scanned";
    public static final String KEY_NOT_LOADED = PREFIX + "not_loaded";
    public static final String KEY_BLOCKED_INTERIOR = PREFIX + "blocked_interior";
    public static final String KEY_TOO_LARGE = PREFIX + "too_large";
    public static final String KEY_INVALID_FLOOR = PREFIX + "invalid_floor";
    public static final String KEY_INVALID_WALL = PREFIX + "invalid_wall";
    public static final String KEY_INVALID_CEILING = PREFIX + "invalid_ceiling";
    public static final String KEY_CLAIMED = PREFIX + "claimed";
    public static final String KEY_CORE_OUTSIDE = PREFIX + "core_outside";

    /**
     * Outcome of one scan: the interior when it formed, plus every I/O block and tank the structure
     * covers, which the controller claims so no smeltery can take them out from under it (upstream
     * assigns its master to every structure block; see {@link SmelteryScan#claimedByAnotherCore}).
     */
    public record Result(@Nullable SmelteryStructure structure, List<BlockPos> io, List<BlockPos> tanks, Component message) {
        public boolean formed() {
            return structure != null;
        }
    }

    /** One plane's or layer's verdict, plus what it contributed to the structure. */
    private record Part(@Nullable Component failure, boolean interiorBlocked, List<BlockPos> io, List<BlockPos> tanks) {
        static Part fail(Component failure) {
            return new Part(failure, false, List.of(), List.of());
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

        List<BlockPos> io = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();

        Part floor = detectPlane(level, corePos, bottom.getY() - 1, west, east, north, south, false);
        if (floor.failure() != null) {
            return failure(floor.failure());
        }
        collect(io, tanks, floor);

        Part layerFailure = null;
        int height = 0;
        for (int y = bottom.getY(); y < level.getMaxBuildHeight(); y++) {
            Part layer = detectLayer(level, corePos, y, west, east, north, south);
            if (layer.failure() != null) {
                layerFailure = layer;
                break;
            }
            collect(io, tanks, layer);
            height++;
        }
        if (height < 1 + corePos.getY() - bottom.getY()) {
            return failure(layerFailure != null ? layerFailure.failure() : Component.translatable(KEY_CORE_OUTSIDE));
        }

        Part ceiling = detectPlane(level, corePos, bottom.getY() + height, west, east, north, south, true);
        if (ceiling.failure() != null) {
            // Same tie-break as SearedFurnaceScan: the layer above the last good one failed either
            // because the ceiling sits in it (report the ceiling's own complaint) or because a wall
            // block there was wrong, in which case the hole in the wall is the better report.
            boolean wallHole = layerFailure != null && !layerFailure.interiorBlocked();
            return failure(wallHole ? layerFailure.failure() : ceiling.failure());
        }
        collect(io, tanks, ceiling);

        SmelteryStructure structure = new SmelteryStructure(
                new BlockPos(west + 1, bottom.getY(), north + 1),
                new BlockPos(east - 1, bottom.getY() + height - 1, south - 1));
        return new Result(structure, List.copyOf(io), List.copyOf(tanks),
                Component.translatable(KEY_FORMED, width, depth, height));
    }

    private static void collect(List<BlockPos> io, List<BlockPos> tanks, Part part) {
        io.addAll(part.io());
        tanks.addAll(part.tanks());
    }

    /**
     * Upstream {@code detectPlaneXZ}: the frame ring, then the interior footprint. The frame's rule
     * differs from the footprint's in both planes -- see {@link #isFloorFrame} and
     * {@link #isCeilingFrame}.
     */
    private static Part detectPlane(Level level, BlockPos corePos, int y, int west, int east, int north, int south, boolean ceiling) {
        if (!level.hasChunksAt(new BlockPos(west, y, north), new BlockPos(east, y, south))) {
            return Part.fail(Component.translatable(KEY_NOT_LOADED));
        }
        List<BlockPos> io = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();
        String failureKey = ceiling ? KEY_INVALID_CEILING : KEY_INVALID_FLOOR;
        for (int x = west; x <= east; x++) {
            for (int z = north; z <= south; z++) {
                // No controller exemption in either plane, unlike upstream's isFrameBlock: the
                // controller has to sit in a wall layer for the scan to get this far, and the floor
                // and ceiling planes are respectively below and above every wall layer.
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                boolean ring = x == west || x == east || z == north || z == south;
                boolean valid = ring
                        ? (ceiling ? isCeilingFrame(state) : isFloorFrame(state))
                        : (ceiling ? isCeilingBlock(state) : isFloorBlock(state));
                if (!valid) {
                    return Part.fail(at(failureKey, pos));
                }
                if (SmelteryScan.claimedByAnotherCore(level, pos, corePos)) {
                    return Part.fail(at(KEY_CLAIMED, pos));
                }
                record(io, tanks, pos, state.getBlock());
            }
        }
        return new Part(null, false, io, tanks);
    }

    /**
     * Upstream {@code detectLayer}: interior air, then the four corners ({@code WALL} frame) and the
     * plain walls -- which for a reservoir are the same rule, upstream's {@code validTinkerTankBlocks}.
     * The interior is checked first for the reason {@link SearedFurnaceScan} checks it first.
     */
    private static Part detectLayer(Level level, BlockPos corePos, int y, int west, int east, int north, int south) {
        if (!level.hasChunksAt(new BlockPos(west, y, north), new BlockPos(east, y, south))) {
            return Part.fail(Component.translatable(KEY_NOT_LOADED));
        }
        for (int x = west + 1; x < east; x++) {
            for (int z = north + 1; z < south; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isInterior(level, pos)) {
                    return new Part(at(KEY_BLOCKED_INTERIOR, pos), true, List.of(), List.of());
                }
            }
        }
        List<BlockPos> io = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();
        for (int x = west; x <= east; x++) {
            for (int z = north; z <= south; z++) {
                if (x != west && x != east && z != north && z != south) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                if (pos.equals(corePos)) {
                    continue;
                }
                Block block = level.getBlockState(pos).getBlock();
                if (!SmelteryScan.wallBlocks().contains(block)) {
                    return Part.fail(at(KEY_INVALID_WALL, pos));
                }
                if (SmelteryScan.claimedByAnotherCore(level, pos, corePos)) {
                    return Part.fail(at(KEY_CLAIMED, pos));
                }
                record(io, tanks, pos, block);
            }
        }
        return new Part(null, false, io, tanks);
    }

    private static void record(List<BlockPos> io, List<BlockPos> tanks, BlockPos pos, Block block) {
        if (SmelteryScan.tankBlocks().contains(block)) {
            tanks.add(pos);
        } else if (SmelteryScan.ioBlocks().contains(block)) {
            io.add(pos);
        }
    }

    /** Upstream {@code validTinkerTankFloorBlocks}: a seared block, seared glass, or an I/O block -- never a tank. */
    static boolean isFloorBlock(BlockState state) {
        Block block = state.getBlock();
        return SmelteryScan.searedBlocks().contains(block)
                || SmelteryScan.ioBlocks().contains(block)
                || block == ForgeweaveBlocks.SEARED_GLASS.get();
    }

    /** Upstream {@code isFrameBlock}'s fall-through for {@code FLOOR}: {@code searedBlock} or {@code smelteryIO}. */
    static boolean isFloorFrame(BlockState state) {
        Block block = state.getBlock();
        return SmelteryScan.searedBlocks().contains(block) || SmelteryScan.ioBlocks().contains(block);
    }

    /** Upstream {@code MultiblockTinkerTank#isCeilingBlock}: a bottom-half seared slab/stairs, or any wall block. */
    static boolean isCeilingBlock(BlockState state) {
        return SearedFurnaceScan.isCeilingBlock(state) || SmelteryScan.wallBlocks().contains(state.getBlock());
    }

    /** Upstream {@code isFrameBlock}'s {@code CEILING} branch: a bottom-half seared slab/stairs, else the floor frame's rule. */
    static boolean isCeilingFrame(BlockState state) {
        return SearedFurnaceScan.isCeilingBlock(state) || isFloorFrame(state);
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
        return new Result(null, List.of(), List.of(), message);
    }

    private static Component at(String key, BlockPos pos) {
        return Component.translatable(key, pos.getX(), pos.getY(), pos.getZ());
    }

    private SearedReservoirScan() {}
}
