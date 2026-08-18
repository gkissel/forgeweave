package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Parity audit T69 (issue #500): upstream's reinforced-plate recipe ({@code
 * recipes/tools/materials/reinforcement.json}) fills its center slot with {@code #REINFORCEMENT_CENTER},
 * a constant that resolves to a gold ingot when the smeltery pulse is unloaded but a gold cast
 * ({@code ore:cast}, {@code recipes/_constants.json}) once it is. Forgeweave's smeltery always ships
 * (no optional-pulse system), so the center slot must always be the cast form -- see
 * {@code ForgeweaveRecipeProvider#buildModifierRecipes} and {@code ForgeweaveItemTagsProvider#CASTS_GOLD}.
 *
 * <p>Plain filesystem scan of the generated recipe JSON, same approach as {@link BrokenToolModelTest}.
 */
class ReinforcedPlateRecipeTest {

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    @Test
    void centerSlotIsTheGoldCastsTagNotAGoldIngot() throws IOException {
        Path recipe = projectRoot()
                .resolve("src/generated/resources/data/forgeweave/recipe/reinforced_plate.json");
        assertTrue(Files.isRegularFile(recipe), "expected recipe JSON at " + recipe);
        JsonObject json = JsonParser.parseString(Files.readString(recipe, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject center = json.getAsJsonObject("key").getAsJsonObject("B");

        assertFalse(center.has("item"), "center slot still keys a plain item, not the gold-casts tag: " + center);
        assertTrue(center.has("tag"), "center slot should key the gold-casts tag: " + center);
        assertEquals("forgeweave:casts/gold", center.get("tag").getAsString());
    }
}
