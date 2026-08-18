package dev.gkissel.forgeweave.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;

import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Issue #482 (parity audit T51): upstream 1.12's five heart-effect particles reach 1.21 as five
 * registered particle types, and a particle type on 1.21 is only as good as the three files behind
 * it -- the registration, the {@code assets/forgeweave/particles/<id>.json} definition the particle
 * atlas reads, and the sprite that definition names. Miss any one and the type still registers, the
 * server still sends the packet, and the client shows nothing at all (a missing definition is a
 * startup log line, not a crash) -- which is exactly how a heart burst rots unnoticed.
 *
 * <p>So this walks the registry rather than a hand list and asserts the whole chain per type, plus
 * the geometry of the upstream-derived sprite ({@code particles.png}'s 8x8 cells).
 */
class ForgeweaveParticlesTest {

    private static final Path PARTICLE_DEFINITIONS = Path.of("src/main/resources/assets/forgeweave/particles");
    private static final Path PARTICLE_TEXTURES =
            Path.of("src/main/resources/assets/forgeweave/textures/particle");

    /** Upstream {@code ParticleEffect.Type}'s five, in its own declaration order. */
    private static final List<String> UPSTREAM_TYPES =
            List.of("heart_fire", "heart_cactus", "heart_electro", "heart_blood", "heart_armor");

    @Test
    void everyUpstreamHeartTypeIsRegistered() {
        assertEquals(UPSTREAM_TYPES,
                ForgeweaveParticles.HEARTS.stream().map(holder -> holder.getId().getPath()).toList(),
                "the registered heart particles no longer match upstream ParticleEffect.Type");
        assertTrue(ForgeweaveParticles.HEARTS.stream()
                        .allMatch(holder -> "forgeweave".equals(holder.getId().getNamespace())),
                "a heart particle escaped the forgeweave namespace");
    }

    @Test
    void everyRegisteredParticleTypeHasADefinitionAndSprite() throws IOException {
        Path root = projectRoot();
        for (DeferredHolder<ParticleType<?>, SimpleParticleType> heart : ForgeweaveParticles.HEARTS) {
            Path definition = root.resolve(PARTICLE_DEFINITIONS).resolve(heart.getId().getPath() + ".json");
            assertTrue(Files.isRegularFile(definition), () -> "missing particle definition: " + definition);

            JsonArray textures = JsonParser.parseString(Files.readString(definition))
                    .getAsJsonObject().getAsJsonArray("textures");
            assertNotNull(textures, () -> definition + " names no textures");
            assertEquals(1, textures.size(), () -> definition + " should name exactly one sprite");

            String sprite = textures.get(0).getAsString();
            assertTrue(sprite.startsWith("forgeweave:derived/"),
                    () -> definition + " names " + sprite + ", but every heart sprite is derived from"
                            + " upstream's particles.png and belongs under textures/particle/derived/");
            Path png = root.resolve(PARTICLE_TEXTURES)
                    .resolve(sprite.substring("forgeweave:".length()) + ".png");
            assertTrue(Files.isRegularFile(png), () -> "missing particle sprite: " + png
                    + " -- run scripts/derive_particle_art.py and commit its output");
            assertEquals(List.of(8, 8), pngSize(png),
                    () -> png + " is not an 8x8 cell of upstream's particles.png");
        }
    }

    /** Width and height straight out of the PNG's IHDR, so the test needs no image library. */
    private static List<Integer> pngSize(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png)) {
            byte[] header = in.readNBytes(24);
            assertEquals(24, header.length, () -> png + " is not a PNG");
            return List.of(intAt(header, 16), intAt(header, 20));
        }
    }

    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }
}
