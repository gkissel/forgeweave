package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * Builds one {@link MeltingDisplay} per melting registry entry that still wins at least one item
 * after most-specific-wins resolution (docs/SCOPE.md M2 issue #109; #96's PR #124 warning).
 *
 * <p>A naive per-entry enumeration would show every item a tag {@code Ingredient} matches,
 * including ones a more specific item-keyed recipe overrides -- e.g. vanilla copper ore would show
 * up under both {@code c:ores/copper} (144) and its own 504 override. Instead, each entry's
 * candidate items ({@code Ingredient#getItems}) are individually resolved through {@link
 * MeltingRecipe#resolve} (the same tie-break {@code MeltingRecipe#find} uses at melt time) and kept
 * only where this entry is the actual winner; an entry that loses every one of its candidates to
 * more specific overrides contributes no display recipe at all.
 */
final class MeltingRecipes {
    static List<MeltingDisplay> build(Map<ResourceLocation, MeltingRecipe> recipes, Map<ResourceLocation, SmelteryFuel> fuels) {
        List<MeltingDisplay> displays = new ArrayList<>();
        for (MeltingRecipe recipe : recipes.values()) {
            List<ItemStack> inputs = wonInputs(recipes, recipe);
            if (inputs.isEmpty()) {
                continue; // every candidate item is won by a more specific override -- nothing to show here.
            }
            displays.add(new MeltingDisplay(inputs, recipe.fluid(), recipe.amount(), recipe.temperature(), recipe.ore(),
                    acceptedFuels(fuels, recipe.temperature())));
        }
        return displays;
    }

    /** Issue #893: upstream's own fuel filter -- every registered fuel hot enough to reach this recipe. */
    private static List<SmelteryFuel> acceptedFuels(Map<ResourceLocation, SmelteryFuel> fuels, int temperature) {
        List<SmelteryFuel> accepted = new ArrayList<>();
        for (SmelteryFuel fuel : fuels.values()) {
            if (fuel.temperature() >= temperature) {
                accepted.add(fuel);
            }
        }
        return accepted;
    }

    private static List<ItemStack> wonInputs(Map<ResourceLocation, MeltingRecipe> recipes, MeltingRecipe recipe) {
        List<ItemStack> inputs = new ArrayList<>();
        for (ItemStack candidate : recipe.input().getItems()) {
            if (MeltingRecipe.resolve(recipes, candidate).orElse(null) == recipe) {
                inputs.add(candidate);
            }
        }
        return inputs;
    }

    private MeltingRecipes() {}
}
