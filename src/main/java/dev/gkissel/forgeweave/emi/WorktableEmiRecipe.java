package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.WorktableDisplay;

/**
 * One Modifier Worktable function (issue #1057): a tool plus a reagent, and what the reagent does to
 * the modifiers already on it. The JEI {@code WorktableCategory}'s layout, itself upstream's {@code
 * ModifierWorktableCategory} strip: no background art, four framed slots and the function's title.
 *
 * <p>The tool and the second reagent slot are context, not inputs -- the table works on any tool,
 * and the second slot is the sorting direction rather than a second cost -- so only the first reagent
 * is in {@link #getInputs()}, which is also the one slot a fill puts anything in.
 */
final class WorktableEmiRecipe extends BasicEmiRecipe {
    /** Upstream {@code ModifierWorktableCategory}'s own {@code (121, 35)}. */
    private static final int WIDTH = 121;
    private static final int HEIGHT = 35;
    private static final int TOOL_X = 23;
    private static final int FIRST_INPUT_X = 43;
    private static final int SECOND_INPUT_X = 61;
    private static final int RESULT_X = 82;
    private static final int SLOT_Y = 16;
    /** Upstream {@code createRecipeExtras}: the title at (3, 2), 0x404040. */
    private static final int TITLE_X = 3;
    private static final int TITLE_Y = 2;
    private static final int TITLE_COLOR = 0x404040;
    private static final int TITLE_WIDTH = 115;

    private final EmiIngredient input;
    private final EmiIngredient leftovers;
    private final Component title;

    WorktableEmiRecipe(ResourceLocation id, WorktableDisplay display) {
        super(ForgeweaveEmiCategories.MODIFIER_WORKTABLE, id, WIDTH, HEIGHT);
        input = EmiIngredient.of(display.recipe().input());
        leftovers = EmiArt.items(display.recipe().leftovers());
        title = Component.translatable("jei.category.forgeweave.modifier_worktable."
                + display.recipe().kind().getSerializedName());
        inputs = List.of(input);
        outputs = EmiArt.outputs(display.recipe().leftovers());
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.slotFrame(widgets, TOOL_X, SLOT_Y);
        EmiArt.slotFrame(widgets, FIRST_INPUT_X, SLOT_Y);
        EmiArt.slotFrame(widgets, SECOND_INPUT_X, SLOT_Y);
        EmiArt.slotFrame(widgets, RESULT_X, SLOT_Y);
        EmiArt.slot(widgets, EmiArt.items(WorktableDisplay.TOOLS), TOOL_X, SLOT_Y);
        EmiArt.slot(widgets, input, FIRST_INPUT_X, SLOT_Y);
        EmiArt.slot(widgets, input.copy(), SECOND_INPUT_X, SLOT_Y);
        EmiArt.slot(widgets, leftovers, RESULT_X, SLOT_Y).recipeContext(this);
        EmiArt.leftText(widgets, title, TITLE_X, TITLE_Y, TITLE_COLOR, TITLE_WIDTH);
    }
}
