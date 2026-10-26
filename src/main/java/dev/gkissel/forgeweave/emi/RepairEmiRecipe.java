package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;
import dev.gkissel.forgeweave.jei.RepairRecipe;

/**
 * One Tool Station repair: any tool + one of a material's repair items -> the same tool, repaired.
 * The JEI {@code RepairCategory}'s layout: upstream's {@code SeveringCategory} row, with upstream's
 * slot frame drawn under the two inputs its blank art lacks. The tool slot and the result cycle
 * through the same list, so EMI's once-a-second cycling keeps the two in step, as JEI's does.
 */
final class RepairEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.REPAIR;
    private static final int TOOL_X = 3;
    private static final int REPAIR_ITEM_X = 25;
    private static final int INPUT_Y = 11;
    /** Upstream {@code SeveringCategory}'s own output slot -- `addSlot(OUTPUT, 76, 11)`. */
    private static final int RESULT_X = 76;
    private static final int RESULT_Y = 11;

    private final EmiIngredient tools;
    private final EmiIngredient repairItems;
    private final EmiIngredient repairedTools;

    RepairEmiRecipe(ResourceLocation id, RepairRecipe recipe) {
        super(ForgeweaveEmiCategories.TOOL_REPAIR, id, PANEL.width(), PANEL.height());
        tools = EmiArt.items(recipe.tools());
        repairItems = EmiArt.items(recipe.repairItems());
        repairedTools = EmiArt.items(recipe.repairedTools());
        inputs = List.of(tools, repairItems);
        outputs = EmiArt.outputs(recipe.repairedTools());
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // The arrow and the output slot's frame are baked into the panel; the input side is blank art.
        EmiArt.panel(widgets, PANEL);
        EmiArt.slotFrame(widgets, TOOL_X, INPUT_Y);
        EmiArt.slotFrame(widgets, REPAIR_ITEM_X, INPUT_Y);
        EmiArt.slot(widgets, tools, TOOL_X, INPUT_Y);
        EmiArt.slot(widgets, repairItems, REPAIR_ITEM_X, INPUT_Y);
        EmiArt.slot(widgets, repairedTools, RESULT_X, RESULT_Y).recipeContext(this);
    }
}
