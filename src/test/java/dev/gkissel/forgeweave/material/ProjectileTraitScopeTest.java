package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.item.PartItem;

/**
 * The {@code projectile} trait scope (issue #653, parity audit T17): upstream's one
 * PROJECTILE-scoped trait is endstone's {@code enderference}
 * ({@code TinkerMaterials:264, endstone.addTrait(enderference, PROJECTILE)}), which exists so the
 * arrow head's two-scope {@code PartMaterialType(HEAD, PROJECTILE)} read keeps enderference on
 * arrow heads where the head-scoped {@code alien} list would otherwise occlude it
 * ({@code Material#getAllTraitsForStats} falls back to the default list only when a stat has no
 * list of its own).
 */
class ProjectileTraitScopeTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = ProjectileTraitScopeTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped material JSON: " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    /** {@code TinkerMaterials:262-264}: alien on the head, enderference everywhere else and PROJECTILE-scoped. */
    @Test
    void endstoneScopesEnderferenceToProjectiles() {
        Material.Traits traits = shipped("endstone").traits();
        assertEquals(List.of(id("alien")), traits.forPart(PartItem.Kind.HEAD));
        assertEquals(List.of(id("enderference")), traits.forPart(PartItem.Kind.PROJECTILE));
        assertEquals(List.of(id("enderference")), traits.forPart(PartItem.Kind.HANDLE));
    }

    /** A material with no projectile-scoped list falls back to its general list, upstream's own rule. */
    @Test
    void projectileScopeFallsBackToGeneral() {
        Material.Traits traits = new Material.Traits(List.of(id("magnetic")), List.of(id("magnetic2")));
        assertEquals(List.of(id("magnetic")), traits.forPart(PartItem.Kind.PROJECTILE));
    }

    /** The dummy PROJECTILE stat rides HEAD, upstream {@code TinkerRegistry#addMaterialStats:260-262}. */
    @Test
    void everyHeadMaterialHasProjectileStatsForFree() {
        assertTrue(shipped("endstone").hasStatsFor(PartItem.Kind.PROJECTILE));
        assertTrue(!shipped("feather").hasStatsFor(PartItem.Kind.PROJECTILE),
                "a headless material carries no PROJECTILE stat either");
    }
}
