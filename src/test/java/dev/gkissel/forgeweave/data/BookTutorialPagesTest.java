package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Release-test findings on the guide book's worked examples (PR #1140): "Your First Pickaxe" said
 * one log where following it takes two, quoted the wood head's stats as the pickaxe's, and priced a
 * stone head at four cobblestone. "Your First Iron Pickaxe" said to work the faucet twice, when one
 * right-click pours until the cast is full. Each number here is read off the shipped data or code.
 */
class BookTutorialPagesTest {

    private static RegistryOps<JsonElement> ops;
    private static JsonObject lang;

    @BeforeAll
    static void bootstrap() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        lang = read(root().resolve("src/generated/resources/assets/forgeweave/lang/en_us.json"));
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject materialJson(String name) throws IOException {
        return read(root().resolve("src/main/resources/data/forgeweave/forgeweave/material/" + name + ".json"));
    }

    private static Material material(String name) throws IOException {
        return Material.CODEC.parse(ops, materialJson(name)).getOrThrow();
    }

    /** What one {@code ingredient} of a material's crafting items is worth, in units. */
    private static int value(String material, String ingredientKey, String ingredient) throws IOException {
        for (JsonElement item : materialJson(material).getAsJsonArray("crafting_items")) {
            JsonObject entry = item.getAsJsonObject();
            JsonElement key = entry.getAsJsonObject("ingredient").get(ingredientKey);
            if (key != null && key.getAsString().equals(ingredient)) {
                return entry.get("value").getAsInt();
            }
        }
        throw new AssertionError(material + " has no crafting item " + ingredient);
    }

    private static String text(String key) {
        return lang.get("book.forgeweave." + key + ".text").getAsString();
    }

    private static String stats(ToolStats.Stats stats) {
        return String.format(Locale.ROOT, "%d durability, %.1f mining speed and %.1f attack",
                stats.durability(), stats.miningSpeed(), stats.attackDamage());
    }

    @Test
    void theFirstPickaxePageQuotesTheFinishedPickaxesStats() throws IOException {
        Material wood = material("wood");
        Material stone = material("stone");
        String page = text("intro.first_pickaxe");

        assertTrue(page.contains(stats(ToolStats.compute(wood, wood, wood))), "wooden pickaxe stats in: " + page);
        assertTrue(page.contains(stats(ToolStats.compute(stone, wood, wood))), "stone-headed pickaxe stats in: " + page);
    }

    @Test
    void theFirstPickaxePagePricesItsPartsRight() throws IOException {
        int log = value("wood", "tag", "minecraft:logs");
        int cobblestone = value("stone", "item", "minecraft:cobblestone");
        int parts = PartBuilderRecipes.HEAD_COST + 2 * PartBuilderRecipes.SMALL_PART_COST;
        String page = text("intro.first_pickaxe");

        // The three parts are exactly one log, so the patterns' planks need a log of their own.
        assertEquals(log, parts, "head, handle and binding against one log");
        assertTrue(page.contains("it takes two logs"), page);
        assertEquals(2, PartBuilderRecipes.HEAD_COST / cobblestone, "cobblestone in a stone head");
        assertTrue(page.contains("from two cobblestone"), page);
        assertEquals(4, (log - PartBuilderRecipes.HEAD_COST) / PartBuilderRecipes.SHARD_VALUE, "shards of change");
        assertTrue(page.contains("four wood shards"), page);
    }

    @Test
    void theCastingPagesSayOneRightClickFillsTheTarget() {
        // A pickaxe head is two faucet transactions, and the faucet keeps going until the cast is full.
        assertEquals(2, PartBuilderRecipes.HEAD_COST / FaucetBlockEntity.TRANSACTION_AMOUNT);
        assertTrue(text("casting.first_iron_part").contains("right-click the faucet once"));
        assertFalse(text("casting.first_iron_part").contains("faucet twice"));
        assertFalse(text("casting.intro").contains("Right-click again for the next"));
    }
}
