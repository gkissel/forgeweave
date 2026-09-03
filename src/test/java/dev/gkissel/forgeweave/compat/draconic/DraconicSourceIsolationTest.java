package dev.gkissel.forgeweave.compat.draconic;

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
 * Draconic Evolution is a compileOnly dependency with no runtime presence (build.gradle), so any
 * file outside {@code compat/draconic/} that names a {@code com.brandon3055} type would crash a
 * Forgeweave-only install the moment that file was classloaded -- and it would crash on a player's
 * machine, not here, since nothing in CI runs with the mod installed.
 *
 * <p>Guards that by scanning the whole main and test source tree for the import and allowing it
 * only under this package. Deliberately cheap rather than exhaustive, the same call
 * {@code LocalizationAuditTest} makes: a plain text scan, no AST. A fully-qualified reference with
 * no import would slip past it, which is a trade this accepts -- the shape it is actually guarding
 * against is someone reaching for {@code TechLevel} from a station or JEI class, and an IDE writes
 * that as an import.
 */
class DraconicSourceIsolationTest {

    private static final String DRACONIC_PACKAGE = "com.brandon3055";

    /** The one package allowed to name Draconic Evolution's API, plus this test. */
    private static final String ALLOWED_DIR = "dev/gkissel/forgeweave/compat/draconic";

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
    void onlyTheDraconicCompatPackageImportsDraconicEvolution() throws IOException {
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
                    if (Files.readString(java, StandardCharsets.UTF_8).contains("import " + DRACONIC_PACKAGE)) {
                        offenders.add(relative);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Draconic Evolution is compileOnly and absent at runtime, so only " + ALLOWED_DIR
                        + " may name a " + DRACONIC_PACKAGE + " type (see ForgeweaveDraconicCompat). Found it in:\n"
                        + String.join("\n", offenders));
    }
}
