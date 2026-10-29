package dev.gkissel.forgeweave.jei;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One stack per item an {@link Ingredient} accepts, in its own order. 1.21.2 removed
 * {@code Ingredient#getItems()}; this builds the same list from {@link Ingredient#items()}, which
 * also covers NeoForge custom ingredients. No JEI import, same as {@link RecipeViewerContent}.
 */
final class IngredientStacks {
    static ItemStack[] of(Ingredient ingredient) {
        return ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
    }

    private IngredientStacks() {}
}
