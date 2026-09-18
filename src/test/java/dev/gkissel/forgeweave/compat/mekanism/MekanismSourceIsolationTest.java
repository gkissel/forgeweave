package dev.gkissel.forgeweave.compat.mekanism;

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
 * Mekanism is a compileOnly dependency with no runtime presence (build.gradle), so any file outside
 * {@code compat/mekanism/} that names a {@code mekanism} type would crash a Forgeweave-only install
 * the moment that file was classloaded -- and it would crash on a player's machine, not here, since
 * nothing in CI runs with the mod installed.
 *
 * <p>Same shape {@code DraconicSourceIsolationTest} already set, and the same trade: a plain text
 * scan, no AST, so a fully-qualified reference with no import would slip past. What it is actually
 * guarding against is someone reaching for {@code MekanismModules} or {@code IModuleContainer} from a
 * station, tooltip or JEI class, and an IDE writes that as an import.
 */
class MekanismSourceIsolationTest {

    private static final String MEKANISM_PACKAGE = "mekanism.";

    /** The one package allowed to name Mekanism's API, plus this test. */
    private static final String ALLOWED_DIR = "dev/gkissel/forgeweave/compat/mekanism";

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
    void onlyTheMekanismCompatPackageImportsMekanism() throws IOException {
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
                    if (Files.readString(java, StandardCharsets.UTF_8).contains("import " + MEKANISM_PACKAGE)) {
                        offenders.add(relative);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Mekanism is compileOnly and absent at runtime, so only " + ALLOWED_DIR
                        + " may name a " + MEKANISM_PACKAGE + "* type (see ForgeweaveMekanismCompat). Found it in:\n"
                        + String.join("\n", offenders));
    }
}
