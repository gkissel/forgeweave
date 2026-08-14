package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #276: upstream 1.12's {@code temperatureCelsius}, whose "on" branch is
 * {@code temperature - 300} ({@code Util#temperatureString}). Drives the preference as a parameter
 * because it lives in a {@code CLIENT}-type config spec, which no unit test environment -- and no
 * dedicated server, hence no GameTest -- ever loads.
 */
class TemperatureTextTest {

    /** Molten iron's melting temperature, the number the smeltery and JEI actually draw. */
    private static final int IRON = 534;

    @Test
    void celsiusSubtractsAmbientAndLabelsTheUnit() {
        assertRenders(TemperatureText.format(IRON, true),
                "gui.forgeweave.temperature.celsius", IRON - MeltingRecipe.AMBIENT_TEMPERATURE);
    }

    @Test
    void kelvinKeepsTheInternalNumber() {
        assertRenders(TemperatureText.format(IRON, false), "gui.forgeweave.temperature.kelvin", IRON);
    }

    @Test
    void ambientIsZeroCelsius() {
        assertRenders(TemperatureText.format(MeltingRecipe.AMBIENT_TEMPERATURE, true),
                "gui.forgeweave.temperature.celsius", 0);
    }

    private static void assertRenders(Component actual, String key, int value) {
        TranslatableContents contents = (TranslatableContents) actual.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(List.of(value), List.of(contents.getArgs()));
    }
}
