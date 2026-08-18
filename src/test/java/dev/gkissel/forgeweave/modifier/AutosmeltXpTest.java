package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * Issue #458's pure half: upstream {@code TraitAutosmelt#blockHarvestDrops}'s probabilistic
 * round-up ({@code float xp = ...; if(xp < 1 && Math.random() < xp) xp += 1f; if(xp >= 1f)
 * dropXp((int) xp)}), ported as {@code ForgeweaveModifiers#roundedSmeltingXp}. The rest of issue
 * #458 (the effectiveness gate, the Silk Touch exclusion, the XP actually landing on the block
 * event) needs a real {@code ServerLevel} and lives in {@code gametest.ModifierGameTests}.
 */
class AutosmeltXpTest {

    @Test
    void oneOrMoreXpAlwaysDropsWithoutRolling() {
        // A random that would fail the test if ever asked for a float: xp >= 1 must never roll.
        RandomSource random = fixedFloat(() -> {
            throw new AssertionError("xp >= 1 must not consult the random source");
        });
        assertEquals(1, ForgeweaveModifiers.roundedSmeltingXp(1.0F, random));
        assertEquals(2, ForgeweaveModifiers.roundedSmeltingXp(2.7F, random), "upstream truncates, it doesn't round");
    }

    @Test
    void zeroXpDropsNothing() {
        assertEquals(0, ForgeweaveModifiers.roundedSmeltingXp(0.0F, fixedFloat(() -> 0.99F)));
    }

    @Test
    void fractionalXpRoundsUpWhenTheRollLandsUnderIt() {
        // 0.7 xp, roll of 0.5 < 0.7 -> rounds up to the full 1 xp.
        assertEquals(1, ForgeweaveModifiers.roundedSmeltingXp(0.7F, fixedFloat(() -> 0.5F)));
    }

    @Test
    void fractionalXpDropsNothingWhenTheRollMissesIt() {
        // 0.7 xp, roll of 0.8 >= 0.7 -> the fraction is lost, same as upstream.
        assertEquals(0, ForgeweaveModifiers.roundedSmeltingXp(0.7F, fixedFloat(() -> 0.8F)));
    }

    private static RandomSource fixedFloat(java.util.function.Supplier<Float> value) {
        return new LegacyRandomSource(0L) {
            @Override
            public float nextFloat() {
                return value.get();
            }
        };
    }
}
