package dev.gkissel.forgeweave.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Issue #98's codec and the ratio math behind {@link AlloyRecipe#matches}, plus the three shipped
 * ratios as JSON. The molten metals are {@code DeferredHolder}-backed and so unavailable without a
 * running mod loader (see {@code ForgeweaveFluidsTest}), which is why the parsed recipes here alloy
 * vanilla water and lava; the shipped recipes run for real in {@code SmelteryAlloyGameTests}.
 */
class AlloyRecipeTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static AlloyRecipe parse(String json) {
        return AlloyRecipe.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
    }

    /** Water + lava, 2:1 to 1, standing in for a real alloy so the math is checkable without the mod loader. */
    private static AlloyRecipe testRecipe() {
        return parse("""
                {"inputs": [{"fluid": "minecraft:water", "amount": 2}, {"fluid": "minecraft:lava", "amount": 1}],
                 "result": {"fluid": "minecraft:flowing_water", "amount": 1}}
                """);
    }

    // ------------------------------------------------------------------ the codec

    @Test
    void aRecipeRoundTripsThroughItsCodec() {
        AlloyRecipe recipe = testRecipe();

        JsonElement encoded = AlloyRecipe.CODEC.encodeStart(ops, recipe).getOrThrow();
        AlloyRecipe decoded = AlloyRecipe.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(2, decoded.inputs().size());
        assertEquals(Fluids.WATER, decoded.inputs().get(0).getFluid());
        assertEquals(2, decoded.inputs().get(0).getAmount());
        assertEquals(Fluids.LAVA, decoded.inputs().get(1).getFluid());
        assertEquals(1, decoded.inputs().get(1).getAmount());
        assertEquals(Fluids.FLOWING_WATER, decoded.result().getFluid());
        assertEquals(1, decoded.result().getAmount());
        assertEquals(0, decoded.priority(), "priority defaults to 0 when the JSON omits it");
    }

    /**
     * #291: an explicit {@code priority} is how a recipe claims a contested fluid ahead of another
     * recipe that also wants it -- lower resolves first, so this is netherite's own value against
     * rose gold's default 0 (see the shipped {@code netherite.json}).
     */
    @Test
    void aRecipeCanDeclareAnExplicitPriority() {
        AlloyRecipe recipe = parse("""
                {"inputs": [{"fluid": "minecraft:water", "amount": 2}, {"fluid": "minecraft:lava", "amount": 1}],
                 "result": {"fluid": "minecraft:flowing_water", "amount": 1}, "priority": 1}
                """);

        assertEquals(1, recipe.priority());
    }

    /** Upstream's constructor check: fewer than two inputs is a mixing recipe that mixes nothing. */
    @Test
    void aRecipeWithFewerThanTwoInputsIsRejected() {
        DataResult<AlloyRecipe> result = AlloyRecipe.CODEC.parse(ops, JsonParser.parseString("""
                {"inputs": [{"fluid": "minecraft:water", "amount": 1}],
                 "result": {"fluid": "minecraft:lava", "amount": 1}}
                """));

        assertTrue(result.isError(), "a one-input alloy recipe must not parse");
    }

    /**
     * Upstream's other constructor check, and the one that actually matters here: a recipe consuming
     * its own output alloys forever, and {@code SmelteryControllerBlockEntity}'s pass runs to
     * exhaustion rather than once per tick.
     */
    @Test
    void aRecipeWhoseResultIsAlsoAnInputIsRejected() {
        DataResult<AlloyRecipe> result = AlloyRecipe.CODEC.parse(ops, JsonParser.parseString("""
                {"inputs": [{"fluid": "minecraft:water", "amount": 1}, {"fluid": "minecraft:lava", "amount": 1}],
                 "result": {"fluid": "minecraft:water", "amount": 2}}
                """));

        assertTrue(result.isError(), "an alloy recipe that feeds itself must not parse");
    }

    @Test
    void aZeroAmountIsRejected() {
        assertTrue(AlloyRecipe.CODEC.parse(ops, JsonParser.parseString("""
                {"inputs": [{"fluid": "minecraft:water", "amount": 0}, {"fluid": "minecraft:lava", "amount": 1}],
                 "result": {"fluid": "minecraft:flowing_water", "amount": 1}}
                """)).isError(), "an input needing none of a fluid must not parse");
    }

    // ------------------------------------------------------------------ upstream's matches()

    /** Upstream {@code AlloyRecipe#matches}: the smallest whole multiple any one input allows. */
    @Test
    void matchesReturnsTheLargestWholeMultipleAvailable() {
        AlloyRecipe recipe = testRecipe();

        assertEquals(1, recipe.matches(tank(2, 1)), "exactly one application's worth");
        assertEquals(3, recipe.matches(tank(6, 3)), "three of each");
        assertEquals(3, recipe.matches(tank(7, 3)), "the scarcer input decides, and the remainder stays put");
        assertEquals(2, recipe.matches(tank(6, 2)), "and so does the other one");
    }

    @Test
    void matchesReturnsZeroWhenAnInputIsMissingOrShort() {
        AlloyRecipe recipe = testRecipe();

        assertEquals(0, recipe.matches(tank(2, 0)), "an absent input alloys nothing");
        assertEquals(0, recipe.matches(tank(1, 1)), "and neither does less than one application's worth");
        assertEquals(0, recipe.matches(List.of()), "nor does an empty smeltery");
        assertEquals(0, recipe.matches(List.of(new FluidStack(Fluids.LAVA, 1000))),
                "a tank of fluids the recipe never asked for alloys nothing");
    }

    @Test
    void inputVolumeIsOneApplicationsWorth() {
        assertEquals(3, testRecipe().inputVolume());
    }

    // ------------------------------------------------------------------ the shipped ratios

    /**
     * Upstream {@code TinkerSmeltery#registerAlloys}: {@code registerAlloy(manyullyn 2, cobalt 2,
     * ardite 2)}, commented there as "1 ingot cobalt + 1 ingot ardite = 1 ingot manyullyn" -- so one
     * ingot of each in the tank yields exactly one ingot of manyullyn, and the ratio is written in
     * upstream's own minimal units so a partial melt still alloys the part it can.
     */
    @Test
    void manyullynShipsUpstreamsOneToOneRatio() {
        JsonObject json = shipped("manyullyn");

        assertEquals("forgeweave:molten_cobalt", input(json, 0).get("fluid").getAsString());
        assertEquals(2, input(json, 0).get("amount").getAsInt());
        assertEquals("forgeweave:molten_ardite", input(json, 1).get("fluid").getAsString());
        assertEquals(2, input(json, 1).get("amount").getAsInt());
        assertEquals("forgeweave:molten_manyullyn", json.getAsJsonObject("result").get("fluid").getAsString());
        assertEquals(2, json.getAsJsonObject("result").get("amount").getAsInt());
    }

    /**
     * No upstream counterpart (1.12 predates netherite). The ratio is vanilla's own smithing
     * economics: 4 netherite scrap + 4 gold ingots = 1 netherite ingot, i.e. 576 mB + 576 mB = 144 mB,
     * reduced to its 4:4:1 minimal form. Alloying is therefore neither a shortcut around nor a tax on
     * the vanilla recipe.
     */
    @Test
    void netheritePreservesVanillaSmithingEconomics() {
        JsonObject json = shipped("netherite");

        assertEquals("forgeweave:molten_netherite_scrap", input(json, 0).get("fluid").getAsString());
        assertEquals("forgeweave:molten_gold", input(json, 1).get("fluid").getAsString());
        assertEquals(4, input(json, 0).get("amount").getAsInt());
        assertEquals(4, input(json, 1).get("amount").getAsInt());
        assertEquals(1, json.getAsJsonObject("result").get("amount").getAsInt());
        assertEquals(1, json.get("priority").getAsInt(),
                "#291: netherite must resolve after rose gold's default priority 0 when both want the tank's gold");

        // The same ratio read at the scale a player sees: four ingots of each in, one ingot out.
        AlloyRecipe recipe = parse("""
                {"inputs": [{"fluid": "minecraft:water", "amount": 4}, {"fluid": "minecraft:lava", "amount": 4}],
                 "result": {"fluid": "minecraft:flowing_water", "amount": 1}}
                """);
        assertEquals(MeltingRecipe.VALUE_INGOT,
                recipe.matches(tank(4 * MeltingRecipe.VALUE_INGOT, 4 * MeltingRecipe.VALUE_INGOT))
                        * recipe.result().getAmount(),
                "576 mB scrap + 576 mB gold is one netherite ingot, exactly as vanilla smithing");
    }

    /**
     * Also no upstream counterpart. Shaped after upstream's own volume-preserving two-metal alloys
     * (electrum: 1 gold + 1 silver = 2 electrum), so one ingot of copper and one of gold make two
     * ingots of rose gold. Recorded as Forgeweave's, not derived.
     */
    @Test
    void roseGoldIsAVolumePreservingOneToOne() {
        JsonObject json = shipped("rose_gold");

        assertEquals("forgeweave:molten_copper", input(json, 0).get("fluid").getAsString());
        assertEquals("forgeweave:molten_gold", input(json, 1).get("fluid").getAsString());
        assertEquals(1, input(json, 0).get("amount").getAsInt());
        assertEquals(1, input(json, 1).get("amount").getAsInt());
        assertEquals("forgeweave:molten_rose_gold", json.getAsJsonObject("result").get("fluid").getAsString());
        assertEquals(2, json.getAsJsonObject("result").get("amount").getAsInt(), "two in, two out");
    }

    private static JsonObject input(JsonObject recipe, int index) {
        return recipe.getAsJsonArray("inputs").get(index).getAsJsonObject();
    }

    private static JsonObject shipped(String name) {
        String path = "/data/forgeweave/forgeweave/alloy_recipe/" + name + ".json";
        try (InputStream in = AlloyRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped alloy recipe: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** A smeltery holding {@code water} mB of water and {@code lava} mB of lava, in tank order. */
    private static List<FluidStack> tank(int water, int lava) {
        return java.util.stream.Stream.of(new FluidStack(Fluids.WATER, water), new FluidStack(Fluids.LAVA, lava))
                .filter(fluid -> !fluid.isEmpty())
                .toList();
    }
}
