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
