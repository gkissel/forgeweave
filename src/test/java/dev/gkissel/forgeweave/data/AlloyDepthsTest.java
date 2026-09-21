package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Issue #1106: what {@link AlloyDepths} reads off the shipped {@code alloy_recipe} JSON is what the
 * {@code forgeweave:alloys/deep} and {@code alloys/deepest} item tags are built from, and those two
 * tags are the whole criterion for the alloy advancements. The invariants below are the ones the
 * advancements lean on, not a copy of today's table -- the roster is meant to move without this test
 * having to be rewritten.
 */
class AlloyDepthsTest {

    private static final Map<String, Integer> DEPTHS = AlloyDepths.byMaterial();

    @Test
    void everyShippedAlloyIsReadAndSitsAtLeastOneStepDeep() {
        assertFalse(DEPTHS.isEmpty(), "no alloy recipes were read at all");
        DEPTHS.forEach((metal, depth) ->
                assertTrue(depth >= 1, metal + " is an alloy recipe result, so its depth must be at least 1"));
    }

    @Test
    void manyullynIsOneStepBecauseCobaltAndArditeAreMined() {
        assertEquals(1, DEPTHS.get("manyullyn"),
                "cobalt and ardite are melted straight from ore, so manyullyn is one alloying step");
    }

    @Test
    void anAlloyThatNeedsAnotherAlloyIsDeeperThanIt() {
        // truesteel takes sunsteel, which takes daybrass: the two tags exist to tell those apart, so
        // a change that flattened them would quietly make "deepest" mean nothing.
        assertTrue(DEPTHS.get("truesteel") > DEPTHS.get("sunsteel"),
                "truesteel alloys from sunsteel, so it must read deeper: " + DEPTHS);
        assertTrue(DEPTHS.get("sunsteel") >= 3,
                "sunsteel is the shipped roster's deep end and must stay in the deepest tag: " + DEPTHS);
    }

    @Test
    void bothTagsHaveSomethingToName() {
        assertTrue(DEPTHS.values().stream().anyMatch(depth -> depth >= 2),
                "nothing would land in forgeweave:alloys/deep: " + DEPTHS);
        assertTrue(DEPTHS.values().stream().anyMatch(depth -> depth >= 3),
                "nothing would land in forgeweave:alloys/deepest: " + DEPTHS);
    }
}
