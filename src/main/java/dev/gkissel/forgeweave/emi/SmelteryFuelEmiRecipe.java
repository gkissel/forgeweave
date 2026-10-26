package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.client.TemperatureText;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;
import dev.gkissel.forgeweave.jei.SmelteryFuelDisplay;

/**
 * One smeltery fuel (issue #890): what the smeltery burns, how much per drain, how long that lasts
 * and how hot it runs. The JEI {@code SmelteryFuelCategory}'s layout on the melting panel: the fuel's
 * bucket where melting has its item, the amount burned in the tank, the arrow sweeping over the
 * fuel's own duration, and the temperature above it. The energized tank's synthetic row (issue #972)
 * shows the tank itself in the bucket's place.
 */
final class SmelteryFuelEmiRecipe extends BasicEmiRecipe {
    private static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.MELTING;
    private static final int BUCKET_X = 24;
    private static final int BUCKET_Y = 18;
    private static final int FLUID_X = 96;
    private static final int FLUID_Y = 4;
    private static final int FLUID_SIZE = 32;
    private static final int OVERLAY_U = 132;
    private static final int OVERLAY_V = 0;
    private static final int ARROW_U = 150;
    private static final int ARROW_V = 41;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;
    private static final int ARROW_X = 56;
    private static final int ARROW_Y = 18;
    private static final int TEMPERATURE_CENTER_X = 56;
    private static final int TEMPERATURE_Y = 3;
    private static final int TEXT_COLOR = 0x404040;

    private final SmelteryFuelDisplay display;
    private final EmiStack input;
    private final EmiStack fluid;

    SmelteryFuelEmiRecipe(ResourceLocation id, SmelteryFuelDisplay display) {
        super(ForgeweaveEmiCategories.SMELTERY_FUEL, id, PANEL.width(), PANEL.height());
        this.display = display;
        Item item = display.energizedTank() ? ForgeweaveItems.ENERGIZED_TANK.get() : display.fluid().getBucket();
        input = item == Items.AIR ? EmiStack.EMPTY : EmiStack.of(item);
        fluid = EmiStack.of(display.fluid(), display.amount());
        inputs = input.isEmpty() ? List.of() : List.of(input);
        outputs = List.of(fluid);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, PANEL);
        EmiArt.arrow(widgets, PANEL.background(), ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, ARROW_X, ARROW_Y,
                display.duration());
        EmiArt.centeredText(widgets, TemperatureText.format(display.temperature()), TEMPERATURE_CENTER_X, TEMPERATURE_Y,
                TEXT_COLOR, false, PANEL.width());
        if (!input.isEmpty()) {
            EmiArt.slot(widgets, input, BUCKET_X, BUCKET_Y);
        }
        SlotWidget tank = EmiArt.tank(widgets, fluid, FLUID_X, FLUID_Y, FLUID_SIZE, FLUID_SIZE, display.amount())
                .recipeContext(this);
        if (display.energizedTank()) {
            tank.appendTooltip(Component.translatable("jei.category.forgeweave.smeltery_fuel.energized_sample"));
            tank.appendTooltip(Component.translatable("jei.category.forgeweave.smeltery_fuel.energized_cost",
                    display.energizedCost(), display.temperature()));
            tank.appendTooltip(Component.translatable("jei.category.forgeweave.smeltery_fuel.energized_overdrive"));
        } else {
            tank.appendTooltip(Component.translatable("jei.category.forgeweave.smeltery_fuel.duration", display.duration()));
            if (display.hotterThanLavaBy() > 0) {
                tank.appendTooltip(Component.translatable("jei.category.forgeweave.smeltery_fuel.vs_lava",
                        display.hotterThanLavaBy()));
            }
        }
        EmiArt.texture(widgets, PANEL.background(), OVERLAY_U, OVERLAY_V, FLUID_SIZE, FLUID_SIZE, FLUID_X, FLUID_Y);
    }
}
