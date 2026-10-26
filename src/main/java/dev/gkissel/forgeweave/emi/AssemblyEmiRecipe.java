package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.AssemblyRecipe;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;

/**
 * One Tool Station/Tool Forge assembly: one slot per part, each cycling every material, into the
 * tool. The JEI {@code AssemblyCategory}'s own layout: upstream's panel, the parts as a centred grid
 * at most two wide in the blank zone upstream keeps for its 3D preview, each with upstream's slot
 * frame, and the tool in the panel's own output slot.
 */
final class AssemblyEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.ASSEMBLY;
    /** Upstream's own result slot -- `addSlot(OUTPUT, WIDTH - 26, 23)`. */
    private static final int RESULT_X = PANEL.width() - 26;
    private static final int RESULT_Y = 23;
    /** The blank zone upstream fills with its 3D preview, 70px wide. */
    private static final int PART_ZONE_WIDTH = 70;
    private static final int SLOT_PITCH = 20;
    private static final int MAX_COLUMNS = 2;

    private final Item tool;
    private final EmiStack result;

    AssemblyEmiRecipe(EmiRecipeCategory category, ResourceLocation id, AssemblyRecipe recipe) {
        super(category, id, PANEL.width(), PANEL.height());
        tool = recipe.tool();
        result = EmiStack.of(recipe.result());
        inputs = recipe.parts().stream().map(EmiArt::items).toList();
        outputs = List.of(result);
    }

    /** The tool this recipe builds, which decides the Tool Station tab a fill has to select first. */
    Item tool() {
        return tool;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, PANEL);
        int count = inputs.size();
        for (int slot = 0; slot < count; slot++) {
            EmiArt.slotFrame(widgets, partX(count, slot), partY(count, slot));
            EmiArt.slot(widgets, inputs.get(slot), partX(count, slot), partY(count, slot));
        }
        EmiArt.slot(widgets, result, RESULT_X, RESULT_Y).recipeContext(this);
    }

    /** JEI {@code AssemblyCategory#partX}: the grid is centred in the part zone. */
    private static int partX(int count, int slot) {
        int columns = Math.min(count, MAX_COLUMNS);
        return (PART_ZONE_WIDTH - columns * SLOT_PITCH) / 2 + (slot % MAX_COLUMNS) * SLOT_PITCH;
    }

    /** JEI {@code AssemblyCategory#partY}: centred in the panel's height, so two parts line up with the output. */
    private static int partY(int count, int slot) {
        int rows = (count + MAX_COLUMNS - 1) / MAX_COLUMNS;
        return (PANEL.height() - rows * SLOT_PITCH) / 2 + (slot / MAX_COLUMNS) * SLOT_PITCH;
    }
}
