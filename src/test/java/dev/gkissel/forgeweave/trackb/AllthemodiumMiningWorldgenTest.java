package dev.gkissel.forgeweave.trackb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * D-M8-19 (issue #998): {@code scripts/generate_track_b_worldgen.py}'s fourth output tree, the
 * {@code allthemodium_mining_ores.json} biome modifier, against {@link TrackBOre#ALL} -- the same
 * "the generated file must name exactly what the Java table names" check
 * {@code TrackBOreGameTests#everyTrackBOreGeneratesOnlyInItsHostDimension} runs for the other three
 * trees, done here as a plain file read (not a GameTest) because a {@code neoforge:mod_loaded}
 * gated biome modifier is not even registered in {@code runGameTestServer}, where Allthemodium is
 * never present.
 */
class AllthemodiumMiningWorldgenTest {

    @Test
    void mentionsEveryTrackBOreAndNoStandaloneOne() throws IOException {
        JsonObject modifier = readJson("data/forgeweave/neoforge/biome_modifier/allthemodium_mining_ores.json");

        assertEquals("neoforge:add_features", modifier.get("type").getAsString());
        assertEquals("underground_ores", modifier.get("step").getAsString());
        assertEquals("#allthemodium:mining_features/mining_biomes", modifier.get("biomes").getAsString());

        Set<String> features = new LinkedHashSet<>();
        for (var element : modifier.getAsJsonArray("features")) {
            features.add(element.getAsString());
        }

        Set<String> expected = TrackBOre.ALL.stream()
                .map(ore -> "forgeweave:" + ore.id() + "_ore")
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(expected, features,
                "the mining-dimension biome modifier must name exactly TrackBOre.ALL's ore features, "
                        + "no more (brimspar is a standalone ore, not Track B) and no fewer");
    }

    @Test
    void isModLoadedGated() throws IOException {
        JsonObject modifier = readJson("data/forgeweave/neoforge/biome_modifier/allthemodium_mining_ores.json");
        JsonArray conditions = modifier.getAsJsonArray("neoforge:conditions");
        assertTrue(conditions != null && conditions.size() == 1, "expected exactly one condition, got " + conditions);
        JsonObject condition = conditions.get(0).getAsJsonObject();
        assertEquals("neoforge:mod_loaded", condition.get("type").getAsString());
        assertEquals("allthemodium", condition.get("modid").getAsString());
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        Path path = projectRoot().resolve("src/main/resources").resolve(relativePath);
        assertTrue(Files.exists(path), "missing generated worldgen file (run "
                + "scripts/generate_track_b_worldgen.py): " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }
}
