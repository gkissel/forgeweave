package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.recipe.AlloyRecipe;

/**
 * Every {@link AlloyRecipe} the smeltery reads, one JEI recipe each (docs/SCOPE.md M2 issue #109).
 * Unlike melting, alloying has no override-collision to dedupe -- a recipe is matched by its whole
 * input fluid combination, not by looking up one item against every recipe -- so this is a plain
 * snapshot of the registry, not a transform.
 */
final class AlloyingRecipes {
    static List<AlloyRecipe> build(Map<ResourceLocation, AlloyRecipe> recipes) {
        return new ArrayList<>(recipes.values());
    }

    private AlloyingRecipes() {}
}
