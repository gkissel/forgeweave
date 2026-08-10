package dev.gkissel.forgeweave.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.minecraft.world.level.material.Fluids;

/**
 * Pins upstream 1.12's {@code TinkerSmeltery.registerSmelteryFuel(new FluidStack(LAVA, 50), 100)}
 * (NOTICE.md) and the JSON shape docs/SCOPE.md M2 promises datapack authors. The shipped {@code
 * lava.json} fuel is exercised for real, against the actual fuel-consumption math, by {@code
 * SmelteryFuelGameTests}.
 */
class SmelteryFuelTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static SmelteryFuel parse(String json) {
        return SmelteryFuel.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void aFuelRoundTripsThroughItsCodec() {
        SmelteryFuel fuel = parse("""
                {"fluid": "minecraft:lava", "amount": 50, "duration": 100, "temperature": 1234}
                """);

        JsonElement encoded = SmelteryFuel.CODEC.encodeStart(ops, fuel).getOrThrow();
        SmelteryFuel decoded = SmelteryFuel.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(fuel.fluid(), decoded.fluid());
        assertEquals(fuel.amount(), decoded.amount());
        assertEquals(fuel.duration(), decoded.duration());
        assertEquals(fuel.temperature(), decoded.temperature());
    }

    /** Leaving {@code temperature} out derives it from the fluid's own {@code FluidType}, same as {@link MeltingRecipe}'s optional field. */
    @Test
    void temperatureDefaultsToTheFluidsOwnTemperature() {
        SmelteryFuel fuel = parse("""
                {"fluid": "minecraft:lava", "amount": 50, "duration": 100}
                """);

        assertEquals(Fluids.LAVA.getFluidType().getTemperature(), fuel.temperature());
    }

    @Test
    void aFuelWithoutAnAmountIsRejected() {
        DataResult<SmelteryFuel> result = SmelteryFuel.CODEC.parse(ops, JsonParser.parseString("""
                {"fluid": "minecraft:lava", "duration": 100}
                """));

        assertTrue(result.isError(), "a fuel with no drain amount must not parse");
    }

    @Test
    void aFuelWithoutADurationIsRejected() {
        DataResult<SmelteryFuel> result = SmelteryFuel.CODEC.parse(ops, JsonParser.parseString("""
                {"fluid": "minecraft:lava", "amount": 50}
                """));

        assertTrue(result.isError(), "a fuel with no burn duration must not parse");
    }

    /**
     * docs/SCOPE.md M2: "lava is the only fuel registered in M2" -- pinned against upstream's own
     * {@code registerSmelteryFuel(new FluidStack(FluidRegistry.LAVA, 50), 100)}: 50 mB per burn cycle,
     * a cycle lasting 100 melt ticks (upstream's own {@code fuel} unit, which -- like Forgeweave's
     * melt tick -- only ever advances once every four real ticks).
     */
    @Test
    void theShippedLavaFuelMatchesTheCloneConstants() {
        JsonObject json = shipped("lava");

        assertEquals("minecraft:lava", json.get("fluid").getAsString());
        assertEquals(50, json.get("amount").getAsInt(), "clone's registered FluidStack amount");
        assertEquals(100, json.get("duration").getAsInt(), "clone's registered fuel duration");
        assertTrue(!json.has("temperature"), "lava needs no override, its own FluidType is already 1300");
    }

    private static JsonObject shipped(String name) {
        String path = "/data/forgeweave/forgeweave/smeltery_fuel/" + name + ".json";
        try (InputStream in = SmelteryFuelTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped smeltery fuel: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
