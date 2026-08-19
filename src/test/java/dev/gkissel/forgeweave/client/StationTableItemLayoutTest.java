package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * Pins the per-station position/scale tables from issue #567 (T75's leftover half of parity audit
 * 2026-08-18 §3.6): {@link StencilTableBlockEntityRenderer}, {@link PartBuilderBlockEntityRenderer},
 * {@link CraftingStationBlockEntityRenderer} and {@link ToolStationBlockEntityRenderer} each pull
 * their numbers from a different {@code TileTable} subclass's own {@code setInventoryDisplay}
 * override -- see each renderer's javadoc for its own derivation.
 */
class StationTableItemLayoutTest {
    private static final float TOLERANCE = 0.0001f;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --- Stencil Table: untouched TileTable#getTableItem, no per-station override. ---

    @Test
    void stencilTableFlatItemUsesUpstreamsDefaultScale() {
        assertEquals(0.8f, StencilTableBlockEntityRenderer.scaleFor(new ItemStack(Items.PAPER)), TOLERANCE);
    }

    @Test
    void stencilTableStandingCubeUsesUpstreamsDefaultCubeScale() {
        assertEquals(0.375f, StencilTableBlockEntityRenderer.scaleFor(new ItemStack(Items.IRON_BLOCK)), TOLERANCE);
    }

    // --- Part Builder: four corners at c=0.2125f, s *= 0.46875f. ---

    @Test
    void partBuilderCornersAlternateSign() {
        assertEquals(0.2125f, PartBuilderBlockEntityRenderer.X_BY_ROLE[PartBuilderBlockEntityRenderer.ROLE_MATERIAL],
                TOLERANCE);
        assertEquals(-0.2125f,
                PartBuilderBlockEntityRenderer.X_BY_ROLE[PartBuilderBlockEntityRenderer.ROLE_MATERIAL_2], TOLERANCE);
        assertEquals(-0.2125f,
                PartBuilderBlockEntityRenderer.Z_BY_ROLE[PartBuilderBlockEntityRenderer.ROLE_MATERIAL], TOLERANCE);
        assertEquals(0.2125f, PartBuilderBlockEntityRenderer.Z_BY_ROLE[PartBuilderBlockEntityRenderer.ROLE_PATTERN],
                TOLERANCE);
        assertEquals(0.2125f, PartBuilderBlockEntityRenderer.Z_BY_ROLE[PartBuilderBlockEntityRenderer.ROLE_OUTPUT],
                TOLERANCE);
    }

    @Test
    void partBuilderScaleIsUpstreamsDefaultTimesTheCornerFactor() {
        assertEquals(0.8f * 0.46875f, PartBuilderBlockEntityRenderer.scaleFor(new ItemStack(Items.PAPER)),
                TOLERANCE);
        assertEquals(0.375f * 0.46875f, PartBuilderBlockEntityRenderer.scaleFor(new ItemStack(Items.IRON_BLOCK)),
                TOLERANCE);
    }

    // --- Crafting Station: 3x3 grid at a fixed 0.125f scale for every item, flat or standing. ---

    @Test
    void craftingStationGridCellsStepByOneCellPerColumnAndRow() {
        float cell = CraftingStationBlockEntityRenderer.CELL;
        assertEquals(cell, CraftingStationBlockEntityRenderer.x(0), TOLERANCE);
        assertEquals(0f, CraftingStationBlockEntityRenderer.x(1), TOLERANCE);
        assertEquals(-cell, CraftingStationBlockEntityRenderer.x(2), TOLERANCE);
        assertEquals(cell, CraftingStationBlockEntityRenderer.z(0), TOLERANCE);
        assertEquals(-cell, CraftingStationBlockEntityRenderer.z(2), TOLERANCE);
    }

    @Test
    void craftingStationScaleIgnoresFlatVsStanding() {
        assertEquals(CraftingStationBlockEntityRenderer.SCALE, 0.125f, TOLERANCE);
    }

    // --- Tool Station: six fixed positions, head slot gets a 1.3x bonus. ---

    @Test
    void toolStationCentreSlotIsAtTheOrigin() {
        assertEquals(0f, ToolStationBlockEntityRenderer.POSITION_X[ToolStationBlockEntityRenderer.HEAD_INDEX],
                TOLERANCE);
        assertEquals(0f, ToolStationBlockEntityRenderer.POSITION_Z[ToolStationBlockEntityRenderer.HEAD_INDEX],
                TOLERANCE);
    }

    @Test
    void toolStationHeadSlotGetsTheBonusScale() {
        ItemStack paper = new ItemStack(Items.PAPER);
        float headScale = ToolStationBlockEntityRenderer.scaleFor(paper, ToolStationBlockEntityRenderer.HEAD_INDEX);
        float otherScale = ToolStationBlockEntityRenderer.scaleFor(paper, 1);
        assertEquals(0.8f * 0.46875f * 1.3f, headScale, TOLERANCE);
        assertEquals(0.8f * 0.46875f, otherScale, TOLERANCE);
    }

    @Test
    void toolStationPositionsAreSymmetricAboutTheCentre() {
        // Positions 1/5 and 2/4 mirror across x, matching upstream's own left/right symmetric layout.
        assertEquals(-ToolStationBlockEntityRenderer.POSITION_X[1], ToolStationBlockEntityRenderer.POSITION_X[5],
                TOLERANCE);
        assertEquals(ToolStationBlockEntityRenderer.POSITION_Z[1], ToolStationBlockEntityRenderer.POSITION_Z[5],
                TOLERANCE);
        assertEquals(-ToolStationBlockEntityRenderer.POSITION_X[2], ToolStationBlockEntityRenderer.POSITION_X[4],
                TOLERANCE);
    }
}
