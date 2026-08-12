package dev.gkissel.forgeweave.client;

import static dev.gkissel.forgeweave.client.SmelteryControllerBlockEntityRenderer.MIN_LAYER_HEIGHT;
import static dev.gkissel.forgeweave.client.SmelteryControllerBlockEntityRenderer.layerHeights;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The height model behind #179's in-world smeltery pool: which fraction of the interior each fluid
 * band occupies. Same reason {@code SearedTankBlockEntityRendererTest} exists -- a screenshot proves
 * something renders, not that a 3/4-full smeltery is drawn 3/4 full.
 */
class SmelteryControllerBlockEntityRendererTest {

    private static final float TOLERANCE = 0.0001f;
    /** A two-tall interior with the renderer's own inset already taken off, as {@code render} passes it. */
    private static final float HEIGHT = 2f - 0.02f;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<FluidStack> fluids(int... amounts) {
        return Arrays.stream(amounts).mapToObj(amount -> new FluidStack(Fluids.LAVA, amount)).toList();
    }

    @Test
    void aFullSmelteryFillsTheWholeInterior() {
        float[] heights = layerHeights(fluids(1000), 1000, HEIGHT);

        assertEquals(HEIGHT, heights[0], TOLERANCE);
    }

    @Test
    void aHalfFullSmelteryFillsHalfTheInterior() {
        float[] heights = layerHeights(fluids(500), 1000, HEIGHT);

        assertEquals(HEIGHT / 2f, heights[0], TOLERANCE);
    }

    @Test
    void layersAreProportionalToTheirShareOfCapacity() {
        // Two thirds iron under one third copper, in tank order (bottom fluid first).
        float[] heights = layerHeights(fluids(400, 200), 600, HEIGHT);

        assertEquals(2f * HEIGHT / 3f, heights[0], TOLERANCE);
        assertEquals(HEIGHT / 3f, heights[1], TOLERANCE);
    }

    @Test
    void aTraceFluidStillGetsAVisibleBand() {
        // One millibucket in a 9x9-sized smeltery would otherwise be a sub-pixel sliver.
        float[] heights = layerHeights(fluids(1), 93312, HEIGHT);

        assertEquals(MIN_LAYER_HEIGHT, heights[0], TOLERANCE);
    }

    @Test
    void theMinimumBandIsPaidForOutOfTheOtherLayers() {
        // A nearly-full smeltery plus a trace of a second metal: the floor under the trace layer must
        // come off the big one rather than pushing the stack through the smeltery's rim.
        float[] heights = layerHeights(fluids(999, 1), 1000, HEIGHT);

        assertEquals(MIN_LAYER_HEIGHT, heights[1], TOLERANCE);
        assertEquals(HEIGHT, heights[0] + heights[1], TOLERANCE);
    }

    @Test
    void aNotQuiteFullSmelteryKeepsHeadroomClear() {
        // Upstream's own "leave a few pixels for the empty tank display": full and nearly-full must
        // not look identical from outside.
        float[] heights = layerHeights(fluids(1999), 2000, HEIGHT);
        float total = heights[0];

        assertTrue(total <= HEIGHT - MIN_LAYER_HEIGHT + TOLERANCE,
                "a smeltery that is not full must leave headroom, was " + total);
    }

    @Test
    void moreFluidsThanTheInteriorHasRoomForStillFit() {
        // Twenty trace fluids at the 0.1-block floor want 2.0 blocks of a 1.98-block interior.
        int[] amounts = new int[20];
        Arrays.fill(amounts, 1);
        float[] heights = layerHeights(fluids(amounts), 100000, HEIGHT);

        float total = 0f;
        for (float height : heights) {
            total += height;
        }
        assertTrue(total <= HEIGHT + TOLERANCE, "the stack must never exceed the interior, was " + total);
    }
}
