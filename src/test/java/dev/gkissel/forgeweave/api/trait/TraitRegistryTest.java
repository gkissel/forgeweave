package dev.gkissel.forgeweave.api.trait;

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

import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.TraitBehaviors;
import dev.gkissel.forgeweave.trait.TraitDefinition;

/**
 * Issue #1065's gates for the Java trait seam: what a partner mod registers is reachable from the
 * same lookups a built-in trait is, a second claim on an id fails, a claim after the window has
 * closed fails, and a built-in id is the one collision the built-in wins instead.
 */
class TraitRegistryTest {

    private static RegistryOps<JsonElement> ops;

    private static final ResourceLocation ADDON = ResourceLocation.fromNamespaceAndPath("testaddon", "frosty");
    private static final ResourceLocation ADDON_BEHAVIOR =
            ResourceLocation.fromNamespaceAndPath("testaddon", "flat_bonus");

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
        TraitRegistry.resetForTests();
    }

    /** A partner mod's trait, reachable through the lookup every trait hook already calls. */
    @Test
    void aRegisteredTraitIsReachableFromTheTraitLookup() {
        Trait trait = new Trait() { };
        TraitRegistry.register(ADDON, trait);

        assertSame(trait, TraitRegistry.trait(ADDON));
        assertSame(trait, ForgeweaveTraits.lookup(ADDON));
    }

    /**
     * The KubeJS toggle governs script traits and nothing else (#968, split by #1065). The lookup
     * order is what enforces that: a Java registration is answered before {@code compat.kubejsTraits}
     * is read at all, so the same id claimed by both sources resolves to the Java one whatever the
     * toggle says.
     */
    @Test
    void aRegisteredTraitIsAnsweredBeforeTheKubeJsToggleIsRead() {
        Trait scripted = new Trait() { };
        Trait fromJava = new Trait() { };
        ForgeweaveTraits.registerScripted(ADDON, scripted);
        TraitRegistry.register(ADDON, fromJava);

        assertSame(fromJava, ForgeweaveTraits.lookup(ADDON));
    }

    /** Two mods claiming one id is a packaging bug, not a precedence question. */
    @Test
    void aSecondClaimOnAnIdFails() {
        TraitRegistry.register(ADDON, new Trait() { });

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> TraitRegistry.register(ADDON, new Trait() { }));
        assertTrue(thrown.getMessage().contains(ADDON.toString()), thrown.getMessage());
    }

    /** Past the window there is nothing left to read the registration, so it throws instead. */
    @Test
    void registeringAfterTheWindowHasClosedFails() {
        TraitRegistry.closeRegistration();

        assertThrows(IllegalStateException.class, () -> TraitRegistry.register(ADDON, new Trait() { }));
        assertThrows(IllegalStateException.class,
                () -> TraitRegistry.registerBehavior(ADDON_BEHAVIOR, MapCodec.unit(new Trait() { })));
    }

    /** The one collision that does not throw: the built-in behaviour keeps the id. */
    @Test
    void aBuiltInIdWins() {
        ResourceLocation builtIn = ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological");
        Trait shipped = ForgeweaveTraits.lookup(builtIn);
        assertNotNull(shipped, "forgeweave:ecological should be a built-in trait");

        Trait shadow = new Trait() { };
        TraitRegistry.register(builtIn, shadow);

        assertSame(shipped, ForgeweaveTraits.lookup(builtIn));
    }

    /** A registered behaviour type is what a {@code trait_definition} file may then name. */
    @Test
    void aRegisteredBehaviorIsReachableFromADatapackDefinition() {
        TraitRegistry.registerBehavior(ADDON_BEHAVIOR,
                Codec.FLOAT.fieldOf("amount").xmap(Chilling::new, Chilling::amount));
        assertTrue(TraitBehaviors.ids().contains(ADDON_BEHAVIOR), "the behaviour joins the discoverable ids");

        JsonElement json = JsonParser.parseString(
                "{\"behavior\":\"testaddon:flat_bonus\",\"amount\":2.5}");
        TraitDefinition definition = TraitDefinition.CODEC.parse(ops, json).getOrThrow();

        assertEquals(ADDON_BEHAVIOR, definition.behavior());
        assertEquals(2.5F, assertInstanceOf(Chilling.class, definition.trait()).amount());
        assertEquals(json, TraitDefinition.CODEC.encodeStart(ops, definition).getOrThrow());
    }

    /** An unregistered behaviour id still fails the parse rather than decoding to a no-op trait. */
    @Test
    void anUnregisteredBehaviorIdStillFails() {
        assertTrue(TraitDefinition.CODEC
                .parse(ops, JsonParser.parseString("{\"behavior\":\"testaddon:flat_bonus\",\"amount\":2.5}"))
                .error()
                .isPresent());
    }

    /** A partner mod's parameterized behaviour, standing in for whatever an addon would ship. */
    private record Chilling(float amount) implements Trait { }
}
