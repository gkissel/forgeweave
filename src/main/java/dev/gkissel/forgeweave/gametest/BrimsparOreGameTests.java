package dev.gkissel.forgeweave.gametest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.BrimsparOreBlock;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #903: brimspar ore's instability, both rolls, forced in both directions.
 *
 * <p>Every test here installs a fixed {@link RandomSource} through
 * {@link BrimsparOreBlock#forceRolls} and clears it in a {@code finally} -- that is the block's own
 * documented test seam, and the reason these assertions are exact instead of statistical. GameTests
 * in one batch tick concurrently and that seam is static, so set/assert/restore has to complete
 * inside a single synchronous test method, the same discipline {@code SmelteryAlloyGameTests} follows
 * for its config flips.
 *
 * <p>The harvest tests reproduce {@code ServerPlayerGameMode#destroyBlock}'s tail by hand
 * ({@link #harvest}) rather than driving a real break: {@code GameTestHelper#makeMockServerPlayerInLevel}
 * hardcodes {@code isCreative() == true}, and creative is exactly the case
 * {@link BrimsparOreBlock#playerWillDestroy} skips. The three lines it stands in for are vanilla's own
 * -- {@code playerWillDestroy}, then {@code onDestroyedByPlayer}, then {@code playerDestroy}'s
 * {@code dropResources} against the state {@code playerWillDestroy} returned -- so the "no drop" claim
 * is measured on the same state the real path would drop from, not asserted about the block in
 * isolation.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BrimsparOreGameTests {

    /** Somewhere well inside the 15x6x9 {@code empty} template, with room for a 2.5-power blast. */
    private static final BlockPos VEIN = new BlockPos(7, 1, 4);
    /** A second vein one block over, for the chain test: brimspar's blast resistance is 1.5, so a blast at {@link #VEIN} destroys it. */
    private static final BlockPos NEIGHBOUR = new BlockPos(8, 1, 4);

    /** A roll of 0 is below every chance in {@link BrimsparOreBlock}, so the vein always goes off. */
    private static final float ALWAYS = 0.0F;
    /** A roll of 0.999 is above every chance there, so it never does. */
    private static final float NEVER = 0.999F;

    /**
     * The harvest roll's "boom" branch: the vein is gone, the break path is handed an air state, and
     * not one crystal reaches the ground. Losing the drop is the entire cost of the gamble, so this is
     * the assertion that matters most.
     */
    @GameTest(template = "empty")
    public static void anExplodingHarvestDropsNothing(GameTestHelper helper) {
        withRoll(ALWAYS, () -> {
            helper.setBlock(VEIN, ForgeweaveBlocks.BRIMSPAR_ORE.get());
            BlockState after = harvest(helper, VEIN);

            helper.assertTrue(after.isAir(),
                    "an exploding harvest must hand the break path an air state so playerDestroy drops nothing, got " + after);
            helper.assertBlockNotPresent(ForgeweaveBlocks.BRIMSPAR_ORE.get(), VEIN);
            helper.assertItemEntityNotPresent(ForgeweaveItems.BRIMSPAR_CRYSTAL.get(), VEIN, 6.0);
        });
        helper.succeed();
    }

    /** And the other branch: a vein that does not go off pays out exactly like any other ore. */
    @GameTest(template = "empty")
    public static void aQuietHarvestDropsItsCrystals(GameTestHelper helper) {
        withRoll(NEVER, () -> {
            helper.setBlock(VEIN, ForgeweaveBlocks.BRIMSPAR_ORE.get());
            harvest(helper, VEIN);

            helper.assertBlockNotPresent(ForgeweaveBlocks.BRIMSPAR_ORE.get(), VEIN);
            helper.assertItemEntityPresent(ForgeweaveItems.BRIMSPAR_CRYSTAL.get(), VEIN, 2.0);
        });
        helper.succeed();
    }

    /**
     * The chain roll: a vein caught in someone else's blast detonates in turn, so one careless
     * explosion cascades through a cluster. Counted off {@code ExplosionEvent.Start} rather than
     * inferred from collateral damage -- "a second explosion happened" is the actual claim, and a
     * blast-radius assertion would only measure vanilla's own falloff maths.
     */
    @GameTest(template = "empty")
    public static void aVeinCaughtInABlastChains(GameTestHelper helper) {
        withRoll(ALWAYS, () -> {
            helper.setBlock(VEIN, ForgeweaveBlocks.BRIMSPAR_ORE.get());
            int explosions = countExplosions(helper, () -> detonateAt(helper, VEIN));

            helper.assertValueEqual(explosions, 2, "explosions after a blast catches one brimspar vein");
            helper.assertBlockNotPresent(ForgeweaveBlocks.BRIMSPAR_ORE.get(), VEIN);
        });
        helper.succeed();
    }

    /** The same blast against a vein whose chain roll misses: one explosion, not two. */
    @GameTest(template = "empty")
    public static void aVeinThatDoesNotChainLeavesOneExplosion(GameTestHelper helper) {
        withRoll(NEVER, () -> {
            helper.setBlock(VEIN, ForgeweaveBlocks.BRIMSPAR_ORE.get());
            int explosions = countExplosions(helper, () -> detonateAt(helper, VEIN));

            helper.assertValueEqual(explosions, 1, "explosions after a blast catches a vein that does not chain");
        });
        helper.succeed();
    }

    /**
     * The cascade the chain rule exists for, end to end from a pickaxe: a player breaks one vein of a
     * two-block cluster, its harvest blast destroys the neighbour, and the neighbour chains -- three
     * explosions from one swing, and both veins gone.
     */
    @GameTest(template = "empty")
    public static void anExplodingHarvestCascadesThroughTheVein(GameTestHelper helper) {
        withRoll(ALWAYS, () -> {
            helper.setBlock(VEIN, ForgeweaveBlocks.BRIMSPAR_ORE.get());
            helper.setBlock(NEIGHBOUR, ForgeweaveBlocks.BRIMSPAR_ORE.get());

            int explosions = countExplosions(helper, () -> harvest(helper, VEIN));

            helper.assertValueEqual(explosions, 2, "the harvest blast plus the neighbour's chain");
            helper.assertBlockNotPresent(ForgeweaveBlocks.BRIMSPAR_ORE.get(), VEIN);
            helper.assertBlockNotPresent(ForgeweaveBlocks.BRIMSPAR_ORE.get(), NEIGHBOUR);
        });
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** Installs a {@link RandomSource} whose {@code nextFloat} always answers {@code roll}, runs {@code body}, and clears it. */
    private static void withRoll(float roll, Runnable body) {
        BrimsparOreBlock.forceRolls(new FixedRoll(roll));
        try {
            body.run();
        } finally {
            BrimsparOreBlock.forceRolls(null);
        }
    }

    /**
     * {@code ServerPlayerGameMode#destroyBlock}'s tail, line for line: ask the block what state the
     * break should proceed against, clear the position, then drop that state's loot. Returns the state
     * {@code playerWillDestroy} answered.
     */
    private static BlockState harvest(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = level.getBlockState(pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        BlockState after = state.getBlock().playerWillDestroy(level, pos, state, player);
        level.removeBlock(pos, false);
        Block.dropResources(after, level, pos, null, player, ItemStack.EMPTY);
        return after;
    }

    /** A vanilla-scale blast centred on {@code relative}; brimspar's 1.5 resistance is well inside its reach. */
    private static void detonateAt(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0F,
                Level.ExplosionInteraction.BLOCK);
    }

    /**
     * Explosions started while {@code body} runs, counted near the test structure only. The event bus
     * is global and GameTests batch concurrently, so an unfiltered counter would pick up any other
     * test's blast; nothing else detonates within 16 blocks of this structure's own vein.
     */
    private static int countExplosions(GameTestHelper helper, Runnable body) {
        Vec3 near = Vec3.atCenterOf(helper.absolutePos(VEIN));
        AtomicInteger seen = new AtomicInteger();
        Consumer<ExplosionEvent.Start> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getExplosion().center().distanceToSqr(near) < 256.0) {
                seen.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        return seen.get();
    }

    /** A {@link RandomSource} whose {@code nextFloat} always answers one fixed value. */
    private static final class FixedRoll extends LegacyRandomSource {
        private final float value;

        FixedRoll(float value) {
            super(0L);
            this.value = value;
        }

        @Override
        public float nextFloat() {
            return value;
        }
    }

    private BrimsparOreGameTests() {}
}
