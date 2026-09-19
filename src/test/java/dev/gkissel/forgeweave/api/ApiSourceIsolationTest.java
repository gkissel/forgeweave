package dev.gkissel.forgeweave.api;

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
 * {@code dev.gkissel.forgeweave.api} is what a partner mod compiles against (issue #1065), so two
 * things have to stay true of it: it names no partner mod's type, and it names nothing from
 * Forgeweave's own internals either. The first keeps it classloadable on any install, the second
 * keeps the package a real boundary rather than a folder -- an api class reaching into
 * {@code menu} or {@code config} would drag the whole mod behind the promise.
 *
 * <p>The same plain text scan the seven {@code *SourceIsolationTest} classes under
 * {@code compat/} make, inverted: instead of one package allowed to name a foreign type, one
 * package allowed to name only a short list of prefixes. Deliberately cheap rather than
 * exhaustive -- a fully-qualified reference with no import slips past it, which is the same trade
 * those tests already accept.
 */
class ApiSourceIsolationTest {

    private static final String API_DIR = "src/main/java/dev/gkissel/forgeweave/api";

    /**
     * What an api class may import: the JDK, Minecraft and its DataFixerUpper codecs, NeoForge,
     * annotations, and the api package itself. Nothing else.
     */
    private static final List<String> ALLOWED = List.of(
            "java.",
            "javax.",
            "com.mojang.",
            "net.minecraft.",
            "net.neoforged.",
            "org.jetbrains.annotations.",
            "dev.gkissel.forgeweave.api.");

    @Test
    void theApiPackageImportsNothingOutsideMinecraftNeoForgeAndItself() throws IOException {
        Path apiDir = projectRoot().resolve(API_DIR);
        assertTrue(Files.isDirectory(apiDir), API_DIR + " should exist");
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(apiDir)) {
            for (Path java : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                for (String line : Files.readAllLines(java, StandardCharsets.UTF_8)) {
                    String imported = importedPackage(line);
                    if (imported != null && ALLOWED.stream().noneMatch(imported::startsWith)) {
                        offenders.add(java.getFileName() + ": " + line.trim());
                    }
                }
            }
        }

        // Non-vacuity: an empty walk would pass without proving anything.
        assertTrue(scanned >= 5, "expected the api package to hold several classes, walked only " + scanned);
        assertTrue(offenders.isEmpty(),
                "An addon compiles against " + API_DIR + ", so a class there may import only " + ALLOWED
                        + ". Move the type into the api package or keep the seam out of it. Found:\n"
                        + String.join("\n", offenders));
    }

    /** The package an {@code import} line names, or {@code null} for any other line. */
    private static String importedPackage(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("import ") || !trimmed.endsWith(";")) {
            return null;
        }
        String body = trimmed.substring("import ".length(), trimmed.length() - 1).trim();
        if (body.startsWith("static ")) {
            body = body.substring("static ".length()).trim();
        }
        return body;
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
