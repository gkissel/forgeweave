package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry.Panel;

/**
 * Pins each JEI category's background crop and pixel size (issues #785, #804). This test touches
 * {@link JeiCategoryGeometry} only, never a category class itself: every category implements JEI's
 * {@code IRecipeCategory}, and JEI is compileOnly/optional (see {@code JeiRecipesTest}'s own javadoc)
 * -- referencing one of those classes in a plain unit test throws {@code NoClassDefFoundError} the
 * moment the JVM verifies it, which is exactly why the background/size constants live in the
 * JEI-free {@link JeiCategoryGeometry} instead.
 *
 * <p>Every rect asserted below is an upstream category's own {@code createDrawable} call in the 1.20
 * clone (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT). #785 kept only the
 * sizes, cropping every panel from (0,0) -- which is what let assembly read 20 rows past the end of
 * upstream's panel and let the station-panel categories be tiled to widths their art was never drawn
 * at. Origins are pinned here so that cannot silently come back.
 */
class JeiCategoryBackgroundsTest {
    private static ResourceLocation derived(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/jei/" + path);
    }

    private static void assertPanel(Panel panel, String texture, int u, int v, int width, int height) {
        assertEquals(derived(texture), panel.background());
        assertEquals(u, panel.u(), "upstream crop origin u");
        assertEquals(v, panel.v(), "upstream crop origin v");
        assertEquals(width, panel.width());
        assertEquals(height, panel.height());
    }

    /** Upstream {@code AlloyRecipeCategory}: `createDrawable(alloy.png, 0, 0, 172, 62)`. */
    @Test
    void alloyingIsUpstreamsAlloyPanel() {
        assertPanel(JeiCategoryGeometry.ALLOYING, "alloy.png", 0, 0, 172, 62);
    }

    /** Upstream {@code AbstractCastingCategory}: `createDrawable(casting.png, 0, 0, 117, 54)`. */
    @Test
    void castingIsUpstreamsCastingPanel() {
        assertPanel(JeiCategoryGeometry.CASTING, "casting.png", 0, 0, 117, 54);
    }

    /** Upstream {@code AbstractMeltingCategory}: `createDrawable(melting.png, 0, 0, 132, 40)`. */
    @Test
    void meltingIsUpstreamsMeltingPanel() {
        assertPanel(JeiCategoryGeometry.MELTING, "melting.png", 0, 0, 132, 40);
    }

    /**
     * Upstream {@code ToolBuildingCategory}: `createDrawable(tinker_station.png, 122, 77, 134, 66)`.
     * Issue #804: the height was 86 and the origin (0,0), so the crop was neither this panel nor
     * anything else -- 20 rows of it came from whatever sits below the panel in the atlas.
     */
    @Test
    void assemblyIsUpstreamsToolBuildingPanel() {
        assertPanel(JeiCategoryGeometry.ASSEMBLY, "tinker_station.png", 122, 77, 134, 66);
    }

    /** Upstream {@code PartBuilderCategory}: `createDrawable(tinker_station.png, 0, 117, 121, 46)`. */
    @Test
    void partCraftingIsUpstreamsPartBuilderPanel() {
        assertPanel(JeiCategoryGeometry.PART_CRAFTING, "tinker_station.png", 0, 117, 121, 46);
    }

    /**
     * Repair has no upstream category, so it borrows the nearest upstream panel shape:
     * {@code SeveringCategory}'s `createDrawable(tinker_station.png, 0, 78, 100, 38)`.
     */
    @Test
    void repairBorrowsUpstreamsSeveringPanel() {
        assertPanel(JeiCategoryGeometry.REPAIR, "tinker_station.png", 0, 78, 100, 38);
    }

    /**
     * Upstream {@code ModifierRecipeCategory}: `createDrawable(tinker_station.png, 0, 0, 128, 77)`.
     * Embossing is the same picture (reagents plus a tool produce a named modifier on that tool), so
     * it is the very same panel rather than a second copy of these numbers.
     */
    @Test
    void modifierApplicationAndEmbossingShareUpstreamsModifierPanel() {
        assertPanel(JeiCategoryGeometry.MODIFIER_APPLICATION, "tinker_station.png", 0, 0, 128, 77);
        assertSame(JeiCategoryGeometry.MODIFIER_APPLICATION, JeiCategoryGeometry.EMBOSSING);
    }

    /**
     * Issue #804's own regression: two categories were 274px wide, past the width JEI's recipe popup
     * will draw, so they ran off its right edge into the item list. Nothing here may exceed the
     * widest panel upstream itself ships (alloying's 172).
     */
    @Test
    void noCategoryIsWiderThanUpstreamsWidestPanel() {
        for (Panel panel : new Panel[] {
                JeiCategoryGeometry.ALLOYING, JeiCategoryGeometry.CASTING, JeiCategoryGeometry.MELTING,
                JeiCategoryGeometry.ASSEMBLY, JeiCategoryGeometry.PART_CRAFTING, JeiCategoryGeometry.REPAIR,
                JeiCategoryGeometry.MODIFIER_APPLICATION, JeiCategoryGeometry.EMBOSSING}) {
            assertTrue(panel.width() <= JeiCategoryGeometry.ALLOYING.width(),
                    () -> "category panel " + panel + " is wider than upstream's widest");
        }
    }
}
