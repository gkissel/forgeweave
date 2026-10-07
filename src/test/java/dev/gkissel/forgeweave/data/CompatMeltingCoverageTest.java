package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class CompatMeltingCoverageTest {
    private static final Path DATA = LocalizationAuditTest.projectRoot()
            .resolve("src/main/resources/data/forgeweave/forgeweave");

    @Test
    void everyMekanismOreChainMeltsWithoutMultiplyingProcessedMetal() throws IOException {
        for (String metal : List.of("iron", "gold", "copper", "osmium", "tin", "lead", "uranium")) {
            for (String family : List.of("ores", "raw_materials", "storage_blocks/raw_",
                    "dusts", "dirty_dusts", "clumps", "shards", "crystals")) {
                String tag = "c:" + (family.endsWith("_") ? family + metal : family + "/" + metal);
                JsonObject recipe = findMeltingTag(tag);
                boolean ore = family.equals("ores") || family.equals("raw_materials") || family.endsWith("_");
                assertEquals(ore, recipe.has("ore") && recipe.get("ore").getAsBoolean(), tag);
                assertEquals(family.endsWith("_") ? 1296 : 144, recipe.get("amount").getAsInt(), tag);
                assertEquals("forgeweave:molten_" + metal, recipe.get("fluid").getAsString(), tag);
            }
        }
    }

    @Test
    void refinedMekanismMetalsStayAtOneToOneYield() throws IOException {
        for (String metal : List.of("bronze", "steel", "refined_obsidian", "refined_glowstone")) {
            for (String family : List.of("ingots", "nuggets", "storage_blocks")) {
                JsonObject recipe = findMeltingTag("c:" + family + "/" + metal);
                assertFalse(recipe.has("ore") && recipe.get("ore").getAsBoolean(), metal);
            }
        }
    }

    @Test
    void craftingAlloysKeepMekanismsCraftingProgression() throws IOException {
        for (var entry : Map.of("infused_alloy", "alloy_infused", "reinforced_alloy", "alloy_reinforced",
                "atomic_alloy", "alloy_atomic").entrySet()) {
            JsonObject material = read("material/" + entry.getKey() + ".json");
            assertFalse(material.has("cast_only") && material.get("cast_only").getAsBoolean());
            assertEquals("mekanism:" + entry.getValue(), material.getAsJsonObject("repair_item").get("item").getAsString());
            assertEquals("mekanism:" + entry.getValue(), material.getAsJsonArray("neoforge:conditions")
                    .get(0).getAsJsonObject().get("item").getAsString());
            assertTrue(material.getAsJsonObject("traits").has("armor"));
        }
    }

    @Test
    void neoVitaeUsesRealItemsAndOnlyMultipliesRawDemonite() throws IOException {
        for (String form : List.of("ore", "ingot", "block", "raw", "raw_block", "dust", "fragment", "gravel")) {
            JsonObject recipe = read("melting_recipe/hellforged_" + form + ".json");
            if (!form.equals("ore")) {
                assertTrue(recipe.getAsJsonObject("input").get("item").getAsString().startsWith("neovitae:"));
            }
            assertEquals(form.equals("ore") || form.startsWith("raw"), recipe.has("ore") && recipe.get("ore").getAsBoolean());
            assertEquals(1600, recipe.get("temperature").getAsInt());
        }
        JsonObject trait = read("trait_definition/vitae_siphon.json");
        assertEquals("forgeweave:lifesteal", trait.get("behavior").getAsString());
        assertEquals(0.1, trait.get("fraction").getAsDouble());
        assertEquals(2.0, trait.get("cap").getAsDouble());
    }

    private static JsonObject findMeltingTag(String tag) throws IOException {
        try (var files = Files.list(DATA.resolve("melting_recipe"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject recipe = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (recipe.get("input").isJsonObject()) {
                    var input = recipe.getAsJsonObject("input");
                    if (input.has("tag") && input.get("tag").getAsString().equals(tag)) {
                        return recipe;
                    }
                }
            }
        }
        throw new AssertionError("Missing melting recipe for " + tag);
    }

    private static JsonObject read(String relative) throws IOException {
        return JsonParser.parseString(Files.readString(DATA.resolve(relative))).getAsJsonObject();
    }
}
