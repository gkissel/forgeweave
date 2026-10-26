package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;
import dev.gkissel.forgeweave.jei.PartCraftingRecipe;

/**
 * One Part Builder craft: pattern + material -> part + shard change, at the JEI {@code
 * PartCraftingCategory}'s own panel and slot positions.
 *
 * <p>One EMI recipe per crafting-item option, where JEI cycles all of a material's options through
 * one row: the options need different item counts (a log overpays a head, a shard pays it exactly),
 * and an EMI slot cycling stacks of different counts shows the same count for all of them. Split
 * up, each row shows its real count and the change that count actually produces.
 */
final class PartCraftingEmiRecipe extends BasicEmiRecipe {
    /** Upstream's own slots -- pattern (4,16), material (25,16), change in the pattern-type slot (46,16), result (96,15). */
    private static final int PATTERN_X = 4;
    private static final int MATERIAL_X = 25;
    private static final int CHANGE_X = 46;
    private static final int INPUT_Y = 16;
    private static final int RESULT_X = 96;
    private static final int RESULT_Y = 15;

    private final EmiIngredient pattern;
    private final EmiIngredient material;
    private final EmiStack result;
    private final EmiStack change;

    PartCraftingEmiRecipe(ResourceLocation id, PartCraftingRecipe recipe, int option) {
        super(ForgeweaveEmiCategories.PART_CRAFTING, id, JeiCategoryGeometry.PART_CRAFTING.width(),
                JeiCategoryGeometry.PART_CRAFTING.height());
        pattern = EmiStack.of(recipe.pattern());
        material = EmiStack.of(recipe.materialInputs().get(option));
        result = EmiStack.of(recipe.result());
        ItemStack changeStack = recipe.changeOutputs().get(option);
        change = changeStack == null ? EmiStack.EMPTY : EmiStack.of(changeStack);
        inputs = List.of(pattern, material);
        outputs = change.isEmpty() ? List.of(result) : List.of(result, change);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // The arrow and all four slot frames are baked into upstream's own panel.
        EmiArt.panel(widgets, JeiCategoryGeometry.PART_CRAFTING);
        EmiArt.slot(widgets, pattern, PATTERN_X, INPUT_Y);
        EmiArt.slot(widgets, material, MATERIAL_X, INPUT_Y);
        EmiArt.slot(widgets, change, CHANGE_X, INPUT_Y).recipeContext(this);
        EmiArt.slot(widgets, result, RESULT_X, RESULT_Y).recipeContext(this);
    }
}
