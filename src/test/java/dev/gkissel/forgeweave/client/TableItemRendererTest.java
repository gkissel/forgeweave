package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * Pins the flat/standing rule and the block-local vertical centre {@link TableItemRenderer} derives
 * from upstream's {@code TileTable#getTableItem} -- see that class's javadoc for the {@code item.y +
 * 1f} conversion this checks against. Issue #567 (T75's leftover half of parity audit 2026-08-18
 * §3.6).
 */
class TableItemRendererTest {
    private static final float TOLERANCE = 0.0001f;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nonBlockItemIsFlat() {
        assertTrue(TableItemRenderer.isFlat(new ItemStack(Items.PAPER)));
    }

    @Test
    void regularBlockItemStands() {
        assertFalse(TableItemRenderer.isFlat(new ItemStack(Items.IRON_BLOCK)));
    }

    /** Upstream's one carve-out: a pane has no cube to show, so it lies flat like any other item. */
    @Test
    void paneStaysFlat() {
        assertTrue(TableItemRenderer.isFlat(new ItemStack(Items.IRON_BARS)));
    }

    @Test
    void flatCenterIsAConstantIndependentOfScale() {
        assertEquals(1.03125f, TableItemRenderer.FLAT_CENTER_Y, TOLERANCE);
        assertEquals(TableItemRenderer.centerY(new ItemStack(Items.PAPER), 0.1f),
                TableItemRenderer.centerY(new ItemStack(Items.PAPER), 0.8f), TOLERANCE);
    }

    /** Upstream's default standing case: {@code y=-0.3125f} -> block-local {@code 1.1875f} at scale 0.375f. */
    @Test
    void standingCubeSitsWithItsOwnBottomOnTheSurface() {
        assertEquals(1.1875f, TableItemRenderer.standingCenterY(0.375f), TOLERANCE);
        assertEquals(TableItemRenderer.SURFACE_Y,
                TableItemRenderer.standingCenterY(0.375f) - 0.375f / 2f, TOLERANCE);
    }

    @Test
    void centerYPicksTheOrientationTheStackWouldRenderIn() {
        ItemStack cube = new ItemStack(Items.IRON_BLOCK);
        assertEquals(TableItemRenderer.standingCenterY(0.375f), TableItemRenderer.centerY(cube, 0.375f), TOLERANCE);

        ItemStack pane = new ItemStack(Items.IRON_BARS);
        assertEquals(TableItemRenderer.FLAT_CENTER_Y, TableItemRenderer.centerY(pane, 0.375f), TOLERANCE);
    }
}
