package dev.gkissel.forgeweave.jei;

import java.util.List;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import dev.gkissel.forgeweave.recipe.RetexturedShapedRecipe;

/**
 * Draws a {@link RetexturedShapedRecipe} in JEI's crafting category with every wood (or metal)
 * variant it can produce in the output slot, instead of the bare untextured result the recipe JSON
 * carries. That is what lets "R" on an oak Stencil Table find its own recipe: the creative tab
 * lists the tables per wood and {@link SubtypeKeys#texture} keeps them apart, so the output slot
 * has to hold the textured stacks for the lookup to match (maintainer report 2026-09-07).
 */
final class RetexturedCraftingExtension implements ICraftingCategoryExtension<RetexturedShapedRecipe> {

    @Override
    public void setRecipe(RecipeHolder<RetexturedShapedRecipe> holder, IRecipeLayoutBuilder builder,
            ICraftingGridHelper helper, IFocusGroup focuses) {
        RetexturedShapedRecipe recipe = holder.value();
        List<List<ItemStack>> inputs = recipe.getIngredients().stream()
                .map(Ingredient::getItems).map(List::of).toList();
        helper.createAndSetInputs(builder, inputs, recipe.getWidth(), recipe.getHeight());
        helper.createAndSetOutputs(builder, recipe.displayResults());
    }

    @Override
    public int getWidth(RecipeHolder<RetexturedShapedRecipe> holder) {
        return holder.value().getWidth();
    }

    @Override
    public int getHeight(RecipeHolder<RetexturedShapedRecipe> holder) {
        return holder.value().getHeight();
    }
}
