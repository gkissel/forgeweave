package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #973's unit gates for the {@code modifier_definition} codec, the sibling of
 * {@code TraitBehaviorsTest}: every behavior round-trips through its {@link ModifierBehaviors}
 * entry, an unknown {@code behavior} id and a missing parameter both fail loudly, a definition whose
 * {@code neoforge:conditions} fail decodes to nothing, and a tool carrying a pack-defined id still
 * saves as {@code id + level} and still decodes with the definition gone (ADR-0004 item 2).
 */
class ModifierBehaviorsTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /**
     * One definition per registered behavior, written without default-valued optional fields so the
     * re-encoded JSON is the input JSON. The numbers are the shipped modifiers' own, so each row also
     * reads as the datapack spelling of a Java modifier: diamond, sharpness's cap, knockback
     * resistance, emerald, reinforced, extra_slot, resonant, wind burst, netherite, Width++,
     * knockback, shulking, smite, fiery, necrotic, thorns and fire protection in turn.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"behavior\":\"forgeweave:stat_bonus\",\"stat\":\"durability\",\"flat\":500.0}",
            "{\"behavior\":\"forgeweave:stat_bonus\",\"units_per_level\":72,\"stat\":\"attack_damage\","
                    + "\"per_level\":0.25,\"fraction_of_base\":0.2,\"minimum\":1.0}",
            "{\"behavior\":\"forgeweave:attribute_bonus\",\"attribute\":\"knockback_resistance\",\"per_level\":0.1}",
            "{\"behavior\":\"forgeweave:attribute_bonus\",\"attribute\":\"armor_toughness\",\"flat\":1.0}",
            "{\"behavior\":\"forgeweave:attribute_bonus\",\"attribute\":\"submerged_mining_speed\",\"flat\":0.8}",
            "{\"behavior\":\"forgeweave:attribute_bonus\",\"attribute\":\"block_interaction_range\",\"per_level\":1.0}",
            "{\"behavior\":\"forgeweave:tool_tier\",\"bump\":1,\"cap\":3}",
            "{\"behavior\":\"forgeweave:tool_tier\",\"at_least\":4}",
            "{\"behavior\":\"forgeweave:durability_negation\",\"chance_per_level\":0.2}",
            "{\"behavior\":\"forgeweave:bonus_slots\",\"slots\":\"first_level_only\",\"flat\":1,\"per_level\":1}",
            "{\"behavior\":\"forgeweave:bonus_experience\",\"fraction_per_level\":0.5}",
            "{\"behavior\":\"forgeweave:grant_enchantment\",\"enchantment\":\"minecraft:wind_burst\",\"max_level\":3}",
            "{\"behavior\":\"forgeweave:grant_enchantment\",\"enchantment\":\"minecraft:silk_touch\",\"level\":1}",
            "{\"behavior\":\"forgeweave:fire_resistant\",\"slots\":\"none\"}",
            "{\"behavior\":\"forgeweave:aoe_expansion\",\"slots\":\"first_level_only\",\"tools\":\"harvest\","
                    + "\"axis\":\"width\"}",
            "{\"behavior\":\"forgeweave:knockback_on_hit\",\"units_per_level\":10,\"per_unit\":0.1}",
            "{\"behavior\":\"forgeweave:effect_on_hit\",\"units_per_level\":50,\"effect\":\"minecraft:levitation\","
                    + "\"duration_per_unit\":0.5,\"duration_offset\":10}",
            "{\"behavior\":\"forgeweave:effect_on_hit\",\"effect\":\"minecraft:slowness\",\"amplifier\":1,"
                    + "\"duration_per_unit\":20.0}",
            "{\"behavior\":\"forgeweave:bonus_damage_vs\",\"units_per_level\":24,"
                    + "\"entities\":\"minecraft:sensitive_to_smite\",\"damage_per_level\":7.0}",
            "{\"behavior\":\"forgeweave:ignite_on_hit\",\"units_per_level\":25,\"seconds_per_unit\":0.125,"
                    + "\"seconds_offset\":1,\"damage_per_unit\":0.066667}",
            "{\"behavior\":\"forgeweave:lifesteal_on_hit\",\"fraction_per_level\":0.1}",
            "{\"behavior\":\"forgeweave:thorns_counter\",\"units_per_level\":25,\"tools\":\"armor\","
                    + "\"chance_per_level\":0.15,\"constant_damage\":1.0,\"random_damage\":3.0}",
            "{\"behavior\":\"forgeweave:protection\",\"units_per_level\":5,\"tools\":\"armor_or_held\","
                    + "\"per_level\":5.0,\"damage_type\":\"forgeweave:fire_protection\"}",
            "{\"behavior\":\"forgeweave:protection\",\"units_per_level\":5,\"tools\":\"armor_or_held\","
                    + "\"per_level\":3.0,\"damage_type\":\"forgeweave:melee_protection\",\"direct_only\":true}" })
    void everyBehaviorRoundTrips(String json) {
        JsonElement input = JsonParser.parseString(json);
        ModifierDefinition definition = ModifierDefinition.CODEC.parse(ops, input).getOrThrow();
        assertEquals(input.getAsJsonObject().get("behavior").getAsString(), definition.behavior().toString());

        JsonElement encoded = ModifierDefinition.CODEC.encodeStart(ops, definition).getOrThrow();
        assertEquals(canonical(input), canonical(encoded));
    }

    /** Every registered behavior id has a row above -- a new entry without a round-trip case fails here. */
    @Test
    void everyRegisteredBehaviorIsCoveredAbove() {
        assertEquals(16, ModifierBehaviors.ids().size(),
                "add a round-trip case for the new behavior: " + ModifierBehaviors.ids());
    }

    /**
     * Gson compares a parsed {@code 0.05} and an encoded {@code float} by double value, and
     * {@code (double) 0.05f != 0.05}; rounding every number to six places makes the comparison about
     * the schema rather than float widening. {@code TraitBehaviorsTest}'s own helper.
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

    /**
     * The shared {@code Application} fields answer the {@link Modifier} hooks the Tool Station reads,
     * so a pack-defined modifier is gated and charged exactly like a Java-registered one.
     */
    @Test
    void theSharedApplicationFieldsDriveSlotsAndToolGates() {
        Modifier perLevel = parse("{\"behavior\":\"forgeweave:stat_bonus\",\"units_per_level\":50,"
                + "\"stat\":\"mining_speed\",\"per_level\":0.5}");
        assertEquals(50, perLevel.unitsPerLevel());
        assertEquals(2, perLevel.occupiedSlots(51), "one slot per displayed level, upstream's MultiAspect");
        assertFalse(perLevel.utility());

        Modifier firstOnly = parse("{\"behavior\":\"forgeweave:bonus_slots\",\"slots\":\"first_level_only\","
                + "\"flat\":1,\"per_level\":1}");
        assertEquals(1, firstOnly.occupiedSlots(5), "upstream's FreeFirstModifierAspect charges once");
        assertEquals(6, firstOnly.bonusSlots(5), "extra_slot's net +1 per level (Modifier#bonusSlots's trap)");

        Modifier utility = parse("{\"behavior\":\"forgeweave:fire_resistant\",\"slots\":\"none\","
                + "\"tools\":\"helmet\"}");
        assertEquals(0, utility.occupiedSlots(3));
        assertTrue(utility.utility());
        assertTrue(utility.helmetOnly() && utility.armorOnly(), "a helmet gate is an armor gate too");

        Modifier protections = parse("{\"behavior\":\"forgeweave:protection\",\"tools\":\"armor_or_held\","
                + "\"per_level\":5.0}");
        assertTrue(protections.armorOnly() && protections.alsoHeld(), "issue #729's pair");

        Modifier luckLike = parse("{\"behavior\":\"forgeweave:bonus_experience\",\"tools\":\"not_launcher\","
                + "\"fraction_per_level\":0.5}");
        assertFalse(luckLike.appliesToLaunchers());
    }

    /**
     * The extraction is faithful: a definition carrying a shipped modifier's own numbers answers its
     * hooks with the shipped modifier's own answers.
     */
    @Test
    void aDefinitionReproducesTheJavaModifierItWasExtractedFrom() {
        Modifier diamond = parse("{\"behavior\":\"forgeweave:stat_bonus\",\"stat\":\"durability\",\"flat\":500.0}");
        assertEquals(ForgeweaveModifiers.DIAMOND.durability(1, 300, 300), diamond.durability(1, 300, 300));

        Modifier emerald = parse("{\"behavior\":\"forgeweave:stat_bonus\",\"stat\":\"durability\","
                + "\"fraction_of_base\":0.5}");
        assertEquals(ForgeweaveModifiers.EMERALD.durability(1, 300, 300), emerald.durability(1, 300, 300));

        Modifier silky = parse("{\"behavior\":\"forgeweave:stat_bonus\",\"stat\":\"mining_speed\",\"flat\":-3.0,"
                + "\"minimum\":1.0}");
        assertEquals(ForgeweaveModifiers.SILKY.miningSpeed(1, 2.0F), silky.miningSpeed(1, 2.0F, 2.0F),
                "silky's penalty is floored at 1");

        Modifier reinforced = parse("{\"behavior\":\"forgeweave:durability_negation\",\"chance_per_level\":0.2}");
        assertEquals(ForgeweaveModifiers.REINFORCED.durabilityNegationChance(5),
                reinforced.durabilityNegationChance(5), "level 5 is a certainty, not 1.2");

        Modifier fireProtection = parse("{\"behavior\":\"forgeweave:protection\",\"units_per_level\":5,"
                + "\"per_level\":5.0,\"damage_type\":\"forgeweave:fire_protection\"}");
        Protection seam = assertInstanceOf(Protection.class, fireProtection.combatSeam(10).orElseThrow());
        assertEquals(1.0F, seam.perLevel(), "5 per level over 5 units is 1 per unit");
        assertEquals(10, seam.level());
    }

    @Test
    void anUnknownBehaviorIdFailsLoudly() {
        DataResult<ModifierDefinition> result = ModifierDefinition.CODEC.parse(ops,
                JsonParser.parseString("{\"behavior\":\"somepack:no_such_behavior\",\"flat\":1.0}"));
        assertTrue(result.isError());
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("Unknown modifier behavior 'somepack:no_such_behavior'"), message);
        assertTrue(message.contains("forgeweave:stat_bonus"), "the error should list the known ids: " + message);
    }

    @Test
    void aMissingRequiredParameterFailsLoudly() {
        DataResult<ModifierDefinition> result = ModifierDefinition.CODEC.parse(ops,
                JsonParser.parseString("{\"behavior\":\"forgeweave:stat_bonus\",\"flat\":1.0}"));
        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("stat"), result.error().orElseThrow().message());
    }

    /** An unknown enum value lists the alternatives rather than failing blank. */
    @Test
    void anUnknownEnumValueListsTheAlternatives() {
        DataResult<ModifierDefinition> result = ModifierDefinition.CODEC.parse(ops,
                JsonParser.parseString("{\"behavior\":\"forgeweave:stat_bonus\",\"stat\":\"luck\"}"));
        assertTrue(result.isError());
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("mining_speed"), "the error should list the known stats: " + message);
    }

    /**
     * The registry loader decodes every element through {@link ConditionalOps#createConditionalCodec};
     * a failing condition yields an empty optional, i.e. the definition is never registered.
     */
    @Test
    void aDefinitionFailingItsConditionsDecodesToNothing() {
        ConditionalOps<JsonElement> conditional = new ConditionalOps<>(ops, ICondition.IContext.EMPTY);
        String body = "\"behavior\":\"forgeweave:stat_bonus\",\"stat\":\"durability\",\"flat\":500.0}";

        Optional<ModifierDefinition> failing = ConditionalOps.createConditionalCodec(ModifierDefinition.CODEC)
                .parse(conditional, JsonParser.parseString("{\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}],"
                        + body))
                .getOrThrow();
        assertTrue(failing.isEmpty(), "a failing condition must drop the definition");

        Optional<ModifierDefinition> passing = ConditionalOps.createConditionalCodec(ModifierDefinition.CODEC)
                .parse(conditional, JsonParser.parseString("{\"neoforge:conditions\":[{\"type\":\"neoforge:true\"}],"
                        + body))
                .getOrThrow();
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "stat_bonus")),
                passing.map(ModifierDefinition::behavior));
    }

    /**
     * ADR-0004 item 2, the rule this registry does not move: a tool carrying a pack-defined modifier
     * saves the id and the level and nothing else, and it still decodes -- and still holds its slot --
     * on an install where no datapack defines that id, which is exactly what an uninstalled pack looks
     * like. Nothing in this test registers a definition, so the id is absent by construction.
     */
    @Test
    void aPackDefinedModifierSavesAsIdAndLevelAndDecodesWithoutItsDefinition() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mypack", "frosty");

        JsonElement encoded = ModifierEntry.CODEC.encodeStart(JsonOps.INSTANCE, new ModifierEntry(id, 2)).getOrThrow();
        assertEquals(JsonParser.parseString("{\"id\":\"mypack:frosty\",\"level\":2}"), encoded,
                "a pack-defined modifier is id + level, never a reference to its definition");

        List<ModifierEntry> entries = ModifierEntry.CODEC.listOf()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("[{\"id\":\"mypack:frosty\",\"level\":2}]"))
                .getOrThrow();
        ItemStack tool = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), entries);

        assertEquals(entries, ForgeweaveModifiers.of(tool), "the entry must survive with no definition loaded");
        assertNull(ForgeweaveModifiers.get(id), "and resolve to no behavior, like any unknown id");
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS - 1, ForgeweaveModifiers.freeSlots(tool),
                "while keeping the slot the player spent on it");
    }

    private static Modifier parse(String json) {
        return ModifierDefinition.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow().modifier();
    }
}
