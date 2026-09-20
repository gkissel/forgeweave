package dev.gkissel.forgeweave.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Issue #1083: every example in {@code docs/addons.md} comes from the test addon, so the guide shows
 * code the build compiles and GameTests drive rather than something written once and left to rot.
 *
 * <p>The marker is one HTML comment on the line before the fence:
 *
 * <pre>{@code
 * <!-- from src/gametest/resources/data/gametest_addon/forgeweave/material/addon_alloy.json -->
 * ```json
 * ...
 * ```
 * }</pre>
 *
 * <p>The check is deliberately small: every non-blank line of the block, trimmed, has to be a line
 * of the file the marker names. That survives re-indentation and quoting a fragment of a method,
 * and it still fails the moment a field is renamed on one side only. It is not a documentation
 * system and is not meant to grow into one.
 */
class AddonDocsExampleTest {

    private static final String MARKER = "<!-- from ";

    /** Below this, a rewrite that dropped the markers would pass by having nothing left to check. */
    private static final int MINIMUM_EXAMPLES = 10;

    @Test
    void everyMarkedExampleStillMatchesTheFileItNames() throws IOException {
        Path root = projectRoot();
        List<String> lines = Files.readAllLines(root.resolve("docs/addons.md"), StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        int examples = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith(MARKER) || !line.endsWith("-->")) {
                continue;
            }
            examples++;
            String named = line.substring(MARKER.length(), line.length() - "-->".length()).trim();
            Path source = root.resolve(named);
            if (!Files.exists(source)) {
                problems.add(named + ": no such file");
                continue;
            }
            if (i + 1 >= lines.size() || !lines.get(i + 1).startsWith("```")) {
                problems.add(named + ": the marker is not directly above a fenced block");
                continue;
            }

            Set<String> sourceLines = new HashSet<>();
            for (String sourceLine : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                sourceLines.add(sourceLine.trim());
            }
            for (int j = i + 2; j < lines.size() && !lines.get(j).startsWith("```"); j++) {
                String quoted = lines.get(j).trim();
                if (!quoted.isEmpty() && !sourceLines.contains(quoted)) {
                    problems.add(named + " has no line '" + quoted + "' (docs/addons.md line " + (j + 1) + ")");
                }
            }
        }

        assertTrue(problems.isEmpty(), "docs/addons.md quotes the test addon; these no longer match:\n"
                + String.join("\n", problems));
        assertTrue(examples >= MINIMUM_EXAMPLES,
                "expected at least " + MINIMUM_EXAMPLES + " examples marked as coming from the test addon, found "
                        + examples);
    }

    private static Path projectRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        return root;
    }
}
