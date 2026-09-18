package dev.gkissel.forgeweave.compat.mekanism;

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
 * Issue #993's recipe gate: the one nucleosynthesizing row is generated with the configured amounts,
 * and it carries its {@code mod_loaded} condition so a Forgeweave-only datapack drops it and the metal
 * is simply unobtainable.
 *
 * <p>Reads the committed generated file rather than calling the provider, the way
 * {@code ReinforcedPlateRecipeTest} does: what ships is what a server loads, and a provider that
 * silently stopped running would still pass a test that called it directly.
 *
 * <p>The field names are checked against Mekanism's own shipped rows, not against Forgeweave's idea of
 * them -- see {@code ForgeweaveMekanismRecipeProvider}'s javadoc for the row this was read off.
 */
class MekanismNucleosynthesizingRecipeTest {

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static JsonObject recipe() throws IOException {
        Path path = projectRoot().resolve(
                "src/generated/resources/data/forgeweave/recipe/compat/mekanism/atomic_matter_alloy_ingot.json");
        assertTrue(Files.isRegularFile(path), "expected the generated recipe at " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theRowIsANucleosynthesizingRecipeGatedOnMekanism() throws IOException {
        JsonObject json = recipe();
        assertEquals("mekanism:nucleosynthesizing", json.get("type").getAsString());

        JsonObject modLoaded = json.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
        assertEquals("neoforge:mod_loaded", modLoaded.get("type").getAsString());
        assertEquals("mekanism", modLoaded.get("modid").getAsString());
    }

    @Test
    void theRowIsAlsoGatedOnTheMekanismModulesToggle() throws IOException {
        JsonObject toggle = recipe().getAsJsonArray("neoforge:conditions").get(1).getAsJsonObject();
        assertEquals("forgeweave:compat_toggle", toggle.get("type").getAsString());
        assertEquals("mekanismModules", toggle.get("toggle").getAsString(),
                "issue #993's off path: with the toggle off the recipe is absent, not broken");
    }

    @Test
    void theAmountsAreTheConfiguredOnes() throws IOException {
        JsonObject json = recipe();

        JsonObject itemInput = json.getAsJsonObject("item_input");
        assertEquals(ForgeweaveMekanismCompat.nucleosynthesizingAlloyCount(), itemInput.get("count").getAsInt());
        assertEquals(ForgeweaveMekanismCompat.ATOMIC_ALLOY_ITEM, itemInput.get("item").getAsString());

        JsonObject chemicalInput = json.getAsJsonObject("chemical_input");
        assertEquals(ForgeweaveMekanismCompat.nucleosynthesizingAntimatterAmount(),
                chemicalInput.get("amount").getAsInt());
        assertEquals(ForgeweaveMekanismCompat.ANTIMATTER_CHEMICAL, chemicalInput.get("chemical").getAsString());

        assertEquals(ForgeweaveMekanismCompat.nucleosynthesizingDuration(), json.get("duration").getAsInt());
        assertFalse(json.get("per_tick_usage").getAsBoolean(),
                "the antimatter is spent once for the whole craft, not per tick");
    }

    @Test
    void theOutputIsOneAtomicMatterAlloyIngot() throws IOException {
        JsonObject output = recipe().getAsJsonObject("output");
        assertEquals(1, output.get("count").getAsInt());
        assertEquals("forgeweave:atomic_matter_alloy_ingot", output.get("id").getAsString());
    }

    @Test
    void nothingElseInTheTreeMakesTheIngot() throws IOException {
        // The whole of "only makeable in the nucleosynthesizer": no alloy table row, and no crafting,
        // smelting or blasting recipe. Melting the ingot back down and recasting it is the one loop the
        // smeltery closes, which is why the melting and casting rows are allowed.
        Path alloys = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/alloy_recipe");
        assertTrue(Files.isDirectory(alloys), "expected the alloy recipe directory at " + alloys);
        try (var files = Files.list(alloys)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains("atomic_matter_alloy")),
                    "atomic_matter_alloy must have no smeltery alloy recipe");
        }
    }
}
