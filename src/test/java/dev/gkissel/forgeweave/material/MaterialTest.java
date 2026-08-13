package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import dev.gkissel.forgeweave.item.PartItem;

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
                new Material.Traits(List.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "fractured")),
                        List.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "splintering"))),
                List.of(new Material.CraftingItem(Ingredient.of(Items.BONE), 2)),
                Ingredient.of(Items.BONE),
                TextColor.parseColor("#EDE6BF").getOrThrow());

        JsonElement encoded = Material.CODEC.encodeStart(ops, material).getOrThrow();
        Material decoded = Material.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(material.head(), decoded.head());
        assertEquals(material.handle(), decoded.handle());
        assertEquals(material.extraDurability(), decoded.extraDurability());
        assertEquals(material.incorrectForTool(), decoded.incorrectForTool());
        assertEquals(material.traits(), decoded.traits());
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
        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological")),
                wood.traits().general());
        assertEquals(0x8E661B, wood.color().getValue());
    }

    /**
     * Wood and stone carry their one trait as a general trait, i.e. every part they make grants it
     * -- which is what the pre-#94 single {@code trait} field meant.
     */
    @ParameterizedTest
    @CsvSource({ "wood,ecological", "stone,cheap" })
    void shippedMaterialsGrantTheirTraitThroughEveryPart(String name, String trait) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forgeweave", trait);

        assertEquals(List.of(id), material.traits().all());
        for (PartItem.Kind kind : PartItem.Kind.values()) {
            assertEquals(List.of(id), material.traits().forPart(kind), name + " through a " + kind + " part");
        }
    }

    /**
     * Issue #231's retrofits: flint and bone gained the head-scoped trait upstream gives them
     * ({@code crude2} / {@code splintering}), which replaces the general list on head parts only.
     */
    @ParameterizedTest
    @CsvSource({ "flint,crude,crude2", "bone,fractured,splintering" })
    void retrofittedMaterialsScopeTheirHeadTrait(String name, String general, String head) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();
        ResourceLocation generalId = ResourceLocation.fromNamespaceAndPath("forgeweave", general);
        ResourceLocation headId = ResourceLocation.fromNamespaceAndPath("forgeweave", head);

        assertEquals(List.of(generalId, headId), material.traits().all());
        assertEquals(List.of(headId), material.traits().forPart(PartItem.Kind.HEAD));
        assertEquals(List.of(generalId), material.traits().forPart(PartItem.Kind.HANDLE));
        assertEquals(List.of(generalId), material.traits().forPart(PartItem.Kind.EXTRA));
    }

    /**
     * Issue #282: upstream restates the general trait under HEAD alongside the head-specific one,
     * so the head part doesn't lose the general trait entirely (head lists replace, not merge).
     * pig_iron already does this correctly; prismarine and netherrack must restate
     * aquadynamic/hellish under head too.
     */
    @ParameterizedTest
    @CsvSource({ "pig_iron,tasty,baconlicious", "prismarine,aquadynamic,jagged", "netherrack,hellish,aridiculous" })
    void headScopedMaterialsRestateTheGeneralTraitUnderHead(String name, String general, String headOnly) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();
        ResourceLocation generalId = ResourceLocation.fromNamespaceAndPath("forgeweave", general);
        ResourceLocation headOnlyId = ResourceLocation.fromNamespaceAndPath("forgeweave", headOnly);

        assertEquals(List.of(generalId), material.traits().general());
        assertEquals(List.of(headOnlyId, generalId), material.traits().forPart(PartItem.Kind.HEAD));
        assertEquals(List.of(generalId), material.traits().forPart(PartItem.Kind.HANDLE));
        assertEquals(List.of(generalId), material.traits().forPart(PartItem.Kind.EXTRA));
    }

    /** Pre-#94 packs keep loading: one {@code trait} id means one trait on every part (ADR-0002). */
    @Test
    void acceptsTheLegacySingleTraitField() {
        Material material = Material.CODEC.parse(ops, parse(withTraits("\"trait\": \"forgeweave:ecological\""))).getOrThrow();

        ResourceLocation ecological = ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological");
        assertEquals(List.of(ecological), material.traits().general());
        assertEquals(List.of(), material.traits().head());
        assertEquals(List.of(ecological), material.traits().forPart(PartItem.Kind.HEAD));
    }

    /**
     * The upstream 1.12 scoping rule ({@code Material#getAllTraitsForStats}): a part-scoped list
     * replaces the general one for that part rather than adding to it -- iron's {@code magnetic2} on
     * the head, {@code magnetic} everywhere else.
     */
    @Test
    void headScopedTraitsReplaceTheGeneralOnesOnHeadPartsOnly() {
        Material material = Material.CODEC.parse(ops, parse(withTraits("""
                "traits": {
                  "general": ["forgeweave:magnetic"],
                  "head": ["forgeweave:magnetic2"]
                }"""))).getOrThrow();

        ResourceLocation magnetic = ResourceLocation.fromNamespaceAndPath("forgeweave", "magnetic");
        ResourceLocation magnetic2 = ResourceLocation.fromNamespaceAndPath("forgeweave", "magnetic2");

        assertEquals(List.of(magnetic2), material.traits().forPart(PartItem.Kind.HEAD));
        assertEquals(List.of(magnetic), material.traits().forPart(PartItem.Kind.HANDLE));
        assertEquals(List.of(magnetic), material.traits().forPart(PartItem.Kind.EXTRA));
        assertEquals(List.of(magnetic, magnetic2), material.traits().all());
    }

    /** Both shapes decode to the same {@link Material.Traits}, and encoding always writes the new one. */
    @Test
    void encodesTheNewShapeAndOmitsEmptyScopes() {
        Material legacy = Material.CODEC.parse(ops, parse(withTraits("\"trait\": \"forgeweave:ecological\""))).getOrThrow();

        JsonElement encoded = Material.CODEC.encodeStart(ops, legacy).getOrThrow();
        JsonObject traits = encoded.getAsJsonObject().getAsJsonObject("traits");

        assertTrue(!encoded.getAsJsonObject().has("trait"), "the legacy field must not be re-emitted");
        assertEquals(1, traits.getAsJsonArray("general").size());
        // Empty scopes stay off the wire, so registry sync carries only what a material actually has.
        assertTrue(!traits.has("head"), "an empty head scope must not be encoded, got " + traits);
        assertEquals(legacy.traits(), Material.CODEC.parse(ops, encoded).getOrThrow().traits());
    }

    /** A material naming neither {@code trait} nor {@code traits} is still rejected. */
    @Test
    void rejectsAMaterialWithNoTraitsAtAll() {
        DataResult<Material> result = Material.CODEC.parse(ops, parse(withTraits(null)));

        assertTrue(result.isError(), "a material without traits must not parse");
    }

    /** A whole material JSON with {@code traitsField} spliced in (or omitted entirely when null). */
    private static String withTraits(@Nullable String traitsField) {
        return """
                {
                  "head": {"durability": 35, "mining_speed": 2.0, "attack_damage": 2.0},
                  "handle": {"durability_modifier": 1.0, "durability": 25},
                  "extra_durability": 15,
                  "incorrect_for_tool": "minecraft:incorrect_for_wooden_tool",
                  %s
                  "crafting_items": [{"ingredient": {"tag": "minecraft:planks"}, "value": 2}],
                  "repair_item": {"tag": "minecraft:planks"},
                  "color": "#8E661B"
                }""".formatted(traitsField == null ? "" : traitsField + ",");
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
