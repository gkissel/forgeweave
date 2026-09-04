package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Issue #954: every Track A (existence-gated, other-mod) melting recipe -- identified the same way
 * {@code scripts/generate_compat_smeltery.py} itself does, by a {@code neoforge:conditions} row --
 * must carry an explicit {@code temperature} keyed to its material's tool tier and the fuel ladder
 * (lava 1300, blazing blood 1500, molten magma 1700, brimspar 1900, pyrealloy 2100), rather than the
 * per-form value {@link dev.gkissel.forgeweave.recipe.MeltingRecipe#calcTemperature} would otherwise
 * derive from the output fluid's own temperature and the item amount -- which landed nearly every
 * modded ingot inside lava's reach regardless of tier, since only a full block ever reached the
 * fluid's own temperature and an ingot only reached about half of it.
 *
 * <p>Mirrors {@code scripts/_compat_smeltery_data.py}'s {@code TIER_MELT_TEMPERATURE} /
 * {@code MATERIAL_MELT_TEMPERATURE_OVERRIDES} tables -- the single source of truth the generator
 * itself reads -- so an edit to one side that forgets the other fails here instead of at review.
 */
class TrackAMeltingTemperatureTest {

    /** Mirrors {@code scripts/_compat_smeltery_data.py}'s {@code TIER_MELT_TEMPERATURE}. */
    private static final Map<String, Integer> TIER_MELT_TEMPERATURE = Map.of(
            "minecraft:incorrect_for_stone_tool", 1200,
            "minecraft:incorrect_for_iron_tool", 1200,
            "minecraft:incorrect_for_diamond_tool", 1400,
            "minecraft:incorrect_for_netherite_tool", 1600);

    /** Mirrors {@code scripts/_compat_smeltery_data.py}'s {@code MATERIAL_MELT_TEMPERATURE_OVERRIDES}. */
    private static final Map<String, Integer> MATERIAL_MELT_TEMPERATURE_OVERRIDES = Map.of(
            "draconium_awakened", 1800,
            "emberweld", 1800,
            "starweld", 2000,
            "voidweld", 2000);

    /**
     * The generator's own {@code form_suffix}: a melting recipe file is named {@code
     * <material_id><suffix>.json}, so stripping a known suffix recovers the material id that names
     * the material JSON to look its tier up in. ponytail: fixed-suffix stripping rather than a real
     * grammar, matches the exact four suffixes the generator ever writes.
     */
    private static final List<String> FORM_SUFFIXES = List.of("_ingot", "_nugget", "_block", "_raw");

    @Test
    void everyTrackAMeltingRecipeCarriesItsTableTemperature() throws IOException {
        Path root = LocalizationAuditTest.projectRoot();
        Path meltingDir = root.resolve("src/main/resources/data/forgeweave/forgeweave/melting_recipe");
        Path materialDir = root.resolve("src/main/resources/data/forgeweave/forgeweave/material");

        List<String> failures = new ArrayList<>();
        int trackACount = 0;
        try (Stream<Path> files = Files.list(meltingDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject recipe = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!recipe.has("neoforge:conditions")) {
                    continue; // Not existence-gated -- Track B and vanilla recipes are untouched (#954).
                }
                trackACount++;

                String materialId = materialIdFor(file.getFileName().toString());
                int expected = expectedTemperature(materialId, materialDir);

                if (!recipe.has("temperature")) {
                    failures.add(file.getFileName() + ": missing an explicit temperature (expected " + expected + ")");
                } else if (recipe.get("temperature").getAsInt() != expected) {
                    failures.add(file.getFileName() + ": temperature " + recipe.get("temperature").getAsInt()
                            + ", expected " + expected + " for material " + materialId);
                }
            }
        }

        assertTrue(trackACount > 0, "expected to find at least one Track A (neoforge:conditions) melting recipe");
        assertTrue(failures.isEmpty(), "Track A melting recipe(s) off the #954 temperature table:\n"
                + String.join("\n", failures));
    }

    private static String materialIdFor(String fileName) {
        String stem = fileName.substring(0, fileName.length() - ".json".length());
        for (String suffix : FORM_SUFFIXES) {
            if (stem.endsWith(suffix)) {
                return stem.substring(0, stem.length() - suffix.length());
            }
        }
        return stem;
    }

    private static int expectedTemperature(String materialId, Path materialDir) throws IOException {
        Integer override = MATERIAL_MELT_TEMPERATURE_OVERRIDES.get(materialId);
        if (override != null) {
            return override;
        }
        Path materialJson = materialDir.resolve(materialId + ".json");
        assertTrue(Files.isRegularFile(materialJson), "expected a material JSON for " + materialId + " at " + materialJson);
        JsonObject material = JsonParser.parseString(Files.readString(materialJson, StandardCharsets.UTF_8)).getAsJsonObject();
        String tier = material.get("incorrect_for_tool").getAsString();
        Integer temperature = TIER_MELT_TEMPERATURE.get(tier);
        assertTrue(temperature != null, "no #954 tier-temperature table entry for " + tier + " (material " + materialId + ")");
        return temperature;
    }
}
