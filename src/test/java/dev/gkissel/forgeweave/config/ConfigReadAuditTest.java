package dev.gkissel.forgeweave.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Issue #1023: a {@code SERVER} config value read with a raw {@code .get()} throws when no world is
 * loaded, and other mods reach Forgeweave recipes and items before one is. Reads outside the config
 * package go through {@code ForgeweaveConfig.read(...)} or a named helper. GameTests are exempt:
 * they run inside a loaded world, and some of them set values.
 */
class ConfigReadAuditTest {

    private static final Pattern RAW_READ =
            Pattern.compile("ForgeweaveConfig\\.[A-Z][A-Z0-9_]*\\.get(AsInt|AsDouble|AsBoolean|AsLong)?\\(\\)");

    private static final List<String> EXEMPT_DIRS = List.of("config", "gametest");

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("settings.gradle not found above " + Path.of("").toAbsolutePath());
    }

    @Test
    void noRawServerConfigReadsOutsideTheConfigPackage() throws IOException {
        Path sources = projectRoot().resolve("src/main/java/dev/gkissel/forgeweave");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String top = sources.relativize(java).getName(0).toString();
                if (EXEMPT_DIRS.contains(top)) {
                    continue;
                }
                Matcher matcher = RAW_READ.matcher(Files.readString(java, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    offenders.add(sources.relativize(java) + ": " + matcher.group());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Read server config through ForgeweaveConfig.read(...) or a named helper, not a raw get():\n"
                        + String.join("\n", offenders));
    }
}
