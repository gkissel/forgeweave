package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Pins each JEI category's background texture and pixel size (issue #785): #765 gave every category
 * a flat procedural bevel because nothing was derived from upstream; #785 replaces it with real art
 * derived from the 1.20 clone (NOTICE.md). This test touches {@link JeiCategoryGeometry} only, never
 * a category class itself: every category implements JEI's {@code IRecipeCategory}, and JEI is
 * compileOnly/optional (see {@code JeiRecipesTest}'s own javadoc) -- referencing one of those classes
 * in a plain unit test throws {@code NoClassDefFoundError} the moment the JVM verifies it, which is
 * exactly why the background/size constants live in the JEI-free {@link JeiCategoryGeometry} instead.
 */
class JeiCategoryBackgroundsTest {
    private static ResourceLocation derived(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/jei/" + path);
    }

    @Test
    void alloyingUsesUpstreamsAlloyBackgroundAtItsRealSize() {
        assertEquals(derived("alloy.png"), JeiCategoryGeometry.ALLOYING.background());
        assertEquals(172, JeiCategoryGeometry.ALLOYING.width());
        assertEquals(62, JeiCategoryGeometry.ALLOYING.height());
    }

    @Test
    void castingUsesUpstreamsCastingBackgroundAtItsRealSize() {
        assertEquals(derived("casting.png"), JeiCategoryGeometry.CASTING.background());
        assertEquals(117, JeiCategoryGeometry.CASTING.width());
        assertEquals(54, JeiCategoryGeometry.CASTING.height());
    }

    @Test
    void meltingUsesUpstreamsMeltingBackgroundAtItsRealSize() {
        assertEquals(derived("melting.png"), JeiCategoryGeometry.MELTING.background());
        assertEquals(132, JeiCategoryGeometry.MELTING.width());
        assertEquals(40, JeiCategoryGeometry.MELTING.height());
    }

    @Test
    void assemblyUsesUpstreamsToolBuildingBackgroundWidth() {
        assertEquals(derived("tinker_station.png"), JeiCategoryGeometry.ASSEMBLY.background());
        assertEquals(134, JeiCategoryGeometry.ASSEMBLY.width(), "upstream ToolBuildingCategory's own background width");
        assertEquals(86, JeiCategoryGeometry.ASSEMBLY.height(),
                "still grows with the tool's own part count, unlike upstream's fixed panel");
    }

    /**
     * Part crafting, modifier application, repair and embossing have no upstream JEI counterpart, so
     * they all reuse the same Part Builder panel crop inside {@code tinker_station.png} (issue #785's
     * "closest upstream background... so the set reads as one family").
     */
    @Test
    void categoriesWithNoUpstreamCounterpartShareTheStationPanelTexture() {
        ResourceLocation stationPanel = derived("tinker_station.png");
        assertEquals(stationPanel, JeiCategoryGeometry.PART_CRAFTING.background());
        assertEquals(stationPanel, JeiCategoryGeometry.REPAIR.background());
        assertEquals(stationPanel, JeiCategoryGeometry.EMBOSSING.background());
        assertEquals(stationPanel, JeiCategoryGeometry.MODIFIER_APPLICATION.background());
    }

    @Test
    void partCraftingAndRepairShareTheirNarrowPanelSize() {
        assertEquals(72, JeiCategoryGeometry.PART_CRAFTING.width());
        assertEquals(46, JeiCategoryGeometry.PART_CRAFTING.height());
        assertEquals(72, JeiCategoryGeometry.REPAIR.width());
        assertEquals(46, JeiCategoryGeometry.REPAIR.height());
    }

    @Test
    void embossingAndModifierApplicationShareTheirWideReagentRowSize() {
        assertEquals(228, JeiCategoryGeometry.EMBOSSING.width());
        assertEquals(46, JeiCategoryGeometry.EMBOSSING.height());
        assertEquals(228, JeiCategoryGeometry.MODIFIER_APPLICATION.width());
        assertEquals(46, JeiCategoryGeometry.MODIFIER_APPLICATION.height());
    }
}
