package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * M3.5 issue #400: the draw-stage art routing, pinned against upstream 1.12's own
 * {@code models/item/tools/<bow>.tcon.json} overrides at {@code c01173c0}.
 *
 * <ul>
 *   <li>{@code shortbow}/{@code longbow}: {@code {"pulling":1}}, {@code {"pulling":1,"pull":0.65}},
 *       {@code {"pulling":1,"pull":0.9}} -- the first with no {@code pull} key at all, which vanilla
 *       reads as the {@code >= 0} this pins.</li>
 *   <li>{@code crossbow}: {@code pull} 0, 0.5, 0.999.</li>
 *   <li>Stage 1 re-points the bowstring layer only; stages 2 and 3 also re-point both limbs (the
 *       crossbow's single limb). No other layer -- the longbow's grip, the crossbow's body and
 *       binding -- ever changes.</li>
 *   <li>The crossbow's fourth override, {@code {"loaded":1}}, points at the <em>stage 3</em>
 *       textures rather than at art of its own.</li>
 * </ul>
 */
class BowDrawArtTest {

    @Test
    void bowsCarryUpstreamsThreePullThresholds() {
        assertArrayEquals(new float[] {0.0f, 0.65f, 0.9f}, ToolArt.drawThresholds("shortbow"), 0.0f,
                "shortbow.tcon.json: pulling, pull >= 0.65, pull >= 0.9");
        assertArrayEquals(new float[] {0.0f, 0.65f, 0.9f}, ToolArt.drawThresholds("longbow"), 0.0f,
                "longbow.tcon.json: the shortbow's thresholds");
        assertArrayEquals(new float[] {0.0f, 0.5f, 0.999f}, ToolArt.drawThresholds("crossbow"), 0.0f,
                "crossbow.tcon.json: pull >= 0, >= 0.5, >= 0.999");
    }

    @Test
    void aToolWithNoDrawArtHasNoStages() {
        assertNull(ToolArt.drawThresholds("broadsword"), "a melee tool is never drawn");
        assertEquals(0, ToolArt.drawStage("broadsword", 1.0f));
        assertFalse(ToolArt.hasLoadedState("shortbow"), "only the crossbow has a loaded state");
        assertTrue(ToolArt.hasLoadedState("crossbow"));
    }

    /** Which override vanilla's "last predicate that still matches wins" resolution lands on. */
    @Test
    void drawStageIsTheLastThresholdTheProgressClears() {
        assertEquals(1, ToolArt.drawStage("shortbow", 0.0f), "a bow being drawn is at least stage 1");
        assertEquals(1, ToolArt.drawStage("shortbow", 0.64f));
        assertEquals(2, ToolArt.drawStage("shortbow", 0.65f));
        assertEquals(2, ToolArt.drawStage("shortbow", 0.89f));
        assertEquals(3, ToolArt.drawStage("shortbow", 0.9f));
        assertEquals(3, ToolArt.drawStage("shortbow", 1.0f));

        assertEquals(1, ToolArt.drawStage("crossbow", 0.49f));
        assertEquals(2, ToolArt.drawStage("crossbow", 0.5f));
        assertEquals(2, ToolArt.drawStage("crossbow", 0.998f), "0.999, not 1: upstream's own threshold");
        assertEquals(3, ToolArt.drawStage("crossbow", 0.999f));
    }

    @Test
    void onlyTheStringMovesAtStageOneAndTheLimbsJoinAtStageTwo() {
        assertEquals("derived/tools/shortbow_string_draw1", ToolArt.drawLayer("shortbow", "string", 1));
        assertEquals("derived/tools/shortbow_limb", ToolArt.drawLayer("shortbow", "limb", 1),
                "shortbow.tcon.json's first override re-points layer2 only");
        assertEquals("derived/tools/shortbow_limb2", ToolArt.drawLayer("shortbow", "limb2", 1));

        assertEquals("derived/tools/shortbow_limb_draw2", ToolArt.drawLayer("shortbow", "limb", 2));
        assertEquals("derived/tools/shortbow_limb2_draw2", ToolArt.drawLayer("shortbow", "limb2", 2));
        assertEquals("derived/tools/shortbow_string_draw2", ToolArt.drawLayer("shortbow", "string", 2));

        assertEquals("derived/tools/shortbow_limb_draw3", ToolArt.drawLayer("shortbow", "limb", 3));
        assertEquals("derived/tools/shortbow_string_draw3", ToolArt.drawLayer("shortbow", "string", 3));

        // The longbow's grip and the crossbow's body/binding keep their undrawn art at every stage.
        for (int stage = 1; stage <= ToolArt.DRAW_STAGES; stage++) {
            assertEquals("derived/tools/longbow_binding", ToolArt.drawLayer("longbow", "binding", stage),
                    "longbow.tcon.json never re-points layer2, the grip");
            assertEquals("derived/tools/crossbow_body", ToolArt.drawLayer("crossbow", "body", stage));
            assertEquals("derived/tools/crossbow_binding", ToolArt.drawLayer("crossbow", "binding", stage));
        }
    }

    /** {@code {"loaded":1}} reuses {@code limb_3}/{@code bowstring_3}; nothing extra is derived. */
    @Test
    void theLoadedCrossbowDrawsItsFullDrawArt() {
        assertEquals(3, ToolArt.LOADED_STAGE);
        assertEquals(ToolArt.drawLayer("crossbow", "limb", 3),
                ToolArt.drawLayer("crossbow", "limb", ToolArt.LOADED_STAGE));
        assertEquals("derived/tools/crossbow_string_draw3",
                ToolArt.drawLayer("crossbow", "string", ToolArt.LOADED_STAGE));
    }

    /**
     * The modifier overlays get the same treatment: {@code models/item/modifiers/haste.json} carries
     * {@code shortbow1/2/3} keys beside {@code shortbow}, and {@code ToolModelLoader} fills the base
     * map in first and lets the stage keys overwrite it -- so a modifier with no art for a stage
     * keeps its undrawn overlay there. Upstream ships {@code mod_haste_1/2/3} for all three bows but,
     * for instance, only {@code mod_sharpness_2/3} for the crossbow and no staged emerald at all for
     * the shortbow.
     */
    @Test
    void modifierOverlaysFallBackToTheUndrawnArtWhenAStageHasNone() {
        ResourceLocation haste = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "haste");
        ResourceLocation smite = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "smite");

        assertEquals("derived/tools/mods/shortbow_haste", ModifierArt.overlay("shortbow", haste, 0));
        assertEquals("derived/tools/mods/shortbow_haste_draw1", ModifierArt.overlay("shortbow", haste, 1));
        assertEquals("derived/tools/mods/shortbow_haste_draw3", ModifierArt.overlay("shortbow", haste, 3));

        // items/shortbow/ has no mod_smite_*.png: the undrawn overlay stays at every stage.
        assertEquals("derived/tools/mods/shortbow_smite", ModifierArt.overlay("shortbow", smite, 2));

        // A melee tool has no stages at all, and asking for one is still its plain overlay.
        assertEquals("derived/tools/mods/broadsword_haste", ModifierArt.overlay("broadsword", haste, 2));

        // Luck still has no shortbow art, staged or not.
        ResourceLocation luck = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "luck");
        assertNull(ModifierArt.overlay("shortbow", luck, 3));
    }

    /**
     * T52 (issue #483): where the nocked ammo sits, read off the same {@code .tcon.json} files'
     * {@code ammoPosition} blocks. Each of a bow's three overrides carries a {@code pos} and no
     * {@code rot}, and {@code ToolModel#getBakedToolModel} combines an override's block with the
     * root's, so all four states share the root's {@code rot [0, 180, 0]}. The offsets are whole
     * pixels: the arrow rides three out from the string at the start of the draw and one at full
     * draw, which is also where the undrawn bow holds it.
     */
    @Test
    void theNockedArrowFollowsTheStringInAsTheDrawProgresses() {
        for (String bow : List.of("shortbow", "longbow")) {
            assertArrayEquals(new float[] {-0.0630f, 0.0630f, 0.01f, 0.0f, 180.0f, 0.0f},
                    ToolArt.ammoPosition(bow, 0), 0.0f, bow + ".tcon.json's root ammoPosition");
            assertArrayEquals(new float[] {-0.1880f, 0.1880f, 0.01f, 0.0f, 180.0f, 0.0f},
                    ToolArt.ammoPosition(bow, 1), 0.0f, "three pixels out at the start of the draw");
            assertArrayEquals(new float[] {-0.1255f, 0.1255f, 0.01f, 0.0f, 180.0f, 0.0f},
                    ToolArt.ammoPosition(bow, 2), 0.0f, "two at stage 2");
            assertArrayEquals(new float[] {-0.0630f, 0.0630f, 0.01f, 0.0f, 180.0f, 0.0f},
                    ToolArt.ammoPosition(bow, 3), 0.0f, "one at full draw, the root position again");
        }
    }

    /**
     * The crossbow's own: only its {@code {"loaded":1}} override carries an {@code ammoPosition}
     * ({@code pos [0.0625, -0.0625, 0.0625]}, {@code rot [0, 0, 90]} -- a bolt laid across the stock
     * and lifted a pixel clear of the body). Its three cranking overrides carry none at all, so
     * upstream bakes them as plain tool models that can draw no ammo; its root block is empty, and
     * that state never renders ammo either because {@code CrossBow#getAmmoToRender} is empty unless
     * the crossbow is loaded.
     */
    @Test
    void onlyALoadedCrossbowCarriesAnAmmoPosition() {
        assertArrayEquals(new float[] {0.0625f, -0.0625f, 0.0625f, 0.0f, 0.0f, 90.0f},
                ToolArt.ammoPosition("crossbow", ToolArt.LOADED_STAGE), 0.0f);
        assertNull(ToolArt.ammoPosition("crossbow", 0), "an unloaded crossbow shows no bolt");
        assertNull(ToolArt.ammoPosition("crossbow", 1), "crossbow.tcon.json's cranking overrides carry none");
        assertNull(ToolArt.ammoPosition("crossbow", 2));
    }

    /** A melee tool nocks nothing, however hard its caller asks. */
    @Test
    void aToolWithNoDrawArtHasNoAmmoPosition() {
        assertNull(ToolArt.ammoPosition("broadsword", 0));
        assertNull(ToolArt.ammoPosition("broadsword", 3));
        assertNull(ToolArt.ammoPosition("shortbow", 4), "there are only three stages");
    }
}
