package dev.gkissel.forgeweave.tool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.neoforged.neoforge.event.level.BlockEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import dev.gkissel.forgeweave.item.ToolItem;

/**
 * The large tools' area mining (docs/SCOPE.md M3 issue #157), ported from upstream 1.12's
 * {@code library/utils/ToolHelper#calcAOEBlocks} plus the per-tool shapes in
 * {@code tools/tools/{Hammer,Excavator,LumberAxe,Scythe}.java} (NOTICE.md).
 *
 * <h2>Where it attaches</h2>
 *
 * <p>Upstream hooks {@code IForgeItem#onBlockStartBreak}, which NeoForge 1.21 no longer has. The
 * equivalent single choke point is {@link BlockEvent.BreakEvent}: fired from
 * {@code ServerPlayerGameMode#destroyBlock} before the block is removed, for every player break,
 * server side. Each extra block is then broken through {@code ServerPlayerGameMode#destroyBlock}
 * itself rather than through a re-implementation of it, so drops, XP, block entities, advancement
 * triggers and -- via {@link ToolItem#mineBlock} -- the per-block durability cost are all vanilla's,
 * exactly as upstream's {@code breakExtraBlock} sets out to reproduce by hand.
 *
 * <p>That means each extra block costs the tool the same 1 (block in its {@code mineable/*} tag) or
 * 2 (anything else) that the first block does -- upstream's {@code ToolCore#onBlockDestroyed}, which
 * its {@code breakExtraBlock} reaches through the same {@code stack.onBlockDestroyed} call.
 *
 * <h2>Which blocks</h2>
 *
 * <p>{@link #canBreakExtra} is upstream's {@code canBreakExtraBlock}: never air, only blocks this
 * tool is actually effective on and can harvest, and never something an order of magnitude harder
 * than the block that started the break ("don't hoover up obsidian while mining stone"). The first
 * two collapse into {@link ItemStack#isCorrectToolForDrops}, because a Forgeweave tool's {@code tool}
 * component already carries both the {@code mineable/*} tag and the head material's
 * {@code incorrect_for_tool} tier gate ({@link ToolItem#toolComponent}) -- so the 3x3 honors tool
 * tier without a tier check of its own.
 */
public final class AoeHarvest {

    /** Which extra blocks a tool takes with the one it broke. One constant per shipped behavior. */
    public enum Shape {
        /** Every M1/M2 tool: no extra blocks. */
        NONE,
        /** Hammer and excavator: the 3x3 plane facing the player, upstream's {@code (3, 3, 1)}. */
        PLANE_3X3,
        /** Lumber axe: fells a whole tree, or a 3x3x3 cube when the block is not a tree. */
        TREE_FELL,
        /**
         * Scythe: a 3x3x3 cube, plus the crop harvest {@link CropHarvest} runs on right-click. Takes
         * no extra blocks at all without Silk Touch (docs/SCOPE.md issue #298, see {@link #hasSilkTouch}).
         */
        CUBE_3X3X3,
        /** Vein hammer: the connected run of the same block, capped at {@link #VEIN_LIMIT}. */
        VEIN
    }

    /**
     * How many extra blocks one vein-mine takes (maintainer decision on issue #157, 2026-08-12).
     * Upstream's 1.20 {@code VeiningAOEIterator} caps by <em>distance</em> instead; a flat block cap
     * is what the decision names, and it is also the one bound that holds however the ore is shaped.
     */
    public static final int VEIN_LIMIT = 64;

    /**
     * A safety bound on one tree fell. Upstream has none -- its {@code TreeChopTask} spreads the
     * chop over ticks, so an enormous tree merely takes longer -- but this fells in one call, so a
     * pathological build (a solid 100x100 log platform) would otherwise stall the server thread.
     * A dark-oak or large-jungle trunk is well under this, so no real tree ever reaches it.
     * ponytail: one constant instead of a tick-spread chop task; port the task if trees ever get big
     * enough for the single-tick chop to show up as a hitch.
     */
    public static final int TREE_LIMIT = 512;

    /** Upstream {@code LumberAxe#detectTree}: this many leaves above the trunk make it a tree. */
    private static final int LEAVES_FOR_TREE = 5;

