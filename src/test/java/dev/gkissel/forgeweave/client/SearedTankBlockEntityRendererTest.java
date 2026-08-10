package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Regression for #145's fill-height mismatch: a real-display capture showed a 2/3-full tank
 * rendering at roughly 1/8 height and a 1/4-full tank rendering nearly full. Pins {@link
 * SearedTankBlockEntityRenderer#topY} to the fraction it's actually meant to produce, independent
 * of the block entity, camera and capture pipeline the original bug report couldn't rule out.
 */
class SearedTankBlockEntityRendererTest {

    private static final float INSET = 0.01f;
    private static final float TOLERANCE = 0.0001f;

    @Test
    void emptyTankTopIsAtTheInset() {
        assertEquals(INSET, SearedTankBlockEntityRenderer.topY(0f), TOLERANCE);
    }

    @Test
    void fullTankTopIsAtOneMinusInset() {
        assertEquals(1f - INSET, SearedTankBlockEntityRenderer.topY(1f), TOLERANCE);
    }

    @Test
    void twoThirdsFullTopIsAboutTwoThirdsUp() {
        // The reported defect: 2/3 rendered at ~1/8, not ~2/3.
        assertEquals(0.6634f, SearedTankBlockEntityRenderer.topY(2f / 3f), TOLERANCE);
    }

    @Test
    void quarterFullTopIsAboutAQuarterUp() {
        // The reported defect: 1/4 rendered nearly full, not ~1/4.
        assertEquals(0.255f, SearedTankBlockEntityRenderer.topY(0.25f), TOLERANCE);
    }

    @Test
    void topYIsMonotonicInFraction() {
        float previous = SearedTankBlockEntityRenderer.topY(0f);
        for (float fraction = 0.1f; fraction <= 1f; fraction += 0.1f) {
            float current = SearedTankBlockEntityRenderer.topY(fraction);
            assertEquals(true, current > previous, "topY must strictly increase with fraction");
            previous = current;
        }
    }
}
