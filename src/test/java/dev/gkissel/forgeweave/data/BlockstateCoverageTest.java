package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Playtest 1.0.0 line 8: a world with Forgeweave alone logged 2352 "missing model for variant
 * forgeweave:molten_*#level=N" WARNs, one per molten fluid block per level, because none of those
 * blocks had a blockstate. Every registered Forgeweave block needs one, hand-written or generated.
 */
class BlockstateCoverageTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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

    @Test
    void everyRegisteredForgeweaveBlockHasABlockstate() {
        List<Path> roots = List.of(
                projectRoot().resolve("src/main/resources/assets/forgeweave/blockstates"),
                projectRoot().resolve("src/generated/resources/assets/forgeweave/blockstates"));
        Set<String> missing = new TreeSet<>();
        int blocks = 0;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!id.getNamespace().equals(Forgeweave.MODID)) {
                continue;
            }
            blocks++;
            if (roots.stream().noneMatch(root -> Files.isRegularFile(root.resolve(id.getPath() + ".json")))) {
                missing.add(id.getPath());
            }
        }
        assertTrue(blocks > 100, "non-vacuity: only " + blocks + " forgeweave blocks registered");
        assertTrue(missing.isEmpty(), missing.size() + " blocks have no blockstate: " + missing);
    }
}
