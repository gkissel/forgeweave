package dev.gkissel.forgeweave.emi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * EMI is compileOnly (build.gradle), so any file outside {@code emi/} that names a {@code dev.emi}
 * type would crash a Forgeweave install without EMI the moment that file was classloaded -- and on a
 * player's machine, since CI never runs with EMI loaded. {@code DraconicSourceIsolationTest}'s shape:
 * a plain text scan of the import, no AST. It also keeps the plugin original, per issue #971: the
 * only EMI code this repository may contain is code that calls EMI's API.
 *
 * <p>The second test is the same guard the other way round, and the reason issue #971 had to move
 * code at all. EMI runs this plugin with no JEI installed, so nothing the plugin reaches in the
 * {@code jei} package -- the shared builders and display records -- may import a JEI type either.
 */
class EmiSourceIsolationTest {

    private static final String EMI_DIR = "dev/gkissel/forgeweave/emi";
    private static final String JEI_DIR = "src/main/java/dev/gkissel/forgeweave/jei";
    private static final Pattern JEI_IMPORT = Pattern.compile("import dev\\.gkissel\\.forgeweave\\.jei\\.(\\w+)");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Z]\\w*\\b");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static List<Path> javaFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void onlyTheEmiPackageImportsEmi() throws IOException {
        Path root = projectRoot();
        List<String> offenders = new ArrayList<>();
        for (String sourceSet : List.of("src/main/java", "src/test/java", "src/gametest/java")) {
            for (Path java : javaFiles(root.resolve(sourceSet))) {
                String relative = root.relativize(java).toString().replace('\\', '/');
                if (!relative.contains(EMI_DIR) && Files.readString(java, StandardCharsets.UTF_8).contains("import dev.emi")) {
                    offenders.add(relative);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "EMI is compileOnly and absent at runtime, so only " + EMI_DIR
                + " may name a dev.emi type. Found it in:\n" + String.join("\n", offenders));
    }

    /**
     * Follows every {@code jei} class the EMI package imports, then every {@code jei} class those
     * name in their code (same package, so no import to see; comments are stripped first, so a
     * javadoc link to a JEI category does not count), and fails if any of them imports JEI.
     */
    @Test
    void nothingTheEmiPluginReachesInTheJeiPackageImportsJei() throws IOException {
        Path root = projectRoot();
        Path jeiDir = root.resolve(JEI_DIR);
        Set<String> jeiClasses = new HashSet<>();
        for (Path java : javaFiles(jeiDir)) {
            jeiClasses.add(java.getFileName().toString().replace(".java", ""));
        }

        Deque<String> pending = new ArrayDeque<>();
        for (Path java : javaFiles(root.resolve("src/main/java").resolve(EMI_DIR))) {
            Matcher imports = JEI_IMPORT.matcher(Files.readString(java, StandardCharsets.UTF_8));
            while (imports.find()) {
                pending.add(imports.group(1));
            }
        }
        assertTrue(!pending.isEmpty(), "the EMI plugin shares the JEI plugin's builders, or this test proves nothing");

        Set<String> reached = new HashSet<>();
        List<String> offenders = new ArrayList<>();
        while (!pending.isEmpty()) {
            String name = pending.pop();
            if (!jeiClasses.contains(name) || !reached.add(name)) {
                continue;
            }
            String source = Files.readString(jeiDir.resolve(name + ".java"), StandardCharsets.UTF_8);
            if (source.contains("import mezz.jei")) {
                offenders.add(name);
            }
            Matcher names = IDENTIFIER.matcher(COMMENTS.matcher(source).replaceAll(""));
            while (names.find()) {
                pending.add(names.group());
            }
        }
        assertTrue(offenders.isEmpty(), "EMI loads its plugin without JEI, so these jei classes it reaches must not"
                + " import JEI (move the JEI-free part out, as RecipeViewerContent did):\n" + String.join("\n", offenders));
    }
}
