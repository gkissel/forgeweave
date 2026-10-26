package dev.gkissel.forgeweave.emi;

import dev.emi.emi.api.EmiApi;

/**
 * The EMI half of {@code client.ScreenshotHarness}'s recipe-viewer captures (issue #971): opens EMI's
 * own recipe screen on each Forgeweave category in turn, {@code jei.JeiScreenshotHarness}'s job for a
 * client running EMI without JEI. Every method takes and returns only {@code int}/{@code
 * String}/{@code boolean}, for that class's reason: {@code ScreenshotHarness} is always loaded, and
 * this keeps EMI's types off its classpath.
 */
public final class EmiScreenshotHarness {
    private EmiScreenshotHarness() {}

    public static int categoryCount() {
        return ForgeweaveEmiCategories.ALL.size();
    }

    /** {@code emi_<category id path>}, e.g. {@code emi_alloying}; {@code ScreenshotHarness} appends the extension. */
    public static String categoryFileName(int index) {
        return "emi_" + ForgeweaveEmiCategories.ALL.get(index).getId().getPath();
    }

    /**
     * Whether EMI has finished its reload and holds Forgeweave's recipes. EMI reloads on a worker
     * thread after a world is joined, so the captures wait for this rather than shooting an empty
     * screen. Melting is the probe because every world has melting recipes.
     */
    public static boolean ready() {
        return !EmiApi.getRecipeManager().getRecipes(ForgeweaveEmiCategories.MELTING).isEmpty();
    }

    /** Opens EMI's recipe screen on the category at {@code index}, the screen a player gets from its tab. */
    public static void openCategory(int index) {
        EmiApi.displayRecipeCategory(ForgeweaveEmiCategories.ALL.get(index));
    }
}