    /** Upstream {@code canBreakExtraBlock}'s {@code refStrength / strength > 10f} bail-out. */
    private static final float MAX_HARDNESS_RATIO = 10.0F;

    /**
     * Breaking an extra block fires {@link BlockEvent.BreakEvent} again, which would sweep from
     * that block in turn. A plain field is enough: every path that sets it runs on the server thread.
     */
    private static boolean breaking;

    /** Registered on the game event bus in {@code Forgeweave}; see the class javadoc. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (breaking || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ToolItem item) || ToolItem.isBroken(tool)) {
            return;
        }
        Shape shape = item.aoeShape();
        if (shape == Shape.NONE) {
            return;
        }
        breakAll(tool, player, extraBlocks(tool, player.level(), player, event.getPos(), event.getState(), shape));
    }

    /**
     * Breaks each position in turn, exactly as the player breaking one block by hand would. Stops
     * the moment the tool goes Broken, upstream's {@code TreeChopTask}'s own {@code isBroken} check.
     */
    private static void breakAll(ItemStack tool, ServerPlayer player, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return;
        }
        breaking = true;
        try {
            for (BlockPos pos : positions) {
                if (ToolItem.isBroken(tool)) {
                    return;
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            breaking = false;
        }
    }

    /**
     * The extra blocks {@code shape} takes along with {@code origin}, already filtered by
     * {@link #canBreakExtra}. Public and side-effect free so a GameTest can assert the shape and the
     * cap directly, without staging a break.
     */
    public static List<BlockPos> extraBlocks(ItemStack tool, Level level, Player player, BlockPos origin,
            BlockState originState, Shape shape) {
        return switch (shape) {
            case NONE -> List.of();
            case PLANE_3X3 -> breakable(tool, level, player, origin, originState, plane(origin, minedFace(player, origin)));
            case CUBE_3X3X3 -> hasSilkTouch(tool)
                    ? breakable(tool, level, player, origin, originState, cube(origin))
                    : List.of();
            case TREE_FELL -> isTree(level, origin, originState)
                    ? breakable(tool, level, player, origin, originState, trunk(level, origin))
                    : breakable(tool, level, player, origin, originState, cube(origin));
            case VEIN -> breakable(tool, level, player, origin, originState, vein(level, origin, originState));
        };
    }

    private static List<BlockPos> breakable(ItemStack tool, Level level, Player player, BlockPos origin,
            BlockState originState, List<BlockPos> candidates) {
        List<BlockPos> out = new ArrayList<>(candidates.size());
        for (BlockPos pos : candidates) {
            if (canBreakExtra(tool, level, player, pos, origin, originState)) {
                out.add(pos);
            }
        }
        return out;
    }

    /** Upstream's {@code canBreakExtraBlock}; see the class javadoc for how the checks map over. */
    private static boolean canBreakExtra(ItemStack tool, Level level, Player player, BlockPos pos, BlockPos origin,
            BlockState originState) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !tool.isCorrectToolForDrops(state)) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return false; // bedrock and friends
        }
        // Written as a product rather than upstream's ratio so an instant-break origin (hardness 0)
        // lands where upstream's division does: it takes nothing harder than itself along.
        return hardness <= originState.getDestroySpeed(level, origin) * MAX_HARDNESS_RATIO;
    }

    /**
     * The 3x3 plane perpendicular to {@code face}, centered on {@code origin}, minus the origin --
     * what upstream's {@code calcAOEBlocks(stack, world, player, origin, 3, 3, 1)} works out to.
     * All of that method's half-block centering arithmetic exists for <em>even</em> widths; at 3x3
     * the plane is centered on the block hit whichever half of the face the cursor was on, so the
     * face is the only thing that has to be recovered.
     */
    private static List<BlockPos> plane(BlockPos origin, Direction face) {
        List<BlockPos> out = new ArrayList<>(8);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                out.add(switch (face.getAxis()) {
                    case X -> origin.offset(0, a, b);
                    case Y -> origin.offset(a, 0, b);
                    case Z -> origin.offset(a, b, 0);
                });
            }
        }
        return out;
    }

    /** Upstream's {@code calcAOEBlocks(..., 3, 3, 3)}: the cube around the origin, minus the origin. */
    private static List<BlockPos> cube(BlockPos origin) {
        List<BlockPos> out = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        out.add(origin.offset(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Which face of {@code origin} the player is breaking. Upstream reads it off the ray trace it
     * does anyway; 1.21's break event doesn't carry it, so this re-traces, and falls back to the
     * axis the player is looking down when the trace lands somewhere else (a block broken through a
     * modifier's reach bonus, a GameTest's mock player looking at nothing).
     */
    private static Direction minedFace(Player player, BlockPos origin) {
        HitResult trace = player.pick(player.blockInteractionRange() + 1.0, 0.0F, false);
        if (trace instanceof BlockHitResult block && block.getBlockPos().equals(origin)) {
            return block.getDirection();
        }
        return Direction.getNearest(player.getLookAngle()).getOpposite();
    }

    /**
     * Upstream {@code LumberAxe#detectTree}, simplified to the trunk directly under the break: walk
     * up from the origin while there are logs, then count leaves in the 3x3x3 around the top. Five
     * is upstream's own threshold, and what keeps a player's log-cabin wall from felling itself.
     */
    private static boolean isTree(Level level, BlockPos origin, BlockState originState) {
        if (!originState.is(BlockTags.LOGS)) {
            return false;
        }
        BlockPos top = origin;
        while (level.getBlockState(top.above()).is(BlockTags.LOGS)) {
            top = top.above();
        }
        int leaves = 0;
        for (BlockPos pos : cube(top)) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES) && ++leaves >= LEAVES_FOR_TREE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every log connected to {@code origin}, upstream's {@code TreeChopTask} expansion exactly: the
     * four horizontal neighbours plus the whole 3x3 one block up, which is what follows a trunk
     * through its branches without wandering into the next tree. Stops at the first non-log in every
     * direction, which is the requirement.
     */
    private static List<BlockPos> trunk(Level level, BlockPos origin) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && out.size() < TREE_LIMIT) {
            BlockPos pos = queue.remove();
            if (!pos.equals(origin)) {
                if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                    continue;
                }
                out.add(pos);
            }
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(facing);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos next = pos.offset(x, 1, z);
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return out;
    }

    /**
     * The connected run of the same block as {@code origin}, six-neighbour flood fill, stopping at
     * {@link #VEIN_LIMIT} blocks -- the shape of the 1.20 branch's {@code VeiningAOEIterator} with
     * the maintainer's block cap in place of its distance cap (see {@link #VEIN_LIMIT}).
     */
    private static List<BlockPos> vein(Level level, BlockPos origin, BlockState originState) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && out.size() < VEIN_LIMIT) {
            BlockPos pos = queue.remove();
            if (!pos.equals(origin)) {
                if (!level.getBlockState(pos).is(originState.getBlock())) {
                    continue;
                }
                out.add(pos);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return out;
    }

    /**
     * The scythe's Silk Touch gate (docs/SCOPE.md issue #298), upstream's {@code Scythe#breakBlock}/
     * {@code #breakExtraBlock}: {@code isSilkTouch(stack) && super.breakBlock(...)} for the block
     * clicked, {@code isSilkTouch(stack) ? shearExtraBlock(...) : breakExtraBlock(...)} for every
     * extra one. Read plainly, upstream still breaks a silk-touch-less scythe's extra blocks --
     * {@code shearExtraBlock}'s own fallback, when a block isn't {@code IShearable}, is
     * {@code breakExtraBlock}, the exact same plain break the non-silk-touch branch calls directly --
     * Silk Touch there only adds a shearing attempt in front of it.
     *
     * <p>Forgeweave has no block-shearing to add in front of anything (deliberate, see
     * {@link CropHarvest}'s own javadoc: leaves already come off effectively through the
     * {@code mineable/hoe} tag alone), so upstream's real distinction -- sheared drops vs plain ones
     * -- has nothing to attach to here. Gating the whole {@link Shape#CUBE_3X3X3} area on the
     * enchantment instead is a deliberate deviation (flagged in the PR for issue #298): the
     * alternative was a silk-touch-less scythe area-mining its whole cube exactly like a silk-touch
     * one, which would make the enchantment invisible on this tool's signature behavior entirely.
     */
    private static boolean hasSilkTouch(ItemStack tool) {
        return tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream()
                .anyMatch(holder -> holder.is(Enchantments.SILK_TOUCH));
    }

    private AoeHarvest() {}
}
