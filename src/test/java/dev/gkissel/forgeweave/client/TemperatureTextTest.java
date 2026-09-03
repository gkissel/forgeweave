package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #932: {@link TemperatureText#format} renders the raw effective number, the same one the
 * recipes and fuels check against -- no unit conversion, no config toggle.
 */
class TemperatureTextTest {

    /** Lava's smeltery fuel temperature, the number the smeltery and JEI actually draw. */
    private static final int LAVA = 1300;

    @Test
    void formatRendersTheRawNumberUnchanged() {
        assertRenders(TemperatureText.format(LAVA), "gui.forgeweave.temperature", LAVA);
    }

    @Test
    void formatDoesNotSubtractAmbient() {
        // Regression guard for the removed offset: this used to render 0 when the temperatureCelsius
        // preference was on (`kelvin - MeltingRecipe.AMBIENT_TEMPERATURE`).
        assertRenders(TemperatureText.format(MeltingRecipe.AMBIENT_TEMPERATURE),
                "gui.forgeweave.temperature", MeltingRecipe.AMBIENT_TEMPERATURE);
    }

    private static void assertRenders(Component actual, String key, int value) {
        TranslatableContents contents = (TranslatableContents) actual.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(List.of(value), List.of(contents.getArgs()));
    }
}
