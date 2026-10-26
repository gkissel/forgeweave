package dev.gkissel.forgeweave.emi;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import dev.emi.emi.api.recipe.EmiPatternCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.api.widget.SlotWidget;

import dev.gkissel.forgeweave.recipe.RetexturedShapedRecipe;

/**
 * A retextured station's crafting recipe with every wood (or metal) variant it can make as its
 * output, the EMI side of the JEI plugin's {@code RetexturedCraftingExtension}. EMI's own crafting
 * recipe shows only the bare untextured result, which the texture comparison keeps apart from every
 * variant in the item list, so "R" on an oak Stencil Table found no recipe (maintainer report
 * 2026-09-07, the same bug the JEI extension fixed). The output slot cycles the variants.
 */
final class RetexturedCraftingEmiRecipe extends EmiPatternCraftingRecipe {
    private static final int GRID = 3;

    private final List<EmiStack> variants;

    RetexturedCraftingEmiRecipe(RecipeHolder<CraftingRecipe> holder, RetexturedShapedRecipe recipe) {
        super(grid(recipe), EmiStack.EMPTY, holder.id(), false);
        variants = recipe.displayResults().stream().map(EmiStack::of).toList();
    }

    /** The recipe's ingredients laid into the 3x3 grid by row, padded with empty slots. */
    private static List<EmiIngredient> grid(RetexturedShapedRecipe recipe) {
        List<EmiIngredient> grid = new ArrayList<>(GRID * GRID);
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                boolean inside = x < recipe.getWidth() && y < recipe.getHeight();
                Ingredient ingredient = inside ? recipe.getIngredients().get(y * recipe.getWidth() + x) : Ingredient.EMPTY;
                grid.add(EmiIngredient.of(ingredient));
            }
        }
        return grid;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return variants;
    }

    @Override
    public SlotWidget getInputWidget(int slot, int x, int y) {
        return new SlotWidget(input.get(slot), x, y);
    }

    @Override
    public SlotWidget getOutputWidget(int x, int y) {
        return new GeneratedSlotWidget(random -> variants.get(random.nextInt(variants.size())), unique, x, y);
    }
}
