package dev.gkissel.forgeweave.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/**
 * Pins upstream 1.12's melting math (NOTICE.md) and the JSON shape docs/SCOPE.md M2 promises datapack
 * authors. The molten metals themselves are {@code DeferredHolder}-backed and so unavailable without
 * a running mod loader (see {@code ForgeweaveFluidsTest}), which is why every recipe parsed here
 * melts into vanilla lava; the shipped recipes are exercised for real by
 * {@code SmelteryMeltingGameTests}.
 */
class MeltingRecipeTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static MeltingRecipe parse(String json) {
        return MeltingRecipe.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
    }

    // ------------------------------------------------------------------ upstream's temperature curve

    /**
     * Upstream {@code MeltingRecipe.calcTemperature}: a full block melts at the fluid's own
     * temperature, and its {@code LOG9_2} exponent is picked so a ninth of a block lands exactly
     * halfway up the range above 300 -- which is why an ingot of a 769-degree metal wants 534.
     */
    @Test
    void theTemperatureCurveMatchesUpstream() {
        int moltenIron = 769;

        assertEquals(moltenIron, MeltingRecipe.calcTemperature(moltenIron, MeltingRecipe.VALUE_BLOCK), "a block melts at the fluid's own temperature");
        assertEquals(534, MeltingRecipe.calcTemperature(moltenIron, MeltingRecipe.VALUE_INGOT), "an ingot is a ninth of a block: halfway up");
        assertEquals(417, MeltingRecipe.calcTemperature(moltenIron, MeltingRecipe.VALUE_NUGGET), "a nugget is a ninth of that: halfway again");
        assertEquals(MeltingRecipe.AMBIENT_TEMPERATURE, MeltingRecipe.calcTemperature(200, MeltingRecipe.VALUE_INGOT),
                "a fluid colder than ambient never asks for heat");
    }

    /** Upstream {@code getUsableTemperature} and {@code updateHeatRequired}/{@code setHeatRequiredForSlot}. */
    @Test
    void heatRequiredIsTheUsableTemperatureTimesTheTimeFactor() {
        MeltingRecipe ingot = lavaRecipe(MeltingRecipe.VALUE_INGOT, 534);

        assertEquals(234, ingot.usableTemperature());
        assertEquals(234 * MeltingRecipe.TIME_FACTOR, ingot.heatRequired());
        assertEquals(5 * MeltingRecipe.TIME_FACTOR, lavaRecipe(1, 301).heatRequired(), "upstream's floor of 5 for near-ambient recipes");
    }

    // ------------------------------------------------------------------ the codec

    @Test
    void aRecipeRoundTripsThroughItsCodec() {
        MeltingRecipe recipe = lavaRecipe(MeltingRecipe.VALUE_BLOCK, 1234);

        JsonElement encoded = MeltingRecipe.CODEC.encodeStart(ops, recipe).getOrThrow();
        MeltingRecipe decoded = MeltingRecipe.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(recipe.fluid(), decoded.fluid());
        assertEquals(recipe.amount(), decoded.amount());
        assertEquals(recipe.temperature(), decoded.temperature());
        assertEquals(recipe.ore(), decoded.ore());
        assertTrue(decoded.input().test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(!decoded.input().test(new ItemStack(Items.GOLD_INGOT)));
    }

    /**
     * Issue #99's field: a JSON with no {@code ore} key -- every recipe written before #99, and any
     * third-party datapack recipe -- must still parse, defaulting to {@code false} so an unmarked
     * recipe stays 1:1 rather than picking up a yield multiplier nobody asked for.
     */
    @Test
    void oreDefaultsToFalseForDatapackCompatibility() {
        MeltingRecipe recipe = parse("""
                {"input": {"item": "minecraft:iron_ingot"}, "fluid": "minecraft:lava", "amount": 144}
                """);

        assertTrue(!recipe.ore(), "a recipe with no ore key must default to false");
    }

    @Test
    void oreCanBeMarkedTrueAndRoundTrips() {
        MeltingRecipe recipe = parse("""
                {"input": {"tag": "c:ores/iron"}, "fluid": "minecraft:lava", "amount": 144, "ore": true}
                """);

        assertTrue(recipe.ore());

        JsonElement encoded = MeltingRecipe.CODEC.encodeStart(ops, recipe).getOrThrow();
        assertTrue(MeltingRecipe.CODEC.parse(ops, encoded).getOrThrow().ore(), "ore: true must survive a codec round trip");
    }

    /** Leaving {@code temperature} out is upstream's two-argument constructor: derive it from the output fluid. */
    @Test
    void temperatureDefaultsToTheOutputFluidsCurve() {
        MeltingRecipe recipe = parse("""
                {"input": {"item": "minecraft:iron_ingot"}, "fluid": "minecraft:lava", "amount": 144}
                """);

        assertEquals(MeltingRecipe.calcTemperature(Fluids.LAVA.getFluidType().getTemperature(), 144), recipe.temperature());
    }

    /**
     * Parity audit T60: upstream bakes {@code Config.oreToIngotRatio} into {@code Material.VALUE_Ore()}
     * (ingot x ratio) before deriving {@code calcTemperature} from it; Forgeweave's {@code amount}
     * field on an ore recipe is deliberately the un-doubled raw-drop equivalent (#99), so a derived
     * (no explicit {@code temperature} key) ore-class recipe must fold the baseline doubling back in
     * before calling {@link MeltingRecipe#calcTemperature}, same as upstream's default 2.0 ratio would.
     */
    @Test
    void oreClassRecipesDeriveTemperatureFromTheDoubledAmount() {
        MeltingRecipe recipe = parse("""
                {"input": {"tag": "c:ores/iron"}, "fluid": "minecraft:lava", "amount": 144, "ore": true}
                """);

        assertEquals(MeltingRecipe.calcTemperature(Fluids.LAVA.getFluidType().getTemperature(), 288), recipe.temperature(),
                "an ore-class recipe derives its temperature from the doubled (baseline oreToIngotRatio) amount");
    }

    @Test
    void aTagInputIsAcceptedAndIsWhatMakesTheCTagLadderWork() {
        MeltingRecipe recipe = parse("""
                {"input": {"tag": "c:ingots/iron"}, "fluid": "minecraft:lava", "amount": 144}
                """);

        // Tag contents are not bound without a server, so this only pins that a tag input parses --
        // SmelteryMeltingGameTests proves the matching for real against a live tag.
        assertNotNull(recipe.input());
    }

    @Test
    void aRecipeWithoutAnAmountIsRejected() {
        DataResult<MeltingRecipe> result = MeltingRecipe.CODEC.parse(ops, JsonParser.parseString("""
                {"input": {"item": "minecraft:iron_ingot"}, "fluid": "minecraft:lava"}
                """));

        assertTrue(result.isError(), "a melting recipe with no output amount must not parse");
    }

    // ------------------------------------------------------------------ the shipped recipes

    /**
     * docs/SCOPE.md M2: "Ore blocks melt as their raw-drop equivalent" and "melting recipes hold base
     * amounts, the core multiplies" -- so the shipped iron ore recipe is one ingot's worth keyed off
     * the {@code c:} tag, with no 1.5x baked in, and marked {@code ore: true} so issue #99's core
     * multiplier applies to it at melt time.
     */
    @Test
    void theShippedIronOreRecipeHoldsTheBaseAmountAndIsTagKeyed() {
        JsonObject json = shipped("iron_ore");

        assertEquals("c:ores/iron", json.getAsJsonObject("input").get("tag").getAsString());
        assertEquals("forgeweave:molten_iron", json.get("fluid").getAsString());
        assertEquals(MeltingRecipe.VALUE_INGOT, json.get("amount").getAsInt(), "one raw iron, not the Standard Core's 1.5x of it");
        assertTrue(json.get("ore").getAsBoolean(), "an ore-class input, so issue #99's core multiplier applies");
    }

    /**
     * SCOPE.md M2: "core tier is the ONLY yield axis; ingot re-melts 1:1" -- the shipped ingot/nugget/
     * block recipes are not marked {@code ore: true} (issue #99 leaves the JSON key out entirely,
     * relying on {@link MeltingRecipe.CODEC}'s default-false), so they never pick up a core multiplier.
     */
    @Test
    void theShippedIngotRecipeRemeltsOneForOne() {
        assertEquals(MeltingRecipe.VALUE_INGOT, shipped("iron_ingot").get("amount").getAsInt());
        assertEquals(MeltingRecipe.VALUE_BLOCK, shipped("iron_block").get("amount").getAsInt());
        assertEquals(MeltingRecipe.VALUE_NUGGET, shipped("iron_nugget").get("amount").getAsInt());
        assertTrue(!shipped("iron_ingot").has("ore"), "an ingot re-melt must not be marked ore-class");
        assertTrue(!shipped("iron_block").has("ore"), "a metal storage block re-melt must not be marked ore-class");
        assertTrue(!shipped("iron_nugget").has("ore"), "a nugget re-melt must not be marked ore-class");
    }

    /**
     * The two vanilla ores whose expected un-fortuned drop is not one raw unit, straight off their
     * 1.21.1 loot tables:
     *
     * <ul>
     *   <li>{@code blocks/copper_ore} and {@code blocks/deepslate_copper_ore} drop {@code raw_copper}
     *       with {@code set_count} uniform 2..5 -- four equally likely counts averaging 3.5, so
     *       3.5 x 144 = <b>504</b>.
     *   <li>{@code blocks/nether_gold_ore} drops {@code gold_nugget} with {@code set_count} uniform
     *       2..6 -- five counts averaging 4, so 4 x 16 = <b>64</b>.
     * </ul>
     *
     * Melting one ore block therefore matches mining it and melting the raws, which is what
     * docs/SCOPE.md's "ore blocks melt as their raw-drop equivalent" asks for. Fortune is not in the
     * expectation: it is an axis melting deliberately does not have, same as silk touch.
     */
    @Test
    void oresThatDropMoreThanOneRawUnitGetAnItemKeyedOverride() {
        JsonObject copper = shipped("vanilla_copper_ore");
        assertEquals(504, copper.get("amount").getAsInt(), "3.5 raw copper x 144 mB");
        assertEquals("forgeweave:molten_copper", copper.get("fluid").getAsString());
        assertEquals(2, copper.getAsJsonArray("input").size(), "stone and deepslate variants in one recipe");
        assertTrue(copper.get("ore").getAsBoolean(), "an ore block override is still ore-class");

        JsonObject netherGold = shipped("vanilla_nether_gold_ore");
        assertEquals(64, netherGold.get("amount").getAsInt(), "4 gold nuggets x 16 mB");
        assertEquals("forgeweave:molten_gold", netherGold.get("fluid").getAsString());
        assertTrue(netherGold.get("ore").getAsBoolean(), "an ore block override is still ore-class");
    }

    /**
     * And the tie-break that lets those overrides win: {@link MeltingRecipe#find} sorts item inputs
     * ahead of tag inputs, so a modded copper ore still falls back on {@code c:ores/copper}'s 144
     * while {@code minecraft:copper_ore} takes its own 504.
     */
    @Test
    void itemInputsAreMoreSpecificThanTagInputs() {
        assertTrue(parse("""
                {"input": {"tag": "c:ores/copper"}, "fluid": "minecraft:lava", "amount": 144}
                """).isTagInput(), "a c: tag recipe is the family default");
        assertTrue(!parse("""
                {"input": {"item": "minecraft:copper_ore"}, "fluid": "minecraft:lava", "amount": 504}
                """).isTagInput(), "a per-item override is not");
        assertTrue(!parse("""
                {"input": [{"item": "minecraft:copper_ore"}, {"item": "minecraft:deepslate_copper_ore"}],
                 "fluid": "minecraft:lava", "amount": 504}
                """).isTagInput(), "nor is a list of items, which is how the shipped override is written");
    }

    /**
     * Issue #440 (parity audit T8): upstream 1.12's {@code TinkerSmeltery.java:377-386} melts stone
     * and cobblestone into seared stone at {@code Material.VALUE_SearedMaterial} = 72 mB -- the audit's
     * own number for stone/cobblestone is right, but its claim of "grout (24)" is not: upstream's
     * grout row ({@code TinkerSmeltery.java:438}) passes 24 only as {@code MeltingRecipe.forAmount}'s
     * speed-shaping {@code timeAmount}, and the fluid stack it actually registers is sized off the
     * {@code RecipeMatch}'s own {@code amountMatched}, which is 72 -- exactly like stone and
     * cobblestone. None of the three is ore-class (a building-material block, not a mined ore).
     */
    @Test
    void theShippedStoneCobblestoneAndGroutAllMeltIntoSearedStoneAtTheSameAmount() {
        for (String name : new String[] {"stone", "cobblestone", "grout"}) {
            JsonObject json = shipped(name);
            assertEquals("forgeweave:molten_seared_stone", json.get("fluid").getAsString(), name + " melts into seared stone");
            assertEquals(MeltingRecipe.VALUE_SEARED_MATERIAL, json.get("amount").getAsInt(),
                    name + " melts at Material.VALUE_SearedMaterial, not the audit's claimed 24 for grout");
            assertTrue(!json.has("ore"), name + " is a building-material block, not ore-class");
        }
    }

    /**
     * T41 (#472): the emerald ore/gem/block trio, ported from upstream {@code TinkerSmeltery} (lines
     * 471-476) -- registered inline rather than through {@code registerOredictMeltingCasting}, so
     * unlike the metal rows above there is no ingot/nugget/plate/gear form, only ore, gem and block.
     */
    @Test
    void theShippedEmeraldOreRecipeHoldsTheBaseGemAmountAndIsOreClass() {
        JsonObject json = shipped("emerald_ore");

        assertEquals("c:ores/emerald", json.getAsJsonObject("input").get("tag").getAsString());
        assertEquals("forgeweave:molten_emerald", json.get("fluid").getAsString());
        assertEquals(666, json.get("amount").getAsInt(), "Material.VALUE_Gem, not the Standard Core's 1.5x of it");
        assertTrue(json.get("ore").getAsBoolean(), "an ore-class input, so issue #99's core multiplier applies");
    }

    @Test
    void theShippedEmeraldGemRecipeRemeltsOneForOne() {
        JsonObject json = shipped("emerald");

        assertEquals("c:gems/emerald", json.getAsJsonObject("input").get("tag").getAsString());
        assertEquals(666, json.get("amount").getAsInt(), "Material.VALUE_Gem");
        assertTrue(!json.has("ore"), "the refined gem re-melt must not be marked ore-class");
    }

    /**
     * T41 (#472): {@code emerald_block.json} pins an explicit {@code "temperature": 999} -- the
     * fluid's own -- rather than letting {@link MeltingRecipe#calcTemperature} derive one. That
     * formula assumes a "full block" sits at {@link MeltingRecipe#VALUE_BLOCK} = 1296 mB, true for
     * every metal but not a gem: {@code Material.VALUE_Gem * 9} = 5994 mB would otherwise derive a
     * temperature hotter than the fluid can ever reach, an un-meltable recipe under any fuel this
     * pack ships. Exercised through the real codec (fluid substituted for lava, since the molten
     * emerald {@code DeferredHolder} needs a running mod loader -- see the class javadoc) to prove
     * the override is actually honoured, not just present in the JSON.
     */
    @Test
    void theShippedEmeraldBlockRecipeIsNotOreClassAndPinsAnExplicitTemperature() {
        JsonObject json = shipped("emerald_block");

        assertEquals("c:storage_blocks/emerald", json.getAsJsonObject("input").get("tag").getAsString());
        assertEquals(5994, json.get("amount").getAsInt(), "Material.VALUE_Gem * 9");
        assertEquals(999, json.get("temperature").getAsInt(), "pinned at the fluid's own temperature");
        assertTrue(!json.has("ore"), "a storage block re-melt must not be marked ore-class");

        int derivedInstead = MeltingRecipe.calcTemperature(999, 5994);
        assertTrue(derivedInstead > 999,
                "sanity check: without the pin, calcTemperature's 1296 mB assumption overshoots the fluid's own temperature (" + derivedInstead + ")");

        MeltingRecipe decoded = parse("""
                {"input": {"tag": "c:storage_blocks/emerald"}, "fluid": "minecraft:lava", "amount": 5994, "temperature": 999}
                """);
        assertEquals(999, decoded.temperature(), "the codec must honour the pinned temperature rather than re-deriving it");
        assertEquals(699 * MeltingRecipe.TIME_FACTOR, decoded.heatRequired());
    }

    // ------------------------------------------------------------------ #502 (T71 parity audit)

    /**
     * Upstream {@code TinkerSmeltery#registerMeltingCasting}'s ice/snow rows: each sets an explicit
     * temperature just above {@link MeltingRecipe#AMBIENT_TEMPERATURE} rather than deriving one from
     * water's own (colder) {@code FluidType} temperature, so a smeltery still needs real heat to melt
     * them. {@code minecraft:water} is a real vanilla fluid, unlike Forgeweave's molten fluids, so
     * these round-trip through the real codec with no mod loader.
     */
    @Test
    void iceMeltsIntoOneBucketOfWaterAtUpstreamsExplicitTemperature() {
        MeltingRecipe recipe = parse(shipped("ice").toString());

        assertEquals(Fluids.WATER, recipe.fluid());
        assertEquals(1000, recipe.amount(), "upstream's Fluid.BUCKET_VOLUME");
        assertEquals(305, recipe.temperature(), "upstream's explicit ice temperature");
    }

    @Test
    void packedIceMeltsIntoTwoBucketsOfWaterHotterThanPlainIce() {
        MeltingRecipe recipe = parse(shipped("packed_ice").toString());

        assertEquals(Fluids.WATER, recipe.fluid());
        assertEquals(2000, recipe.amount(), "upstream's Fluid.BUCKET_VOLUME * 2");
        assertEquals(310, recipe.temperature(), "upstream's explicit packed ice temperature");
    }

    @Test
    void snowMeltsIntoOneBucketOfWaterLikeIce() {
        MeltingRecipe recipe = parse(shipped("snow").toString());

        assertEquals(Fluids.WATER, recipe.fluid());
        assertEquals(1000, recipe.amount());
        assertEquals(305, recipe.temperature());
    }

    @Test
    void snowballMeltsIntoAnEighthOfABucketAtTheLowestTemperature() {
        MeltingRecipe recipe = parse(shipped("snowball").toString());

        assertEquals(Fluids.WATER, recipe.fluid());
        assertEquals(125, recipe.amount(), "upstream's Fluid.BUCKET_VOLUME / 8");
        assertEquals(301, recipe.temperature(), "upstream's lowest explicit temperature, one degree above ambient");
    }

    /**
     * Upstream {@code TinkerSmeltery}: "melt all the dirt into mud" -- one dirt block (any of 1.12's
     * three {@code Blocks.DIRT} metadata variants, split into their own modern block ids here) melts
     * for {@code Material.VALUE_BrickBlock} (576 mB), the same base amount molten clay's own block
     * recipe uses.
     */
    @Test
    void theShippedDirtRecipeCoversAllThreeVariantsAtTheBrickBlockValue() {
        JsonObject json = shipped("dirt");

        assertEquals("forgeweave:molten_dirt", json.get("fluid").getAsString());
        assertEquals(576, json.get("amount").getAsInt(), "Material.VALUE_BrickBlock, same base as molten clay's own block recipe");
        assertEquals(3, json.getAsJsonArray("input").size(), "dirt, coarse dirt and podzol -- 1.12's Blocks.DIRT metas");
    }

    /**
     * Upstream {@code TinkerRegistry.registerTableCasting(TinkerCommons.mudBrick, ...)} re-melts:
     * SCOPE.md M2's "core tier is the ONLY yield axis; ingot re-melts 1:1" applies to mud brick's own
     * ingot-equivalent (144) and block-equivalent (576) the same way it does to every metal.
     */
    @Test
    void theShippedMudBrickRecipesRemeltOneForOne() {
        assertEquals(144, shipped("mud_brick").get("amount").getAsInt());
        assertEquals(576, shipped("mud_brick_block").get("amount").getAsInt());
        assertEquals("forgeweave:molten_dirt", shipped("mud_brick").get("fluid").getAsString());
        assertEquals("forgeweave:molten_dirt", shipped("mud_brick_block").get("fluid").getAsString());
    }

    // ------------------------------------------------------------------ #473 (T42 parity audit)

    /**
     * Upstream {@code TinkerSmeltery}'s glass block: one {@code MeltingRecipe(RecipeMatch.of("sand",
     * Material.VALUE_Glass), TinkerFluids.glass)} plus the {@code addKnownOreFluid} pair for
     * {@code blockGlass} ({@code VALUE_Glass}) and {@code paneGlass} ({@code VALUE_Glass * 6 / 16}).
     * None of the three is ore-class -- sand and glass are building materials, not mined ore -- and
     * none pins a temperature, exactly as upstream's two-argument constructor derives one.
     */
    @Test
    void theShippedGlassMeltingRowsMatchUpstreamsAmountsAndTags() {
        JsonObject sand = shipped("sand");
        assertEquals("c:sands", sand.getAsJsonObject("input").get("tag").getAsString(), "upstream's \"sand\" ore dict, red sand included");
        assertEquals("forgeweave:molten_glass", sand.get("fluid").getAsString());
        assertEquals(1000, sand.get("amount").getAsInt(), "Material.VALUE_Glass");

        JsonObject glass = shipped("glass");
        assertEquals("c:glass_blocks", glass.getAsJsonObject("input").get("tag").getAsString(), "upstream's \"blockGlass\" ore dict");
        assertEquals("forgeweave:molten_glass", glass.get("fluid").getAsString());
        assertEquals(1000, glass.get("amount").getAsInt(), "Material.VALUE_Glass");

        JsonObject pane = shipped("glass_pane");
        assertEquals("c:glass_panes", pane.getAsJsonObject("input").get("tag").getAsString(), "upstream's \"paneGlass\" ore dict");
        assertEquals("forgeweave:molten_glass", pane.get("fluid").getAsString());
        assertEquals(375, pane.get("amount").getAsInt(), "Material.VALUE_Glass * 6 / 16");

        for (String name : new String[] {"sand", "glass", "glass_pane"}) {
            assertTrue(!shipped(name).has("ore"), name + " is a building material, not ore-class");
            assertTrue(!shipped(name).has("temperature"), name + " derives its temperature, as upstream's two-argument constructor does");
        }
    }

    // ------------------------------------------------------------------ #439 (T7 parity audit)

    /**
     * T7 (#439) -- upstream 1.12 melts every vanilla item whose crafting recipe is metal-only. It
     * does that at runtime ({@code TinkerSmeltery#registerRecipeOredictMelting}, lines 746-873) by
     * walking the crafting registry, skipping the ingredients on {@code Config.oredictMeltingIgnore}
     * (redstone dust, planks, sticks, string, chest), summing the ore-dictionary value of the rest,
     * and dividing by the recipe's output count. Here that scan is done once, at authoring time, and
     * its results ship as datapack rows -- which is what upstream itself did when it moved to modern
     * Minecraft. Each entry is {@code file, item, mB}, and every amount is the exact value 1.12's
     * scan (or one of its four hand-written rows at {@code TinkerSmeltery.java:392-399}) produces.
     */
    private static final String[][] IRON_CRAFTED = {
            // 1 ingot: shovel and shield (their planks/sticks are on the ignore list), plus two of
            // upstream's hand-written rail rows -- their redstone torch / pressure plate is why the
            // scan cannot derive them.
            {"iron_ingot_1", "minecraft:iron_shovel", "144"},
            {"iron_ingot_1", "minecraft:shield", "144"},
            {"iron_ingot_1", "minecraft:activator_rail", "144"},
            {"iron_ingot_1", "minecraft:detector_rail", "144"},
            // 2 ingots (the iron door is 6 ingots for 3 doors)
            {"iron_ingot_2", "minecraft:iron_sword", "288"},
            {"iron_ingot_2", "minecraft:iron_hoe", "288"},
            {"iron_ingot_2", "minecraft:shears", "288"},
            {"iron_ingot_2", "minecraft:heavy_weighted_pressure_plate", "288"},
            {"iron_ingot_2", "minecraft:iron_door", "288"},
            // 3 ingots
            {"iron_ingot_3", "minecraft:iron_pickaxe", "432"},
            {"iron_ingot_3", "minecraft:iron_axe", "432"},
            {"iron_ingot_3", "minecraft:bucket", "432"},
            // 4 ingots (the compass ignores its redstone; horse armor is a hand-written row)
            {"iron_ingot_4", "minecraft:iron_boots", "576"},
            {"iron_ingot_4", "minecraft:iron_trapdoor", "576"},
            {"iron_ingot_4", "minecraft:compass", "576"},
            {"iron_ingot_4", "minecraft:iron_horse_armor", "576"},
            // 5 ingots (the hopper ignores its chest)
            {"iron_ingot_5", "minecraft:iron_helmet", "720"},
            {"iron_ingot_5", "minecraft:minecart", "720"},
            {"iron_ingot_5", "minecraft:hopper", "720"},
            // 7 and 8 ingots
            {"iron_ingot_7", "minecraft:iron_leggings", "1008"},
            {"iron_ingot_7", "minecraft:cauldron", "1008"},
            {"iron_ingot_8", "minecraft:iron_chestplate", "1152"},
            // 6 ingots split over 16 outputs
            {"iron_rail", "minecraft:rail", "54"},
            {"iron_rail", "minecraft:iron_bars", "54"},
            // 1 ingot split over 2 outputs
            {"iron_tripwire_hook", "minecraft:tripwire_hook", "72"},
            // 3 blocks + 4 ingots
            {"iron_anvil", "minecraft:anvil", "4464"},
            {"iron_anvil", "minecraft:chipped_anvil", "4464"},
            {"iron_anvil", "minecraft:damaged_anvil", "4464"},
    };

    /** The gold half of {@link #IRON_CRAFTED}; the powered rail is one of upstream's hand-written rows. */
    private static final String[][] GOLD_CRAFTED = {
            {"gold_ingot_1", "minecraft:golden_shovel", "144"},
            {"gold_ingot_1", "minecraft:powered_rail", "144"},
            {"gold_ingot_2", "minecraft:golden_sword", "288"},
            {"gold_ingot_2", "minecraft:golden_hoe", "288"},
            {"gold_ingot_2", "minecraft:light_weighted_pressure_plate", "288"},
            {"gold_ingot_3", "minecraft:golden_pickaxe", "432"},
            {"gold_ingot_3", "minecraft:golden_axe", "432"},
            {"gold_ingot_4", "minecraft:golden_boots", "576"},
            {"gold_ingot_4", "minecraft:clock", "576"},
            {"gold_ingot_4", "minecraft:golden_horse_armor", "576"},
            {"gold_ingot_5", "minecraft:golden_helmet", "720"},
            {"gold_ingot_7", "minecraft:golden_leggings", "1008"},
            {"gold_ingot_8", "minecraft:golden_chestplate", "1152"},
    };

    @Test
    void everyVanillaIronCraftedItemMeltsForWhatItsCraftingRecipeCost() {
        for (String[] row : IRON_CRAFTED) {
            assertMeltsFor(row[0], row[1], Integer.parseInt(row[2]), "forgeweave:molten_iron");
        }
    }

    @Test
    void everyVanillaGoldCraftedItemMeltsForWhatItsCraftingRecipeCost() {
        for (String[] row : GOLD_CRAFTED) {
            assertMeltsFor(row[0], row[1], Integer.parseInt(row[2]), "forgeweave:molten_gold");
        }
    }

    /**
     * A crafted item is never ore-class: re-melting a bucket returns exactly the three ingots it cost
     * under any core tier, the same rule {@link #theShippedIngotRecipeRemeltsOneForOne} pins for
     * ingots ("core tier is the ONLY yield axis; ingot re-melts 1:1").
     */
    @Test
    void noVanillaCraftedMetalRowIsOreClass() {
        for (String[][] table : new String[][][] {IRON_CRAFTED, GOLD_CRAFTED}) {
            for (String[] row : table) {
                assertTrue(!shipped(row[0]).has("ore"), row[0] + " melts a crafted item, so it is not ore-class");
            }
        }
    }

    /**
     * The two rows where 1.12 and the 1.20 branch disagree, decided for 1.12 per the parity default:
     * rails and bars are 6 ingots split over 16 outputs -- upstream's own
     * {@code Material.VALUE_Ingot * 6 / 16} = 54 mB, where 1.20 rounds them down to 3 nuggets (48) --
     * and horse armor is {@code Material.VALUE_Ingot * 4}, where 1.20 charges 7 ingots.
     */
    @Test
    void railsAndHorseArmorFollowTheTwelveNumbersNotTheTwentyOnes() {
        assertEquals(MeltingRecipe.VALUE_INGOT * 6 / 16, shipped("iron_rail").get("amount").getAsInt());
        assertEquals(MeltingRecipe.VALUE_INGOT * 4, shipped("iron_ingot_4").get("amount").getAsInt());
        assertEquals(MeltingRecipe.VALUE_INGOT * 4, shipped("gold_ingot_4").get("amount").getAsInt());
    }

    /**
     * Upstream's scan aborts a recipe the moment one ingredient neither melts nor sits on the ignore
     * list, so a golden apple (its apple), flint and steel (its flint) and a piston (its cobblestone,
     * which is not one of the metal ore-dictionary entries {@code registerOredictMeltingCasting}
     * feeds the scanner) get no row at all -- and chainmail armor has no 1.12 crafting recipe to
     * scan. Pinned so nobody "completes the set" later without a maintainer decision.
     */
    @Test
    void itemsUpstreamsScanAbortsOnGetNoRow() {
        for (String[][] table : new String[][][] {IRON_CRAFTED, GOLD_CRAFTED}) {
            for (String[] row : table) {
                String inputs = shipped(row[0]).get("input").toString();
                for (String skipped : new String[] {"golden_apple", "flint_and_steel", "piston", "chainmail"}) {
                    assertTrue(!inputs.contains(skipped), row[0] + " must not melt " + skipped);
                }
            }
        }
    }

    private static void assertMeltsFor(String file, String item, int amount, String fluid) {
        JsonObject json = shipped(file);
        assertEquals(fluid, json.get("fluid").getAsString(), file + " melts into " + fluid);
        assertEquals(amount, json.get("amount").getAsInt(), file + " melts for " + amount + " mB");
        assertTrue(json.get("input").toString().contains('"' + item + '"'), file + " must list " + item);
    }

    private static JsonObject shipped(String name) {
        String path = "/data/forgeweave/forgeweave/melting_recipe/" + name + ".json";
        try (InputStream in = MeltingRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped melting recipe: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static MeltingRecipe lavaRecipe(int amount, int temperature) {
        return parse("""
                {"input": {"item": "minecraft:iron_ingot"}, "fluid": "minecraft:lava",
                 "amount": %d, "temperature": %d}
                """.formatted(amount, temperature));
    }
}
