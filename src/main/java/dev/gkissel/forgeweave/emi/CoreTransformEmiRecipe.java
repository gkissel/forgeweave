package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;
import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;

/**
 * Pour-to-transform (issue #890): pouring a fluid over one core turns it into another. The JEI
 * {@code CoreTransformCategory}'s layout on the casting panel: the fluid in the tank, the from-core
 * in the cast's place, the to-core in the output, and the faucet where casting draws its station.
 */
final class CoreTransformEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.CASTING;
    private static final int FLUID_X = 3;
    private static final int FLUID_Y = 3;
    private static final int TANK_SIZE = 32;
    private static final int OVERLAY_U = 133;
    private static final int OVERLAY_V = 0;
    private static final int FROM_BLOCK_X = 38;
    private static final int FROM_BLOCK_Y = 19;
    private static final int TO_BLOCK_X = 93;
    private static final int TO_BLOCK_Y = 18;
    private static final int ARROW_U = 117;
    private static final int ARROW_V = 32;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 58;
    private static final int ARROW_Y = 18;
    /** No fill time to read, so the JEI category's one fixed sweep period. */
    private static final int ARROW_TICKS = 100;
    private static final int FAUCET_ICON_X = 38;
    private static final int FAUCET_ICON_Y = 35;
    private static final int ICON_SIZE = 16;

    private final int amount;
    private final EmiStack fromBlock;
    private final EmiStack fluid;
    private final EmiStack toBlock;

    CoreTransformEmiRecipe(ResourceLocation id, CoreTransformRecipe recipe) {
        super(ForgeweaveEmiCategories.CORE_TRANSFORM, id, PANEL.width(), PANEL.height());
        amount = recipe.amount();
        fromBlock = EmiStack.of(recipe.fromBlock());
        fluid = EmiStack.of(recipe.fluid(), recipe.amount());
        toBlock = EmiStack.of(recipe.toBlock());
        inputs = List.of(fromBlock, fluid);
        outputs = List.of(toBlock);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, PANEL);
        EmiArt.arrow(widgets, PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, ARROW_X, ARROW_Y, ARROW_TICKS);
        EmiStack faucet = EmiStack.of(ForgeweaveItems.FAUCET.get());
        widgets.addDrawable(FAUCET_ICON_X, FAUCET_ICON_Y, ICON_SIZE, ICON_SIZE,
                (guiGraphics, mouseX, mouseY, delta) -> faucet.render(guiGraphics, 0, 0, delta));
        EmiArt.slot(widgets, fromBlock, FROM_BLOCK_X, FROM_BLOCK_Y);
        EmiArt.tank(widgets, fluid, FLUID_X, FLUID_Y, TANK_SIZE, TANK_SIZE, amount);
        EmiArt.texture(widgets, PANEL.background(), OVERLAY_U, OVERLAY_V, TANK_SIZE, TANK_SIZE, FLUID_X, FLUID_Y);
        EmiArt.slot(widgets, toBlock, TO_BLOCK_X, TO_BLOCK_Y).recipeContext(this);
    }
}
