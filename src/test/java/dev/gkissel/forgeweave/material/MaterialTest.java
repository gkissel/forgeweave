package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

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

    /**
     * Issue #593's field, both ways round: named in the JSON it survives the round trip, omitted it
     * reads as {@link Material#DEFAULT_ENCHANTABILITY} and stays omitted on re-encode -- the
     * datapack-facing half of "existing packs don't change shape" (ADR-0002), and what keeps the
     * synced material of a pack that never heard of the field exactly the shape it was.
     */
    @Test
    void enchantabilityRoundTripsAndDefaultsWhenAbsent() {
        JsonElement withField = Material.CODEC.encodeStart(ops, gilded(31)).getOrThrow();
        assertEquals(31, withField.getAsJsonObject().get("enchantability").getAsInt());
        assertEquals(31, Material.CODEC.parse(ops, withField).getOrThrow().enchantability());

        JsonElement noField =
                Material.CODEC.encodeStart(ops, gilded(Material.DEFAULT_ENCHANTABILITY)).getOrThrow();
        assertFalse(noField.getAsJsonObject().has("enchantability"),
                "a material at the default must not write the field, or every pre-#593 pack's synced "
                        + "material grows a field it never had");
        assertEquals(Material.DEFAULT_ENCHANTABILITY,
                Material.CODEC.parse(ops, noField).getOrThrow().enchantability());
    }

    /** Every shipped material names one, so none of them silently rides on the fallback. */
    @ParameterizedTest
    @ValueSource(strings = { "wood", "stone", "iron", "paper", "string" })
    void shippedMaterialsNameAnEnchantability(String name) {
        assertTrue(shipped(name).getAsJsonObject().has("enchantability"),
                name + " should name an enchantability (issue #593)");
        assertTrue(Material.CODEC.parse(ops, shipped(name)).getOrThrow().enchantability() > 0);
    }

    private static Material gilded(int enchantability) {
        return new Material(
                Optional.of(new Material.Head(200, 5.09f, 2.5f)),
                Optional.of(new Material.Handle(1.1f, 50)),
                Optional.of(65),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                Material.Traits.general(ResourceLocation.fromNamespaceAndPath("forgeweave", "fractured")),
                List.of(new Material.CraftingItem(Ingredient.of(Items.BONE), 2)),
                Ingredient.of(Items.BONE),
                TextColor.parseColor("#EDE6BF").getOrThrow(),
                Optional.empty(),
                Optional.empty(),
                false,
                enchantability);
    }

    @ParameterizedTest
    @ValueSource(strings = { "wood", "stone", "flint", "bone" })
    void shippedMaterialsParse(String name) {
        Material.CODEC.parse(ops, shipped(name)).getOrThrow();
    }

    /**
     * Issue #433 -- the whole harvest ladder, one row per head-bearing shipped material.
     *
     * <p>Upstream's {@code HarvestLevels} constants ({@code library/utils/HarvestLevels.java:15-19},
     * pinned {@code c01173c}) are named for the <em>block</em> each level unlocks, not for the
     * vanilla tool tier of the same name: {@code STONE = 0} is the level that mines stone, which a
     * wooden pickaxe already has. PR #81 read them as tool-tier names and shipped every material one
     * rung too generous. The correct mapping onto the vanilla {@code incorrect_for_*_tool} ladder is
     * {@code STONE -> wooden}, {@code IRON -> stone}, {@code DIAMOND -> iron},
     * {@code OBSIDIAN -> diamond}, {@code COBALT -> netherite} -- five upstream levels onto five
     * vanilla tiers, exactly.
     *
     * <p>Levels below come from {@code TinkerMaterials#registerToolMaterialStats:409-534}; the six
     * materials with no 1.12 counterpart (chorus, rose gold, amethyst bronze, nahuatl, ancient,
     * netherite) take the modern {@code Tiers} value the 1.20 clone gives them
     * ({@code MaterialStatsDataProvider}), which is already a tool tier and needs no remapping.
     * String and vine ship no head stats, so their {@code incorrect_for_tool} is inert and is left
     * where it was.
     */
    @ParameterizedTest
    @CsvSource({
            // HarvestLevels.STONE (0)
            "wood,wooden", "paper,wooden", "sponge,wooden", "slime,wooden", "blueslime,wooden",
            "magmaslime,wooden", "firewood,wooden",
            // HarvestLevels.IRON (1)
            "stone,stone", "flint,stone", "cactus,stone", "bone,stone", "prismarine,stone",
            "netherrack,stone", "copper,stone", "lead,stone", "silver,stone", "electrum,stone",
            // HarvestLevels.DIAMOND (2)
            "iron,iron", "pig_iron,iron", "bronze,iron",
            // HarvestLevels.OBSIDIAN (3)
            "endstone,diamond", "knightslime,diamond", "steel,diamond",
            // HarvestLevels.COBALT (4)
            "obsidian,netherite", "cobalt,netherite", "ardite,netherite", "manyullyn,netherite",
            // no 1.12 counterpart -- 1.20 clone's modern Tiers value
            "chorus,stone", "rose_gold,wooden", "amethyst_bronze,diamond", "nahuatl,diamond",
            "ancient,netherite", "netherite,netherite"
    })
    void shippedMaterialsSitOnUpstreamsHarvestTier(String name, String tier) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();

        assertTrue(material.head().isPresent(), name + " must have head stats to carry a tier");
        assertEquals("minecraft:incorrect_for_" + tier + "_tool",
                material.incorrectForTool().location().toString(), name);
    }

    @Test
    void woodMatchesItsShippedStats() {
        Material wood = Material.CODEC.parse(ops, shipped("wood")).getOrThrow();

        assertEquals(Optional.of(new Material.Head(35, 2.0f, 2.0f)), wood.head());
        assertEquals(Optional.of(new Material.Handle(1.0f, 25)), wood.handle());
        assertEquals(Optional.of(15), wood.extraDurability());
        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological")),
                wood.traits().general());
        assertEquals(0x8E661B, wood.color().getValue());
    }

    /**
     * Wood carries its one trait as a general trait, i.e. every part it makes grants it -- which is
     * what the pre-#94 single {@code trait} field meant. (Stone used to as well, but issue #493 split
     * its {@code cheap}/{@code cheapskate} pair onto {@link #retrofittedMaterialsScopeTheirHeadTrait}'s
     * pattern.)
     */
    @ParameterizedTest
    @CsvSource({ "wood,ecological" })
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
     * Issue #493 gives stone the same treatment: upstream's {@code stone.addTrait(cheapskate, HEAD)}
     * replaces {@code cheap}'s general repair bonus with {@code cheapskate}'s durability penalty on
     * the head part, exactly like flint and bone.
     */
    @ParameterizedTest
    @CsvSource({ "flint,crude,crude2", "bone,fractured,splintering", "stone,cheap,cheapskate" })
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
    // VALUE_Shard), .addItem("plankWood", 1, VALUE_Ingot), .addItem("logWood", 1, VALUE_Ingot * 4)).
    // Since parity audit T58 (issue #489) the JSON `value` is upstream's own unit, unscaled.
    @Test
    void woodCraftingItemsMatchUpstreamValueTable() {
        Material wood = Material.CODEC.parse(ops, shipped("wood")).getOrThrow();

        assertEquals(3, wood.craftingItems().size());
        assertEquals(PartBuilderRecipes.SHARD_VALUE, wood.craftingItems().get(0).value(), "stick is VALUE_Shard");
        assertEquals(PartBuilderRecipes.INGOT_VALUE, wood.craftingItems().get(1).value(), "planks are VALUE_Ingot");
        assertEquals(4 * PartBuilderRecipes.INGOT_VALUE, wood.craftingItems().get(2).value(), "logs are VALUE_Ingot * 4");
    }

    @ParameterizedTest
    @ValueSource(strings = { "stone", "flint", "cactus", "obsidian", "netherrack", "endstone", "sponge",
            "firewood", "slime", "blueslime", "magmaslime", "string", "vine" })
    void ingotEquivalentMaterialsCostOneIngot(String name) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();

        assertTrue(!material.craftingItems().isEmpty(), name + " must have crafting items");
        for (Material.CraftingItem item : material.craftingItems()) {
            assertEquals(PartBuilderRecipes.INGOT_VALUE, item.value(), name + "'s crafting items are addItemIngot");
        }
    }

    // T58 (issue #489): the sub-shard crafting items upstream lists and the shard-unit scale could
    // not express -- TinkerMaterials.java:243-246 (prismarine), :269 (bonemeal), :276 (paper), and
    // Material#addCommonItems (nuggets, VALUE_Nugget = 16) for every metal.
    @Test
    void boneAcceptsBonemealAtAFragment() {
        Material bone = Material.CODEC.parse(ops, shipped("bone")).getOrThrow();

        assertEquals(PartBuilderRecipes.INGOT_VALUE, valueOf(bone, Items.BONE));
        assertEquals(PartBuilderRecipes.FRAGMENT_VALUE, valueOf(bone, Items.BONE_MEAL));
    }

    @Test
    void paperIsAFragmentNotAShard() {
        Material paper = Material.CODEC.parse(ops, shipped("paper")).getOrThrow();

        assertEquals(PartBuilderRecipes.FRAGMENT_VALUE, valueOf(paper, Items.PAPER));
    }

    @Test
    void prismarineValuesMatchUpstream() {
        Material prismarine = Material.CODEC.parse(ops, shipped("prismarine")).getOrThrow();

        assertEquals(PartBuilderRecipes.FRAGMENT_VALUE, valueOf(prismarine, Items.PRISMARINE_SHARD), "gemPrismarine");
        assertEquals(PartBuilderRecipes.INGOT_VALUE, valueOf(prismarine, Items.PRISMARINE), "blockPrismarine");
        assertEquals(9 * PartBuilderRecipes.FRAGMENT_VALUE, valueOf(prismarine, Items.PRISMARINE_BRICKS), "blockPrismarineBrick");
        assertEquals(2 * PartBuilderRecipes.INGOT_VALUE, valueOf(prismarine, Items.DARK_PRISMARINE), "blockPrismarineDark");
    }

    @ParameterizedTest
    @ValueSource(strings = { "iron", "pig_iron", "cobalt", "ardite", "manyullyn", "copper", "bronze", "lead",
            "silver", "electrum", "steel", "knightslime" })
    void addCommonItemsMetalsListIngotAndNugget(String name) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();

        assertTrue(material.craftingItems().stream().anyMatch(item -> item.value() == PartBuilderRecipes.INGOT_VALUE),
                name + " lists an ingot at VALUE_Ingot");
        assertTrue(material.craftingItems().stream().anyMatch(item -> item.value() == PartBuilderRecipes.NUGGET_VALUE),
                name + " lists a nugget at VALUE_Nugget");
    }

    private static int valueOf(Material material, Item item) {
        return material.craftingItems().stream()
                .filter(crafting -> crafting.ingredient().test(new ItemStack(item)))
                .mapToInt(Material.CraftingItem::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no crafting item matches " + item));
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

    /**
     * Issue #435 (parity audit T3): upstream 1.12 keeps two flags per material -- {@code craftable}
     * (Part Builder) and {@code castable} (Smeltery + cast), and {@code Material#isCraftable}
     * returns {@code craftable || (Config.craftCastableMaterials && castable)} with the config
     * defaulting to {@code false} ({@code library/materials/Material.java:173-190},
     * {@code common/config/Config.java:38,178-180}). Every metal is castable-and-not-craftable --
     * {@code MaterialIntegration:100-108} sets {@code castable} for any material handed a fluid and
     * {@code craftable} only for the ones without -- so the whole metal roster is cast-only by
     * default. Forgeweave carries the same information in one field: {@code cast_only} is exactly
     * upstream's {@code castable && !craftable}, which is why obsidian and knightslime -- the two
     * upstream materials that set <em>both</em> flags ({@code TinkerMaterials:236-237,299}) -- do
     * not carry it.
     */
    @Test
    void castOnlyDefaultsToFalseAndRoundTrips() {
        Material craftable = Material.CODEC.parse(ops, shipped("stone")).getOrThrow();
        assertFalse(craftable.castOnly(), "a material that does not name cast_only is Part Builder craftable");

        JsonElement encoded = Material.CODEC.encodeStart(ops, craftable).getOrThrow();
        assertFalse(encoded.getAsJsonObject().has("cast_only"),
                "the default must not be written, so every already-shipped material encodes unchanged");

        Material castOnly = Material.CODEC.parse(ops, shipped("iron")).getOrThrow();
        assertTrue(castOnly.castOnly(), "iron is cast-only");
        assertTrue(Material.CODEC.encodeStart(ops, castOnly).getOrThrow()
                .getAsJsonObject().get("cast_only").getAsBoolean(), "cast_only must survive a round trip");
    }

    /**
     * The cast-only roster, verified material by material against the 1.12 clone: every metal
     * {@code TinkerIntegration#preInit} hands a fluid and that {@code TinkerMaterials} never calls
     * {@code setCraftable} on. Forgeweave adds the three metals with a full Forgeweave casting
     * chain but no 1.12 counterpart (amethyst bronze, rose gold, netherite -- the 1.20 clone's own
     * {@code MaterialDataProvider:80-84} marks the first two {@code craftable = false} too) and
     * nahuatl, which is composite-cast only and already ships an empty {@code crafting_items} list.
     */
    @ParameterizedTest
    @ValueSource(strings = { "iron", "copper", "cobalt", "ardite", "manyullyn", "pig_iron", "steel",
            "amethyst_bronze", "rose_gold", "netherite", "nahuatl" })
    void castableMetalsAreCastOnly(String name) {
        assertTrue(Material.CODEC.parse(ops, shipped(name)).getOrThrow().castOnly(),
                name + " is castable and not craftable upstream, so the Part Builder must not take it");
    }

    /**
     * The deliberate exceptions. Obsidian and knightslime set <em>both</em> upstream flags
     * ({@code TinkerMaterials:236-237,299}), so they stay craftable however the config is set; the
     * four tag-gated compat metals have no Forgeweave fluid or casting recipe at all (docs/SCOPE.md
     * M3.2 "Part Builder path only"), so cast-only would make them unobtainable.
     */
    @ParameterizedTest
    @ValueSource(strings = { "obsidian", "knightslime", "bronze", "lead", "silver", "electrum",
            "ancient", "chorus", "wood", "stone" })
    void craftableMaterialsStayCraftable(String name) {
        assertFalse(Material.CODEC.parse(ops, shipped(name)).getOrThrow().castOnly(),
                name + " must stay Part Builder craftable");
    }

    /**
     * Issue #492 (parity audit T61): upstream's {@code addCommonItems} registers ingot, nugget
     * <em>and block</em> for every metal ({@code Material.java:345-348}), and cobalt/ardite/manyullyn
     * all call it ({@code TinkerMaterials:321,325,329}). Forgeweave already ships a storage block for
     * all four metals here -- including rose gold, which has no 1.12 counterpart but gets the same
     * treatment as every other Forgeweave-cast metal (issue #206) -- but their {@code crafting_items}
     * stopped at the ingot, unlike iron/copper/steel/pig_iron/netherite, which all list their block at
     * value 18 (nine ingots' worth, matching {@code VALUE_Block = VALUE_Ingot * 9}). The gate is inert
     * while {@code craftCastableMaterials} defaults off (T3), but the data is still wrong: it would
     * silently refuse a storage block the moment a pack turns that config on.
     */
    @ParameterizedTest
    @CsvSource({ "cobalt,forgeweave:cobalt_block", "ardite,forgeweave:ardite_block",
            "manyullyn,forgeweave:manyullyn_block", "rose_gold,forgeweave:rose_gold_block" })
    void castOnlyStorageBlocksAreCraftingItemsAtNineIngots(String name, String blockId) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();

        boolean hasBlockRow = material.craftingItems().stream()
                .filter(item -> item.value() == 9 * PartBuilderRecipes.INGOT_VALUE)
                .flatMap(item -> java.util.Arrays.stream(item.ingredient().getItems()))
                .anyMatch(stack -> ResourceLocation.parse(blockId).equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));

        assertTrue(hasBlockRow, name + " must list its storage block (" + blockId + ") at 9 ingots (VALUE_Block)");
    }
}
