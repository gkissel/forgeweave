package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.modifier.ModifierRecipe;

/**
 * Every {@link ModifierRecipe} the Tool Station's modify tab accepts, one JEI recipe each
 * (docs/SCOPE.md M2 issues #105-#108, JEI category per issue #109). No dedup or cycling is needed:
 * each recipe's reagent {@code Ingredient} is its own display input, and two recipes are free to
 * target the same modifier id with different reagents (extra_slot's crafted item and netherite
 * ingot) -- both simply show up as their own row, which is the desired "show both" behavior rather
 * than something to collapse.
 */
final class ModifierApplicationRecipes {
    static List<ModifierRecipe> build(Map<ResourceLocation, ModifierRecipe> recipes) {
        return new ArrayList<>(recipes.values());
    }

    private ModifierApplicationRecipes() {}
}
