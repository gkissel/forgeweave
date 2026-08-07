package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FastColor;

import dev.gkissel.forgeweave.material.Material;

/**
 * Regression for the part-tint bug (#8): {@code Material.color()} is a bare 0xRRGGBB with alpha 0,
 * so the color must be forced opaque before use as an item tint or the rendered layer is fully
 * transparent (see {@code ForgeweaveItemColors#materialTint}).
 */
class ForgeweaveItemColorsTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = ForgeweaveItemColorsTest.class.getResourceAsStream(path)) {
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "wood", "stone", "flint", "bone" })
    void tintIsFullyOpaqueForEveryShippedMaterial(String name) {
        int rawColor = shipped(name).color().getValue();
        int tint = FastColor.ARGB32.opaque(rawColor);

        assertEquals(0xFF, FastColor.ARGB32.alpha(tint), name + ": tint must be fully opaque or the part renders invisible");
    }
}
