package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the seared furnace's heat arithmetic to upstream 1.12's {@code TileSearedFurnace} (issue
 * #442, NOTICE.md): {@code getHeatForStack} is {@code 200 * count / 4}, times {@code 0.8} for
 * food, truncated; and {@code updateHeatRequired} refuses a stack whose result would exceed the
 * input's max stack size or the furnace's 16-per-slot limit.
 */
class SearedFurnaceHeatTest {

    @Test
    void oneItemCostsAQuarterOfVanillasCookTime() {
        assertEquals(50, SearedFurnaceBlockEntity.heatFor(1, false));
    }

    @Test
    void aFullSlotOfSixteenCostsEightHundred() {
        assertEquals(800, SearedFurnaceBlockEntity.heatFor(16, false));
    }

    @Test
    void foodIsTwentyPercentCheaperAndTruncated() {
        // 200 * 3 / 4 = 150; * 0.8 = 120.
        assertEquals(120, SearedFurnaceBlockEntity.heatFor(3, true));
        // 200 * 1 / 4 = 50; * 0.8 = 40.
        assertEquals(40, SearedFurnaceBlockEntity.heatFor(1, true));
        // 200 * 7 / 4 = 350; * 0.8 = 280.0 -> 280 (float 280.00003 truncates to 280).
        assertEquals(280, SearedFurnaceBlockEntity.heatFor(7, true));
    }

    @Test
    void resultMustFitTheInputsOwnStackSizeAndSixteen() {
        assertTrue(SearedFurnaceBlockEntity.resultFits(16, 1, 64), "16 ore -> 16 ingots fits");
        assertFalse(SearedFurnaceBlockEntity.resultFits(16, 2, 64), "16 -> 32 exceeds the 16-per-slot limit");
        assertFalse(SearedFurnaceBlockEntity.resultFits(2, 1, 1), "an unstackable input's result of 2 exceeds its own max of 1");
        assertTrue(SearedFurnaceBlockEntity.resultFits(1, 1, 1), "one unstackable item cooks fine");
    }
}
