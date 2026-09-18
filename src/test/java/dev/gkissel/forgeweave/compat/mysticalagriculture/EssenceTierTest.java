package dev.gkissel.forgeweave.compat.mysticalagriculture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * The essence-tier map (issue #999): total over {@link TrackBOre.Tier}, monotone, and the source of
 * the augment slot count. {@link EssenceTier#forOre} is written as a switch with no default branch,
 * so a new mining rung fails compilation rather than silently landing on prudentium -- these tests
 * pin the behaviour that switch is there to protect, which the compiler alone cannot express.
 */
class EssenceTierTest {

    /** Every mining rung answers, which is what "total" means from the outside. */
    @ParameterizedTest
    @EnumSource(TrackBOre.Tier.class)
    void everyMiningRungMapsToAnEssenceTier(TrackBOre.Tier tier) {
        assertNotNull(EssenceTier.forOre(tier), tier + " has no essence tier");
    }

    /** The ladder never goes down: a harder-to-mine material never grows a cheaper essence. */
    @Test
    void theMapIsMonotoneOverTheMiningLadder() {
        TrackBOre.Tier[] rungs = TrackBOre.Tier.values();
        for (int i = 1; i < rungs.length; i++) {
            EssenceTier lower = EssenceTier.forOre(rungs[i - 1]);
            EssenceTier higher = EssenceTier.forOre(rungs[i]);
            assertTrue(higher.ordinal() >= lower.ordinal(),
                    rungs[i] + " (" + higher + ") sits below " + rungs[i - 1] + " (" + lower + ")");
        }
    }

    /** The table #999 settled, spelled out so a silent re-pairing shows up as a diff here. */
    @Test
    void theTableIsTheOneTheIssueSettled() {
        assertEquals(EssenceTier.PRUDENTIUM, EssenceTier.forOre(TrackBOre.Tier.STONE));
        assertEquals(EssenceTier.TERTIUM, EssenceTier.forOre(TrackBOre.Tier.DIAMOND));
        assertEquals(EssenceTier.IMPERIUM, EssenceTier.forOre(TrackBOre.Tier.NETHERITE));
        assertEquals(EssenceTier.SUPREMIUM, EssenceTier.forOre(TrackBOre.Tier.HARDCINDER));
        assertEquals(EssenceTier.SUPREMIUM, EssenceTier.forOre(TrackBOre.Tier.WARSPAR));
        // The one judgment call: the top rung takes the top essence rather than a third supremium.
        assertEquals(EssenceTier.AWAKENED_SUPREMIUM, EssenceTier.forOre(TrackBOre.Tier.RESONITE));
    }

    /** Inferium is Mystical Agriculture's own starter essence and no Forgeweave crop claims it. */
    @Test
    void noCropTierMapsToInferium() {
        for (TrackBOre.Tier tier : TrackBOre.Tier.values()) {
            assertTrue(EssenceTier.forOre(tier) != EssenceTier.INFERIUM,
                    tier + " maps to inferium, which belongs to Mystical Agriculture's own crops");
        }
    }

    /** D-M8-20's slot rule: one, two for awakened and the rung above it. */
    @Test
    void augmentSlotsAreOneExceptAwakenedAndAbove() {
        for (EssenceTier tier : EnumSet.range(EssenceTier.INFERIUM, EssenceTier.SUPREMIUM)) {
            assertEquals(1, tier.augmentSlots(), tier + " should have one augment slot");
        }
        assertEquals(2, EssenceTier.AWAKENED_SUPREMIUM.augmentSlots());
        assertEquals(2, EssenceTier.INSANIUM.augmentSlots());
    }

    /** Every tier carries the path of a real Mystical Agriculture id, lowercase and underscored. */
    @ParameterizedTest
    @EnumSource(EssenceTier.class)
    void tierIdsAreRegistryPaths(EssenceTier tier) {
        assertTrue(tier.id().matches("[a-z_]+"), tier + " has a non-path id: " + tier.id());
        assertEquals(tier.name().toLowerCase(java.util.Locale.ROOT), tier.id());
    }
}
