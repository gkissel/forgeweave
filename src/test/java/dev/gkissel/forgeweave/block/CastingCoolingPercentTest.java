package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Issue #720: pins the pure percentage math behind {@link CastingBlockEntity#coolingPercent()},
 * which the Jade/WTHIT compat overlays (see the {@code jade}/{@code wthit} packages) read to show
 * a casting table/basin's cooling progress.
 */
class CastingCoolingPercentTest {

    @Test
    void noElapsedTimeIsZeroPercent() {
        assertEquals(0, CastingBlockEntity.coolingPercent(0, 100));
    }

    @Test
    void halfwayThroughTheCooldownIsFiftyPercent() {
        assertEquals(50, CastingBlockEntity.coolingPercent(50, 100));
    }

    @Test
    void fullyElapsedIsOneHundredPercent() {
        assertEquals(100, CastingBlockEntity.coolingPercent(100, 100));
    }

    @Test
    void anOverdueCooldownClampsAtOneHundredPercent() {
        // A stale tick firing late (or a client whose game time briefly outruns the server's) must
        // not report more than "done".
        assertEquals(100, CastingBlockEntity.coolingPercent(250, 100));
    }

    @Test
    void aZeroLengthCooldownIsZeroPercentRatherThanDividingByZero() {
        assertEquals(0, CastingBlockEntity.coolingPercent(10, 0));
    }
}
