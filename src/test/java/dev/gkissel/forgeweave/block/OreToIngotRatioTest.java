package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #276: upstream 1.12's {@code oreToIngotRatio}, ported onto the melt-time ore bonus
 * Forgeweave already had (#99). Drives {@code SmelteryControllerBlockEntity#oreAmount} directly --
 * it takes the ratio as a parameter for exactly this reason, because the option lives in a
 * {@code SERVER}-type config spec that no unit test environment loads.
 *
 * <p>The other half of the coverage is the existing {@code SmelteryMeltingGameTests}: those melt
 * real ore in a real smeltery at the default ratio and pin the same numbers asserted here, so they
 * are what proves {@code finishMelting} actually routes through this method.
 */
class OreToIngotRatioTest {

    private static final float STANDARD = SmelteryCore.STANDARD.yieldMultiplier();
    private static final float NETHER = SmelteryCore.NETHER.yieldMultiplier();

    @Test
    void theDefaultRatioLeavesEveryShippedYieldExactlyWhereIssue99PutIt() {
        assertEquals(216, SmelteryControllerBlockEntity.oreAmount(
                MeltingRecipe.VALUE_INGOT, STANDARD, ForgeweaveConfig.ORE_TO_INGOT_BASELINE),
                "one iron ore under a Standard Core is still 1.5 ingots");
        assertEquals(288, SmelteryControllerBlockEntity.oreAmount(
                MeltingRecipe.VALUE_INGOT, NETHER, ForgeweaveConfig.ORE_TO_INGOT_BASELINE),
                "one iron ore under a Nether Core is still 2 ingots, upstream's own oreToIngotRatio");
    }

    @Test
    void loweringTheRatioLowersBothCoreTiersTogether() {
        // Ratio 1 is upstream's floor: one ore, one ingot -- at the top tier.
        assertEquals(MeltingRecipe.VALUE_INGOT,
                SmelteryControllerBlockEntity.oreAmount(MeltingRecipe.VALUE_INGOT, NETHER, 1.0D));
        assertEquals(108, SmelteryControllerBlockEntity.oreAmount(MeltingRecipe.VALUE_INGOT, STANDARD, 1.0D),
                "the Standard Core keeps its 3/4-of-the-top-tier share rather than being flattened");
    }

    @Test
    void raisingTheRatioRaisesYieldProportionally() {
        assertEquals(4 * MeltingRecipe.VALUE_INGOT,
                SmelteryControllerBlockEntity.oreAmount(MeltingRecipe.VALUE_INGOT, NETHER, 4.0D));
    }

    @Test
    void aFractionalResultFloorsRatherThanRounds() {
        // 144 x 1.5 x (1.1 / 2) = 118.8 -- the ordinary int cast this replaced, kept deliberately.
        assertEquals(118, SmelteryControllerBlockEntity.oreAmount(MeltingRecipe.VALUE_INGOT, STANDARD, 1.1D));
    }
}
