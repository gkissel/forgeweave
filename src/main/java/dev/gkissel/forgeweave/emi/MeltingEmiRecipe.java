package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.client.TemperatureText;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;
import dev.gkissel.forgeweave.jei.MeltingDisplay;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * One smeltery melting row: an item melts into a fluid amount at a required temperature. The JEI
 * {@code MeltingCategory}'s layout, upstream's {@code AbstractMeltingCategory}: the item at (24,18),
 * the result in the 32x32 tank at (96,4) under its graduation overlay, upstream's animated arrow,
 * the temperature centred above it, and the fuel column at (4,4) cycling every fuel hot enough for
 * the recipe (issue #893). The fuels are shown, not required -- the smeltery reads the registry at
 * melt time -- so like JEI's render-only slot they are no part of the recipe's inputs.
 */
final class MeltingEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.MELTING;
    private static final int INPUT_X = 24;
    private static final int INPUT_Y = 18;
    private static final int OUTPUT_X = 96;
    private static final int OUTPUT_Y = 4;
    private static final int OUTPUT_SIZE = 32;
    /** Upstream's own tank overlay -- `createDrawable(melting.png, 132, 0, 32, 32)`. */
    private static final int OVERLAY_U = 132;
    private static final int OVERLAY_V = 0;
    /** Upstream's own arrow -- `(melting.png, 150, 41, 24, 17)` at (56,18). */
    private static final int ARROW_U = 150;
    private static final int ARROW_V = 41;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 56;
    private static final int ARROW_Y = 18;
    /** {@code MeltingDisplay} carries no time, so the JEI category's one fixed sweep period. */
    private static final int ARROW_TICKS = 100;
    private static final int FUEL_X = 4;
    private static final int FUEL_Y = 4;
    private static final int FUEL_WIDTH = 12;
    private static final int FUEL_HEIGHT = 32;
    /** Upstream forces the tank display to always read full regardless of the fuel's registered amount. */
    private static final int FUEL_DISPLAY_AMOUNT = 1;
    private static final int TEMPERATURE_CENTER_X = 56;
    private static final int TEMPERATURE_Y = 3;
    private static final int TEXT_COLOR = 0x404040;

    private final MeltingDisplay display;
    private final EmiIngredient input;
    private final EmiStack result;

    MeltingEmiRecipe(ResourceLocation id, MeltingDisplay display) {
        super(ForgeweaveEmiCategories.MELTING, id, PANEL.width(), PANEL.height());
        this.display = display;
        input = EmiArt.items(display.inputs());
        result = EmiStack.of(display.fluid(), display.amount());
        inputs = List.of(input);
        outputs = List.of(result);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, PANEL);
        EmiArt.arrow(widgets, PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, ARROW_X, ARROW_Y, ARROW_TICKS);
        EmiArt.centeredText(widgets, TemperatureText.format(display.temperature()), TEMPERATURE_CENTER_X, TEMPERATURE_Y,
                TEXT_COLOR, false, PANEL.width());

        EmiArt.slot(widgets, input, INPUT_X, INPUT_Y);

        var output = EmiArt.tank(widgets, result, OUTPUT_X, OUTPUT_Y, OUTPUT_SIZE, OUTPUT_SIZE, display.amount())
                .recipeContext(this);
        if (display.ore()) {
            output.appendTooltip(Component.translatable("jei.category.forgeweave.melting.core_multiplier"));
        }
        EmiArt.texture(widgets, PANEL.background(), OVERLAY_U, OVERLAY_V, OUTPUT_SIZE, OUTPUT_SIZE, OUTPUT_X, OUTPUT_Y);

        if (!display.fuels().isEmpty()) {
            EmiIngredient fuels = EmiIngredient.of(display.fuels().stream()
                    .map(fuel -> EmiStack.of(fuel.fluid(), FUEL_DISPLAY_AMOUNT))
                    .toList());
            EmiArt.tank(widgets, fuels, FUEL_X, FUEL_Y, FUEL_WIDTH, FUEL_HEIGHT, FUEL_DISPLAY_AMOUNT)
                    .appendTooltip(shown -> EmiArt.line(fuelTemperature(shown)));
        }
    }

    /** The temperature line for whichever fuel the cycling tank is showing right now. */
    private Component fuelTemperature(EmiIngredient shown) {
        Object fluid = shown.getEmiStacks().get(0).getKey();
        return display.fuels().stream()
                .filter(fuel -> fuel.fluid() == fluid)
                .findFirst()
                .map(SmelteryFuel::temperature)
                .map(temperature -> Component.translatable("jei.category.forgeweave.melting.fuel_temperature",
                        TemperatureText.format(temperature)))
                .orElse(Component.empty());
    }
}
