package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * D-M8-19 (issue #998): {@code scripts/generate_elementarium_materials.py}'s interpolation rule,
 * pinned at both endpoints of its curated roster (vanadium, atomic number 23; tantalum, atomic
 * number 73) and at one interior point (molybdenum, atomic number 42, fraction 0.38) -- so a retune
 * of the script shows up as a test failure here rather than silent drift, per the issue's own ask.
 *
 * <p>Reads the shipped material JSON directly rather than re-running the Python script: the
 * generated files are committed input data (the same shape {@code TrackBOre}'s worldgen JSON is),
 * so this is the same kind of golden-file check {@code AllthemodiumMiningWorldgenTest} runs for the
 * mining-dimension tree.
 */
class ElementariumMaterialTest {

    @Test
    void lowEndpointMatchesTheIronAnchorExactly() throws IOException {
        // vanadium, atomic number 23 -- the roster's own floor, fraction 0.0: every stat must equal
        // iron.json's own value verbatim, and the tier is iron (fraction < 0.5).
        JsonObject vanadium = readMaterial("elementarium_vanadium");
        JsonObject head = vanadium.getAsJsonObject("head");
        assertEquals(204, head.get("durability").getAsInt());
        assertEquals(6.0, head.get("mining_speed").getAsDouble());
        assertEquals(4.0, head.get("attack_damage").getAsDouble());
        JsonObject handle = vanadium.getAsJsonObject("handle");
        assertEquals(0.85, handle.get("durability_modifier").getAsDouble());
        assertEquals(60, handle.get("durability").getAsInt());
        assertEquals(50, vanadium.get("extra_durability").getAsInt());
        assertEquals(14, vanadium.get("enchantability").getAsInt());
        assertEquals("minecraft:incorrect_for_iron_tool", vanadium.get("incorrect_for_tool").getAsString());
    }

    @Test
    void highEndpointMatchesTheTungstenAnchorExactly() throws IOException {
        // tantalum, atomic number 73 -- the roster's own ceiling, fraction 1.0: every stat must equal
        // tungsten.json's own value verbatim, and the tier is diamond (fraction >= 0.5).
        JsonObject tantalum = readMaterial("elementarium_tantalum");
        JsonObject head = tantalum.getAsJsonObject("head");
        assertEquals(620, head.get("durability").getAsInt());
        assertEquals(5.4, head.get("mining_speed").getAsDouble());
        assertEquals(6.0, head.get("attack_damage").getAsDouble());
        JsonObject handle = tantalum.getAsJsonObject("handle");
        assertEquals(1.15, handle.get("durability_modifier").getAsDouble());
        assertEquals(30, handle.get("durability").getAsInt());
        assertEquals(30, tantalum.get("extra_durability").getAsInt());
        assertEquals(10, tantalum.get("enchantability").getAsInt());
        assertEquals("minecraft:incorrect_for_diamond_tool", tantalum.get("incorrect_for_tool").getAsString());
    }

    @Test
    void interiorPointInterpolatesLinearly() throws IOException {
        // molybdenum, atomic number 42 -- fraction (42-23)/(73-23) = 0.38, hand-derived from the same
        // lerp the script's docstring describes: iron + (tungsten - iron) * 0.38.
        JsonObject molybdenum = readMaterial("elementarium_molybdenum");
        JsonObject head = molybdenum.getAsJsonObject("head");
        assertEquals(362, head.get("durability").getAsInt(), "204 + (620-204)*0.38 = 362.08, rounded");
        assertEquals(5.77, head.get("mining_speed").getAsDouble(), 0.001);
        assertEquals(4.76, head.get("attack_damage").getAsDouble(), 0.001);
        JsonObject handle = molybdenum.getAsJsonObject("handle");
        assertEquals(0.96, handle.get("durability_modifier").getAsDouble(), 0.001);
        assertEquals(49, handle.get("durability").getAsInt());
        assertEquals(42, molybdenum.get("extra_durability").getAsInt());
        assertEquals(12, molybdenum.get("enchantability").getAsInt(), "14 + (10-14)*0.38 = 12.48, rounded");
        // Below the 0.5 tier-split midpoint: iron-tier, same as the low endpoint.
        assertEquals("minecraft:incorrect_for_iron_tool", molybdenum.get("incorrect_for_tool").getAsString());
    }

    @Test
    void everyGeneratedPresetCarriesBothExistenceConditions() throws IOException {
        // The compat_toggle condition is ForgeweaveConfigCondition, shared with #995's four
        // processing-mod toggles -- a dedicated ElementariumEnabledCondition was this issue's first
        // attempt and never actually gated anything (a datapack registry loads before
        // ForgeweaveConfig.loaded() is ever true), so this pins the fix instead of the mistake.
        for (String id : new String[] {"vanadium", "chromium", "molybdenum", "palladium", "hafnium", "tantalum"}) {
            JsonObject material = readMaterial("elementarium_" + id);
            var conditions = material.getAsJsonArray("neoforge:conditions");
            assertTrue(conditions != null && conditions.size() == 2,
                    "elementarium_" + id + " must carry exactly two conditions, got " + conditions);
            assertEquals("neoforge:mod_loaded", conditions.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("elementarium", conditions.get(0).getAsJsonObject().get("modid").getAsString());
            assertEquals("forgeweave:compat_toggle", conditions.get(1).getAsJsonObject().get("type").getAsString());
            assertEquals("elementariumMaterials", conditions.get(1).getAsJsonObject().get("toggle").getAsString());
        }
    }

    private static JsonObject readMaterial(String id) throws IOException {
        Path path = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material/" + id + ".json");
        assertTrue(Files.exists(path), "missing shipped material JSON (run "
                + "scripts/generate_elementarium_materials.py): " + path);
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
