package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards against the issue #65 regression class: a player-facing string built with {@code
 * Component.literal("...")} instead of a lang key through {@link ForgeweaveLanguageProvider}. Scans
 * the same surfaces the issue's audit covered -- screens, menus, item/block hover text, and JEI --
 * for a quoted string literal passed to {@code Component.literal} that contains a letter.
 *
 * <p>Deliberately cheap rather than exhaustive (SCOPE.md's regression rule wants automation, not
 * perfection here): a plain {@code src/main/java} text scan, no AST parsing. Requiring a letter in
 * the literal is what keeps it from flagging the legitimate glue every screen already uses --
 * separators ({@code "/"}, {@code ": "}), bullets ({@code " * "}), and {@code DecimalFormat}
 * patterns ({@code "#.##"}) -- since none of those contain one. A literal that's entirely player
 * input (e.g. a rename field's typed name) isn't caught either, because it's a variable, not a
 * quoted string, at the call site.
 *
 * <p>A future legitimate letter-containing literal (a debug/technical label never shown
 * untranslated, say) can be added to {@link #ALLOWED_LITERALS} with a comment explaining why it's
 * exempt, rather than weakening the pattern.
 */
class LocalizationAuditTest {

    private static final Pattern LITERAL_WITH_LETTERS = Pattern.compile("Component\\.literal\\(\"([^\"]*[A-Za-z][^\"]*)\"");

    /** Scoped to the issue #65 audit's surfaces: screens, menus, item/block hover text, JEI. */
    private static final List<String> AUDITED_DIRS = List.of("client", "menu", "item", "jei");

    /** Literal text explicitly reviewed and exempted from translation; empty until one is needed. */
    private static final Set<String> ALLOWED_LITERALS = Set.of();

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
    void noPlayerFacingLiteralTextOutsideLangKeys() throws IOException {
        Path root = projectRoot();
        List<String> offenders = new ArrayList<>();

        for (String dir : AUDITED_DIRS) {
            Path scanDir = root.resolve("src/main/java/dev/gkissel/forgeweave/" + dir);
            if (!Files.isDirectory(scanDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(scanDir)) {
                for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(java, StandardCharsets.UTF_8);
                    Matcher matcher = LITERAL_WITH_LETTERS.matcher(content);
                    while (matcher.find()) {
                        String text = matcher.group(1);
                        if (!ALLOWED_LITERALS.contains(text)) {
                            offenders.add(java + ": Component.literal(\"" + text + "\")");
                        }
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "hardcoded player-facing text found -- move it to a lang key via ForgeweaveLanguageProvider "
                        + "(or add it to LocalizationAuditTest#ALLOWED_LITERALS with a reason):\n"
                        + String.join("\n", offenders));
    }
}
