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
 * An ore vein that is as likely to go off in your face as it is to hand you its drop. Shipped by
 * {@code brimspar_ore} (issue #903, the Nether fuel ore whose crystals melt into the fuel ladder's
 * 1900-degree rung) and, since issue #910, {@code fulmenite_ore} -- the same behaviour with its own
 * numbers, which is what made this class shared rather than brimspar's own subclass.
 *
 * <p>Two rolls, both parameters rather than constants because the two ores gamble at different odds
 * (see {@code ForgeweaveBlocks} for each one's numbers and the reasoning behind them):
 *
 * <ul>
 *   <li>{@code harvestBlastChance} -- a player breaking it detonates it instead of harvesting it.
 *       The block is removed first, so the blast drops nothing at all: the drop is lost, which is
 *       the whole cost. Silk Touch does not defuse it (the roll happens before any tool is consulted)
 *       and creative mode skips it, matching how vanilla's own unstable TNT reads its player.</li>
 *   <li>{@code chainBlastChance} -- a vein caught in someone else's explosion chains, so one
 *       careless detonation can cascade through a cluster. Rolled in {@link #wasExploded}, which the
 *       explosion calls after it has already cleared the block.</li>
 * </ul>
 *
 * <p>{@code blastPower} is small-to-medium on vanilla's own scale: below TNT's 4, deliberately
 * survivable in decent armor -- enough to open a hole in the host rock and hurt, not enough to erase
 * a mining corridor. A vein that chains has to be destructible by the blast that sets it off, so
 * every block using this class registers a blast resistance well under its hardness (#907); at the
 * hardness-matching 10 every other Forgeweave ore carries, the chain rule would be dead code.
 *
 * <p><b>Test seam.</b> Both rolls read {@link #rolls(Level)}, which answers {@link #rollOverride}
 * when a test has installed one and the level's own random otherwise. That is what lets
 * {@code UnstableOreGameTests} force "always explodes" and "never explodes" deterministically instead
 * of hammering a probabilistic block; production never touches it. Same fixed-{@code RandomSource}
 * technique {@code BeheadingGameTests} already uses on the beheading roll, moved behind a field here
 * because the roll sits inside a vanilla block override with no argument to inject through.
 */
public class UnstableOreBlock extends Block {

    /** Brimspar's flavor color: what the art script recolors its sprites to, and {@code ForgeweaveFluids#BRIMSPAR}'s own tint. */
    public static final int BRIMSPAR_CRYSTAL_COLOR = 0xE8B923;

    /** @see UnstableOreBlock the class javadoc's "Test seam" note. */
    @Nullable
    private static RandomSource rollOverride;

    private final float harvestBlastChance;
    private final float chainBlastChance;
    private final float blastPower;

    /**
     * Installs (or clears, with {@code null}) the {@link RandomSource} both instability rolls read.
     * GameTests only -- set it, drive the break, and clear it in a {@code finally}, the same
     * set/assert/restore shape {@code SmelteryAlloyGameTests} uses for its config flips, because
     * GameTests in one batch tick concurrently and this is global state.
     */
    public static void forceRolls(@Nullable RandomSource random) {
        rollOverride = random;
    }

    public UnstableOreBlock(Properties properties, float harvestBlastChance, float chainBlastChance, float blastPower) {
        super(properties);
        this.harvestBlastChance = harvestBlastChance;
        this.chainBlastChance = chainBlastChance;
        this.blastPower = blastPower;
    }

    /** Chance a player-harvested vein detonates instead of dropping its ore. */
    public float harvestBlastChance() {
        return harvestBlastChance;
    }

    /** Chance a vein destroyed by an explosion detonates in turn. */
    public float chainBlastChance() {
        return chainBlastChance;
    }

    private static RandomSource rolls(Level level) {
        RandomSource override = rollOverride;
        return override != null ? override : level.getRandom();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && rolls(level).nextFloat() < harvestBlastChance) {
            level.removeBlock(pos, false);
            detonate(level, pos);
            return Blocks.AIR.defaultBlockState();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide() && rolls(level).nextFloat() < chainBlastChance) {
            detonate(level, pos);
        }
    }

    private void detonate(Level level, BlockPos pos) {
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, blastPower,
                Level.ExplosionInteraction.BLOCK);
    }
}
