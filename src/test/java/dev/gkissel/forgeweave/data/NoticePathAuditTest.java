package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Issue #1049 (Part 2 item 3): walks {@code NOTICE.md} and fails on a row whose Forgeweave path (the
 * first table column) does not exist -- the same check the PR #1048 designer-brief audit ran by hand
 * and found seven stale rows from ({@code CropHarvest.java} sitting at {@code item/} instead of its
 * real {@code tool/} package, and six worldgen rows carrying a doubled {@code forgeweave/forgeweave}
 * segment that vanilla's own {@code worldgen} registry never uses, unlike this mod's custom
 * datapack-registry rows such as {@code melting_recipe} or {@code casting_recipe}, which really do
 * live at that doubled path).
 *
 * <h2>Column 1 only</h2>
 *
 * <p>Only the Forgeweave path column is checked -- the upstream path column names files in the
 * read-only reference clones this repo does not ship, so there is nothing on disk here to check it
 * against.
 *
 * <h2>Extracting a path from free-form prose</h2>
 *
 * <p>Column 1 is not machine-readable table data; it is a sentence with backtick-quoted paths and
 * code identifiers mixed into free prose (see the class-level examples in {@code NOTICE.md} itself).
 * A backtick span only counts as a path to check when it also matches {@link #PATH_PATTERN}: starts
 * with {@code src/}, {@code docs/} or {@code scripts/}, uses only path-safe characters, and ends in a
 * real file extension. That rules out the things a naive "contains a slash" scan would misfire on --
 * arithmetic like `` `gravity/250` ``, lang key families like
 * `` `trait.forgeweave.{a,b,c}.name/.description` ``, and bare Java identifiers like `` `arditeGen` ``
 * -- while still catching every real path in the document (verified against the full 1,800+ backtick
 * spans in {@code NOTICE.md} at the time this test was written: this pattern isolates exactly the
 * seven rows the audit found broken, nothing else).
 *
 * <h2>Brace and wildcard expansion</h2>
 *
 * <p>The audit's own words (issue #1049): "expand brace and wildcard notation the way the audit did".
 * {@code {a,b,c}} groups expand to one candidate per option (recursively, for the rare row with more
 * than one group). A {@code <placeholder>} segment -- {@code shard_<metal>.json}, standing in for one
 * concrete file per metal -- normalizes to a glob {@code *} first, the same substitution the brief's
 * own prose makes when it calls these "wildcard notation". A candidate with no remaining {@code *}
 * must exist as a literal file; a candidate that still has one after expansion only has to match at
 * least one real file, since it was never a single path to begin with.
 */
class NoticePathAuditTest {

    /**
     * A path worth checking: starts under one of this repo's real source roots, uses only
     * filename-safe characters (plus {@code {}} for brace groups and {@code <>} for placeholders,
     * both resolved before the existence check), and ends in an extension this repo actually ships.
     * See the class javadoc for why this -- not a bare "contains a slash" scan -- is what keeps this
     * test free of false positives against NOTICE.md's free-form prose.
     */
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "^(src|docs|scripts)/[A-Za-z0-9_\\-./{},<>]*\\.(java|json|png|md|txt|snbt|mcmeta|toml)$");

    private static final Pattern BACKTICK_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>");
    private static final Pattern BRACE_GROUP = Pattern.compile("\\{([^{}]+)\\}");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    /**
     * The table's first column: everything from the row's start up to the first {@code " | "} that
     * appears <em>outside</em> a backtick span. A plain split on {@code " | "} would also cut inside a
     * backtick span that happens to contain a literal pipe (one row does, a bitwise-OR Java snippet),
     * so this walks the line backtick-span-aware instead, exactly like a hand reviewer would.
     */
    private static String firstColumn(String line) {
        String[] segments = line.split("`", -1);
        StringBuilder column = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            boolean insideBackticks = i % 2 == 1;
            if (insideBackticks) {
                column.append('`').append(segments[i]).append('`');
                continue;
            }
            int boundary = segments[i].indexOf(" | ");
            if (boundary >= 0) {
                column.append(segments[i], 0, boundary);
                return column.toString();
            }
            column.append(segments[i]);
        }
        return column.toString();
    }

    private static List<String> expandBraces(String path) {
        Matcher m = BRACE_GROUP.matcher(path);
        if (!m.find()) {
            return List.of(path);
        }
        List<String> results = new ArrayList<>();
        for (String option : m.group(1).split(",")) {
            String expanded = path.substring(0, m.start()) + option + path.substring(m.end());
            results.addAll(expandBraces(expanded));
        }
        return results;
    }

    @Test
    void everyForgeweavePathInNoticeExists() throws IOException {
        Path root = projectRoot();
        Path notice = root.resolve("NOTICE.md");
        List<String> lines = Files.readAllLines(notice);

        // One directory walk per source root, cached, rather than re-walking the tree per candidate
        // path -- scoped to the three prefixes PATH_PATTERN allows, so this never descends into
        // .git/build/.gradle (which would be both slow and pointless: nothing there can match).
        Set<Path> allFiles = new HashSet<>();
        for (String top : List.of("src", "docs", "scripts")) {
            Path topPath = root.resolve(top);
            if (!Files.isDirectory(topPath)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(topPath)) {
                walk.filter(Files::isRegularFile).map(root::relativize).forEach(allFiles::add);
            }
        }

        List<String> failures = new ArrayList<>();
        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1);
            if (!line.startsWith("|") || line.startsWith("| ---") || line.contains("Forgeweave path")) {
                continue;
            }
            String column1 = firstColumn(line);
            Matcher spanMatcher = BACKTICK_SPAN.matcher(column1);
            while (spanMatcher.find()) {
                String span = spanMatcher.group(1);
                if (!PATH_PATTERN.matcher(span).matches()) {
                    continue;
                }
                String normalized = PLACEHOLDER.matcher(span).replaceAll("*");
                for (String candidate : expandBraces(normalized)) {
                    if (candidate.contains("*")) {
                        if (!matchesAnyFile(allFiles, candidate)) {
                            failures.add("NOTICE.md:" + lineNumber + ": `" + span
                                    + "` -> no file matches glob `" + candidate + "`");
                        }
                    } else if (!Files.isRegularFile(root.resolve(candidate))) {
                        failures.add("NOTICE.md:" + lineNumber + ": `" + span + "` -> " + candidate + " does not exist");
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(),
                "NOTICE.md rows whose Forgeweave path does not exist on disk:\n" + String.join("\n", failures));
    }

    private static boolean matchesAnyFile(Set<Path> allFiles, String globPattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        return allFiles.stream().anyMatch(matcher::matches);
    }
}
