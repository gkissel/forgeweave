package dev.gkissel.forgeweave.compat.occultism;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Occultism is a compileOnly dependency with no runtime presence (build.gradle), so any file outside
 * {@code compat/occultism/} that names a {@code com.klikli_dev} type would crash a Forgeweave-only
 * install the moment that file was classloaded -- and it would crash on a player's machine, not here,
 * since nothing in CI runs with the mod installed.
 *
 * <p>Guards that by scanning the whole main, test and gametest source tree for the import, exactly
 * the way {@code DraconicSourceIsolationTest} does, and additionally pins that the one allowed file
 * really is one file rather than a package that has quietly grown a second -- which is issue #997's
 * own "exactly one class names an {@code occultism} type" deliverable.
 *
 * <p>Deliberately cheap rather than exhaustive: a plain text scan, no AST. A fully-qualified
 * reference with no import would slip past it, which is a trade this accepts -- the shape it is
 * actually guarding against is someone reaching for {@code RitualRecipe} from a station, a provider
 * or a JEI class, and an IDE writes that as an import.
 */
class OccultismSourceIsolationTest {

    private static final String OCCULTISM_PACKAGE = "com.klikli_dev";

    /** The one package allowed to name Occultism's API, plus this test. */
    private static final String ALLOWED_DIR = "dev/gkissel/forgeweave/compat/occultism";

    /** The one file inside that package allowed to name it. */
    private static final String ALLOWED_FILE = "SpiritBindingRitual.java";

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static List<String> filesNamingOccultism(boolean insideCompatPackage, List<String> sourceSets)
            throws IOException {
        Path root = projectRoot();
        List<String> found = new ArrayList<>();

        for (String sourceSet : sourceSets) {
            Path scanDir = root.resolve(sourceSet);
            if (!Files.isDirectory(scanDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(scanDir)) {
                for (Path java : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String relative = root.relativize(java).toString().replace('\\', '/');
                    if (relative.contains(ALLOWED_DIR) != insideCompatPackage) {
                        continue;
                    }
                    if (Files.readString(java, StandardCharsets.UTF_8).contains("import " + OCCULTISM_PACKAGE)) {
                        found.add(relative);
                    }
                }
            }
        }
        return found;
    }

    @Test
    void onlyTheOccultismCompatPackageImportsOccultism() throws IOException {
        List<String> offenders =
                filesNamingOccultism(false, List.of("src/main/java", "src/test/java", "src/gametest/java"));

        assertTrue(offenders.isEmpty(),
                "Occultism is compileOnly and absent at runtime, so only " + ALLOWED_DIR
                        + " may name a " + OCCULTISM_PACKAGE + " type (see ForgeweaveOccultismCompat)."
                        + " Found it in:\n" + String.join("\n", offenders));
    }

    /**
     * Shipped code only. A test in this package may name Occultism freely -- it runs with the jar on
     * the test classpath and never reaches a player's machine -- so counting test files here would
     * make the deliverable unmeetable rather than stricter.
     */
    @Test
    void exactlyOneShippedClassNamesOccultism() throws IOException {
        List<String> naming = filesNamingOccultism(true, List.of("src/main/java"));

        assertEquals(1, naming.size(), "issue #997 asks for exactly one class naming an Occultism type,"
                + " which is what keeps the ModList guard's reach a single file. Found:\n"
                + String.join("\n", naming));
        assertTrue(naming.getFirst().endsWith(ALLOWED_FILE),
                "expected " + ALLOWED_FILE + " to be that class, found " + naming.getFirst());
    }
}
