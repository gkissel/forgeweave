package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.EntityMeltingDisplay;
import dev.gkissel.forgeweave.jei.EntityPreview;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;

/**
 * Entity melting (issue #931): what a living entity standing in a smeltery melts into. The JEI
 * {@code EntityMeltingCategory}'s layout, upstream 1.20's {@code EntityMeltingRecipeCategory}: the
 * live entity in the left basin's opening, the damage in hearts against the panel's heart, the fluid
 * in the right basin, and the usable fuels cycling in the tank under the arrow. The fuels are this
 * recipe's catalysts, as they are JEI's, so looking up a fuel's uses lands here.
 */
final class EntityMeltingEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.ENTITY_MELTING;
    private static final int ENTITY_X = 19;
    private static final int ENTITY_Y = 11;
    private static final int ENTITY_BOX = 32;
    private static final int FLUID_X = 115;
    private static final int FLUID_Y = 11;
    private static final int FLUID_WIDTH = 16;
    private static final int FLUID_HEIGHT = 32;
    private static final int FUEL_X = 75;
    private static final int FUEL_Y = 43;
    private static final int FUEL_SIZE = 16;
    private static final int FUEL_OVERLAY_U = 150;
    private static final int FUEL_OVERLAY_V = 74;
    private static final int ARROW_U = 150;
    private static final int ARROW_V = 41;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 71;
    private static final int ARROW_Y = 21;
    private static final int ARROW_TICKS = 200;
    /** Upstream right-aligns the damage, in hearts, against the heart baked into the panel, in red. */
    private static final int DAMAGE_RIGHT_X = 84;
    private static final int DAMAGE_Y = 8;
    private static final int DAMAGE_COLOR = 0xFF0000;

    /** One cache of live render entities for every row, rather than one per recipe. */
    private static final EntityPreview PREVIEW = new EntityPreview();

    private final EntityMeltingDisplay display;
    private final EmiStack fluid;

    EntityMeltingEmiRecipe(ResourceLocation id, EntityMeltingDisplay display) {
        super(ForgeweaveEmiCategories.ENTITY_MELTING, id, PANEL.width(), PANEL.height());
        this.display = display;
        fluid = EmiStack.of(display.fluid(), display.amount());
        inputs = List.of();
        catalysts = display.fuels().isEmpty() ? List.of()
                : List.of(EmiIngredient.of(display.fuels().stream().map(fuel -> EmiStack.of(fuel, 1)).toList()));
        outputs = List.of(fluid);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, PANEL);
        EmiArt.arrow(widgets, PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, ARROW_X, ARROW_Y, ARROW_TICKS);
        // Upstream: Float.toString(damage / 2f), hearts rather than half-hearts.
        widgets.addText(Component.literal(Float.toString(EntityMeltingRecipe.DAMAGE / 2f)), DAMAGE_RIGHT_X, DAMAGE_Y,
                DAMAGE_COLOR, false).horizontalAlign(TextWidget.Alignment.END);
        widgets.addDrawable(ENTITY_X, ENTITY_Y, ENTITY_BOX, ENTITY_BOX, (guiGraphics, mouseX, mouseY, delta) ->
                PREVIEW.draw(guiGraphics, display.primaryEntity(), 0, 0, ENTITY_BOX));

        EmiArt.tank(widgets, fluid, FLUID_X, FLUID_Y, FLUID_WIDTH, FLUID_HEIGHT, display.amount())
                .appendTooltip(Component.translatable("jei.category.forgeweave.entity_melting.damage",
                        (int) EntityMeltingRecipe.DAMAGE))
                .appendTooltip(Component.translatable("jei.category.forgeweave.entity_melting.per_hit"))
                .recipeContext(this);
        if (!catalysts.isEmpty()) {
            EmiArt.tank(widgets, catalysts.get(0), FUEL_X, FUEL_Y, FUEL_SIZE, FUEL_SIZE, 1).catalyst(true);
        }
        EmiArt.texture(widgets, PANEL.background(), FUEL_OVERLAY_U, FUEL_OVERLAY_V, FUEL_SIZE, FUEL_SIZE, FUEL_X, FUEL_Y);

        Component entityName = display.defaultRow()
                ? Component.translatable("jei.category.forgeweave.entity_melting.default")
                : display.primaryEntity().getDescription();
        widgets.addTooltipText(List.of(entityName), ENTITY_X, ENTITY_Y, ENTITY_BOX, ENTITY_BOX);
    }
}
