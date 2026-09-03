package dev.gkissel.forgeweave.client;

import net.minecraft.network.chat.Component;

import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Renders a smeltery temperature as the same effective number the recipes and fuels check against
 * (issue #932): no unit conversion, just the raw value with a degree-sign suffix. Both display
 * sites -- the smeltery's fuel tooltip and JEI's melting/fuel categories -- go through here so they
 * can never disagree.
 *
 * <p><b>Recorded deviation from the 1.12 reference (maintainer directive, issue #932).</b> Upstream
 * 1.12's {@code Util#temperatureString} subtracted its ambient baseline ({@link
 * MeltingRecipe#AMBIENT_TEMPERATURE}) before printing, so a value read as celsius. Forgeweave's own
 * {@code smeltery_fuel}/{@code melting_recipe} datapack JSON and every GameTest talk about the
 * un-subtracted number, so a display-only offset made the same recipe look like it used two
 * different temperatures. Dropping the offset means what a player sees is what the recipe checks.
 */
public final class TemperatureText {
    private static final String KEY = "gui.forgeweave.temperature";

    /**
     * @param temperature a temperature on Forgeweave's internal scale (a {@link
     *     MeltingRecipe#temperature()} or a smeltery's current heat), rendered unchanged
     */
    public static Component format(int temperature) {
        return Component.translatable(KEY, temperature);
    }

    private TemperatureText() {}
}
