package dev.gkissel.forgeweave.compat.mysticalagriculture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Mystical Agriculture and Cucumber are compileOnly dependencies with no runtime presence
 * (build.gradle), so any file outside {@code compat/mysticalagriculture/} that names a
 * {@code com.blakebr0} type would crash a Forgeweave-only install the moment that file was
 * classloaded -- and it would crash on a player's machine, not here, since nothing in CI runs with
 * either mod installed.
 *
 * <p>Guards that by scanning the whole main, test and gametest source tree for the import and
 * allowing it only under this package. Deliberately cheap rather than exhaustive, the call
 * {@code DraconicSourceIsolationTest} already makes for Draconic Evolution: a plain text scan, no
 * AST. A fully-qualified reference with no import would slip past it, which is a trade this accepts
 * -- the shape it is actually guarding against is someone reaching for {@code ITinkerable} or
 * {@code Crop} from an item, station or JEI class, and an IDE writes that as an import.
 */
class MysticalAgricultureSourceIsolationTest {

    /** Both mods share this group, so one prefix covers the API and Cucumber's LazyIngredient alike. */
    private static final String BLAKEBR0_PACKAGE = "com.blakebr0";

    /** The one package allowed to name either mod's API, plus this test. */
    private static final String ALLOWED_DIR = "dev/gkissel/forgeweave/compat/mysticalagriculture";

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
    void onlyTheMysticalAgricultureCompatPackageImportsIt() throws IOException {
        Path root = projectRoot();
        List<String> offenders = new ArrayList<>();

        for (String sourceSet : List.of("src/main/java", "src/test/java", "src/gametest/java")) {
            Path scanDir = root.resolve(sourceSet);
            if (!Files.isDirectory(scanDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(scanDir)) {
                for (Path java : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String relative = root.relativize(java).toString().replace('\\', '/');
                    if (relative.contains(ALLOWED_DIR)) {
                        continue;
                    }
                    if (Files.readString(java, StandardCharsets.UTF_8).contains("import " + BLAKEBR0_PACKAGE)) {
                        offenders.add(relative);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Mystical Agriculture and Cucumber are compileOnly and absent at runtime, so only "
                        + ALLOWED_DIR + " may name a " + BLAKEBR0_PACKAGE
                        + " type (see MysticalAgricultureCompat). Found it in:\n" + String.join("\n", offenders));
    }

    /**
     * The other half of the same rule, and the one that actually bites: the classes in this package
     * that {@code runGameTestServer} and the plain unit tests reach must themselves stay free of the
     * import, or a GameTest covering the off path cannot load them.
     */
    @Test
    void theModFreeHalfOfThePackageStaysModFree() throws IOException {
        Path root = projectRoot();
        List<String> offenders = new ArrayList<>();

        for (String name : List.of("EssenceTier", "ForgeweaveCrop", "MysticalAugments")) {
            Path java = root.resolve("src/main/java").resolve(ALLOWED_DIR).resolve(name + ".java");
            if (Files.readString(java, StandardCharsets.UTF_8).contains("import " + BLAKEBR0_PACKAGE)) {
                offenders.add(name);
            }
        }

        assertTrue(offenders.isEmpty(),
                "These classes are the ones GameTests and unit tests load without Mystical Agriculture "
                        + "present, so they may not import " + BLAKEBR0_PACKAGE + " either. Found it in: "
                        + String.join(", ", offenders));
    }
}
