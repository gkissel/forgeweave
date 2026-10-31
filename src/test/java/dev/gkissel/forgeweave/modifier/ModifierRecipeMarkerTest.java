package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

/**
 * Playtest 1.0.0 line 8: JEI logged "Unknown modifier" for {@code fortification}, {@code overslime}
 * and {@code socket_gem} on load, because every modifier recipe's id goes through
 * {@link ForgeweaveModifiers#get}. Every shipped modifier recipe has to name a modifier Forgeweave
 * implements or one of {@link ForgeweaveModifiers#RECIPE_MARKERS}, which never warn.
 */
class ModifierRecipeMarkerTest {

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
    void everyShippedModifierRecipeNamesAModifierOrAKnownMarker() throws IOException {
        Set<String> unknown = new TreeSet<>();
        int recipes = 0;
        for (String tree : List.of("src/main/resources", "src/generated/resources")) {
            Path root = projectRoot().resolve(tree);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().replace('\\', '/').contains("/modifier_recipe/"))
                        .filter(p -> p.toString().endsWith(".json")).toList()) {
                    JsonElement modifier = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                            .getAsJsonObject().get("modifier");
                    if (modifier == null) {
                        continue;
                    }
                    recipes++;
                    Identifier id = Identifier.parse(modifier.getAsString());
                    if (!ForgeweaveModifiers.RECIPE_MARKERS.contains(id) && ForgeweaveModifiers.get(id) == null) {
                        unknown.add(root.relativize(file) + " -> " + id);
                    }
                }
            }
        }
        assertTrue(recipes > 50, "non-vacuity: found only " + recipes + " modifier recipes");
        assertTrue(unknown.isEmpty(), "these recipes name a modifier that logs as unknown: " + unknown);
    }

    @Test
    void theThreeMarkersThePlaytestLoggedAreKnown() {
        for (String path : List.of("fortification", "overslime", "socket_gem")) {
            assertTrue(ForgeweaveModifiers.RECIPE_MARKERS.contains(Identifier.fromNamespaceAndPath("forgeweave", path)),
                    path + " is a recipe marker and must not log as an unknown modifier");
        }
    }
}
