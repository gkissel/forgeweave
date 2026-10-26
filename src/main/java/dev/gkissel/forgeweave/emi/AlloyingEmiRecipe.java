package dev.gkissel.forgeweave.emi;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;

/**
 * One smeltery alloy: two or more molten metals combine while the tank holds enough of each. The
 * JEI {@code AlloyingCategory}'s layout, upstream's {@code AlloyRecipeCategory}: the inputs share a
 * 48px-wide row of tanks at (19,11), the alloy goes in the 16x32 tank at (137,11), upstream's
 * animated arrow sits at (90,21), and a flame fills the fuel tank at (94,43) in place of the fuel
 * fluids Forgeweave's alloying has none of. EMI's own full flame stands in for JEI's.
 */
final class AlloyingEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.ALLOYING;
    /** ponytail: M2 ships two-input alloys; a modpack recipe with more only shows its first three, as in JEI. */
    private static final int MAX_DISPLAYED_INPUTS = 3;
    private static final int INPUT_X = 19;
    private static final int INPUT_Y = 11;
    private static final int INPUT_TOTAL_WIDTH = 48;
    private static final int TANK_HEIGHT = 32;
    private static final int OUTPUT_X = 137;
    private static final int OUTPUT_Y = 11;
    private static final int OUTPUT_WIDTH = 16;
    /** Upstream's own arrow -- `(alloy.png, 172, 0, 24, 17).buildAnimated(200, LEFT, false)` at (90,21). */
    private static final int ARROW_U = 172;
    private static final int ARROW_V = 0;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_TICKS = 200;
    private static final int ARROW_X = 90;
    private static final int ARROW_Y = 21;
    private static final int FUEL_X = 94;
    private static final int FUEL_Y = 43;
    private static final int FUEL_SIZE = 16;

    private final List<FluidStack> shownInputs;
    private final EmiStack result;

    AlloyingEmiRecipe(ResourceLocation id, AlloyRecipe recipe) {
        super(ForgeweaveEmiCategories.ALLOYING, id, PANEL.width(), PANEL.height());
        shownInputs = recipe.inputs().subList(0, Math.min(recipe.inputs().size(), MAX_DISPLAYED_INPUTS));
        List<EmiIngredient> fluids = new ArrayList<>();
        for (FluidStack input : recipe.inputs()) {
            fluids.add(EmiStack.of(input.getFluid(), input.getAmount()));
        }
        inputs = List.copyOf(fluids);
        result = EmiStack.of(recipe.result().getFluid(), recipe.result().getAmount());
        outputs = List.of(result);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, PANEL);
        EmiArt.arrow(widgets, PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, ARROW_X, ARROW_Y, ARROW_TICKS);
        EmiTexture flame = EmiTexture.FULL_FLAME;
        widgets.addTexture(flame, FUEL_X + (FUEL_SIZE - flame.width) / 2, FUEL_Y + (FUEL_SIZE - flame.height) / 2);

        int shown = shownInputs.size();
        if (shown > 0) {
            int slotWidth = INPUT_TOTAL_WIDTH / shown;
            for (int i = 0; i < shown; i++) {
                // Upstream's own drawVariableFluids: every tank but the last is exactly totalWidth/count
                // wide; the last absorbs the integer-division remainder so the row's total width is exact.
                int width = i == shown - 1 ? INPUT_TOTAL_WIDTH - slotWidth * (shown - 1) : slotWidth;
                EmiArt.tank(widgets, inputs.get(i), INPUT_X + i * slotWidth, INPUT_Y, width, TANK_HEIGHT,
                        shownInputs.get(i).getAmount());
            }
        }
        EmiArt.tank(widgets, result, OUTPUT_X, OUTPUT_Y, OUTPUT_WIDTH, TANK_HEIGHT, result.getAmount())
                .appendTooltip(Component.translatable("jei.category.forgeweave.alloying.ratio_note"))
                .recipeContext(this);
    }
}
