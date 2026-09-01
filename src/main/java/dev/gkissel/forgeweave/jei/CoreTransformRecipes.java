package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;

/**
 * Every {@link CoreTransformRecipe} the smeltery reads, one JEI recipe each (issue #890, #845's
 * pour-to-transform mechanic). Same shape as {@link AlloyingRecipes}: a recipe is matched by its
 * whole {@code (fluid, fromBlock)} pair rather than by looking up one item in isolation, so there is
 * no override-collision to dedupe here either -- a plain snapshot of the registry.
 */
final class CoreTransformRecipes {
    static List<CoreTransformRecipe> build(Map<ResourceLocation, CoreTransformRecipe> recipes) {
        return new ArrayList<>(recipes.values());
    }

    private CoreTransformRecipes() {}
}
