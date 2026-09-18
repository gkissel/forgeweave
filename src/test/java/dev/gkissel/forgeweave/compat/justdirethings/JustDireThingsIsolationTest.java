package dev.gkissel.forgeweave.compat.justdirethings;

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
 * Just Dire Things has no compat package at all, and D-M8-22 in {@code docs/SCOPE.md} records why:
 * the mod decides what can take an upgrade with {@code stack.getItem() instanceof ToggleableTool},
 * hardcodes each item's ability set in that item's constructor, and generates one smithing recipe
 * per own item with {@code Ingredient.of(tool.get())}. There is no tag, capability, data component
 * or registration call a foreign item can use, so route 1 and route 2 of issue #1032 both reach
 * nothing and route 3 is where that issue stops.
 *
 * <p>Two scans hold that finding in place. The first is the isolation rule the other integrations
 * already follow, pre-armed for the day the package exists. The second pins the part-swap answer:
 * Forgeweave keeps no Just Dire Things upgrade state anywhere, so a part replacement has no
 * eligibility to break and no upgrade item to give back.
 *
 * <p>Both scans are the plain text walk {@code CreateSourceIsolationTest} and
 * {@code DraconicSourceIsolationTest} already use: no AST, cheap rather than exhaustive.
 */
class JustDireThingsIsolationTest {

    private static final String JDT_PACKAGE = "com.direwolf20.justdirethings";

    private static final String JDT_NAMESPACE = "justdirethings";

    /** The package that would be allowed to name the mod's API, plus this test. */
    private static final String ALLOWED_DIR = "dev/gkissel/forgeweave/compat/justdirethings";

    private static final List<String> SOURCE_SETS = List.of("src/main/java", "src/test/java", "src/gametest/java");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static List<String> filesContaining(String needle, boolean skipAllowedDir) throws IOException {
        Path root = projectRoot();
        List<String> found = new ArrayList<>();

        for (String sourceSet : SOURCE_SETS) {
            Path scanDir = root.resolve(sourceSet);
            if (!Files.isDirectory(scanDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(scanDir)) {
                for (Path java : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String relative = root.relativize(java).toString().replace('\\', '/');
                    if (skipAllowedDir && relative.contains(ALLOWED_DIR)) {
                        continue;
                    }
                    if (relative.equals(
                            "src/test/java/" + ALLOWED_DIR + "/JustDireThingsIsolationTest.java")) {
                        continue;
                    }
                    if (Files.readString(java, StandardCharsets.UTF_8).contains(needle)) {
                        found.add(relative);
                    }
                }
            }
        }
        return found;
    }

    @Test
    void onlyTheJustDireThingsCompatPackageMayImportTheMod() throws IOException {
        List<String> offenders = filesContaining("import " + JDT_PACKAGE, true);

        assertTrue(offenders.isEmpty(),
                "Just Dire Things is not a Forgeweave dependency (D-M8-22), and if it becomes one it is"
                        + " compileOnly, so only " + ALLOWED_DIR + " may name a " + JDT_PACKAGE
                        + " type. Found it in:\n" + String.join("\n", offenders));
    }

    @Test
    void forgeweaveStoresNoJustDireThingsUpgradeState() throws IOException {
        List<String> offenders = filesContaining(JDT_NAMESPACE, false);

        assertTrue(offenders.isEmpty(),
                "Forgeweave reads and writes no Just Dire Things state, which is what makes D-M8-22's"
                        + " part-swap question vacuous: a Forgeweave tool can never carry one of that mod's"
                        + " upgrades, so replacing a part can never invalidate one and there is nothing to"
                        + " give back. If you are adding the integration, that rule stops being vacuous and"
                        + " the part replacement flow owes the player their upgrade items back. Update"
                        + " D-M8-22 and this test in the same change. Found the namespace in:\n"
                        + String.join("\n", offenders));
    }
}
