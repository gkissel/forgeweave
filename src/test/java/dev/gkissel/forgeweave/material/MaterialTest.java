package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

class MaterialTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }

    private static JsonElement shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = MaterialTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped material JSON: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    void codecRoundTripsAMaterial() {
        Material material = new Material(
                new Material.Head(200, 5.09f, 2.5f),
                new Material.Handle(1.1f, 50),
                65,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                ResourceLocation.fromNamespaceAndPath("forgeweave", "fractured"),
                List.of(new Material.CraftingItem(Ingredient.of(Items.BONE), 2)),
                Ingredient.of(Items.BONE),
                TextColor.parseColor("#EDE6BF").getOrThrow());

        JsonElement encoded = Material.CODEC.encodeStart(ops, material).getOrThrow();
        Material decoded = Material.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(material.head(), decoded.head());
        assertEquals(material.handle(), decoded.handle());
        assertEquals(material.extraDurability(), decoded.extraDurability());
        assertEquals(material.incorrectForTool(), decoded.incorrectForTool());
        assertEquals(material.trait(), decoded.trait());
        assertEquals(material.color(), decoded.color());
        assertEquals(material.repairItem().getItems()[0].getItem(), decoded.repairItem().getItems()[0].getItem());
        assertEquals(1, decoded.craftingItems().size());
        assertEquals(2, decoded.craftingItems().get(0).value());
        assertEquals(material.craftingItems().get(0).ingredient().getItems()[0].getItem(),
                decoded.craftingItems().get(0).ingredient().getItems()[0].getItem());
    }

    @ParameterizedTest
    @ValueSource(strings = { "wood", "stone", "flint", "bone" })
    void shippedMaterialsParse(String name) {
        Material.CODEC.parse(ops, shipped(name)).getOrThrow();
    }

    @Test
    void woodMatchesItsShippedStats() {
        Material wood = Material.CODEC.parse(ops, shipped("wood")).getOrThrow();

        assertEquals(new Material.Head(35, 2.0f, 2.0f), wood.head());
        assertEquals(new Material.Handle(1.0f, 25), wood.handle());
        assertEquals(15, wood.extraDurability());
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological"), wood.trait());
        assertEquals(0x8E661B, wood.color().getValue());
    }

    // Verified against upstream 1.12's slimeknights.tconstruct.tools.TinkerMaterials#setupMaterials
    // (VALUE_Ingot = 144, VALUE_Shard = VALUE_Ingot / 2 = 72; wood.addItem("stickWood", 1,
    // VALUE_Shard), .addItem("plankWood", 1, VALUE_Ingot), .addItem("logWood", 1, VALUE_Ingot * 4)),
    // normalized to Forgeweave's shard-unit scale (see PartBuilderRecipes's class javadoc):
    // 1 shard-unit = 1 VALUE_Shard, so stick = 1, plank = 2, log = 8.
    @Test
    void woodCraftingItemsMatchUpstreamValueTable() {
        Material wood = Material.CODEC.parse(ops, shipped("wood")).getOrThrow();

        assertEquals(3, wood.craftingItems().size());
        assertEquals(1, wood.craftingItems().get(0).value(), "stick should be worth 1 shard-unit");
        assertEquals(2, wood.craftingItems().get(1).value(), "planks should be worth 2 shard-units (1 ingot)");
        assertEquals(8, wood.craftingItems().get(2).value(), "logs should be worth 8 shard-units (4 ingots)");
    }

    @ParameterizedTest
    @ValueSource(strings = { "stone", "flint", "bone" })
    void ingotEquivalentMaterialsCostTwoShardUnits(String name) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();

        assertTrue(!material.craftingItems().isEmpty(), name + " must have crafting items");
        for (Material.CraftingItem item : material.craftingItems()) {
            assertEquals(2, item.value(), name + "'s crafting items should each be worth 1 ingot (2 shard-units)");
        }
    }

    @Test
    void rejectsNonPositiveHeadDurability() {
        JsonElement bad = parse("""
                {
                  "head": {"durability": -1, "mining_speed": 2.0, "attack_damage": 2.0},
                  "handle": {"durability_modifier": 1.0, "durability": 25},
                  "extra_durability": 15,
                  "incorrect_for_tool": "minecraft:incorrect_for_wooden_tool",
                  "trait": "forgeweave:ecological",
                  "crafting_items": [{"ingredient": {"tag": "minecraft:planks"}, "value": 2}],
                  "repair_item": {"tag": "minecraft:planks"},
                  "color": "#8E661B"
                }""");

        DataResult<Material> result = Material.CODEC.parse(ops, bad);

        assertTrue(result.isError(), "negative head durability must not parse");
    }

    @Test
    void rejectsMissingField() {
        JsonElement bad = parse("""
                {
                  "head": {"durability": 35, "mining_speed": 2.0, "attack_damage": 2.0},
                  "handle": {"durability_modifier": 1.0, "durability": 25},
                  "extra_durability": 15,
                  "incorrect_for_tool": "minecraft:incorrect_for_wooden_tool",
                  "crafting_items": [{"ingredient": {"tag": "minecraft:planks"}, "value": 2}],
                  "repair_item": {"tag": "minecraft:planks"},
                  "color": "#8E661B"
                }""");

        DataResult<Material> result = Material.CODEC.parse(ops, bad);

        assertTrue(result.isError(), "material without a trait must not parse");
    }

    @Test
    void rejectsMissingCraftingItems() {
        JsonElement bad = parse("""
                {
                  "head": {"durability": 35, "mining_speed": 2.0, "attack_damage": 2.0},
                  "handle": {"durability_modifier": 1.0, "durability": 25},
                  "extra_durability": 15,
                  "incorrect_for_tool": "minecraft:incorrect_for_wooden_tool",
                  "trait": "forgeweave:ecological",
                  "repair_item": {"tag": "minecraft:planks"},
                  "color": "#8E661B"
                }""");

        DataResult<Material> result = Material.CODEC.parse(ops, bad);

        assertTrue(result.isError(), "material without crafting_items must not parse");
    }

    @Test
    void rejectsNonPositiveCraftingItemValue() {
        JsonElement bad = parse("""
                {
                  "head": {"durability": 35, "mining_speed": 2.0, "attack_damage": 2.0},
                  "handle": {"durability_modifier": 1.0, "durability": 25},
                  "extra_durability": 15,
                  "incorrect_for_tool": "minecraft:incorrect_for_wooden_tool",
                  "trait": "forgeweave:ecological",
                  "crafting_items": [{"ingredient": {"tag": "minecraft:planks"}, "value": 0}],
                  "repair_item": {"tag": "minecraft:planks"},
                  "color": "#8E661B"
                }""");

        DataResult<Material> result = Material.CODEC.parse(ops, bad);

        assertTrue(result.isError(), "a crafting item with a zero/negative value must not parse");
    }

    @Test
    void rejectsCraftingItemMissingIngredient() {
        JsonElement bad = parse("""
                {
                  "head": {"durability": 35, "mining_speed": 2.0, "attack_damage": 2.0},
                  "handle": {"durability_modifier": 1.0, "durability": 25},
                  "extra_durability": 15,
                  "incorrect_for_tool": "minecraft:incorrect_for_wooden_tool",
                  "trait": "forgeweave:ecological",
                  "crafting_items": [{"value": 2}],
                  "repair_item": {"tag": "minecraft:planks"},
                  "color": "#8E661B"
                }""");

        DataResult<Material> result = Material.CODEC.parse(ops, bad);

        assertTrue(result.isError(), "a crafting item without an ingredient must not parse");
    }
}
