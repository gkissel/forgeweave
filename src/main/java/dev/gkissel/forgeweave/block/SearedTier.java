package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The walls of a smeltery wear the tier of the core they are built around (maintainer request,
 * 2026-09-05, widened to every wall block on 2026-09-06). Every block {@code SmelteryScan} accepts
 * in a wall or floor -- the twelve plain seared blocks, seared glass, the tank, gauge and window,
 * the drain, duct and chute -- carries {@link #TIER}; swap a Standard Core for a Nether Core, or
 * pour a core up a tier ({@code CoreTransformRecipe}), and the whole shell takes that tier's look,
 * spreading out from the core over {@link #WAVE_TICKS} the way Just Dire Things' goo creeps across
 * blocks. Only the look changes: same blocks, same scan, same block entities and contents, and a
 * broken block still drops its plain item.
 *
 * <p>The spread is server-side scheduling, not a ticking block entity: the core's scan (the one
 * moment it already knows its shell) calls {@link #spreadTier}, which schedules one block tick per
 * mismatched block at a delay proportional to its distance from the core. Each block's own
 * {@code tick} then calls {@link #flip}, which swaps the state and puffs a few block particles of
 * the new face, which is what reads as the goo front moving. A block placed later into a formed
 * tiered smeltery is caught by the next scan the same way.
 */
public final class SearedTier {
    public static final EnumProperty<SmelteryCore> TIER = EnumProperty.create("tier", SmelteryCore.class);

    /** How long the wave takes to reach the block farthest from the core: three seconds. */
    public static final int WAVE_TICKS = 60;

    // ponytail: transient and server-only. A chunk unloading mid-wave just drops its pending flips;
    // the core's next formed scan reschedules whatever is still mismatched.
    private static final Map<GlobalPos, SmelteryCore> PENDING = new ConcurrentHashMap<>();

    private SearedTier() {}

    /** {@code state} at the standard tier -- what every tiered block registers as its default. */
    public static BlockState standard(BlockState state) {
        return state.setValue(TIER, SmelteryCore.STANDARD);
    }

    /**
     * Schedules every tiered block in {@code structure}'s shell (floor and walls, corners included)
     * whose tier is not {@code tier} to flip, nearest to {@code corePos} first and the farthest one
     * {@link #WAVE_TICKS} later. Blocks already waiting on a tick keep their slot and only have their
     * target updated, so a core swapped twice inside one wave still ends on the last tier.
     */
    public static void spreadTier(ServerLevel level, BlockPos corePos, SmelteryCore tier, SmelteryStructure structure) {
        List<BlockPos> mismatched = new ArrayList<>();
        BlockPos min = structure.interiorMin().offset(-1, -1, -1);
        BlockPos max = structure.interiorMax().offset(1, 0, 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (structure.containsInterior(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(TIER) && state.getValue(TIER) != tier) {
                mismatched.add(pos.immutable());
            }
        }
        if (mismatched.isEmpty()) {
            return;
        }
        int farthest = Math.max(1, mismatched.stream().mapToInt(corePos::distManhattan).max().orElse(1));
        for (BlockPos pos : mismatched) {
            if (PENDING.put(GlobalPos.of(level.dimension(), pos), tier) == null) {
                level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 1 + WAVE_TICKS * pos.distManhattan(corePos) / farthest);
            }
        }
    }

    /**
     * A tiered block's scheduled tick: takes this position's pending tier, if any, and applies it.
     * Same block, so a block entity and its contents survive the state change.
     */
    public static void flip(BlockState state, ServerLevel level, BlockPos pos) {
        SmelteryCore tier = PENDING.remove(GlobalPos.of(level.dimension(), pos));
        if (tier == null || !state.hasProperty(TIER) || state.getValue(TIER) == tier) {
            return;
        }
        BlockState next = state.setValue(TIER, tier);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, next),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.0);
    }
}
