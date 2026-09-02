package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Brimspar ore (issue #903): the Nether ore whose crystals melt into the fuel ladder's 1900-degree
 * rung ({@code ForgeweaveFluids#BRIMSPAR}). Unlike every other Forgeweave ore it is <b>unstable</b> --
 * a vein of it is as likely to go off in your face as it is to hand you crystals.
 *
 * <p>Two rolls, both this issue's own design numbers (TAIGA's unstable ore is the inspiration for the
 * <em>behaviour</em>; CLAUDE.md makes that clone inspiration-only, so nothing here is ported):
 *
 * <ul>
 *   <li>{@link #HARVEST_BLAST_CHANCE} -- a player breaking it detonates it instead of harvesting it.
 *       The block is removed first, so the blast drops nothing at all: the crystals are lost, which is
 *       the whole cost. Silk Touch does not defuse it (the roll happens before any tool is consulted)
 *       and creative mode skips it, matching how vanilla's own unstable TNT reads its player.</li>
 *   <li>{@link #CHAIN_BLAST_CHANCE} -- a vein caught in someone else's explosion chains, so one
 *       careless detonation can cascade through a cluster. Rolled in {@link #wasExploded}, which the
 *       explosion calls after it has already cleared the block.</li>
 * </ul>
 *
 * <p>{@link #BLAST_POWER} is small-to-medium on vanilla's own scale: below TNT's 4, above a creeper's
 * fuse-less 3 only in that it is deliberately survivable in netherite -- enough to open a hole in
 * netherrack and hurt, not enough to erase a mining corridor.
 *
 * <p><b>Test seam.</b> Both rolls read {@link #rolls(Level)}, which answers {@link #ROLL_OVERRIDE}
 * when a test has installed one and the level's own random otherwise. That is what lets
 * {@code BrimsparOreGameTests} force "always explodes" and "never explodes" deterministically instead
 * of hammering a probabilistic block; production never touches it. Same fixed-{@code RandomSource}
 * technique {@code BeheadingGameTests} already uses on the beheading roll, moved behind a field here
 * because the roll sits inside a vanilla block override with no argument to inject through.
 */
public class BrimsparOreBlock extends Block {

    /** The flavor color the art script recolors brimspar's sprites to, and {@code ForgeweaveFluids#BRIMSPAR}'s own tint. */
    public static final int CRYSTAL_COLOR = 0xE8B923;

    /** Chance a player-harvested vein detonates instead of dropping its crystals. */
    public static final float HARVEST_BLAST_CHANCE = 0.25F;
    /** Chance a vein destroyed by an explosion detonates in turn. */
    public static final float CHAIN_BLAST_CHANCE = 0.5F;
    /** Vanilla explosion power -- TNT is 4, a creeper 3. */
    public static final float BLAST_POWER = 2.5F;

    /** @see BrimsparOreBlock the class javadoc's "Test seam" note. */
    @Nullable
    private static RandomSource rollOverride;

    /**
     * Installs (or clears, with {@code null}) the {@link RandomSource} both instability rolls read.
     * GameTests only -- set it, drive the break, and clear it in a {@code finally}, the same
     * set/assert/restore shape {@code SmelteryAlloyGameTests} uses for its config flips, because
     * GameTests in one batch tick concurrently and this is global state.
     */
    public static void forceRolls(@Nullable RandomSource random) {
        rollOverride = random;
    }

    public BrimsparOreBlock(Properties properties) {
        super(properties);
    }

    private static RandomSource rolls(Level level) {
        RandomSource override = rollOverride;
        return override != null ? override : level.getRandom();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && rolls(level).nextFloat() < HARVEST_BLAST_CHANCE) {
            // Clear the vein before the blast so neither the explosion nor the harvest that follows
            // has an ore block left to drop from: playerDestroy runs against the state returned here.
            level.removeBlock(pos, false);
            detonate(level, pos);
            return Blocks.AIR.defaultBlockState();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide() && rolls(level).nextFloat() < CHAIN_BLAST_CHANCE) {
            detonate(level, pos);
        }
    }

    private static void detonate(Level level, BlockPos pos) {
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, BLAST_POWER,
                Level.ExplosionInteraction.BLOCK);
    }
}
