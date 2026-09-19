package dev.gkissel.forgeweave.api.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierBehaviors;
import dev.gkissel.forgeweave.modifier.ModifierDefinition;

/**
 * Issue #1065's gates for the Java modifier seam, the same four the trait side gets: a registered
 * modifier is reachable from {@link ForgeweaveModifiers#get}, a second claim on an id fails, a
 * claim after the window has closed fails, and a built-in id is the one collision the built-in
 * wins instead.
 */
class ModifierRegistryTest {

    private static RegistryOps<JsonElement> ops;

    private static final ResourceLocation ADDON = ResourceLocation.fromNamespaceAndPath("testaddon", "sturdy");
    private static final ResourceLocation ADDON_BEHAVIOR =
            ResourceLocation.fromNamespaceAndPath("testaddon", "flat_slots");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /**
     * Both sides, because the bootstrapped test run loads the real mod: {@code Forgeweave}'s common
     * setup has already shut the window by the time the first test method runs.
     */
    @BeforeEach
    @AfterEach
    void emptyTheRegistry() {
        ModifierRegistry.resetForTests();
    }

    /** A partner mod's modifier, reachable through the lookup every modifier hook already calls. */
    @Test
    void aRegisteredModifierIsReachableFromTheModifierLookup() {
        Modifier modifier = new Modifier() { };
        ModifierRegistry.register(ADDON, modifier);

        assertSame(modifier, ModifierRegistry.modifier(ADDON));
        assertSame(modifier, ForgeweaveModifiers.get(ADDON));
    }

    /** Two mods claiming one id is a packaging bug, not a precedence question. */
    @Test
    void aSecondClaimOnAnIdFails() {
        ModifierRegistry.register(ADDON, new Modifier() { });

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ModifierRegistry.register(ADDON, new Modifier() { }));
        assertTrue(thrown.getMessage().contains(ADDON.toString()), thrown.getMessage());
    }

    /** Past the window there is nothing left to read the registration, so it throws instead. */
    @Test
    void registeringAfterTheWindowHasClosedFails() {
        ModifierRegistry.closeRegistration();

        assertThrows(IllegalStateException.class, () -> ModifierRegistry.register(ADDON, new Modifier() { }));
        assertThrows(IllegalStateException.class,
                () -> ModifierRegistry.registerBehavior(ADDON_BEHAVIOR, MapCodec.unit(new Modifier() { })));
    }

    /** The one collision that does not throw: the built-in behavior keeps the id. */
    @Test
    void aBuiltInIdWins() {
        ResourceLocation builtIn = ResourceLocation.fromNamespaceAndPath("forgeweave", "reinforced");
        Modifier shipped = ForgeweaveModifiers.get(builtIn);
        assertNotNull(shipped, "forgeweave:reinforced should be a built-in modifier");

        ModifierRegistry.register(builtIn, new Modifier() { });

        assertSame(shipped, ForgeweaveModifiers.get(builtIn));
    }

    /** A registered behavior type is what a {@code modifier_definition} file may then name. */
    @Test
    void aRegisteredBehaviorIsReachableFromADatapackDefinition() {
        ModifierRegistry.registerBehavior(ADDON_BEHAVIOR,
                Codec.INT.fieldOf("slots").xmap(FlatSlots::new, FlatSlots::slots));
        assertTrue(ModifierBehaviors.ids().contains(ADDON_BEHAVIOR), "the behavior joins the discoverable ids");

        JsonElement json = JsonParser.parseString("{\"behavior\":\"testaddon:flat_slots\",\"slots\":3}");
        ModifierDefinition definition = ModifierDefinition.CODEC.parse(ops, json).getOrThrow();

        assertEquals(ADDON_BEHAVIOR, definition.behavior());
        assertEquals(3, assertInstanceOf(FlatSlots.class, definition.modifier()).slots());
        assertEquals(json, ModifierDefinition.CODEC.encodeStart(ops, definition).getOrThrow());
    }

    /** An unregistered behavior id still fails the parse rather than decoding to a no-op modifier. */
    @Test
    void anUnregisteredBehaviorIdStillFails() {
        assertTrue(ModifierDefinition.CODEC
                .parse(ops, JsonParser.parseString("{\"behavior\":\"testaddon:flat_slots\",\"slots\":3}"))
                .error()
                .isPresent());
    }

    /**
     * A partner mod's parameterized behavior. It implements {@link Modifier} directly rather than
     * {@code ModifierLibrary.Behavior}, which is the point: an addon reaches the definition registry
     * without naming anything outside the api package.
     */
    private record FlatSlots(int slots) implements Modifier {
        @Override
        public int bonusSlots(int level) {
            return slots;
        }
    }
}
