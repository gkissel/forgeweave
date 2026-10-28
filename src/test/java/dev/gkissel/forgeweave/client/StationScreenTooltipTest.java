package dev.gkissel.forgeweave.client;

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
 * Guards against the issue #75 regression (maintainer acceptance playthrough: no item tooltips in
 * the Crafting Station or Stencil Table).
 *
 * <p>On 1.21.1 {@code AbstractContainerScreen#render} did not call {@code renderTooltip} -- every
 * vanilla container screen overrode {@code render} to add it. A mod screen that didn't shipped with
 * no item tooltips whatsoever, and nothing caught it: it compiled, it ran, it just silently never
 * showed hover text. Issue #43 hit this once and #57 fixed it by pasting the two-line override into
 * two screens; the next three screens added ({@code CraftingStationScreen}, {@code StencilTableScreen},
 * {@code ChestScreen}) each shipped without it again.
 *
 * <p>26.x moved the seam: {@code AbstractContainerScreen#extractRenderState} now calls
 * {@code extractTooltip} itself, so a screen gets item tooltips by default and loses them in two
 * ways only -- overriding {@code extractRenderState} without calling {@code super}, or overriding
 * {@code extractTooltip} (to add its own hover text, as most stations do) without calling
 * {@code super}. This test is the ratchet on both, for every container screen in the
 * {@code client} package. {@link StationScreen} still owns the shared tab-row tooltip.
 *
 * <p>A plain source scan rather than reflection, matching {@code data.TextureReferenceAuditTest} and
 * {@code LocalizationAuditTest}: the unit-test classpath has no Minecraft client, so {@code
 * AbstractContainerScreen} cannot be loaded to walk a real class hierarchy here.
 */
class StationScreenTooltipTest {

    private static final Pattern CONTAINER_SCREEN = Pattern.compile(
            "class\\s+(\\w+)\\s+extends\\s+(AbstractContainerScreen|StationScreen)\\s*<");
    private static final Pattern RENDER_STATE_OVERRIDE = Pattern.compile(
            "void\\s+extractRenderState\\s*\\(\\s*GuiGraphicsExtractor");
    private static final Pattern TOOLTIP_OVERRIDE = Pattern.compile(
            "void\\s+extractTooltip\\s*\\(\\s*GuiGraphicsExtractor");

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
     * {@code dropsTooltips}: the file overrides {@code extractRenderState} or {@code extractTooltip}
     * and that method's body never calls the same method on {@code super}.
     */
    private record Screen(String name, String superClass, boolean dropsTooltips) {}

    /** Whether some override matched by {@code override} has a body that never calls {@code super.<method>(}. */
    private static boolean overrideSkipsSuper(String source, Pattern override, String method) {
        Matcher matcher = override.matcher(source);
        while (matcher.find()) {
            int open = source.indexOf('{', matcher.end());
            int depth = 0;
            int close = open;
            for (; close < source.length(); close++) {
                char c = source.charAt(close);
                if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    break;
                }
            }
            String body = source.substring(open, close).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
            if (!body.contains("super." + method + "(")) {
                return true;
            }
        }
        return false;
    }

    private static List<Screen> containerScreens() throws IOException {
        Path clientDir = projectRoot().resolve("src/main/java/dev/gkissel/forgeweave/client");
        List<Screen> screens = new ArrayList<>();
        try (Stream<Path> files = Files.walk(clientDir)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(java, StandardCharsets.UTF_8);
                Matcher matcher = CONTAINER_SCREEN.matcher(source);
                while (matcher.find()) {
                    if (matcher.group(1).equals("StationScreen")) {
                        continue; // the base class itself is what everything else must extend
                    }
                    screens.add(new Screen(matcher.group(1), matcher.group(2),
                            overrideSkipsSuper(source, RENDER_STATE_OVERRIDE, "extractRenderState")
                                    || overrideSkipsSuper(source, TOOLTIP_OVERRIDE, "extractTooltip")));
                }
            }
        }
        return screens;
    }

    @Test
    void everyContainerScreenRendersTooltips() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Screen screen : containerScreens()) {
            if (screen.dropsTooltips()) {
                offenders.add(screen.name() + " extends " + screen.superClass()
                        + " and overrides extractRenderState or extractTooltip without calling super, so "
                        + "vanilla's item tooltip pass never runs and it shows no item tooltips");
            }
        }
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    /** Sanity check that the scan actually sees the screens, so the test above cannot pass vacuously. */
    @Test
    void theScanFindsEveryKnownStationScreen() throws IOException {
        List<String> found = containerScreens().stream().map(Screen::name).toList();
        for (String expected : List.of("CraftingStationScreen", "StencilTableScreen",
                "PartBuilderScreen", "ToolStationScreen", "ChestScreen")) {
            assertTrue(found.contains(expected), "expected the scan to find " + expected + ", found " + found);
        }
    }
}
