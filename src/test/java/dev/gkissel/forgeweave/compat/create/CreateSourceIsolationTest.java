package dev.gkissel.forgeweave.compat.create;

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
 * Create is a compileOnly dependency with no runtime presence (build.gradle), so any file outside
 * {@code compat/create/} that names a {@code com.simibubi.create} type would crash a Forgeweave-only
 * install the moment that file was classloaded -- and it would crash on a player's machine, not
 * here, since nothing in CI runs with the mod installed.
 *
 * <p>Guards that by scanning the whole main and test source tree for the import and allowing it only
 * under this package, the same shape {@code DraconicSourceIsolationTest} already set for Draconic
 * Evolution: a plain text scan, no AST, cheap rather than exhaustive.
 */
class CreateSourceIsolationTest {

    private static final String CREATE_PACKAGE = "com.simibubi.create";

    /** The one package allowed to name Create's API, plus this test. */
    private static final String ALLOWED_DIR = "dev/gkissel/forgeweave/compat/create";

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
    void onlyTheCreateCompatPackageImportsCreate() throws IOException {
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
                    if (Files.readString(java, StandardCharsets.UTF_8).contains("import " + CREATE_PACKAGE)) {
                        offenders.add(relative);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Create is compileOnly and absent at runtime, so only " + ALLOWED_DIR
                        + " may name a " + CREATE_PACKAGE + " type (see ForgeweaveCreateCompat). Found it in:\n"
                        + String.join("\n", offenders));
    }
}
