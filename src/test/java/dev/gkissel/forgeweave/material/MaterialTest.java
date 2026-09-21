package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
    @ValueSource(strings = { "wood", "stone", "flint", "bone", "bronze", "lead", "silver", "electrum",
            // #833 M6 Track A batch 1: generic tech metals, re-homed across 1.21.1 providers.
            "tin", "aluminium", "nickel", "constantan", "invar", "platinum", "titanium", "tungsten",
            "iridium", "uranium", "graphite",
            // #834 M6 Track A batch 2: Mekanism, AE2 and Occultism.
            "osmium", "refined_obsidian", "refined_glowstone", "hdpe", "fluorite", "certus_quartz",
            "fluix", "sky_stone", "iesnium", "dragonyst",
            // #835 M6 Track A batch 3: Ender IO's eight surviving 1.21.1 alloy ingots.
            "redstone_alloy", "energetic_alloy", "pulsating_alloy", "conductive_alloy", "vibrant_alloy",
            "soularium", "dark_steel", "end_steel",
            // #836 M6 Track A batch 4: the Draconic Evolution pair (the endgame tier's only shippable
            // materials -- ProjectE and Avaritia ship no c: tags at all, see MaterialTest javadoc below).
            "draconium", "draconium_awakened",
            // #837 M6 Track A batch 5: gem/crystal tier -- Actually Additions, Psi, Powah, Industrial
            // Foregoing, Extreme Reactors (yellorium skipped, see PresetBatch5GameTests).
            "black_quartz", "restonia_crystal", "palis_crystal", "diamatine_crystal", "void_crystal",
            "emeradic_crystal", "enori_crystal", "uraninite", "psimetal", "psigem", "ivory_psimetal",
            "ebony_psimetal", "pink_slime", "cyanite", "blutonium", "ludicrite",
            // #841 M6 Track B: the self-contained tool material roster -- 11 ore-sourced metals
            // (TrackBOre) plus 18 alloy metals (TrackBAlloy), no neoforge:conditions (they always
            // exist). Issue #884 (1): "basalt" here (a Part-Builder-only vanilla-item material, not
            // a TrackBOre) replaces the retired "cinderstone".
            "basalt", "fulmenite", "duskspar", "voltcinder", "murkiron", "hardcinder", "nightshale",
            "warspar", "hollowstone", "resonite", "starfall_stone", "voidglass",
            "ironbrand", "quakestone", "shardline", "embercast", "riftalloy", "tideiron", "cinderforge",
            "dreadalloy", "sunsteel", "hollowsteel", "truesteel", "stormalloy", "glowveil", "daybrass",
            "faultsteel", "skipalloy", "mendalloy", "mendstone",
            // #872 M6 recovery batch: concrete item ids in crafting_items/repair_item, unblocked by
            // the LENIENT_INGREDIENT_CODEC schema change -- ProjectE, AvaritiaNeo, Draconic Evolution's
            // core-tier pair, Refined Storage and Powah.
            "dark_matter", "red_matter", "crystal_matrix", "cosmic_neutronium", "infinity",
            "wyvern", "chaotic", "quartz_enriched_iron", "silicon", "energised_steel",
            // #946 M8: the Draconic Evolution fusion metals, gated on the DE core each one's
            // fusion recipe consumes. They sit above the wyvern/chaotic preset pair, which stays as
            // the raw tier under them. #965 added duskweld at the inert tier under the three, and
            // the draconium core material beside the other three cores.
            "duskweld", "emberweld", "starweld", "voidweld", "draconium_core",
            // #993 M8 (D-M8-13): atomic matter alloy, the same shape one mod over -- only a
            // nucleosynthesis run on Mekanism's own machine makes the ingot. Unlike the welds it
            // carries no neoforge:conditions at all (see TrackBAlloy), so it parses on the direct
            // path the Track B roster above uses.
            "atomic_matter_alloy",
            // Issue #1031 (D-M8-21): Just Dire Things' four tool tiers.
            "ferricore", "blazegold", "celestigem", "eclipsealloy",
            // Issue #1059 (D-M8-25): Twilight Forest's four melting tiers plus four Part
            // Builder-only materials, and Ice and Fire Community Edition's four melting tiers plus
            // six Part Builder-only materials. Silver dedupes into the existing material above.
            "ironwood", "steeleaf", "knightmetal", "fiery",
            "naga_scale", "arctic_fur", "alpha_yeti_fur", "carminite",
            "dragon_bone", "dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning",
            "deathworm_chitin_yellow", "deathworm_chitin_white", "deathworm_chitin_red",
            "troll_leather_mountain", "troll_leather_forest", "troll_leather_frost",
            // Issue #1058 (D-M8-24): Silent Gear, PneumaticCraft: Repressurized, Forbidden and
            // Arcanus, The Aether and L_Ender's Cataclysm.
            "crimson_steel", "azure_silver", "azure_electrum", "blaze_gold", "tyrian_steel",
            "compressed_iron", "deorum", "zanite", "gravitite", "ambrosium", "ignitium", "cursium",
            // Issue #1069 (D-M8-26): Actually Additions' six empowered crystals, joining #837
            // batch 5's plain-crystal roster.
            "empowered_restonia_crystal", "empowered_palis_crystal", "empowered_diamatine_crystal",
            "empowered_void_crystal", "empowered_emeradic_crystal", "empowered_enori_crystal" })
    void shippedMaterialsParse(String name) {
        Material.CODEC.parse(ops, shipped(name)).getOrThrow();
    }

    /**
     * Issue #826 deliverable 2: {@link Material#CODEC} is {@code RecordCodecBuilder}-based, so it
     * silently ignores a JSON key it does not name -- {@code neoforge:conditions} is stripped by the
     * real loader's {@code ConditionalOps} before this codec ever sees it, but this test parses the
     * raw shipped JSON the same way {@link #shippedMaterialsParse} above does (plain {@link #ops},
     * no {@code ConditionalOps}) to pin that tolerance down, rather than assume it.
     */
    @ParameterizedTest
    @ValueSource(strings = { "bronze", "lead", "silver", "electrum",
            // #833 M6 Track A batch 1: same neoforge:conditions convention, one or more
            // item_exists primitives (uranium is an neoforge:or over three providers).
            "tin", "aluminium", "nickel", "constantan", "invar", "platinum", "titanium", "tungsten",
            "iridium", "uranium", "graphite",
            // #834 M6 Track A batch 2: Mekanism, AE2 and Occultism.
            "osmium", "refined_obsidian", "refined_glowstone", "hdpe", "fluorite", "certus_quartz",
            "fluix", "sky_stone", "iesnium", "dragonyst",
            // #835 M6 Track A batch 3: single item_exists each, verified against Ender IO's own
            // 1.21.1 tree (EnderIoAlloyGameTests).
            "redstone_alloy", "energetic_alloy", "pulsating_alloy", "conductive_alloy", "vibrant_alloy",
            "soularium", "dark_steel", "end_steel",
            // #836 M6 Track A batch 4: single item_exists each, verified against Draconic Evolution's
            // own 3.1.4.632 jar (DraconicEvolutionGameTests).
            "draconium", "draconium_awakened",
            // #837 M6 Track A batch 5: single item_exists each, verified against each mod's own
            // 1.21.1 tree (PresetBatch5GameTests).
            "black_quartz", "restonia_crystal", "palis_crystal", "diamatine_crystal", "void_crystal",
            "emeradic_crystal", "enori_crystal", "uraninite", "psimetal", "psigem", "ivory_psimetal",
            "ebony_psimetal", "pink_slime", "cyanite", "blutonium", "ludicrite",
            // #872 M6 recovery batch: single item_exists each, verified against each mod's own
            // 1.21.1 tree (RecoveryBatchGameTests, DraconicEvolutionGameTests).
            "dark_matter", "red_matter", "crystal_matrix", "cosmic_neutronium", "infinity",
            "wyvern", "chaotic", "quartz_enriched_iron", "silicon", "energised_steel",
            // Issue #1031 (D-M8-21): Just Dire Things' four tool tiers, single item_exists each,
            // verified against Direwolf20-MC/JustDireThings@v1.5.7 (JustDireThingsGameTests).
            "ferricore", "blazegold", "celestigem", "eclipsealloy",
            // Issue #1059 (D-M8-25): Twilight Forest and Ice and Fire Community Edition, single
            // item_exists each, verified against TeamTwilight/twilightforest@1.21.1 and
            // IAFEnvoy/IceAndFire-CE@1.21.1 (TwilightForestGameTests, IceAndFireGameTests).
            "ironwood", "steeleaf", "knightmetal", "fiery",
            "naga_scale", "arctic_fur", "alpha_yeti_fur", "carminite",
            "dragon_bone", "dragonsteel_fire", "dragonsteel_ice", "dragonsteel_lightning",
            "deathworm_chitin_yellow", "deathworm_chitin_white", "deathworm_chitin_red",
            "troll_leather_mountain", "troll_leather_forest", "troll_leather_frost",
            // Issue #1058 (D-M8-24): single item_exists each, verified against each mod's own
            // 1.21.1 tree (SilentGearGameTests, PneumaticCraftGameTests, ForbiddenArcanusGameTests,
            // AetherGameTests, CataclysmGameTests).
            "crimson_steel", "azure_silver", "azure_electrum", "blaze_gold", "tyrian_steel",
            "compressed_iron", "deorum", "zanite", "gravitite", "ambrosium", "ignitium", "cursium",
            // Issue #1069 (D-M8-26): single item_exists each, verified against the shipped
            // actuallyadditions-1.3.24+mc1.21.1.jar (EmpoweredCrystalsGameTests).
            "empowered_restonia_crystal", "empowered_palis_crystal", "empowered_diamatine_crystal",
            "empowered_void_crystal", "empowered_emeradic_crystal", "empowered_enori_crystal" })
    void conditionalMaterialsCarryAWellFormedConditionsBlockAndStillParse(String name) {
        JsonObject json = shipped(name).getAsJsonObject();
        assertTrue(json.has("neoforge:conditions"), name + " must carry a neoforge:conditions block (issue #826)");
        assertTrue(json.get("neoforge:conditions").isJsonArray(), name + "'s neoforge:conditions must be an array");
        for (JsonElement condition : json.getAsJsonArray("neoforge:conditions")) {
            assertTrue(condition.isJsonObject(), name + "'s condition entries must be objects, got " + condition);
            String type = condition.getAsJsonObject().get("type").getAsString();
            assertTrue(type.startsWith("neoforge:"),
                    name + "'s condition type must be a neoforge: primitive (issue #826, tags throw), got " + type);
        }

        // The codec ignores the extra key rather than rejecting it -- see the class javadoc above.
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
     *
     * <p>Issue #877 (the JC10 reversal, superseding #838's original scaffold) appends three rungs
     * above netherite -- {@code hardcinder}, {@code warspar}, {@code resonite} -- so {@code tier} may
     * now also be one of those three, which {@link #expectedIncorrectForTool} resolves onto the
     * {@code forgeweave:} namespace rather than {@code minecraft:}.
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
            // HarvestLevels.COBALT (4). manyullyn moved off this rung by issue #877 (the JC10
            // reversal) -- see the Track B block below, it now sits at "hardcinder" alongside ancient.
            "obsidian,netherite", "cobalt,netherite", "ardite,netherite",
            // no 1.12 counterpart -- 1.20 clone's modern Tiers value. ancient moved off this rung by
            // issue #877 too -- see the Track B block below.
            "chorus,stone", "rose_gold,wooden", "amethyst_bronze,diamond", "nahuatl,diamond",
            "netherite,netherite",
            // #833 M6 Track A batch 1: no upstream counterpart either, so tiers here are Forgeweave's
            // own placement (proposed on the PR) rather than a ported HarvestLevels row -- tin/
            // aluminium/graphite at stone, nickel/constantan/invar at iron, platinum/titanium/
            // tungsten/uranium at diamond, iridium at netherite (its historical IC2 endgame role).
            "tin,stone", "aluminium,stone", "graphite,stone",
            "nickel,iron", "constantan,iron", "invar,iron",
            "platinum,diamond", "titanium,diamond", "tungsten,diamond", "uranium,diamond",
            "iridium,netherite",
            // #835 M6 Track A batch 3: Ender IO's own alloy chain places redstone alloy as the cheap
            // entry rung (stone), the mid-chain capacitor/conduit metals at iron, vibrant alloy and
            // soularium at diamond, and dark steel/end steel as the late-tier pair (netherite) --
            // proposed on the PR, no upstream HarvestLevels row to port.
            "redstone_alloy,stone",
            "energetic_alloy,iron", "pulsating_alloy,iron", "conductive_alloy,iron",
            "vibrant_alloy,diamond", "soularium,diamond",
            "dark_steel,netherite", "end_steel,netherite",
            // #836 M6 Track A batch 4: the endgame tier -- both sit at the netherite rung alongside
            // cobalt/netherite/obsidian. Compat metals stay within vanilla rungs even after #877 (the
            // JC10 reversal): other mods' blocks and tools are not in Forgeweave's own tier tags, so
            // moving a compat material's own tools onto a Forgeweave-only rung would gate nothing real
            // (the PR body's stat table differentiates these instead).
            "draconium,netherite", "draconium_awakened,netherite",
            // issue #843 (closes #180): the 1.20-branch material gap's five by-name additions.
            "seared_stone,iron", "necrotic_bone,iron", "slimewood,iron",
            "queens_slime,netherite", "hepatizon,netherite",
            // #837 M6 Track A batch 5: no upstream counterpart, so tiers here are Forgeweave's own
            // placement (proposed on the PR) -- black quartz is the common/low crystal (iron), the six
            // coloured Actually Additions crystals and Psi's base psimetal/uraninite/pink slime sit at
            // iron or diamond depending on rarity, Psi's refined ivory/ebony variants and Extreme
            // Reactors' cyanite/blutonium at diamond, and ludicrite -- its endgame reactor casing --
            // at netherite.
            "black_quartz,iron",
            "restonia_crystal,diamond", "palis_crystal,diamond", "diamatine_crystal,diamond",
            "void_crystal,diamond", "emeradic_crystal,diamond", "enori_crystal,diamond",
            "uraninite,diamond",
            // Issue #1069 (D-M8-26): the six empowered crystals, one rung above their plain
            // counterparts (netherite) -- Forgeweave's own placement, no rung above resonite
            // (D-M8-10); the jar ships no decompilable ToolMaterial/Tier class for any crystal color.
            "empowered_restonia_crystal,netherite", "empowered_palis_crystal,netherite",
            "empowered_diamatine_crystal,netherite", "empowered_void_crystal,netherite",
            "empowered_emeradic_crystal,netherite", "empowered_enori_crystal,netherite",
            "psimetal,iron", "psigem,diamond", "ivory_psimetal,diamond", "ebony_psimetal,diamond",
            "pink_slime,diamond",
            "cyanite,diamond", "blutonium,diamond", "ludicrite,netherite",
            // #841 M6 Track B: the self-contained tool material roster's original tier scaffold
            // (docs/research/m6-material-expansion-references.md &sect;7.1, JC10 = no new tags) --
            // superseded by issue #877 (the JC10 reversal). basalt (issue #884 (1), replacing the
            // retired cinderstone) is the roster's own "stone" rung; fulmenite/quakestone/shardline
            // are its "diamond" rung; duskspar/voltcinder/
            // starfall_stone/voidglass/ironbrand/embercast/tideiron/cinderforge/daybrass/faultsteel/
            // skipalloy/mendalloy/mendstone stay at netherite (the reference ladder's own
            // cobalt-equivalent rung, alongside cobalt/ardite/netherite/obsidian/iridium); murkiron/
            // hardcinder/nightshale/riftalloy/dreadalloy/stormalloy move to the new hardcinder rung
            // (the reference ladder's duranite-equivalent, alongside manyullyn and ancient -- see
            // #877's PR body for the "why manyullyn/ancient move" call); warspar/hollowstone/
            // hollowsteel/glowveil move to the new warspar rung (valyrium-equivalent); resonite/
            // sunsteel/truesteel move to the new top resonite rung (vibranium-equivalent).
            "basalt,stone",
            "fulmenite,diamond", "quakestone,diamond", "shardline,diamond",
            "duskspar,netherite", "voltcinder,netherite", "starfall_stone,netherite", "voidglass,netherite",
            "ironbrand,netherite", "embercast,netherite", "tideiron,netherite", "cinderforge,netherite",
            "faultsteel,netherite", "skipalloy,netherite",
            "murkiron,hardcinder", "hardcinder,hardcinder", "nightshale,hardcinder",
            "riftalloy,hardcinder", "dreadalloy,hardcinder", "stormalloy,hardcinder",
            "manyullyn,hardcinder",
            "warspar,warspar", "hollowstone,warspar", "glowveil,warspar",
            "resonite,resonite", "sunsteel,resonite", "truesteel,resonite",
            // Issue #1113 moved four of the rows above. `ancient` left the hardcinder rung for
            // netherite, which closed the Part Builder skip past the whole rung-5 ore trio
            // (review 06-progression.md section 3, "break 3") and puts netherite scrap in the
            // same book stage as the netherite it makes. `hollowsteel` left warspar for
            // resonite, because a depth-4 alloy of resonite was landing a book stage earlier
            // than its own depth-3 ingredient. `mendalloy`, `mendstone` and `daybrass` left
            // netherite for the rung of the ores they are poured from, because the payoff rule
            // lifts them past every rung-6 ore and a netherite-rung tag on those numbers said
            // nothing true.
            "ancient,netherite",
            "mendalloy,hardcinder", "daybrass,hardcinder", "mendstone,warspar",
            "hollowsteel,resonite",
            // #993 (D-M8-10): atomic matter alloy sits on the top resonite rung with them. The
            // "compat metals stay within vanilla rungs" call in the batch 4 comment above does not
            // apply -- the ingot is a Forgeweave item cast from a Forgeweave fluid, so a
            // Forgeweave-only rung gates something real here.
            "atomic_matter_alloy,resonite",
            // #872 M6 recovery batch: no upstream counterpart, so tiers here are Forgeweave's own
            // placement (proposed on the PR). ProjectE's dark/red matter and Avaritia's escalating
            // crystal_matrix/cosmic_neutronium/infinity ladder and Draconic Evolution's wyvern/chaotic
            // core pair all sit at the netherite rung alongside draconium/netherite -- compat metals
            // stay within vanilla rungs even after #877 (the JC10 reversal), see the batch 4 comment
            // above -- differentiated by stats and traits instead. Refined
            // Storage's quartz enriched iron sits at stone (an iron-adjacent utility component, not a
            // combat metal); silicon, its lower-value byproduct, at the same rung as fluorite/graphite.
            // Powah's energised steel is diamond, a step above plain steel.
            "dark_matter,netherite", "red_matter,netherite",
            "crystal_matrix,netherite", "cosmic_neutronium,netherite", "infinity,netherite",
            "wyvern,netherite", "chaotic,netherite",
            "quartz_enriched_iron,stone", "silicon,stone",
            "energised_steel,diamond"
    })
    void shippedMaterialsSitOnUpstreamsHarvestTier(String name, String tier) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();

        assertTrue(material.head().isPresent(), name + " must have head stats to carry a tier");
        assertEquals(expectedIncorrectForTool(tier), material.incorrectForTool().location().toString(), name);
    }

    /** The three rungs #877 mints above netherite live under {@code forgeweave:}, not {@code minecraft:}. */
    private static final List<String> FORGEWEAVE_TIERS = List.of("hardcinder", "warspar", "resonite");

    private static String expectedIncorrectForTool(String tier) {
        String namespace = FORGEWEAVE_TIERS.contains(tier) ? "forgeweave" : "minecraft";
        return namespace + ":incorrect_for_" + tier + "_tool";
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

        // containsAll rather than equals since #626: bone's all() also carries its SHAFT-scoped
        // splitting (ArrowMaterialTest#boneShaftTraitReplacesTheGeneralList pins that scope).
        assertTrue(material.traits().all().containsAll(List.of(generalId, headId)));
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
            "silver", "electrum", "steel", "knightslime",
            // #833 M6 Track A batch 1: every provider ships a c:nuggets/<name> tag except graphite's
            // (no nugget item exists for a non-metal mineral, so it is excluded from this list).
            "tin", "aluminium", "nickel", "constantan", "invar", "platinum", "titanium", "tungsten",
            "iridium", "uranium",
            // #835 M6 Track A batch 3: Ender IO ships a c:nuggets/<name> tag for all eight alloys.
            "redstone_alloy", "energetic_alloy", "pulsating_alloy", "conductive_alloy", "vibrant_alloy",
            "soularium", "dark_steel", "end_steel",
            // #836 M6 Track A batch 4: Draconic Evolution ships c:nuggets/draconium and
            // c:nuggets/draconium_awakened (verified against the mod's own 3.1.4.632 jar).
            "draconium", "draconium_awakened",
            // #841 M6 Track B: every one of the (now 29, issue #884 (1) retired cinderstone --
            // basalt replaces it as a Part-Builder-only vanilla-item material, no ingot/nugget of its
            // own) self-contained materials gets a Forgeweave-minted ingot and nugget item
            // (TrackBOre/TrackBAlloy), same shape as cobalt/ardite/manyullyn.
            "fulmenite", "duskspar", "voltcinder", "murkiron", "hardcinder", "nightshale",
            "warspar", "hollowstone", "resonite", "starfall_stone", "voidglass",
            "ironbrand", "quakestone", "shardline", "embercast", "riftalloy", "tideiron", "cinderforge",
            "dreadalloy", "sunsteel", "hollowsteel", "truesteel", "stormalloy", "glowveil", "daybrass",
            "faultsteel", "skipalloy", "mendalloy", "mendstone" })
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
     * upstream's {@code castable && !craftable}, which is why knightslime -- one of the two
     * upstream materials that set <em>both</em> flags ({@code TinkerMaterials:236-237,299}) -- does
     * not carry it. Obsidian, the other, carries it anyway since issue #1113; see
     * {@link #castableMetalsAreCastOnly} for why.
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
     * {@code MaterialDataProvider:80-84} marks the first two {@code craftable = false} too).
     * Nahuatl is not here (#727): the 1.20 clone marks it {@code craftable = true}
     * ({@code MaterialDataProvider:81}) and its Part Builder input is the nahuatl board.
     */
    @ParameterizedTest
    @ValueSource(strings = { "iron", "copper", "cobalt", "ardite", "manyullyn", "pig_iron", "steel",
            "amethyst_bronze", "rose_gold", "netherite",
            // Issue #1113 (maintainer directive): obsidian joins them. Two obsidian blocks at
            // the Part Builder used to give a netherite-rung head with no smeltery, no cast and
            // no alloy, which is the widest skip the harvest ladder had (review
            // 06-progression.md section 3, "break 3"). Note this is a deliberate deviation from
            // 1.12 rather than a parity fix: upstream really does set both flags on obsidian
            // ({@code TinkerMaterials:236-237}) and really does give its head the top COBALT
            // harvest level, so the skip is upstream behaviour. Knightslime, the other
            // both-flags material, keeps the Part Builder.
            "obsidian",
            // issue #843 (closes #180): the alloy-only half of the 1.20-branch material gap --
            // queen's slime and hepatizon have no raw form, same as amethyst bronze/rose gold above;
            // slimewood has no wood item of its own either (audit table), same cast-only shape.
            "queens_slime", "hepatizon", "slimewood",
            // #841 M6 Track B: the self-contained material roster gets full smeltery integration
            // (#840) but no Part Builder path, same cast-only shape as cobalt/ardite/manyullyn/steel.
            // Issue #884 (1) retired cinderstone from this list -- its replacement, basalt, is the
            // opposite shape (Part-Builder-only, no smeltery casting at all; see basalt.json).
            "fulmenite", "duskspar", "voltcinder", "murkiron", "hardcinder", "nightshale",
            "warspar", "hollowstone", "resonite", "starfall_stone", "voidglass",
            "ironbrand", "quakestone", "shardline", "embercast", "riftalloy", "tideiron", "cinderforge",
            "dreadalloy", "sunsteel", "hollowsteel", "truesteel", "stormalloy", "glowveil", "daybrass",
            "faultsteel", "skipalloy", "mendalloy", "mendstone",
            // #873 (M6 epic #824's JC3 reversal): every compat metal now gets full smeltery
            // integration and flips to cast_only -- the Part-Builder-only exception these materials
            // used to be listed under (see craftableMaterialsStayCraftable's own #873 note) no longer
            // applies. The three PlusTiC-inspiration alloys this issue also ships (alumite,
            // osgloglas, osmiridium) are cast_only the same way every other TrackBAlloy-shaped metal
            // is, so they are listed here too.
            "bronze", "lead", "silver", "electrum",
            "alumite", "osgloglas", "osmiridium",
            // #946: the fusion metals are cast-only the same way -- the Part Builder never takes
            // them, and a fusion craft is the only thing that makes their ingot at all. #965 added
            // duskweld under the three.
            "duskweld", "emberweld", "starweld", "voidweld",
            // #993: atomic matter alloy is cast-only for the same reason -- the Part Builder never
            // takes it, and a nucleosynthesis run on Mekanism's own machine is the only thing that
            // makes its ingot at all.
            "atomic_matter_alloy",
            "tin", "aluminium", "nickel", "constantan", "invar", "platinum", "titanium", "tungsten",
            "iridium", "uranium", "graphite",
            "redstone_alloy", "energetic_alloy", "pulsating_alloy", "conductive_alloy", "vibrant_alloy",
            "soularium", "dark_steel", "end_steel",
            "draconium", "draconium_awakened",
            "uraninite", "psimetal", "ivory_psimetal", "ebony_psimetal", "pink_slime", "cyanite",
            "blutonium", "ludicrite",
            "dark_matter", "red_matter", "crystal_matrix", "cosmic_neutronium", "infinity",
            "quartz_enriched_iron", "silicon", "energised_steel" })
    void castableMetalsAreCastOnly(String name) {
        assertTrue(Material.CODEC.parse(ops, shipped(name)).getOrThrow().castOnly(),
                name + " is castable and not craftable upstream, so the Part Builder must not take it");
    }

    /**
     * The deliberate exceptions. Knightslime sets <em>both</em> upstream flags
     * ({@code TinkerMaterials:299}), so it stays craftable however the config is set. Obsidian set
     * both too and no longer does -- issue #1113 made it cast-only to close the harvest-ladder
     * skip, which is a maintainer decision against parity rather than a parity fix.
     *
     * <p>#873 (M6 epic #824's JC3 reversal) removed the compat-metal exception this javadoc used to
     * document: every compat metal now gets full smeltery integration and moved to
     * {@link #castableMetalsAreCastOnly}. What is left here is materials with no molten form at
     * all -- the gems/crystals/organics #873's own PR lists as excluded (upstream never treats them
     * as meltable) plus a few non-metal survivors from earlier milestones.
     */
    @ParameterizedTest
    @ValueSource(strings = { "knightslime",
            "ancient", "chorus", "wood", "stone", "nahuatl",
            // issue #843 (closes #180): seared stone and necrotic bone both keep the Part Builder
            // item-based route the audit found already sourceable -- seared stone additionally sets
            // <em>both</em> upstream flags like obsidian/knightslime (full smeltery casting too), and
            // necrotic bone has no smeltery integration at all, same shape as bone.
            "seared_stone", "necrotic_bone",
            // #873: excluded gems/crystals/organics (upstream never melts them) -- black_quartz,
            // restonia_crystal, palis_crystal, diamatine_crystal, void_crystal, emeradic_crystal and
            // enori_crystal are Actually Additions' crystal-tier roster (#837 batch 5); psigem is
            // Psi's gem. Listed in #873's PR body alongside certus_quartz/fluix/fluorite/dragonyst/
            // sky_stone/hdpe, which are not in this parametrized list at all (never were).
            "black_quartz", "restonia_crystal", "palis_crystal", "diamatine_crystal", "void_crystal",
            "emeradic_crystal", "enori_crystal", "psigem",
            // Issue #1069 (D-M8-26): the six empowered crystals join the same "crystals do not
            // melt" exclusion -- no melting_recipe, no casting_recipe, no ForgeweaveFluids entry.
            "empowered_restonia_crystal", "empowered_palis_crystal", "empowered_diamatine_crystal",
            "empowered_void_crystal", "empowered_emeradic_crystal", "empowered_enori_crystal",
            // #953 (maintainer directive): the three Draconic Evolution core materials are Part
            // Builder only. A core is a machine part, not an ingot -- there is nothing to pour it
            // into and nothing to pour out of it -- so they lost the fluid, the melting recipe, the
            // casting rows and the cast_only flag #872/#873 had given the pair. `awakened` is this
            // issue's new material, the rung between wyvern and chaotic; the ingot metals
            // (draconium, draconium_awakened) are unaffected and stay cast-only above. #965 added
            // draconium_core, the inert tech level's core, on the same terms.
            "wyvern", "awakened", "chaotic", "draconium_core" })
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

    /**
     * Issue #835's trap guard: {@code electrical_steel} no longer exists on Ender IO's 1.21.1 tree
     * (verified directly against {@code Team-EnderIO/EnderIO}'s {@code 1.21.1} branch -- it shipped
     * exactly eight alloy ingot tags, not the fifteen the 1.12-era roster had), so no shipped
     * material's {@code neoforge:conditions} block may ever name it -- a wrong id here would not
     * crash, it would just silently never register (docs/research/m6-material-expansion-references.md
     * &sect;1.4), which is exactly the failure mode this test exists to catch before it ships again.
     */
    @Test
    void noShippedMaterialConditionsOnEnderIosRemovedElectricalSteel() throws Exception {
        Path materialDir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        List<String> offenders = new java.util.ArrayList<>();

        try (Stream<Path> files = Files.list(materialDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                if (raw.contains("enderio:electrical_steel_ingot")) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "these shipped materials condition on enderio:electrical_steel_ingot, which does not "
                        + "exist on Ender IO's 1.21.1 tree (issue #835): " + offenders);
    }

    /**
     * Issue #872's schema fix, proven directly against the mechanism rather than the shipped
     * roster: {@code projecte:dark_matter} is guaranteed unregistered in this mod-less test JVM
     * (ProjectE is not a build/test dependency, see build.gradle), so this pins both halves of the
     * new {@code Material.LENIENT_INGREDIENT_CODEC} contract down -- the material still parses (it
     * used to fail outright here, the #836/#860 constraint this issue closes), and the resulting
     * {@link Ingredient} never matches a real item stack, exactly like an unfilled {@code c:} tag
     * already behaves.
     */
    @Test
    void unregisteredConcreteItemIdParsesLenientlyAndNeverMatches() {
        Material material = Material.CODEC.parse(ops, parse("""
                {
                  "head": {"durability": 35, "mining_speed": 2.0, "attack_damage": 2.0},
                  "handle": {"durability_modifier": 1.0, "durability": 25},
                  "extra_durability": 15,
                  "incorrect_for_tool": "minecraft:incorrect_for_wooden_tool",
                  "traits": {},
                  "crafting_items": [{"ingredient": {"item": "projecte:dark_matter"}, "value": 144}],
                  "repair_item": {"item": "projecte:dark_matter"},
                  "color": "#8E661B"
                }""")).getOrThrow();

        assertEquals(1, material.craftingItems().size());
        assertFalse(material.craftingItems().get(0).ingredient().test(new ItemStack(Items.IRON_INGOT)),
                "an unresolved item id must never match a real item stack (Part Builder matching)");
        assertFalse(material.repairItem().test(new ItemStack(Items.DIAMOND)),
                "an unresolved item id must never match a real item stack (Tool Station repair)");
    }

    /**
     * Issue #872's own inversion of the guard #836 shipped when the concrete-item-id constraint
     * still stood: ProjectE ({@code sinkillerj/ProjectE}'s {@code mc1.21.1} branch) and Avaritia
     * ({@code AquaThree/AvaritiaNeo}'s {@code main} branch) ship zero {@code c:} tags for any of
     * these five materials, but {@code Material.LENIENT_INGREDIENT_CODEC} means that no longer
     * blocks shipping them -- each keys {@code crafting_items}/{@code repair_item} on its provider's
     * real concrete item id instead of a tag, verified directly against each mod's own tree (PR body).
     */
    @ParameterizedTest
    @CsvSource({
            "dark_matter,projecte:dark_matter",
            "red_matter,projecte:red_matter",
            "crystal_matrix,avaritia:crystal_matrix_ingot",
            "cosmic_neutronium,avaritia:neutronium_ingot",
            "infinity,avaritia:infinity_ingot",
    })
    void projectEAndAvaritiaMaterialsCarryTheirConcreteItemId(String name, String expectedItem) {
        JsonObject json = shipped(name).getAsJsonObject();

        assertEquals(expectedItem, json.getAsJsonObject("repair_item").get("item").getAsString(),
                name + "'s repair_item must key on its provider's concrete item id");
        assertEquals(expectedItem,
                json.getAsJsonArray("crafting_items").get(0).getAsJsonObject()
                        .getAsJsonObject("ingredient").get("item").getAsString(),
                name + "'s crafting_items must key on its provider's concrete item id");

        // Still parses cleanly through the real codec, matching #shippedMaterialsParse.
        Material.CODEC.parse(ops, json).getOrThrow();
    }

    /**
     * Issue #876 (M6 dedupe batch) reverses #837's design note: the seven Actually Additions crystals
     * used to share one damage-scaling trait, then #876 split them onto seven invented ids, and
     * #1103 folded those ids back into the families they were clones of -- {@code unyielding} and
     * {@code keenedge} were both Pristine, {@code bloodgem} was Colossal, {@code stormglass} was
     * Kinetic. What this row still guards is the thing that actually mattered: each of the seven
     * carries an effect of its own, rather than seven materials all reading the same line.
     */
    @ParameterizedTest
    @CsvSource({
            "black_quartz,pristine",
            "restonia_crystal,colossal",
            "palis_crystal,kinetic",
            "diamatine_crystal,surging2",
            "void_crystal,fractured",
            "emeradic_crystal,heft2",
            "enori_crystal,luminous",
    })
    void actuallyAdditionsCrystalsCarryTheirOwnEffect(String name, String expectedTrait) {
        Material material = Material.CODEC.parse(ops, shipped(name)).getOrThrow();
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath("forgeweave", expectedTrait);

        assertTrue(material.traits().general().contains(expected),
                name + " must carry forgeweave:" + expectedTrait + ", got " + material.traits().general());
    }

    /**
     * Issue #1069 (D-M8-26): every Actually Additions material -- the seven #837 already ships plus
     * this issue's six empowered crystals -- must be buildable at the Part Builder from its own raw
     * item, not only from its storage block. #837 batch 5 found no {@code c:gems/*} tag exists for
     * any of the six plain crystals and stopped at the {@code c:storage_blocks/*} tag alone; issue
     * #872 unblocked concrete item ids in {@code crafting_items}/{@code repair_item} after batch 5
     * shipped, and nobody revisited these six to use it -- a single restonia crystal (or any of its
     * five siblings) could not build a part on its own, only a compacted block of nine could. This
     * test pins the fix down for all thirteen: each material's {@code crafting_items} must contain a
     * row keyed on the mod's own raw item id, and {@code repair_item} must key on that same id rather
     * than the block.
     */
    @ParameterizedTest
    @CsvSource({
            "restonia_crystal,actuallyadditions:restonia_crystal",
            "palis_crystal,actuallyadditions:palis_crystal",
            "diamatine_crystal,actuallyadditions:diamatine_crystal",
            "void_crystal,actuallyadditions:void_crystal",
            "emeradic_crystal,actuallyadditions:emeradic_crystal",
            "enori_crystal,actuallyadditions:enori_crystal",
            "empowered_restonia_crystal,actuallyadditions:empowered_restonia_crystal",
            "empowered_palis_crystal,actuallyadditions:empowered_palis_crystal",
            "empowered_diamatine_crystal,actuallyadditions:empowered_diamatine_crystal",
            "empowered_void_crystal,actuallyadditions:empowered_void_crystal",
            "empowered_emeradic_crystal,actuallyadditions:empowered_emeradic_crystal",
            "empowered_enori_crystal,actuallyadditions:empowered_enori_crystal",
    })
    void actuallyAdditionsMaterialsBuildFromTheirOwnRawItem(String name, String expectedItem) {
        JsonObject json = shipped(name).getAsJsonObject();

        assertEquals(expectedItem, json.getAsJsonObject("repair_item").get("item").getAsString(),
                name + "'s repair_item must key on its own raw item, not the storage block");

        boolean hasRawItemRow = false;
        for (JsonElement item : json.getAsJsonArray("crafting_items")) {
            JsonObject ingredient = item.getAsJsonObject().getAsJsonObject("ingredient");
            if (ingredient.has("item") && expectedItem.equals(ingredient.get("item").getAsString())) {
                assertEquals(PartBuilderRecipes.INGOT_VALUE, item.getAsJsonObject().get("value").getAsInt(),
                        name + "'s raw item row must be worth one ingot (144), matching black_quartz's gem row");
                hasRawItemRow = true;
            }
        }
        assertTrue(hasRawItemRow, name + " must list " + expectedItem + " directly in crafting_items "
                + "(issue #1069), not only its storage block");

        // Still parses cleanly through the real codec, matching #shippedMaterialsParse.
        Material.CODEC.parse(ops, json).getOrThrow();
    }

    /**
     * Issue #872's positive coverage for {@code energised_steel}: Powah's real id ({@code
     * powah:steel_energized}, the epic's table had it backwards) backs both {@code crafting_items}
     * and {@code repair_item} directly, leniently accepted by {@code Material.LENIENT_INGREDIENT_CODEC}
     * even though this mod-less test JVM has no Powah item registered under that id.
     *
     * <p>Issue #996 (D-M8-17) unblocked Powah's four remaining untagged materials -- the crystals
     * {@code crystal_blazing}/{@code crystal_niotic}/{@code crystal_spirited}/{@code crystal_nitro} --
     * the same way: #837's schema trap found they ship no per-material {@code c:gems/*} subtag at
     * all, only the flat parent {@code c:gems} tag (verified against Powah's own {@code v6.2.10}
     * tree), so {@code noShippedMaterialConditionsOnPowahsUntaggedCrystals} used to guard every
     * shipped material JSON against referencing one of those four ids at all. With every one of the
     * five now shipped on its own concrete id, that guard has nothing left to check; this
     * parameterized test is its positive replacement, one row per material.
     */
    @ParameterizedTest
    @CsvSource({
            "energised_steel,powah:steel_energized",
            "blazing_crystal,powah:crystal_blazing",
            "niotic_crystal,powah:crystal_niotic",
            "spirited_crystal,powah:crystal_spirited",
            "nitro_crystal,powah:crystal_nitro",
    })
    void powahMaterialsCarryTheirConcreteItemId(String name, String expected) {
        JsonObject json = shipped(name).getAsJsonObject();

        assertEquals(expected, json.getAsJsonObject("repair_item").get("item").getAsString());
        assertEquals(expected, json.getAsJsonArray("crafting_items").get(0).getAsJsonObject()
                .getAsJsonObject("ingredient").get("item").getAsString());

        Material.CODEC.parse(ops, json).getOrThrow();
    }

    /**
     * Issue #1103 withdrew #876's rule that no two materials may name the same trait id (maintainer
     * delegated the call, 2026-09-21). The rule forced a renamed clone per material: 29 groups of
     * byte-identical behaviour under 70 ids, fire immunity under six names, 29 ids ending in
     * {@code -ward} across eight unrelated mechanics. Its own exemption list had grown three times
     * (#876 to #1093 to #1097) to escape its own consequences.
     *
     * <p>What is left of it is the part that ever caught a real mistake: a material naming one id
     * twice. That is always a copy-paste slip, because {@code ForgeweaveTraits#resolve} de-duplicates
     * the list anyway, so the second entry is dead text in a synced payload. The other half of the
     * old guard's job -- "is this a clone of a trait that already exists?" -- moved to
     * {@code TraitDefinitionAuditTest#noTwoTraitsShareABehaviourAndItsParameters}, which compares
     * behaviour and parameters instead of names and therefore catches the clone the old rule
     * created.
     *
     * <p>Walks every shipped material JSON directly rather than the codec, the same "catch it before
     * it ships" shape {@link #noShippedMaterialConditionsOnEnderIosRemovedElectricalSteel} uses.
     */
    @Test
    void noMaterialNamesTheSameTraitIdTwice() throws Exception {
        Path materialDir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        List<String> violations = new java.util.ArrayList<>();

        try (Stream<Path> files = Files.list(materialDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String materialName = file.getFileName().toString().replace(".json", "");
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (!json.has("traits")) {
                    continue;
                }
                JsonElement traits = json.get("traits");
                java.util.List<java.util.Map.Entry<String, JsonElement>> lists = new java.util.ArrayList<>();
                if (traits.isJsonObject()) {
                    lists.addAll(traits.getAsJsonObject().entrySet());
                } else if (traits.isJsonArray()) {
                    lists.add(java.util.Map.entry("traits", traits));
                }
                for (var list : lists) {
                    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                    for (JsonElement id : list.getValue().getAsJsonArray()) {
                        if (!seen.add(id.getAsString())) {
                            violations.add(materialName + "." + list.getKey() + " names " + id.getAsString()
                                    + " twice");
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), "a material names one trait id twice, which resolve() drops "
                + "anyway -- almost certainly a copy-paste slip: " + violations);
    }

    /**
     * Issue #1031 (D-M8-21): the Eternal Ores dedupe. For every real-world metal Forgeweave already
     * shipped a Track A material for, where Eternal Ores also supplies that metal under a matching
     * {@code c:} tag, the material's own existence gate grew an {@code eternalores:<id>_ingot}
     * branch so the metal's preset -- and every recipe cast from it -- resolves with Eternal Ores as
     * the only supplying mod, not just the mod(s) it originally shipped keyed on. Walks the shipped
     * JSON directly (not the codec, which {@code neoforge:conditions} never reaches) so a future
     * edit that drops the branch fails here rather than only showing up as a silent gap in-game.
     */
    @ParameterizedTest
    @CsvSource({
            "aluminium,eternalores:aluminum_ingot", "bronze,eternalores:bronze_ingot",
            "constantan,eternalores:constantan_ingot", "electrum,eternalores:electrum_ingot",
            "graphite,eternalores:graphite_ingot", "invar,eternalores:invar_ingot",
            "iridium,eternalores:iridium_ingot", "lead,eternalores:lead_ingot",
            "nickel,eternalores:nickel_ingot", "osmium,eternalores:osmium_ingot",
            "platinum,eternalores:platinum_ingot", "silver,eternalores:silver_ingot",
            "tin,eternalores:tin_ingot", "titanium,eternalores:titanium_ingot",
            "tungsten,eternalores:tungsten_ingot", "uranium,eternalores:uranium_ingot",
            "uraninite,eternalores:uraninite_ingot",
            "quartz_enriched_iron,eternalores:quartz_enriched_iron_ingot",
            "silicon,eternalores:silicon_ingot" })
    void eternalOresDedupeMaterialsCarryTheEternalOresBranch(String name, String eoItem) {
        JsonObject json = shipped(name).getAsJsonObject();
        assertTrue(conditionNamesItem(json.getAsJsonArray("neoforge:conditions"), eoItem),
                name + "'s neoforge:conditions must name " + eoItem + " as an additional provider (issue #1031)");

        // The codec ignores the extra key -- see conditionalMaterialsCarryAWellFormedConditionsBlockAndStillParse.
        Material.CODEC.parse(ops, json).getOrThrow();
    }

    /** Same walk, over the melting and casting recipe rows the material JSON's own condition mirrors. */
    @ParameterizedTest
    @CsvSource({
            "aluminium,eternalores:aluminum_ingot", "bronze,eternalores:bronze_ingot",
            "constantan,eternalores:constantan_ingot", "electrum,eternalores:electrum_ingot",
            "graphite,eternalores:graphite_ingot", "invar,eternalores:invar_ingot",
            "iridium,eternalores:iridium_ingot", "lead,eternalores:lead_ingot",
            "nickel,eternalores:nickel_ingot", "osmium,eternalores:osmium_ingot",
            "platinum,eternalores:platinum_ingot", "silver,eternalores:silver_ingot",
            "tin,eternalores:tin_ingot", "titanium,eternalores:titanium_ingot",
            "tungsten,eternalores:tungsten_ingot", "uranium,eternalores:uranium_ingot" })
    void eternalOresDedupeIngotMeltsBackIntoItsOwnIngot(String name, String eoItem) throws Exception {
        Path castingFile = projectRoot()
                .resolve("src/main/resources/data/forgeweave/forgeweave/casting_recipe/ingot_" + name + "_eternalores.json");
        assertTrue(Files.exists(castingFile), "expected a new ingot_" + name + "_eternalores.json casting row");
        JsonObject casting = JsonParser.parseString(Files.readString(castingFile, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(eoItem, casting.getAsJsonObject("result").get("id").getAsString());
        assertTrue(conditionNamesItem(casting.getAsJsonArray("neoforge:conditions"), eoItem));
    }

    private static boolean conditionNamesItem(com.google.gson.JsonArray conditions, String item) {
        for (JsonElement entry : conditions) {
            JsonObject node = entry.getAsJsonObject();
            String type = node.get("type").getAsString();
            if (type.equals("neoforge:item_exists") && node.get("item").getAsString().equals(item)) {
                return true;
            }
            if (type.equals("neoforge:or")) {
                for (JsonElement branch : node.getAsJsonArray("values")) {
                    JsonObject branchNode = branch.getAsJsonObject();
                    if (branchNode.get("type").getAsString().equals("neoforge:item_exists")
                            && branchNode.get("item").getAsString().equals(item)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
