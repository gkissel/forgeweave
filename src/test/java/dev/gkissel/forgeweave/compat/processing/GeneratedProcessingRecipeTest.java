package dev.gkissel.forgeweave.compat.processing;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Issue #995 (M8-11): walks every recipe JSON {@code scripts/generate_compat_processing.py} emits for
 * Create, Immersive Engineering and EnderIO, plus the Powah {@code heat_source} data map, and pins
 * the things a test that never sees any of the four mods on its classpath actually can (per the
 * issue's own "Tests" section, JC-B): every file parses, every row carries both a
 * {@code neoforge:mod_loaded} and a {@code forgeweave:compat_toggle} condition, every Forgeweave
 * output item it names is one {@code ForgeweaveLanguageProvider} actually registered a lang line for,
 * and every ingredient tag naming a Forgeweave-owned material id is backed by a real generated
 * {@code c:} tag file. Ingredient tags naming an id Forgeweave does not own (copper, gold, osmium,
 * iridium -- vanilla or another mod's own metal) are left unverified here by design: no shipped file
 * in this tree ever contributes to those tags, so there is nothing of this mod's own to check.
 *
 * <p>What this cannot cover, same as the issue's own text: whether Create's mixer schema accepts the
 * row, whether the arc furnace actually smelts it, or whether a thermo generator actually burns
 * molten magma. Those are release-checklist lines on #975.
 */
class GeneratedProcessingRecipeTest {

    private static final Path RECIPE_DIR =
            projectRoot().resolve("src/main/resources/data/forgeweave/recipe");
    private static final Path POWAH_DATA_MAP =
            projectRoot().resolve("src/main/resources/data/powah/data_maps/fluid/heat_source.json");
    private static final Path LANG =
            projectRoot().resolve("src/generated/resources/assets/forgeweave/lang/en_us.json");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    /** Every material id Forgeweave itself populates a {@code c:} family tag for. */
    private static final Set<String> OWNED_MATERIAL_IDS = Stream.concat(
            Stream.concat(TrackBOre.ALL.stream().map(TrackBOre::id), TrackBAlloy.ALL.stream().map(TrackBAlloy::id)),
            Stream.of("cobalt", "ardite", "manyullyn", "rose_gold", "steel", "knightslime",
                    "pig_iron", "amethyst_bronze", "queens_slime", "hepatizon"))
            .collect(Collectors.toUnmodifiableSet());

    private static List<Path> jsonFilesUnder(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject parse(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void assertHasBothConditions(Path file, JsonArray conditions, String expectedModId) {
        boolean hasModLoaded = false;
        boolean hasCompatToggle = false;
        for (JsonElement element : conditions) {
            JsonObject condition = element.getAsJsonObject();
            String type = condition.get("type").getAsString();
            if (type.equals("neoforge:mod_loaded")) {
                hasModLoaded = true;
                assertTrue(condition.get("modid").getAsString().equals(expectedModId),
                        file + ": mod_loaded condition names " + condition.get("modid") + ", expected " + expectedModId);
            }
            if (type.equals("forgeweave:compat_toggle")) {
                hasCompatToggle = true;
            }
        }
        assertTrue(hasModLoaded, file + ": missing a neoforge:mod_loaded condition");
        assertTrue(hasCompatToggle, file + ": missing a forgeweave:compat_toggle condition");
    }

    private static void assertLangKeyExists(Path file, JsonObject lang, String itemId) {
        assertTrue(itemId.startsWith("forgeweave:"), file + ": output item " + itemId + " should be Forgeweave's own");
        String key = "item." + itemId.replace(':', '.');
        assertTrue(lang.has(key), file + ": no lang entry " + key + " for generated output " + itemId);
    }

    private static void assertIngredientTagBacked(Path file, String tag) {
        // tag shape: "c:<family>/<materialId>"
        String[] parts = tag.substring("c:".length()).split("/", 2);
        if (parts.length != 2) {
            return;
        }
        String family = parts[0];
        String materialId = parts[1];
        if (!OWNED_MATERIAL_IDS.contains(materialId)) {
            // Not Forgeweave's own material (vanilla or another mod's metal) -- nothing of ours to check.
            return;
        }
        Path tagFile = projectRoot().resolve("src/generated/resources/data/c/tags/item/" + family + "/" + materialId + ".json");
        assertTrue(Files.isRegularFile(tagFile),
                file + ": references " + tag + " for an owned material, but " + tagFile + " does not exist");
    }

    private static void collectItemIds(JsonElement element, List<String> out) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("id")) {
                out.add(obj.get("id").getAsString());
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectItemIds(child, out);
            }
        }
    }

    private static void collectIngredientTags(JsonElement element, List<String> out) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("tag")) {
                out.add(obj.get("tag").getAsString());
            }
            for (String key : obj.keySet()) {
                collectIngredientTags(obj.get(key), out);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectIngredientTags(child, out);
            }
        }
    }

    @Test
    void everyGeneratedRecipeParsesCarriesBothConditionsAndNamesRealItemsAndTags() throws IOException {
        record ModFolder(String folder, String modId) {}
        List<ModFolder> mods = List.of(
                new ModFolder("create", "create"),
                new ModFolder("immersive_engineering", "immersiveengineering"),
                new ModFolder("enderio", "enderio"));

        JsonObject lang = parse(LANG);
        int checked = 0;
        List<String> failures = new ArrayList<>();
        for (ModFolder mod : mods) {
            for (Path file : jsonFilesUnder(RECIPE_DIR.resolve(mod.folder()))) {
                JsonObject root = parse(file);
                JsonArray conditions = root.getAsJsonArray("neoforge:conditions");
                assertTrue(conditions != null && !conditions.isEmpty(), file + ": missing neoforge:conditions");
                assertHasBothConditions(file, conditions, mod.modId());

                List<String> itemIds = new ArrayList<>();
                collectItemIds(root, itemIds);
                for (String itemId : itemIds) {
                    if (itemId.startsWith("forgeweave:")) {
                        assertLangKeyExists(file, lang, itemId);
                    }
                }

                List<String> tags = new ArrayList<>();
                collectIngredientTags(root, tags);
                for (String tag : tags) {
                    if (tag.startsWith("c:")) {
                        assertIngredientTagBacked(file, tag);
                    }
                }
                checked++;
            }
        }
        assertTrue(checked == 135, "expected 135 generated recipe files (4 alloys x 3 mods + 11 ores x 3 "
                + "mods + 45 plate materials x 2 mods), found " + checked);
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    @Test
    void thePowahDataMapParsesAndEveryEntryCarriesBothConditions() throws IOException {
        JsonObject root = parse(POWAH_DATA_MAP);
        JsonObject values = root.getAsJsonObject("values");
        assertTrue(values.size() == 4, "expected 4 Powah heat_source entries, found " + values.size());
        for (String fluidId : values.keySet()) {
            assertTrue(fluidId.startsWith("forgeweave:"), fluidId + ": every entry should name a Forgeweave fluid");
            JsonObject entry = values.getAsJsonObject(fluidId);
            assertTrue(entry.has("temperature"), fluidId + ": missing temperature");
            JsonArray conditions = entry.getAsJsonArray("neoforge:conditions");
            assertTrue(conditions != null && !conditions.isEmpty(), fluidId + ": missing neoforge:conditions");
            assertHasBothConditions(POWAH_DATA_MAP, conditions, "powah");
        }
    }
}
