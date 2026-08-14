package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Detects the smeltery multiblock around a core (docs/SCOPE.md M2 issue #95), ported from upstream
 * 1.12's {@code MultiblockCuboid}/{@code MultiblockSmeltery} (NOTICE.md) with those classes'
 * smeltery configuration baked in -- {@code hasFloor = true, hasFrame = false, hasCeiling = false}:
 *
 * <ul>
 *   <li>The open block behind the core is the seed. From it the scan walks out along each horizontal
 *       axis to find the walls, giving a rectangular interior of 1x1 up to {@value #MAX_SIZE}x{@value
 *       #MAX_SIZE} ({@code MAX_SIZE} is upstream's, "consistancy by this point. All others do 9x9").
 *   <li>The floor is the plane one below the lowest interior block, over the interior footprint
 *       only. Upstream restricts the floor to plain seared blocks (its {@code isFloorBlock}
 *       override); tanks and the drain are wall-only.
 *   <li>Wall layers are scanned upward from the floor until one fails; the smeltery is however tall
 *       the walls reach, with no ceiling. The four corner columns are never checked -- upstream skips
 *       them too whenever {@code hasFrame} is false.
 *   <li>At least one seared tank must be part of the walls (upstream's {@code hasTank} flag).
 *   <li>The core must itself be one of those wall blocks, so the scan fails if it sits below the
 *       floor or above where the walls stop.
 * </ul>
 *
 * <p><b>Deviation from 1.12, adopted from the 1.20 clone:</b> upstream 1.12 returns {@code null} for
 * every kind of failure, which gives a player no way to tell a hole in a wall from an oversized
 * interior. The 1.20 generation replaced that with {@code MultiblockResult} -- a failure position
 * plus a translatable reason -- which is exactly what issue #95 and the SCOPE.md M2 acceptance test
 * ("the controller reports why an invalid structure fails to form") ask for, so {@link Result}
 * carries the same shape.
 */
public final class SmelteryScan {
    /** Maximum interior width and depth, upstream {@code TileMultiblock.MAX_SIZE}. */
    public static final int MAX_SIZE = 9;

    /**
     * How far a wall walk may travel. One past {@link #MAX_SIZE} so an interior of exactly
     * {@code MAX_SIZE + 1} blocks is still measured rather than clipped to a passing size -- upstream
     * walks exactly {@code MAX_SIZE} and lets an oversized interior seeded from its own edge slip
     * through as "9 wide".
     */
    private static final int WALK_LIMIT = MAX_SIZE + 1;

    private static final String PREFIX = "gui.forgeweave.smeltery.";
    public static final String KEY_FORMED = PREFIX + "formed";
    public static final String KEY_NOT_SCANNED = PREFIX + "not_scanned";
    public static final String KEY_NOT_LOADED = PREFIX + "not_loaded";
    public static final String KEY_BLOCKED_INTERIOR = PREFIX + "blocked_interior";
    public static final String KEY_TOO_LARGE = PREFIX + "too_large";
    public static final String KEY_INVALID_FLOOR = PREFIX + "invalid_floor";
    public static final String KEY_INVALID_WALL = PREFIX + "invalid_wall";
    public static final String KEY_NO_TANK = PREFIX + "no_tank";
    /** #288: the block is part of another smeltery that is still standing (upstream {@code isValidSlave}). */
    public static final String KEY_CLAIMED = PREFIX + "claimed";
    public static final String KEY_CORE_OUTSIDE = PREFIX + "core_outside";

    /**
     * Outcome of one scan: the interior when it formed, the I/O-block ({@code io}: drains, ducts and
     * chutes) and wall-tank positions found in its walls (so the core can point them back at itself --
     * {@code tanks} is #97's, for waking a melt that stopped for want of fuel when one gets
     * refilled), and a player-facing message that is a success line when {@link #formed()} and the
     * reason for failure otherwise.
     */
    public record Result(@Nullable SmelteryStructure structure, List<BlockPos> io, List<BlockPos> tanks, Component message) {
        public boolean formed() {
            return structure != null;
        }
    }

    /**
     * @param corePos position of the smeltery core block
     * @param facing  the core's {@code FACING}, which points out of the structure; the interior is
     *                the block on the opposite side (upstream {@code TileMultiblock#checkMultiblockStructure}).
     */
    public static Result scan(Level level, BlockPos corePos, Direction facing) {
        BlockPos seed = corePos.relative(facing.getOpposite());
        if (!level.hasChunkAt(seed)) {
            return failure(Component.translatable(KEY_NOT_LOADED));
        }
        if (!isInterior(level, seed)) {
            return failure(at(KEY_BLOCKED_INTERIOR, seed));
        }

        // The floor sits one below the lowest open block in the core's own column. Upstream's own
        // 64-block cap, which also stops a core placed in open air walking to bedrock.
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
        if (width > MAX_SIZE || depth > MAX_SIZE) {
            return failure(Component.translatable(KEY_TOO_LARGE, width, depth, MAX_SIZE));
        }

        Component floorFailure = detectFloor(level, bottom.getY() - 1, west, east, north, south);
        if (floorFailure != null) {
            return failure(floorFailure);
        }

        List<BlockPos> io = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();
        Component layerFailure = null;
        int height = 0;
        for (int y = bottom.getY(); y < level.getMaxBuildHeight(); y++) {
            Layer layer = detectLayer(level, corePos, y, west, east, north, south);
            if (layer.failure() != null) {
                layerFailure = layer.failure();
                break;
            }
            io.addAll(layer.io());
            tanks.addAll(layer.tanks());
            height++;
        }

        // The core has to be inside the walls we just found, otherwise it is stuck to the outside of
        // some other structure; whatever stopped the wall scan is the reason to report.
        if (height < 1 + corePos.getY() - bottom.getY()) {
            return failure(layerFailure != null ? layerFailure : Component.translatable(KEY_CORE_OUTSIDE));
        }
        if (tanks.isEmpty()) {
            return failure(Component.translatable(KEY_NO_TANK));
        }

        SmelteryStructure structure = new SmelteryStructure(
                new BlockPos(west + 1, bottom.getY(), north + 1),
                new BlockPos(east - 1, bottom.getY() + height - 1, south - 1));
        return new Result(structure, List.copyOf(io), List.copyOf(tanks), Component.translatable(KEY_FORMED, width, depth, height));
    }

    /** One wall layer's verdict, plus what it contributed to the structure. */
    private record Layer(@Nullable Component failure, List<BlockPos> io, List<BlockPos> tanks) {}

    @Nullable
    private static Component detectFloor(Level level, int y, int west, int east, int north, int south) {
        if (!level.hasChunksAt(new BlockPos(west, y, north), new BlockPos(east, y, south))) {
            return Component.translatable(KEY_NOT_LOADED);
        }
        for (int x = west + 1; x < east; x++) {
            for (int z = north + 1; z < south; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!Valid.FLOOR.contains(level.getBlockState(pos).getBlock())) {
                    return at(KEY_INVALID_FLOOR, pos);
                }
            }
        }
        return null;
    }

    private static Layer detectLayer(Level level, BlockPos corePos, int y, int west, int east, int north, int south) {
        if (!level.hasChunksAt(new BlockPos(west, y, north), new BlockPos(east, y, south))) {
            return new Layer(Component.translatable(KEY_NOT_LOADED), List.of(), List.of());
        }

        for (int x = west + 1; x < east; x++) {
            for (int z = north + 1; z < south; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isInterior(level, pos)) {
                    return new Layer(at(KEY_BLOCKED_INTERIOR, pos), List.of(), List.of());
                }
            }
        }

        // Walls, corners excluded -- with no frame there is nothing to check at the corner columns.
        List<BlockPos> walls = new ArrayList<>();
        for (int x = west + 1; x < east; x++) {
            walls.add(new BlockPos(x, y, north));
            walls.add(new BlockPos(x, y, south));
        }
        for (int z = north + 1; z < south; z++) {
            walls.add(new BlockPos(west, y, z));
            walls.add(new BlockPos(east, y, z));
        }

        List<BlockPos> io = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();
        for (BlockPos pos : walls) {
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof SmelteryControllerBlock) {
                // A second core would have its own structure state; only ours belongs in this wall.
                if (!pos.equals(corePos)) {
                    return new Layer(at(KEY_INVALID_WALL, pos), List.of(), List.of());
                }
            } else if (!Valid.WALL.contains(block)) {
                return new Layer(at(KEY_INVALID_WALL, pos), List.of(), List.of());
            } else if (claimedByAnotherCore(level, pos, corePos)) {
                return new Layer(at(KEY_CLAIMED, pos), List.of(), List.of());
            }
            if (Valid.TANKS.contains(block)) {
                tanks.add(pos);
            } else if (Valid.IO.contains(block)) {
                io.add(pos);
            }
        }
        return new Layer(null, io, tanks);
    }

    /**
     * Whether {@code pos} already belongs to a different smeltery that is still formed -- upstream's
     * {@code MultiblockTinker#isValidSlave}, which refuses any structure block whose servant tile
     * already points at another master. Without it the scan silently steals the block, leaving two
     * formed smelteries fighting over the same tank or drain on every rescan.
     *
     * <p>Only tanks and I/O blocks (drain, duct, chute) can be claimed here: they are the only
     * structure blocks Forgeweave gives a block entity to remember its core with (see {@link
     * SmelteryControllerBlockEntity}'s note on upstream's servants), and all of them are wall-only, so
     * nothing on the floor or in the interior can carry a claim. A claim from a core that has been broken, or whose structure no
     * longer forms, is not a claim -- same as upstream's {@code hasValidMaster}, and what keeps a
     * dead smeltery from locking its tank away from every future one.
     *
     * <p>Asking the owner whether it is still formed goes through {@link
     * SmelteryControllerBlockEntity#isFormed()}, which rescans a stale answer: the owner has no
     * ticker, so a cached "formed" from before its walls came down would otherwise never be refreshed
     * and the claim would never lift. That rescan cannot recurse back into this one -- a core stamps
     * its scan tick before it scans, so re-entering {@code structure()} returns the cached value.
     */
    private static boolean claimedByAnotherCore(Level level, BlockPos pos, BlockPos corePos) {
        BlockPos owner = claimAt(level, pos);
        if (owner == null || owner.equals(corePos)) {
            return false;
        }
        if (!level.isLoaded(owner)) {
            // The owner is out of reach to ask, so leave its claim standing rather than take a block
            // that may well still be part of a working smeltery (#288's other half).
            return true;
        }
        return level.getBlockEntity(owner) instanceof SmelteryControllerBlockEntity other && other.isFormed();
    }

    /** The core a wall block remembers, or {@code null} if it is not the kind of block that remembers one. */
    @Nullable
    private static BlockPos claimAt(Level level, BlockPos pos) {
        return switch (level.getBlockEntity(pos)) {
            case SearedTankBlockEntity tank -> tank.core();
            case SmelteryIoBlockEntity io -> io.core();
            case null, default -> null;
        };
    }

    /** Upstream's {@code getOuterPos}: step along {@code dir} while inside, returning the first block that is not. */
    private static BlockPos walkWhileInterior(Level level, BlockPos from, Direction dir, int limit) {
        BlockPos pos = from;
        for (int i = 0; i < limit && isInterior(level, pos); i++) {
            pos = pos.relative(dir);
        }
        return pos;
    }

    /** Upstream's {@code isInnerBlock}: loaded and air. */
    private static boolean isInterior(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos) && level.getBlockState(pos).isAir();
    }

    private static Result failure(Component message) {
        return new Result(null, List.of(), List.of(), message);
    }

    private static Component at(String key, BlockPos pos) {
        return Component.translatable(key, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Which blocks may play which role, upstream's {@code TinkerSmeltery.validSmelteryBlocks} plus
     * the {@code isFloorBlock} override. Held in a nested class so the sets are built the first time
     * a scan runs rather than when {@link SmelteryScan} is loaded -- the blocks do not exist until
     * registration has run.
     */
    private static final class Valid {
        static final Set<Block> TANKS = Set.of(
                ForgeweaveBlocks.SEARED_TANK.get(),
                ForgeweaveBlocks.SEARED_GAUGE.get(),
                ForgeweaveBlocks.SEARED_WINDOW.get());

        static final Set<Block> FLOOR = Set.of(
                ForgeweaveBlocks.SEARED_STONE.get(),
                ForgeweaveBlocks.SEARED_COBBLESTONE.get(),
                ForgeweaveBlocks.SEARED_PAVER.get(),
                ForgeweaveBlocks.SEARED_BRICKS.get(),
                ForgeweaveBlocks.SEARED_CRACKED_BRICKS.get(),
                ForgeweaveBlocks.SEARED_FANCY_BRICKS.get(),
                ForgeweaveBlocks.SEARED_SQUARE_BRICKS.get(),
                ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS.get(),
                ForgeweaveBlocks.SEARED_SMALL_BRICKS.get(),
                ForgeweaveBlocks.SEARED_ROAD.get(),
                ForgeweaveBlocks.SEARED_TILE.get(),
                ForgeweaveBlocks.SEARED_CREEPER.get());

        /**
         * The blocks that re-expose something of the core's outside the walls, and which therefore
         * get pointed back at it once a scan succeeds ({@link SmelteryIoBlockEntity}).
         *
         * <p>#277 deviation, flagged on the PR: the 1.20 clone tags its duct and chute into
         * {@code SMELTERY_FLOOR} as well as {@code SMELTERY_WALL}. Forgeweave keeps all three I/O
         * blocks wall-only, because the drain already is -- 1.12's {@code isFloorBlock} override
         * accepts nothing but the plain seared blocks, and a floor that takes a duct but not a drain
         * would be the odd rule out.
         */
        static final Set<Block> IO = Set.of(
                ForgeweaveBlocks.SEARED_DRAIN.get(),
                ForgeweaveBlocks.SEARED_DUCT.get(),
                ForgeweaveBlocks.SEARED_CHUTE.get());

        // Plain seared glass (issue #289): a valid wall block, upstream's validSmelteryBlocks
        // membership, but never a floor block -- upstream's isFloorBlock override only ever accepts
        // its plain seared blocks (this class's FLOOR set), excluding glass, tanks and the I/O blocks
        // alike.
        static final Set<Block> WALL = Stream.concat(
                        Stream.concat(FLOOR.stream(), TANKS.stream()),
                        Stream.concat(IO.stream(), Stream.of(ForgeweaveBlocks.SEARED_GLASS.get())))
                .collect(Collectors.toUnmodifiableSet());
    }

    private SmelteryScan() {}
}
