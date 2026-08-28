package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Issue #728: the pure arithmetic of refilling overslime at the station, the 1.20 clone's
 * {@code OverslimeModifierRecipe#getValidatedResult}/{@code updateInputs} pair -- as many reagents as
 * the missing amount needs, rounded up, the overshoot wasted.
 */
class OverslimeRefillTest {

    @Test
    void fillsFromEveryReagentSlotInOrderAndStopsAtCapacity() {
        // 40 missing: two green balls (20 each) fill it exactly; the blue ball is untouched.
        OverslimeRefill.Fill fill = OverslimeRefill.fill(10, 50, new int[] {2, 1}, new int[] {20, 50});
        assertEquals(50, fill.amount());
        assertArrayEquals(new int[] {2, 0}, fill.used());
    }

    @Test
    void roundsUpAndWastesTheOvershoot() {
        // 10 missing, one blue ball restores 50: the ball is spent, the amount clamps at capacity.
        OverslimeRefill.Fill fill = OverslimeRefill.fill(40, 50, new int[] {3}, new int[] {50});
        assertEquals(50, fill.amount());
        assertArrayEquals(new int[] {1}, fill.used());
    }

    @Test
    void partialFillWhenTheReagentsRunOut() {
        OverslimeRefill.Fill fill = OverslimeRefill.fill(0, 50, new int[] {1, 0}, new int[] {20, 20});
        assertEquals(20, fill.amount());
        assertArrayEquals(new int[] {1, 0}, fill.used());
    }
}
