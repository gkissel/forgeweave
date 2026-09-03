package dev.gkissel.forgeweave.jei;

import java.util.List;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * Issue #804: opens JEI's own Recipes GUI on each registered category, one at a time, so {@code
 * client.ScreenshotHarness} can capture the exact screen a player sees pressing R/U on a catalyst
 * item -- the only way the maintainer's two earlier geometry-only passes (#753, #785) could have
 * caught these layout bugs before playtest.
 *
 * <p>This class -- not {@code ScreenshotHarness} itself -- is the one that names JEI types, for the
 * same reason {@link JeiCategoryGeometry} does: {@code ScreenshotHarness} is an always-loaded
 * {@code @EventBusSubscriber}, verified by the JVM whether or not JEI is installed, while this
 * package is only ever touched by JEI's own plugin loader. Every method below that {@code
 * ScreenshotHarness} calls takes and returns only {@code int}/{@code String}/{@code boolean}, so
 * referencing this class from there never pulls a JEI type onto that class's own classpath.
 */
public final class JeiScreenshotHarness {
    /** One capture per registered category (docs/SCOPE.md M1/M2 issue #109's roster, #165/#782's tiers). */
    private static final List<RecipeType<?>> TYPES = List.of(
            PartCraftingCategory.TYPE,
            AssemblyCategory.TYPE, AssemblyCategory.LARGE_TYPE, AssemblyCategory.ARMOR_TYPE,
            RepairCategory.TYPE,
            MeltingCategory.TYPE, AlloyingCategory.TYPE,
            CastingTableCategory.TYPE, CastingBasinCategory.TYPE,
            ModifierApplicationCategory.TYPE, EmbossingCategory.TYPE,
            // #931
            EntityMeltingCategory.TYPE);

    private JeiScreenshotHarness() {}

    public static int categoryCount() {
        return TYPES.size();
    }

    /** {@code jei_<uid path>}, e.g. {@code jei_alloying}; {@code ScreenshotHarness} appends the extension. */
    public static String categoryFileName(int index) {
        return "jei_" + TYPES.get(index).getUid().getPath();
    }

    /**
     * Opens JEI's real Recipes GUI on the category at {@code index}, the same screen a player gets
     * pressing R/U on that category's catalyst. Returns false (nothing opened) if the runtime is not
     * up yet; the caller logs that itself, since this class stays free of a logger dependency too.
     */
    public static boolean openCategory(int index) {
        IJeiRuntime runtime = ForgeweaveJeiPlugin.runtimeForHarness();
        if (runtime == null) {
            return false;
        }
        runtime.getRecipesGui().showTypes(List.of(TYPES.get(index)));
        return true;
    }
}
