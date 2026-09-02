package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

import dev.gkissel.forgeweave.combat.ConditionalSeam;
import dev.gkissel.forgeweave.combat.EffectOnHit;
import dev.gkissel.forgeweave.combat.FlatBonusDamage;
import dev.gkissel.forgeweave.combat.HitCondition;

/**
 * Issue #832's unit gates for the {@code trait_definition} codec: every behaviour round-trips
 * through its {@link TraitBehaviors} entry, an unknown {@code behavior} id fails loudly, and a
 * definition whose {@code neoforge:conditions} fail decodes to nothing through the same
 * {@link ConditionalOps} path the real registry loader uses.
 */
class TraitBehaviorsTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /**
     * One definition per registered behaviour, written without default-valued optional fields so
     * the re-encoded JSON is the input JSON. Fields that carry a gate prove the shared {@code
     * condition}/{@code chance} pair survives too.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"behavior\":\"forgeweave:damage_scales_with\",\"source\":\"target_max_health\",\"coefficient\":0.05,\"cap\":6.0}",
            "{\"behavior\":\"forgeweave:bonus_damage_vs\",\"condition\":\"armored\",\"amount\":2.0}",
            "{\"behavior\":\"forgeweave:bonus_damage_vs\",\"condition\":\"full_charge\",\"amount\":1.5}",
            "{\"behavior\":\"forgeweave:crit_multiplier_bonus\",\"extra\":0.5}",
            "{\"behavior\":\"forgeweave:effect_on_hit\",\"effect\":\"minecraft:wither\",\"duration\":60,\"stacking_cap\":2}",
            "{\"behavior\":\"forgeweave:effect_on_hit\",\"chance\":0.25,\"effect\":\"minecraft:slowness\",\"duration\":20,\"amplifier\":3}",
            "{\"behavior\":\"forgeweave:effect_on_self_on_hit\",\"condition\":\"full_charge\",\"effect\":\"minecraft:speed\",\"duration\":60,\"amplifier\":1}",
            "{\"behavior\":\"forgeweave:strip_effects\",\"condition\":\"full_charge\",\"chance\":0.5,\"count\":2}",
            "{\"behavior\":\"forgeweave:reduce_target_healing\",\"fraction\":0.5,\"duration\":100}",
            "{\"behavior\":\"forgeweave:shorten_invulnerability\",\"ticks\":10}",
            "{\"behavior\":\"forgeweave:lifesteal\",\"fraction\":0.15,\"cap\":4.0}",
            "{\"behavior\":\"forgeweave:chain_arc\",\"condition\":\"full_charge\",\"chance\":0.35,\"range\":3.0,\"damage_fraction\":0.5,\"max_targets\":2}",
            "{\"behavior\":\"forgeweave:lightning_on_hit\",\"condition\":\"wielder_full_health\"}",
            "{\"behavior\":\"forgeweave:self_repair_when\",\"condition\":\"night\",\"ticks_per_point\":400}",
            "{\"behavior\":\"forgeweave:cascading_break\"}",
            "{\"behavior\":\"forgeweave:cascading_break\",\"blocks\":\"minecraft:logs\"}",
            "{\"behavior\":\"forgeweave:fertilize_on_use\",\"durability_cost\":1,\"chance\":0.5}",
            "{\"behavior\":\"forgeweave:extra_modifier_slots\",\"count\":2}",
            "{\"behavior\":\"forgeweave:energized\",\"capacity\":32000,\"energy_per_durability_point\":40.0}",
            "{\"behavior\":\"forgeweave:solar_recharge\",\"rate_per_tick\":2}",
            "{\"behavior\":\"forgeweave:kinetic_charge\",\"fraction\":5.0}" })
    void everyBehaviorRoundTrips(String json) {
        JsonElement input = JsonParser.parseString(json);
        TraitDefinition definition = TraitDefinition.CODEC.parse(ops, input).getOrThrow();
        assertEquals(input.getAsJsonObject().get("behavior").getAsString(), definition.behavior().toString());

        JsonElement encoded = TraitDefinition.CODEC.encodeStart(ops, definition).getOrThrow();
        assertEquals(canonical(input), canonical(encoded));
    }

    /** Every registered behaviour id has a row above -- a new entry without a round-trip case fails here. */
    @Test
    void everyRegisteredBehaviorIsCoveredAbove() {
        assertEquals(18, TraitBehaviors.ids().size(), "add a round-trip case for the new behaviour: " + TraitBehaviors.ids());
    }

    /**
     * Gson compares a parsed {@code 0.05} and an encoded {@code float} by double value, and
     * {@code (double) 0.05f != 0.05}; rounding every number to six places makes the comparison
     * about the schema rather than float widening.
     */
    private static JsonElement canonical(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject copy = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(entry -> copy.add(entry.getKey(), canonical(entry.getValue())));
            return copy;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return new JsonPrimitive(Math.round(element.getAsDouble() * 1_000_000.0) / 1_000_000.0);
        }
        return element;
    }

    @Test
    void aGatedSeamIsWrappedInAConditionalSeamAndABareOneIsNot() {
        TraitDefinition gated = parse("{\"behavior\":\"forgeweave:effect_on_hit\",\"condition\":\"full_charge\","
                + "\"chance\":0.5,\"effect\":\"minecraft:poison\",\"duration\":100}");
        TraitBehaviors.SeamTrait seam = assertInstanceOf(TraitBehaviors.SeamTrait.class, gated.trait());
        assertEquals(new ConditionalSeam(HitCondition.FULL_CHARGE, 0.5F, new EffectOnHit(MobEffects.POISON, 100, 0, 0)),
                seam.gated());

        TraitDefinition bare = parse("{\"behavior\":\"forgeweave:bonus_damage_vs\",\"amount\":2.0}");
        assertEquals(new FlatBonusDamage(2.0F), ((TraitBehaviors.SeamTrait) bare.trait()).gated());
    }

    @Test
    void nonSeamBehaviorsBuildTheLibraryClassDirectly() {
        assertEquals(2, parse("{\"behavior\":\"forgeweave:extra_modifier_slots\",\"count\":2}").trait().bonusSlots());
        assertEquals(new SelfRepairWhen(SelfRepairCondition.NIGHT, 400),
                parse("{\"behavior\":\"forgeweave:self_repair_when\",\"condition\":\"night\",\"ticks_per_point\":400}").trait());

        TraitBehaviors.CascadingBreakDefinition cascade = assertInstanceOf(TraitBehaviors.CascadingBreakDefinition.class,
                parse("{\"behavior\":\"forgeweave:cascading_break\"}").trait());
        assertTrue(cascade.delegate().blockPredicate().test(Blocks.SAND.defaultBlockState()),
                "no blocks tag means vanilla's FallingBlock marker, which sand carries");
        assertTrue(!cascade.delegate().blockPredicate().test(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void anUnknownBehaviorIdFailsLoudly() {
        DataResult<TraitDefinition> result = TraitDefinition.CODEC.parse(ops,
                JsonParser.parseString("{\"behavior\":\"somepack:no_such_behavior\",\"amount\":1.0}"));
        assertTrue(result.isError());
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("Unknown trait behavior 'somepack:no_such_behavior'"), message);
        assertTrue(message.contains("forgeweave:effect_on_hit"), "the error should list the known ids: " + message);
    }

    @Test
    void aMissingRequiredParameterFailsLoudly() {
        DataResult<TraitDefinition> result = TraitDefinition.CODEC.parse(ops,
                JsonParser.parseString("{\"behavior\":\"forgeweave:effect_on_hit\",\"effect\":\"minecraft:poison\"}"));
        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("duration"), result.error().orElseThrow().message());
    }

    /**
     * The registry loader decodes every element through {@link ConditionalOps#createConditionalCodec};
     * a failing condition yields an empty optional, i.e. the definition is never registered.
     */
    @Test
    void aDefinitionFailingItsConditionsDecodesToNothing() {
        ConditionalOps<JsonElement> conditional = new ConditionalOps<>(ops, ICondition.IContext.EMPTY);
        String body = "\"behavior\":\"forgeweave:effect_on_hit\",\"effect\":\"minecraft:poison\",\"duration\":100}";

        Optional<TraitDefinition> failing = ConditionalOps.createConditionalCodec(TraitDefinition.CODEC)
                .parse(conditional, JsonParser.parseString("{\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}]," + body))
                .getOrThrow();
        assertTrue(failing.isEmpty(), "a failing condition must drop the definition");

        Optional<TraitDefinition> passing = ConditionalOps.createConditionalCodec(TraitDefinition.CODEC)
                .parse(conditional, JsonParser.parseString("{\"neoforge:conditions\":[{\"type\":\"neoforge:true\"}]," + body))
                .getOrThrow();
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "effect_on_hit")),
                passing.map(TraitDefinition::behavior));
    }

    private static TraitDefinition parse(String json) {
        return TraitDefinition.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
    }
}
