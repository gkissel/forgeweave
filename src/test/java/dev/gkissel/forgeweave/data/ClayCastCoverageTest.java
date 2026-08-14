package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Guards issue #387: {@code scripts/generate_clay_cast_textures.py} globs every committed
 * {@code cast_*.png} to produce its clay counterpart, and {@link ForgeweaveItems#CLAY_CASTS} is a
 * separate, hand-maintained name list ({@code #292}) that every new gold cast has to be added to.
 * Nothing previously caught the two drifting apart: {@code #372} registered
 * {@code cast_sharpening_kit} after {@code #292} had already merged, so it never made the list --
 * the texture script still emitted {@code clay_cast_sharpening_kit.png}, but no item, model, lang
 * key, or recipe ever referenced it.
 *
 * <p>Refactoring the two generators to share a roster wasn't the cheap fix -- the texture script is
 * already single-sourced (it globs the committed {@code cast_*.png} outputs instead of hard-coding a
 * list); the Java side is the one hand-maintained list, and it can't be derived from the filesystem
 * at compile time. So this test is the guard instead: every {@code cast_*.png} must have both a
 * {@code clay_cast_*.png} sibling and a {@link ForgeweaveItems#CLAY_CASTS} entry.
 */
class ClayCastCoverageTest {

    private static final Path DERIVED_ITEM_DIR =
            Path.of("src/main/resources/assets/forgeweave/textures/derived/item");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyGoldCastTextureHasAClayCastTextureAndARegisteredItem() throws IOException {
        Path dir = projectRoot().resolve(DERIVED_ITEM_DIR);
        List<String> missingTexture = new ArrayList<>();
        List<String> missingItem = new ArrayList<>();

        try (Stream<Path> files = Files.list(dir)) {
            for (Path png : files.filter(p -> p.getFileName().toString().startsWith("cast_")).toList()) {
                String name = png.getFileName().toString();
                String key = name.substring(0, name.length() - ".png".length());

                if (!Files.exists(dir.resolve("clay_" + name))) {
                    missingTexture.add(name);
                }
                if (!ForgeweaveItems.CLAY_CASTS.containsKey(key)) {
                    missingItem.add(key);
                }
            }
        }

        assertTrue(missingTexture.isEmpty(),
                "missing clay_*.png counterpart for: " + missingTexture
                        + " -- run scripts/generate_clay_cast_textures.py and commit its output");
        assertTrue(missingItem.isEmpty(),
                "missing ForgeweaveItems.CLAY_CASTS entry for: " + missingItem
                        + " -- add these names to the CLAY_CASTS list (issue #292's machinery)");
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
