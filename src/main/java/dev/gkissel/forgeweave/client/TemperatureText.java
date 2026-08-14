package dev.gkissel.forgeweave.client;

import net.minecraft.network.chat.Component;

import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Renders a smeltery temperature in the unit the player asked for (issue #276), the port of upstream
 * 1.12's {@code Util#temperatureString}: celsius by default, or the internal kelvin scale when
 * {@code temperatureCelsius} is off. Both display sites -- the smeltery's fuel tooltip and JEI's
 * melting category -- go through here so they can never disagree.
 *
 * <p>Forgeweave stores temperatures on the same zero-is-{@link MeltingRecipe#AMBIENT_TEMPERATURE}
 * kelvin scale upstream uses, so celsius is that same subtraction; the units were previously drawn
 * unlabelled, which read as celsius numbers labelled as nothing.
 *
 * <p>Client only: {@link ForgeweaveClientConfig} is a {@code CLIENT}-type spec, so nothing that runs
 * on a dedicated server may call this.
 */
public final class TemperatureText {
    private static final String CELSIUS = "gui.forgeweave.temperature.celsius";
    private static final String KELVIN = "gui.forgeweave.temperature.kelvin";

    /**
     * @param kelvin a temperature on Forgeweave's internal scale (a {@link MeltingRecipe#temperature()}
     *     or a smeltery's current heat)
     */
    public static Component format(int kelvin) {
        return format(kelvin, ForgeweaveClientConfig.TEMPERATURE_CELSIUS.get());
    }

    /** Takes the preference as a parameter so unit tests can drive both units. */
    public static Component format(int kelvin, boolean celsius) {
        return celsius
                ? Component.translatable(CELSIUS, kelvin - MeltingRecipe.AMBIENT_TEMPERATURE)
                : Component.translatable(KELVIN, kelvin);
    }

    private TemperatureText() {}
}
