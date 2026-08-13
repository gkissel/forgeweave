package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;

/**
 * Guards the material registry-sync payload (issue #227; docs/SCOPE.md performance budget: "the
 * material sync packet stays trivially small even at M6 scale"). {@code Material.REGISTRY} is a
 * datapack registry whose network codec is {@link Material#CODEC} itself (see
 * {@code Forgeweave#registerDataPackRegistries}), and vanilla registry sync encodes each entry with
 * that codec over {@code NbtOps} and writes the resulting tag to the packet buffer
 * ({@code RegistrySynchronization#packEntry}). This test mirrors that path for every shipped
 * material JSON and asserts the summed buffer size stays under {@link #SYNC_BUDGET_BYTES}.
 */
class MaterialSyncSizeTest {

    /**
     * The whole-roster budget. Measured at 4,701 bytes for the 11 pre-M3.2 materials (~430 bytes
     * each), so the M3.2 roster's ~33 materials land around 14 KB; x5 the measured size covers that
     * 3x count growth plus headroom for richer entries (more crafting items, per-part traits)
     * while still catching a payload that grows an order of magnitude unnoticed. Rounded to 24 KB.
     */
    private static final int SYNC_BUDGET_BYTES = 24 * 1024;

    private static RegistryOps<JsonElement> jsonOps;
    private static RegistryOps<Tag> nbtOps;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries);
        nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries);
    }

    @Test
    void shippedMaterialsFitTheSyncBudget() throws Exception {
        Path materialDir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        int materials = 0;

        try (Stream<Path> files = Files.list(materialDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                materials++;
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                Material material = Material.CODEC.parse(jsonOps, json).getOrThrow();
                buf.writeNbt(Material.CODEC.encodeStart(nbtOps, material).getOrThrow());
            }
        }

        // Non-vacuity: an empty walk encoding zero bytes would prove nothing.
        assertTrue(materials >= 11, "expected at least the 11 pre-M3.2 materials, walked only " + materials);
        assertTrue(buf.readableBytes() <= SYNC_BUDGET_BYTES,
                materials + " materials encode to " + buf.readableBytes() + " bytes of registry-sync payload, "
                        + "over the " + SYNC_BUDGET_BYTES + "-byte budget -- either a material grew far beyond "
                        + "the roster's norm or the budget needs a deliberate revisit (SCOPE.md performance budgets)");
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
