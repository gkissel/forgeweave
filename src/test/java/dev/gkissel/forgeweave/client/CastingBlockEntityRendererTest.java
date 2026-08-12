package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the fill height #182's casting renderer draws its fluid to, the same way {@link
 * SearedTankBlockEntityRendererTest} pins the tank's -- #145 showed that a fill-height formula is
 * exactly what silently drifts when the only check on it is a human looking at a screenshot.
 *
 * <p>The numbers are upstream 1.12's {@code CastingRenderer.Table}/{@code .Basin} constructor
 * arguments: a table's pool lives in the 1/16 recess in its top, a basin's fills the block from its
 * floor at 4/16 to the rim.
 */
class CastingBlockEntityRendererTest {
    private static final float TOLERANCE = 0.0001f;

    private static final float TABLE_SURFACE = 15f / 16f;
    private static final float TABLE_CEILING = 1f + 0.001f;
    private static final float BASIN_SURFACE = 4f / 16f;

    @Test
    void emptyTableSitsOnTheTableSurface() {
        assertEquals(TABLE_SURFACE, CastingBlockEntityRenderer.table().topY(0f), TOLERANCE);
    }

    @Test
    void fullTableBrimsAHairOverTheBlock() {
        assertEquals(TABLE_CEILING, CastingBlockEntityRenderer.table().topY(1f), TOLERANCE);
    }

    @Test
    void halfPouredTableIsHalfwayUpItsRecess() {
        assertEquals(TABLE_SURFACE + (TABLE_CEILING - TABLE_SURFACE) / 2f,
                CastingBlockEntityRenderer.table().topY(0.5f), TOLERANCE);
    }

    @Test
    void emptyBasinSitsOnItsFloor() {
        assertEquals(BASIN_SURFACE, CastingBlockEntityRenderer.basin().topY(0f), TOLERANCE);
    }

    @Test
    void fullBasinReachesTheRim() {
        assertEquals(1f, CastingBlockEntityRenderer.basin().topY(1f), TOLERANCE);
    }

    @Test
    void fractionsOutsideZeroToOneAreClamped() {
        // A block entity mid-sync can report more fluid than its capacity for a tick; the pool must
        // not shoot through the top of the block when it does.
        assertEquals(1f, CastingBlockEntityRenderer.basin().topY(2f), TOLERANCE);
        assertEquals(BASIN_SURFACE, CastingBlockEntityRenderer.basin().topY(-1f), TOLERANCE);
    }

    @Test
    void topYIsMonotonicInFraction() {
        CastingBlockEntityRenderer basin = CastingBlockEntityRenderer.basin();
        float previous = basin.topY(0f);
        for (float fraction = 0.1f; fraction <= 1f; fraction += 0.1f) {
            float current = basin.topY(fraction);
            assertTrue(current > previous, "topY must strictly increase with fraction");
            previous = current;
        }
    }
}
