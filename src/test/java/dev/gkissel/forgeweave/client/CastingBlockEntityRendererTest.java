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
    /** The underside of a cast tucked up under the table's rim: {@code 1 - FLAT_ITEM_THICKNESS}. */
    private static final float TABLE_ITEM_UNDERSIDE = 1f - 1f / 64f;
    private static final float FLAT_ITEM_THICKNESS = 1f / 64f;
    private static final float BASIN_BLOCK_SCALE = 12f / 16f;

    private static final boolean BARE = false;
    private static final boolean WITH_CAST = true;

    private static final int INPUT_LAYER = 0;
    private static final int OUTPUT_LAYER = 1;

    @Test
    void emptyTableSitsOnTheTableSurface() {
        assertEquals(TABLE_SURFACE, CastingBlockEntityRenderer.table().topY(0f, BARE), TOLERANCE);
    }

    @Test
    void fullBareTableBrimsAHairOverTheBlock() {
        assertEquals(TABLE_CEILING, CastingBlockEntityRenderer.table().topY(1f, BARE), TOLERANCE);
    }

    @Test
    void halfPouredTableIsHalfwayUpItsRecess() {
        assertEquals(TABLE_SURFACE + (TABLE_CEILING - TABLE_SURFACE) / 2f,
                CastingBlockEntityRenderer.table().topY(0.5f, BARE), TOLERANCE);
    }

    /**
     * #200: a full table holding a cast used to pour to {@link #TABLE_CEILING}, above the cast's own
     * top face, so the translucent metal tinted and z-fought the cast instead of filling it. The
     * pool now stops at the cast's underside and the cast reads above its own metal.
     */
    @Test
    void aFullTableStopsUnderTheCastLyingOnIt() {
        assertEquals(TABLE_ITEM_UNDERSIDE, CastingBlockEntityRenderer.table().topY(1f, WITH_CAST), TOLERANCE);
    }

    @Test
    void aTableWithACastNeverPoursAboveIt() {
        CastingBlockEntityRenderer table = CastingBlockEntityRenderer.table();
        for (float fraction = 0f; fraction <= 1f; fraction += 0.05f) {
            assertTrue(table.topY(fraction, WITH_CAST) <= TABLE_ITEM_UNDERSIDE + TOLERANCE,
                    "fluid at " + fraction + " of capacity must stay under the cast");
        }
    }

    @Test
    void aBareTableStillOutfillsOneHoldingACast() {
        CastingBlockEntityRenderer table = CastingBlockEntityRenderer.table();
        assertTrue(table.topY(1f, BARE) > table.topY(1f, WITH_CAST),
                "with nothing lying in it there is nothing to stop under");
    }

    @Test
    void emptyBasinSitsOnItsFloor() {
        assertEquals(BASIN_SURFACE, CastingBlockEntityRenderer.basin().topY(0f, BARE), TOLERANCE);
    }

    @Test
    void fullBasinReachesTheRim() {
        assertEquals(1f, CastingBlockEntityRenderer.basin().topY(1f, BARE), TOLERANCE);
    }

    @Test
    void fractionsOutsideZeroToOneAreClamped() {
        // A block entity mid-sync can report more fluid than its capacity for a tick; the pool must
        // not shoot through the top of the block when it does.
        assertEquals(1f, CastingBlockEntityRenderer.basin().topY(2f, BARE), TOLERANCE);
        assertEquals(BASIN_SURFACE, CastingBlockEntityRenderer.basin().topY(-1f, BARE), TOLERANCE);
    }

    @Test
    void topYIsMonotonicInFraction() {
        CastingBlockEntityRenderer basin = CastingBlockEntityRenderer.basin();
        float previous = basin.topY(0f, BARE);
        for (float fraction = 0.1f; fraction <= 1f; fraction += 0.1f) {
            float current = basin.topY(fraction, BARE);
            assertTrue(current > previous, "topY must strictly increase with fraction");
            previous = current;
        }
    }

    /**
     * #407: a block output used to be centred at {@code itemBaseY + height * 1.5}, lifting it by its
     * own 12/16 scale regardless of what -- if anything -- sat beneath it. Block casting never fills
     * the input slot (see {@link dev.gkissel.forgeweave.block.CastingBlockEntity#input}), so with no
     * input the output must sit directly on the basin's floor: half its own height above it, not
     * one and a half.
     */
    @Test
    void basinBlockOutputWithNoInputCentresJustAboveTheFloor() {
        assertEquals(BASIN_SURFACE + BASIN_BLOCK_SCALE / 2f,
                CastingBlockEntityRenderer.basin().centerY(BASIN_BLOCK_SCALE, 0f, OUTPUT_LAYER), TOLERANCE);
    }

    /**
     * A table's output (an ingot or part) always sits over a cast, and both are flat, so the input's
     * height equals the output's own height -- the fix must reproduce today's {@code height * 1.5}
     * centre for this case unchanged.
     */
    @Test
    void tableOutputOverACastKeepsTodaysCentre() {
        assertEquals(TABLE_ITEM_UNDERSIDE + FLAT_ITEM_THICKNESS * 1.5f,
                CastingBlockEntityRenderer.table().centerY(FLAT_ITEM_THICKNESS, FLAT_ITEM_THICKNESS, OUTPUT_LAYER),
                TOLERANCE);
    }

    @Test
    void tableOutputWithNoInputSitsAtTheBase() {
        assertEquals(TABLE_ITEM_UNDERSIDE + FLAT_ITEM_THICKNESS / 2f,
                CastingBlockEntityRenderer.table().centerY(FLAT_ITEM_THICKNESS, 0f, OUTPUT_LAYER), TOLERANCE);
    }

    @Test
    void inputLayerIgnoresInputHeightAndSitsOnTheFloor() {
        // Layer 0 is the input itself; there is nothing beneath it to offset by.
        assertEquals(BASIN_SURFACE + BASIN_BLOCK_SCALE / 2f,
                CastingBlockEntityRenderer.basin().centerY(BASIN_BLOCK_SCALE, 999f, INPUT_LAYER), TOLERANCE);
    }
}
