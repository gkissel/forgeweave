package dev.gkissel.forgeweave.jei;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Background texture crop + pixel size for every recipe category (issue #785), factored out of the
 * category classes themselves so a plain unit test can pin them without touching any JEI class:
 * every {@code IRecipeCategory} implementation pulls JEI's API onto the classpath the moment the JVM
 * verifies it, and JEI is compileOnly/optional there (see {@code JeiRecipesTest}'s own javadoc on
 * why it avoids JEI classes entirely) -- a test that references so much as {@code
 * AlloyingCategory.class} already throws {@code NoClassDefFoundError} in a plain unit test run. This
 * class has no JEI import at all, so {@code JeiCategoryBackgroundsTest} can load it safely.
 *
 * <p>Issue #804: every panel below is now an upstream category's own {@code createDrawable} rect,
 * copied verbatim from the 1.20 clone (`~/development/minecraft/references/tinkers-1.20` @
 * de26560d, MIT -- NOTICE.md), origin included. #785 kept only the sizes and always cropped from
 * (0,0), which for assembly read 20 rows past the end of upstream's panel, and for the four
 * station-panel categories cropped or <em>tiled</em> the Part Builder row to a width it was never
 * drawn at -- the tiling repeated that row's baked slot frames across the panel, which is what put
 * ghost slots and a text column outside the JEI popup in the maintainer's screenshots. A category is
 * now sized to the upstream panel it draws, never to a shared worst case.
 */
final class JeiCategoryGeometry {
    private JeiCategoryGeometry() {}

    /**
     * One upstream {@code IGuiHelper#createDrawable} call: which texture, the rect inside it, and
     * therefore the category's own {@code getWidth()}/{@code getHeight()}.
     */
    record Panel(ResourceLocation background, int u, int v, int width, int height) {}

    private static ResourceLocation derived(String fileName) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/jei/" + fileName);
    }

    /** The derived copy of upstream's `textures/gui/jei/tinker_station.png` (NOTICE.md). */
    static final ResourceLocation TINKER_STATION = derived("tinker_station.png");

    /** Upstream {@code AlloyRecipeCategory}: `createDrawable(alloy.png, 0, 0, 172, 62)`. */
    static final Panel ALLOYING = new Panel(derived("alloy.png"), 0, 0, 172, 62);
    /** Upstream {@code AbstractCastingCategory}: `createDrawable(casting.png, 0, 0, 117, 54)`. */
    static final Panel CASTING = new Panel(derived("casting.png"), 0, 0, 117, 54);
    /** Upstream {@code AbstractMeltingCategory}: `createDrawable(melting.png, 0, 0, 132, 40)`. */
    static final Panel MELTING = new Panel(derived("melting.png"), 0, 0, 132, 40);
    /** Upstream {@code ToolBuildingCategory}: `createDrawable(tinker_station.png, 122, 77, 134, 66)`. */
    static final Panel ASSEMBLY = new Panel(TINKER_STATION, 122, 77, 134, 66);
    /** Upstream {@code PartBuilderCategory}: `createDrawable(tinker_station.png, 0, 117, 121, 46)`. */
    static final Panel PART_CRAFTING = new Panel(TINKER_STATION, 0, 117, 121, 46);
    /**
     * Upstream has no repair category (its Tool Station repair is in-GUI only, with no recipe view),
     * so this borrows the nearest upstream panel shape: {@code SeveringCategory}'s plain
     * "inputs, arrow, one output" row, `createDrawable(tinker_station.png, 0, 78, 100, 38)`. Its
     * input side is blank art, so {@code RepairCategory} draws upstream's own 18x18 slot frame
     * ({@link JeiCategoryChrome#slotFrame}) under each input the way {@code ToolBuildingCategory}
     * does for its own frameless slots.
     */
    static final Panel REPAIR = new Panel(TINKER_STATION, 0, 78, 100, 38);
    /** Upstream {@code ModifierRecipeCategory}: `createDrawable(tinker_station.png, 0, 0, 128, 77)`. */
    static final Panel MODIFIER_APPLICATION = new Panel(TINKER_STATION, 0, 0, 128, 77);
    /**
     * Embossing has no upstream category of its own, but it is the same shape as one that does --
     * reagents plus a tool produce a named modifier on that tool -- so it reuses {@link
     * #MODIFIER_APPLICATION}'s panel and slot ring rather than a bespoke row.
     */
    static final Panel EMBOSSING = MODIFIER_APPLICATION;
}
