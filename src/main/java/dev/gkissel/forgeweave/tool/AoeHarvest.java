package dev.gkissel.forgeweave.tool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.compat.draconic.modules.DraconicModules;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Modifier;

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

    /**
     * Which extra blocks a tool takes with the one it broke. One constant per shipped behavior.
     *
     * <p>Each constant carries the {@code width x height x depth} box upstream's own
     * {@code IAoeTool#getAOEBlocks} hands {@code ToolHelper#calcAOEBlocks} for that tool, plus how far
     * one expander grows an axis of it (issue #438, upstream {@code tools/ToolEvents
     * #onExtraBlockBreak}: {@code +1} for the small harvest tools, {@code +2} for the large ones).
     * {@link #NONE} and {@link #VEIN} are the two shapes with no box at all, so nothing to widen.
     */
    public enum Shape {
        /** Every M1/M2 tool: no extra blocks. */
        NONE(0, 0, 0, 0, -1),
        /**
         * Pickaxe, shovel, hatchet and kama: upstream {@code AoeToolCore}'s own {@code (1, 1, 1)} --
         * just the block hit, so nothing extra until an expander widens it (issue #438). What makes
         * these {@code Category.AOE} tools upstream, and therefore the ones that accept the expanders
         * at all.
         */
        SINGLE(1, 1, 1, 1, -1),
        /**
         * Mattock: the same {@code (1, 1, 1)} base as {@link #SINGLE}, but upstream grows <em>both</em>
         * axes by the number of expanders applied rather than one axis each -- so one expander makes
         * it 2x2 and two make it 3x3, whichever pair was used ({@code ToolEvents}' {@code int c}
         * branch).
         */
        MATTOCK(1, 1, 1, 1, -1),
        /** Hammer and excavator: the 3x3 plane facing the player, upstream's {@code (3, 3, 1)}. */
        PLANE_3X3(3, 3, 1, 2, 3),
        /** Lumber axe: fells a whole tree, or a 3x3x3 cube when the block is not a tree. */
        TREE_FELL(3, 3, 3, 2, 3),
        /**
         * Scythe: a 3x3x3 cube, plus the crop harvest {@link CropHarvest} runs on right-click. Every
         * extra block breaks regardless of Silk Touch -- see {@link #canBreakExtra}'s javadoc
         * (docs/SCOPE.md issue #298) for why, upstream's {@code breakExtraBlock}/{@code
         * shearExtraBlock} split notwithstanding.
         */
        CUBE_3X3X3(3, 3, 3, 2, 3),
        /** Vein hammer: the connected run of the same block, capped at {@link #VEIN_LIMIT}. */
        VEIN(0, 0, 0, 0, -1);

        private final int baseWidth;
        private final int baseHeight;
        private final int baseDepth;
        private final int expansion;
        private final int expandedDistance;

        Shape(int baseWidth, int baseHeight, int baseDepth, int expansion, int expandedDistance) {
            this.baseWidth = baseWidth;
            this.baseHeight = baseHeight;
            this.baseDepth = baseDepth;
            this.expansion = expansion;
            this.expandedDistance = expandedDistance;
        }

        /**
         * Whether an expander does anything to this shape -- upstream's {@code ModifierAspect.aoeOnly}
         * gate as Forgeweave states it (issue #438). Every {@code AoeToolCore} subclass upstream
         * passes that gate; {@link #VEIN} is the one Forgeweave-only shape it excludes, because a
         * flood fill along a vein has no width or height axis to widen (PR deviation, issue #438).
         */
        public boolean expandable() {
            return expansion > 0;
        }
    }

    /**
     * The {@code width x height} of {@code shape}'s box once {@code axes}' expanders are counted in --
     * upstream {@code ToolEvents#onExtraBlockBreak}, which is the one place those magnitudes live.
     * Returns {@code {width, height}}.
     */
    private static int[] expandedDimensions(Shape shape, Set<Modifier.AoeAxis> axes, int draconicAoe) {
        // #956: a Draconic Evolution area module widens the same box. Its AOEData.aoe is a radius --
        // IModularMiningTool#getMiningArea builds the box that far out in each direction perpendicular
        // to the face -- so n grows both the width and the height by 2n. 0 without that mod or an
        // area module, which leaves every number below exactly as it was.
        int draconic = 2 * draconicAoe;
        if (shape == Shape.MATTOCK) {
            // Upstream's mattock branch grows both axes by the count, not one axis each.
            int both = axes.size();
            return new int[] {shape.baseWidth + both + draconic, shape.baseHeight + both + draconic};
        }
        return new int[] {
            shape.baseWidth + (axes.contains(Modifier.AoeAxis.WIDTH) ? shape.expansion : 0) + draconic,
            shape.baseHeight + (axes.contains(Modifier.AoeAxis.HEIGHT) ? shape.expansion : 0) + draconic
        };
    }

    /**
     * How many extra blocks one vein-mine takes (maintainer decision on issue #157, 2026-08-12).
     * Upstream's 1.20 {@code VeiningAOEIterator} caps by <em>distance</em> instead; a flat block cap
     * is what the decision names, and it is also the one bound that holds however the ore is shaped.
     */
    public static final int VEIN_LIMIT = 64;

    /**
     * A safety bound on one tree fell's connected-log search (issue #299). Upstream's own
     * {@code TreeChopTask} has no such cap -- its queue just grows as the BFS below finds more
     * connected logs -- but the BFS itself still runs eagerly, all at once, rather than growing one
     * tick's neighbours at a time; this is the bound that keeps a pathological build (a solid 100x100
     * log platform) from stalling the server thread computing the shape, even though breaking it is
     * now spread across ticks by {@link TreeChopTask}. A dark-oak or large-jungle trunk is well under
     * this, so no real tree ever reaches it.
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

    /**
     * Every tree fell currently in flight (issue #299). A plain list, ticked from
     * {@link #onLevelTick}: every path that touches it runs on the server thread, same as
     * {@link #breaking} above.
     */
    private static final List<TreeChopTask> activeChops = new ArrayList<>();

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
        // #719 -- a vein runs only while the veinmine key is held, and only on the blocks the tool
        // family's veinmine/<family> tag whitelists. The vein hammer's own vein keeps its VEIN_LIMIT;
        // a single-block tool carrying the veinmine modifier veins VEINMINE_BLOCKS_PER_LEVEL per
        // level. The large tools' area shapes are untouched. See VeinmineKey, ForgeweaveModifiers.
        int veinLimit = VEIN_LIMIT;
        if (shape == Shape.VEIN || shape == Shape.NONE || shape == Shape.SINGLE || shape == Shape.MATTOCK) {
            int modifierLevel = ForgeweaveModifiers.veinmineLevel(tool);
            boolean veins = (shape == Shape.VEIN || modifierLevel > 0)
                    && VeinmineKey.held(player) && VeinmineKey.whitelisted(tool, event.getState());
            if (veins && shape != Shape.VEIN) {
                shape = Shape.VEIN;
                veinLimit = modifierLevel * ForgeweaveModifiers.VEINMINE_BLOCKS_PER_LEVEL;
            } else if (!veins && shape == Shape.VEIN) {
                return;
            }
        }
        // #956: a Draconic Evolution area module gives a shape to a tool that has none of its own,
        // the way it does for DE's own tools -- the box below then widens from a single block.
        if (shape == Shape.NONE && DraconicModules.miningAoe(tool) > 0) {
            shape = Shape.SINGLE;
        }
        if (shape == Shape.NONE) {
            return;
        }
        // A small harvest tool with no expander on it still breaks exactly one block, so bail before
        // the re-trace extraBlocks would otherwise do on every pickaxe swing (issue #438).
        if (isBareSingleBlock(tool, shape)) {
            return;
        }
        List<BlockPos> extra = shape == Shape.VEIN
                ? breakable(tool, player.level(), player, event.getPos(), event.getState(),
                        vein(player.level(), event.getPos(), event.getState(), veinLimit))
                : extraBlocks(tool, player.level(), player, event.getPos(), event.getState(), shape);
        // A tree fell spreads its extra blocks over ticks (issue #299, upstream's own TreeChopTask);
        // every other shape still breaks synchronously with the origin, as before.
        if (shape == Shape.TREE_FELL && player.level() instanceof ServerLevel level && !extra.isEmpty()) {
            activeChops.add(new TreeChopTask(tool, player, level, extra));
        } else {
            breakAll(tool, player, extra);
        }
    }

    /**
     * Advances every in-flight tree fell by one tick's worth of blocks (issue #299). Registered on
     * {@code NeoForge.EVENT_BUS} in {@code Forgeweave} alongside {@link #onBlockBreak}.
     */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (activeChops.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        activeChops.removeIf(task -> task.level == level && !task.tick());
    }

    /**
     * Breaks each position in {@code positions} in turn, exactly as {@link #onBlockBreak}'s own
     * extra blocks do -- the durability, drop and Broken-tool accounting a second break loop would
     * otherwise have to re-derive by hand. {@code cascading_break} ({@code CascadingBreak}, issue
     * #829) is the one caller outside this class's own {@link #onBlockBreak}/{@link TreeChopTask}
     * flow, reusing this rather than writing a third breaker as that issue asks.
     */
    public static void breakEach(ItemStack tool, ServerPlayer player, List<BlockPos> positions) {
        breakAll(tool, player, positions);
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
        if (shape == Shape.NONE || isBareSingleBlock(tool, shape)) {
            return List.of();
        }
        return switch (shape) {
            case NONE -> List.of();
            case SINGLE, MATTOCK, PLANE_3X3, CUBE_3X3X3 ->
                    breakable(tool, level, player, origin, originState, box(tool, player, origin, shape));
            case TREE_FELL -> isTree(level, origin, originState)
                    ? breakable(tool, level, player, origin, originState, trunk(level, origin))
                    : breakable(tool, level, player, origin, originState, box(tool, player, origin, shape));
            case VEIN -> breakable(tool, level, player, origin, originState, vein(level, origin, originState, VEIN_LIMIT));
        };
    }

    /**
     * The candidate extra positions {@code shape} takes along with {@code origin} for a transform
     * (issue #617), rather than a break: the same box/tree candidate shapes {@link #extraBlocks} uses,
     * but without its {@link #canBreakExtra} filter. That filter is a <em>mining</em> one -- it rejects
     * any block the tool is not {@code isCorrectToolForDrops} for -- and a transform's applicability
     * has nothing to do with that tag: a copper scrape target is {@code mineable/pickaxe}, not
     * {@code mineable/axe}; a campfire douse target is {@code mineable/axe}, not {@code
     * mineable/shovel}. Reusing {@link #extraBlocks} would silently drop both from their tool's own
     * AoE. Upstream 1.20 draws exactly this split through its shared {@code AOE_ITERATOR} hook --
     * {@code AOEMatchType.TRANSFORM} against the mining iterator's own match type.
     *
     * <p>Whether a given candidate's transform actually applies is left entirely to the caller, the
     * same way the clicked block itself already decides that through {@code
     * BlockState#getToolModifiedState} -- {@link AxeStrip} and {@link ShovelPath} both do. {@link
     * Shape#VEIN} never backs a transform tool, so it answers empty rather than flood-filling.
     */
    public static List<BlockPos> extraTransformBlocks(ItemStack tool, Level level, Player player, BlockPos origin,
            BlockState originState, Shape shape) {
        if (shape == Shape.NONE || isBareSingleBlock(tool, shape)) {
            return List.of();
        }
        return switch (shape) {
            case NONE, VEIN -> List.of();
            case SINGLE, MATTOCK, PLANE_3X3, CUBE_3X3X3 -> box(tool, player, origin, shape);
            case TREE_FELL -> isTree(level, origin, originState)
                    ? trunk(level, origin)
                    : box(tool, player, origin, shape);
        };
    }

    /**
     * Whether {@code shape} is one of the two whose <em>unexpanded</em> box is the mined block alone
     * ({@link Shape#SINGLE}, {@link Shape#MATTOCK}) and {@code tool} carries no expander -- i.e.
     * "an ordinary pickaxe swing", which must cost nothing extra to answer (issue #438).
     */
    private static boolean isBareSingleBlock(ItemStack tool, Shape shape) {
        return (shape == Shape.SINGLE || shape == Shape.MATTOCK)
                && ForgeweaveModifiers.aoeExpansion(tool).isEmpty()
                && DraconicModules.miningAoe(tool) == 0;
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

    /**
     * Upstream's {@code canBreakExtraBlock}; see the class javadoc for how the checks map over.
     *
     * <p>Scythe/Silk Touch note (docs/SCOPE.md issue #298): upstream's {@code Scythe#breakExtraBlock}
     * is {@code isSilkTouch(stack) ? shearExtraBlock(...) : ToolHelper.breakExtraBlock(...)} -- but
     * {@code shearExtraBlock}'s own fallback, for any block that isn't {@code IShearable}, is that
     * exact same {@code breakExtraBlock}. Read plainly, upstream's extra blocks break either way;
     * Silk Touch only adds a shearing attempt in front for blocks that support it. Forgeweave has no
     * block-shearing to add in front of anything (deliberate -- {@link CropHarvest}'s own javadoc:
     * leaves already come off effectively through the {@code mineable/hoe} tag alone), so this method
     * makes no Silk Touch check at all; every extra block breaks through vanilla's normal
     * {@code destroyBlock} regardless of the enchantment.
     *
     * <p>What Silk Touch still changes, for free: {@code destroyBlock} reads the swinging player's
     * actual held item -- enchantments included -- when it resolves each block's drops, so a
     * silk-touch scythe's AoE already gets silk-touch-style drops (a sheared leaf block instead of a
     * sapling roll, {@code minecraft:cobweb} instead of string) precisely where vanilla's own loot
     * tables branch on the enchantment, with no Forgeweave code needed for it. That is this
     * architecture's equivalent of {@code shearExtraBlock}'s intent, adapted from the 1.12
     * {@code IShearable}-block mechanism upstream used to express it.
     */
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
     * {@code shape}'s box around {@code origin}, minus the origin -- upstream's
     * {@code ToolHelper#calcAOEBlocks} ported whole (issue #438). Before the expanders it was enough
     * to hardcode a 3x3 plane and a 3x3x3 cube, because at odd sizes both are simply centered on the
     * block hit; an expander can make an axis <b>even</b> (a pickaxe's 2-wide sweep, a mattock's 2x2),
     * and which of the two candidate positions such a box occupies is exactly what all of upstream's
     * half-block arithmetic decides -- so the whole method is now here rather than its odd-size
     * special case.
     *
     * <p>The three branches are upstream's, unchanged: the horizontal faces map the box onto the
     * player's own facing (so "width" is always across the player's view), the north/south faces map
     * it to x/y and the east/west faces to z/y. {@code distance}, when the shape sets one, is
     * upstream's manhattan clip -- {@code ToolEvents} sets it to 3 for the large tools the moment any
     * expander is present, which is what stops an expanded 5x5 from taking its four far corners.
     */
    private static List<BlockPos> box(ItemStack tool, Player player, BlockPos origin, Shape shape) {
        Set<Modifier.AoeAxis> axes = ForgeweaveModifiers.aoeExpansion(tool);
        int[] dimensions = expandedDimensions(shape, axes, DraconicModules.miningAoe(tool));
        int width = dimensions[0];
        int height = dimensions[1];
        int depth = shape.baseDepth;
        int distance = axes.isEmpty() ? -1 : shape.expandedDistance;

        Aim aim = aim(player, origin);
        Direction face = aim.face();
        Vec3 hit = aim.hit();
        int x;
        int y;
        int z;
        BlockPos start = origin;
        switch (face) {
            case DOWN, UP -> {
                Direction facing = player.getDirection();
                x = facing.getStepX() * height + facing.getStepZ() * width;
                y = face.getAxisDirection().getStep() * -depth;
                z = facing.getStepX() * width + facing.getStepZ() * height;
                start = start.offset(-x / 2, 0, -z / 2);
                if (x % 2 == 0) {
                    if (x > 0 && hit.x - origin.getX() > 0.5) {
                        start = start.offset(1, 0, 0);
                    } else if (x < 0 && hit.x - origin.getX() < 0.5) {
                        start = start.offset(-1, 0, 0);
                    }
                }
                if (z % 2 == 0) {
                    if (z > 0 && hit.z - origin.getZ() > 0.5) {
                        start = start.offset(0, 0, 1);
                    } else if (z < 0 && hit.z - origin.getZ() < 0.5) {
                        start = start.offset(0, 0, -1);
                    }
                }
            }
            case NORTH, SOUTH -> {
                x = width;
                y = height;
                z = face.getAxisDirection().getStep() * -depth;
                start = start.offset(-x / 2, -y / 2, 0);
                if (x % 2 == 0 && hit.x - origin.getX() > 0.5) {
                    start = start.offset(1, 0, 0);
                }
                if (y % 2 == 0 && hit.y - origin.getY() > 0.5) {
                    start = start.offset(0, 1, 0);
                }
            }
            default -> {
                x = face.getAxisDirection().getStep() * -depth;
                y = height;
                z = width;
                start = start.offset(0, -y / 2, -z / 2);
                if (y % 2 == 0 && hit.y - origin.getY() > 0.5) {
                    start = start.offset(0, 1, 0);
                }
                if (z % 2 == 0 && hit.z - origin.getZ() > 0.5) {
                    start = start.offset(0, 0, 1);
                }
            }
        }

        List<BlockPos> out = new ArrayList<>(Math.abs(x * y * z));
        for (int xp = start.getX(); xp != start.getX() + x; xp += Integer.signum(x)) {
            for (int yp = start.getY(); yp != start.getY() + y; yp += Integer.signum(y)) {
                for (int zp = start.getZ(); zp != start.getZ() + z; zp += Integer.signum(z)) {
                    if (xp == origin.getX() && yp == origin.getY() && zp == origin.getZ()) {
                        continue;
                    }
                    if (distance > 0 && Math.abs(xp - origin.getX()) + Math.abs(yp - origin.getY())
                            + Math.abs(zp - origin.getZ()) > distance) {
                        continue;
                    }
                    out.add(new BlockPos(xp, yp, zp));
                }
            }
        }
        return out;
    }

    /** Where on {@code origin} the player is aiming -- {@link #aim}'s answer. */
    private record Aim(Direction face, Vec3 hit) {}

    /**
     * Which face of {@code origin} the player is breaking, and where on it. Upstream reads both off
     * the ray trace it does anyway ({@code mop.sideHit} / {@code mop.hitVec}); 1.21's break event
     * doesn't carry either, so this re-traces, and falls back to the axis the player is looking down
     * -- aimed at the block's centre -- when the trace lands somewhere else (a block broken through a
     * modifier's reach bonus, a GameTest's mock player looking at nothing). A centre hit sits exactly
     * on the {@code > 0.5} boundary {@link #box}'s even-axis arithmetic tests, so it deterministically
     * takes the lower half, which is upstream's own behaviour for a dead-centre hit.
     */
    private static Aim aim(Player player, BlockPos origin) {
        HitResult trace = player.pick(player.blockInteractionRange() + 1.0, 0.0F, false);
        if (trace instanceof BlockHitResult block && block.getBlockPos().equals(origin)) {
            return new Aim(block.getDirection(), block.getLocation());
        }
        return new Aim(Direction.getNearest(player.getLookAngle()).getOpposite(), Vec3.atCenterOf(origin));
    }

    /**
     * Upstream {@code LumberAxe#detectTree} (issue #299): a stack-based search rather than a single
     * column climb, so a multi-trunk or irregular tree (a 2x2 dark oak/jungle base, buttress roots) is
     * still recognized even when the block actually clicked tops out short of the canopy. From each
     * log column found, climb straight up to its top, then push the four horizontal-neighbour columns
     * one block above that top as new candidates -- which is how a short corner column's search still
     * reaches a taller neighbouring trunk's real top before the leaf check runs. {@code highest} always
     * holds the tallest top found so far and gates which candidates are worth exploring (upstream's own
     * {@code candidate.getY() > pos.getY()} bound), so the search terminates once nothing taller is
     * left to climb. Five leaves around that final top is upstream's own threshold, and what keeps a
     * player's log-cabin wall from felling itself.
     */
    private static boolean isTree(Level level, BlockPos origin, BlockState originState) {
        if (!originState.is(BlockTags.LOGS)) {
            return false;
        }
        BlockPos highest = null;
        Deque<BlockPos> candidates = new ArrayDeque<>();
        candidates.push(origin);
        while (!candidates.isEmpty()) {
            BlockPos candidate = candidates.pop();
            if ((highest == null || candidate.getY() > highest.getY()) && level.getBlockState(candidate).is(BlockTags.LOGS)) {
                BlockPos top = candidate.above();
                while (level.getBlockState(top).is(BlockTags.LOGS)) {
                    top = top.above();
                }
                highest = top;
                candidates.push(top.north());
                candidates.push(top.east());
                candidates.push(top.south());
                candidates.push(top.west());
            }
        }
        if (highest == null) {
            return false;
        }
        int leaves = 0;
        for (BlockPos pos : BlockPos.betweenClosed(highest.offset(-1, -1, -1), highest.offset(1, 1, 1))) {
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
    private static List<BlockPos> vein(Level level, BlockPos origin, BlockState originState, int limit) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && out.size() < limit) {
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
     * A tree fell's server-thread-friendly chop, upstream's own {@code TreeChopTask} (issue #299):
     * breaks {@link #BLOCKS_PER_TICK} of its precomputed {@link #blocks} queue per tick instead of the
     * whole trunk in the same tick the origin log came down, and stops the moment the tool goes Broken
     * mid-chop.
     *
     * <p>Adaptation flagged: upstream recomputes each block's neighbours as the chop reaches it, one
     * tick's worth at a time, and sizes {@code blocksPerTick} off a reach/AoE event this port has no
     * equivalent of. Forgeweave's {@link #trunk} already has to run eagerly and once, bounded by
     * {@link #TREE_LIMIT}, to answer {@link #extraBlocks} for the tests that assert the shape directly
     * -- so this task reuses that same precomputed, already-{@link #canBreakExtra}-filtered list rather
     * than re-deriving it incrementally; only the actual breaking (drops, XP, block updates -- the part
     * upstream's own comment says exists to avoid a server hitch) is spread across ticks.
     * ponytail: flat {@link #BLOCKS_PER_TICK} instead of porting the reach-scaled speed upstream derives
     * per swing; revisit if a real tree ever needs felling faster or slower than "a few ticks".
     */
    private static final class TreeChopTask {
        /** A normal tree (well under a few dozen logs) still falls within a handful of ticks. */
        private static final int BLOCKS_PER_TICK = 8;

        private final ItemStack tool;
        private final ServerPlayer player;
        private final ServerLevel level;
        private final Queue<BlockPos> blocks;

        private TreeChopTask(ItemStack tool, ServerPlayer player, ServerLevel level, List<BlockPos> blocks) {
            this.tool = tool;
            this.player = player;
            this.level = level;
            this.blocks = new ArrayDeque<>(blocks);
        }

        /** Breaks one tick's budget. Returns false once finished -- queue drained or tool Broken. */
        private boolean tick() {
            int left = BLOCKS_PER_TICK;
            while (left > 0) {
                if (blocks.isEmpty() || ToolItem.isBroken(tool)) {
                    return false;
                }
                BlockPos pos = blocks.remove();
                if (!level.getBlockState(pos).isAir()) {
                    breaking = true;
                    try {
                        player.gameMode.destroyBlock(pos);
                    } finally {
                        breaking = false;
                    }
                }
                left--;
            }
            return true;
        }
    }

    private AoeHarvest() {}
}
