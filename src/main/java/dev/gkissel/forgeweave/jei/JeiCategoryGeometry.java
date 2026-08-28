package dev.gkissel.forgeweave.jei;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Background texture + pixel size for every recipe category (issue #785), factored out of the
 * category classes themselves so a plain unit test can pin them without touching any JEI class:
 * every {@code IRecipeCategory} implementation pulls JEI's API onto the classpath the moment the JVM
 * verifies it, and JEI is compileOnly/optional there (see {@code JeiRecipesTest}'s own javadoc on
 * why it avoids JEI classes entirely) -- a test that references so much as {@code
 * AlloyingCategory.class} already throws {@code NoClassDefFoundError} in a plain unit test run. This
 * class has no JEI import at all, so {@code JeiCategoryBackgroundsTest} can load it safely.
 *
 * <p>Alloying, casting, melting and assembly each derive their own background from their own
 * upstream analog in the 1.20 clone (`~/development/minecraft/references/tinkers-1.20` @ de26560d,
 * MIT -- NOTICE.md); see each category's own javadoc for the exact upstream class and coordinates.
 * Part crafting, modifier application, repair and embossing have no upstream counterpart, so per the
 * maintainer's decision on #785 they all reuse {@link #TINKER_STATION} instead (upstream {@code
 * PartBuilderCategory}'s own plain station panel, via {@link JeiCategoryChrome#stationPanel}).
 */
final class JeiCategoryGeometry {
    private JeiCategoryGeometry() {}

    record Panel(ResourceLocation background, int width, int height) {}

    private static ResourceLocation derived(String fileName) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/jei/" + fileName);
    }

    /** The derived copy of upstream's `textures/gui/jei/tinker_station.png` (NOTICE.md). */
    static final ResourceLocation TINKER_STATION = derived("tinker_station.png");

    /** Upstream {@code AlloyRecipeCategory}'s own background, (0,0,172,62). */
    static final Panel ALLOYING = new Panel(derived("alloy.png"), 172, 62);
    /** Upstream {@code AbstractCastingCategory}'s own background, (0,0,117,54). */
    static final Panel CASTING = new Panel(derived("casting.png"), 117, 54);
    /** Upstream {@code AbstractMeltingCategory}'s own background, (0,0,132,40). */
    static final Panel MELTING = new Panel(derived("melting.png"), 132, 40);
    /** Upstream {@code ToolBuildingCategory}'s own background width (134); height still grows with the tool's part count. */
    static final Panel ASSEMBLY = new Panel(TINKER_STATION, 134, 86);
    /** The Part Builder panel crop, cropped (not tiled -- narrower than the 121px tile) to this row's own size. */
    static final Panel PART_CRAFTING = new Panel(TINKER_STATION, 72, 46);
    static final Panel REPAIR = new Panel(TINKER_STATION, 72, 46);
    /** The Part Builder panel crop, tiled to cover this wider reagent row (see {@link JeiCategoryChrome#stationPanel}). */
    static final Panel EMBOSSING = new Panel(TINKER_STATION, 228, 46);
    static final Panel MODIFIER_APPLICATION = new Panel(TINKER_STATION, 228, 46);
}
