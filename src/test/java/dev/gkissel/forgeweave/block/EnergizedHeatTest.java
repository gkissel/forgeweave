package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The energized tank's arithmetic (docs/SCOPE.md M8, D-M8-11; issue #972): every number the issue
 * asks to write down, pinned here at the temperatures the shipped fuel ladder actually uses.
 *
 * <p>Original Forgeweave design, so there is no upstream file to pin against -- this pins the
 * decisions instead. The in-world half lives in {@code EnergizedTankGameTests}.
 *
 * <p>Pure integer maths, so no Minecraft bootstrap: {@link EnergizedHeat} takes its config values as
 * parameters, exactly so this can pass them explicitly rather than standing a server up.
 */
class EnergizedHeatTest {
    /** The shipped fuel ladder's own temperatures (#897, {@code SmelteryFuelGameTests}'s own rungs). */
    private static final int LAVA = 1300;
    private static final int BLAZING_BLOOD = 1500;
    private static final int MOLTEN_MAGMA = 1700;
    private static final int BRIMSPAR = 1900;
    private static final int PYREALLOY = 2100;

    private static final int BASE = ForgeweaveConfig.ENERGIZED_TANK_RF_PER_MELT_TICK_BASE_DEFAULT;
    private static final int DIVISOR = ForgeweaveConfig.ENERGIZED_TANK_TEMPERATURE_DIVISOR_DEFAULT;
    private static final double OVERDRIVE = ForgeweaveConfig.ENERGIZED_TANK_OVERDRIVE_DEFAULT;

    private static int cost(int temperature) {
        return EnergizedHeat.costPerMeltTick(temperature, BASE, DIVISOR, false, OVERDRIVE);
    }

    @Test
    void costsBaseTimesTemperatureOverTheDivisorAtEveryLadderRung() {
        assertEquals(130, cost(LAVA), "100 x 1300 / 1000");
        assertEquals(150, cost(BLAZING_BLOOD), "100 x 1500 / 1000");
        assertEquals(170, cost(MOLTEN_MAGMA), "100 x 1700 / 1000");
        assertEquals(190, cost(BRIMSPAR), "100 x 1900 / 1000");
        assertEquals(210, cost(PYREALLOY), "100 x 2100 / 1000");
        assertEquals(100, cost(DIVISOR), "a fuel sitting exactly on the divisor costs the base");
    }

    @Test
    void aSampleWithNoFuelTemperatureCostsNothingBecauseItHeatsNothing() {
        assertEquals(0, cost(0));
        assertEquals(0, cost(-1), "a nonsense temperature is the same non-answer as no temperature");
    }

    @Test
    void overdriveMultipliesTheCostByItsFactor() {
        assertEquals(260, EnergizedHeat.costPerMeltTick(LAVA, BASE, DIVISOR, true, OVERDRIVE),
                "the default factor is 2.0, so lava's 130 doubles");
        assertEquals(325, EnergizedHeat.costPerMeltTick(LAVA, BASE, DIVISOR, true, 2.5D),
                "130 x 2.5, rounded");
    }

    @Test
    void anyRealTemperatureCostsAtLeastOneSoHeatIsNeverFree() {
        assertEquals(1, EnergizedHeat.costPerMeltTick(LAVA, 1, 1_000_000, false, OVERDRIVE),
                "the division floors to 0, and a floor of 1 is what stops the block being an infinite fuel");
        assertEquals(0, EnergizedHeat.costPerMeltTick(LAVA, 0, DIVISOR, false, OVERDRIVE),
                "a base of 0 is a pack deliberately asking for free heat, which is its own business");
    }

    @Test
    void aTankAffordsATickOnlyWithAValidSampleAndEnoughStored() {
        assertTrue(new EnergizedHeat.Source(LAVA, 130, 130).affordable(), "exactly enough is enough");
        assertFalse(new EnergizedHeat.Source(LAVA, 130, 129).affordable(), "one short is not partial heat");
        assertFalse(new EnergizedHeat.Source(LAVA, 130, 0).affordable(), "an empty buffer means no heat at all");
        assertFalse(new EnergizedHeat.Source(0, 0, 999_999).affordable(),
                "a full buffer with no valid sample still heats nothing");
    }

    @Test
    void theHottestAffordableTankPays() {
        int paying = EnergizedHeat.pick(List.of(
                new EnergizedHeat.Source(BLAZING_BLOOD, 150, 5_000),
                new EnergizedHeat.Source(PYREALLOY, 210, 5_000),
                new EnergizedHeat.Source(LAVA, 130, 5_000)));

        assertEquals(1, paying, "pyrealloy is the hottest of the three");
    }

    @Test
    void anEmptyHottestTankFallsThroughToTheNextOneDown() {
        int paying = EnergizedHeat.pick(List.of(
                new EnergizedHeat.Source(PYREALLOY, 210, 0),
                new EnergizedHeat.Source(LAVA, 130, 5_000),
                new EnergizedHeat.Source(BLAZING_BLOOD, 150, 5_000)));

        assertEquals(2, paying,
                "the pyrealloy tank cannot pay, so the hottest of the two that can -- blazing blood -- does");
    }

    @Test
    void noAffordableTankMeansNoTankPays() {
        assertEquals(-1, EnergizedHeat.pick(List.of(
                new EnergizedHeat.Source(PYREALLOY, 210, 0),
                new EnergizedHeat.Source(LAVA, 130, 12))));
        assertEquals(-1, EnergizedHeat.pick(List.of()), "no tanks at all is the same answer");
    }

    @Test
    void aTieGoesToTheEarlierTank() {
        int paying = EnergizedHeat.pick(List.of(
                new EnergizedHeat.Source(LAVA, 130, 5_000),
                new EnergizedHeat.Source(LAVA, 130, 5_000)));

        assertEquals(0, paying, "two equal samples resolve the same way every tick, never alternating");
    }
}
