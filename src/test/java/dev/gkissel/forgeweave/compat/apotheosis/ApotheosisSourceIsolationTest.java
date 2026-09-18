package dev.gkissel.forgeweave.compat.apotheosis;

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
 * Apotheosis, Placebo and Apothic Attributes are compileOnly dependencies with no runtime presence
 * (build.gradle), so any file outside {@code compat/apotheosis/} that names a
 * {@code dev.shadowsoffire} type would crash a Forgeweave-only install the moment that file was
 * classloaded -- and it would crash on a player's machine, not here, since nothing in CI runs with
 * those mods installed.
 *
 * <p>Guards that by scanning the whole main, test and gametest source tree for the import and
 * allowing it only under this package. Deliberately cheap rather than exhaustive, the same call
 * {@code DraconicSourceIsolationTest} makes: a plain text scan, no AST. A fully-qualified reference
 * with no import would slip past it, which is a trade this accepts -- the shape it is actually
 * guarding against is someone reaching for {@code SocketHelper} or {@code GemInstance} from a
 * station, tooltip or JEI class, and an IDE writes that as an import.
 */
class ApotheosisSourceIsolationTest {

    private static final String APOTHEOSIS_PACKAGE = "dev.shadowsoffire";

    /** The one package allowed to name the Apotheosis API, plus this test. */
    private static final String ALLOWED_DIR = "dev/gkissel/forgeweave/compat/apotheosis";

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
    void onlyTheApotheosisCompatPackageImportsApotheosis() throws IOException {
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
                    if (Files.readString(java, StandardCharsets.UTF_8).contains("import " + APOTHEOSIS_PACKAGE)) {
                        offenders.add(relative);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "Apotheosis is compileOnly and absent at runtime, so only " + ALLOWED_DIR
                        + " may name a " + APOTHEOSIS_PACKAGE + " type (see ApotheosisSockets). Found it in:\n"
                        + String.join("\n", offenders));
    }

    /**
     * The other half of the same promise: inside that package, only the bridge implementation names
     * the API. {@code ApotheosisSockets} is classloaded on every install -- the modifier registry
     * holds its modifier and the tooltips walk its sockets -- so it must stay loadable with none of
     * the three mods present.
     */
    @Test
    void theApotheosisFreeSeamNamesNothingFromThatMod() throws IOException {
        Path seam = projectRoot().resolve("src/main/java/" + ALLOWED_DIR + "/ApotheosisSockets.java");
        assertTrue(Files.exists(seam), "missing " + seam);
        assertTrue(!Files.readString(seam, StandardCharsets.UTF_8).contains("import " + APOTHEOSIS_PACKAGE),
                "ApotheosisSockets is loaded on every install and must name no " + APOTHEOSIS_PACKAGE
                        + " type; put the reference in ApotheosisGemBonuses instead");
    }
}
