package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.ModifierApplicationRecipes;
import dev.gkissel.forgeweave.jei.ModifierPanel;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;

/**
 * One Tool Station modifier recipe: reagents applied to a tool with a free slot add or level a
 * modifier. Laid out on {@link ModifierPanel}, exactly as the JEI {@code ModifierApplicationCategory}
 * is: the reagents in upstream's ring of five, the tool in the middle and again, modified, right of
 * the arrow, the modifier's name across the top and its level cap under it. Hovering either line
 * shows the modifier's description (issue #1102).
 *
 * <p>The two tool slots are context, not ingredients -- any tool with a free slot qualifies, and a
 * modifier has no item form to output -- so, like JEI's render-only slots, neither is in {@link
 * #getInputs()} or {@link #getOutputs()}. That leaves the reagents as the recipe's only inputs, in
 * the order the fill handler places them.
 */
final class ModifierApplicationEmiRecipe extends BasicEmiRecipe {
    private static final int MAX_REAGENT_SLOTS = ModifierPanel.INPUT_SLOTS.length;
    private static final int TEXT_LINE_HEIGHT = 9;

    private final ModifierRecipe recipe;
    private final EmiIngredient tools;

    ModifierApplicationEmiRecipe(ResourceLocation id, ModifierRecipe recipe) {
        super(ForgeweaveEmiCategories.MODIFIER_APPLICATION, id, ModifierPanel.WIDTH, ModifierPanel.HEIGHT);
        this.recipe = recipe;
        List<ItemStack> catalog = ModifierApplicationRecipes.tools(recipe,
                Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess());
        tools = EmiArt.items(catalog);
        if (recipe.requireAllReagents()) {
            // Issue #781: an AND recipe needs every reagent visible at once, one slot each.
            inputs = recipe.reagents().stream()
                    .limit(Math.min(recipe.reagentSlotCount(), MAX_REAGENT_SLOTS))
                    .map(reagent -> EmiIngredient.of(reagent.ingredient()))
                    .toList();
        } else {
            // Every accepted reagent cycles through the one slot (issue #259).
            inputs = List.of(EmiIngredient.of(recipe.reagents().stream()
                    .map(reagent -> EmiIngredient.of(reagent.ingredient()))
                    .toList()));
        }
        outputs = List.of();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, ModifierPanel.PANEL);
        EmiArt.slot(widgets, tools, ModifierPanel.TOOL_X, ModifierPanel.TOOL_Y);
        for (int i = 0; i < inputs.size(); i++) {
            EmiArt.slot(widgets, inputs.get(i), ModifierPanel.INPUT_SLOTS[i][0], ModifierPanel.INPUT_SLOTS[i][1]);
        }
        EmiArt.slot(widgets, tools.copy(), ModifierPanel.RESULT_X, ModifierPanel.RESULT_Y);

        EmiArt.centeredText(widgets, ModifierApplication.name(recipe.modifier()), ModifierPanel.NAME_CENTER_X,
                ModifierPanel.NAME_Y, ModifierPanel.NAME_COLOR, true, ModifierPanel.NAME_WIDTH);
        EmiArt.centeredText(widgets, Component.translatable("jei.category.forgeweave.modifier_application.level_cap",
                        recipe.levelsReached(recipe.maxLevel())), ModifierPanel.LEVEL_CENTER_X, ModifierPanel.LEVEL_Y,
                ModifierPanel.TEXT_COLOR, false, ModifierPanel.LEVEL_WIDTH);
        widgets.addTooltipText(List.of(ModifierApplication.description(recipe.modifier())), 0, ModifierPanel.NAME_Y,
                ModifierPanel.WIDTH, ModifierPanel.LEVEL_Y + TEXT_LINE_HEIGHT - ModifierPanel.NAME_Y);
    }
}
