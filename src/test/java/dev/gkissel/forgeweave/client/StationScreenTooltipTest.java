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
 * <p>{@code AbstractContainerScreen#render} does not call {@code renderTooltip} -- every vanilla
 * container screen overrides {@code render} to add it. A mod screen that doesn't ships with no item
 * tooltips whatsoever, and nothing catches it: it compiles, it runs, it just silently never shows
 * hover text. Issue #43 hit this once and #57 fixed it by pasting the two-line override into two
 * screens; the next three screens added ({@code CraftingStationScreen}, {@code StencilTableScreen},
 * {@code ChestScreen}) each shipped without it again.
 *
 * <p>So {@link StationScreen} now owns that override and this test is the ratchet: every container
 * screen in the {@code client} package must either extend it or override {@code render} itself.
 * Adding a screen that does neither fails the build.
 *
 * <p>A plain source scan rather than reflection, matching {@code data.TextureReferenceAuditTest} and
 * {@code LocalizationAuditTest}: the unit-test classpath has no Minecraft client, so {@code
 * AbstractContainerScreen} cannot be loaded to walk a real class hierarchy here.
 */
class StationScreenTooltipTest {

    private static final Pattern CONTAINER_SCREEN = Pattern.compile(
            "class\\s+(\\w+)\\s+extends\\s+(AbstractContainerScreen|StationScreen)\\s*<");
    private static final Pattern RENDER_OVERRIDE = Pattern.compile(
            "public\\s+void\\s+render\\s*\\(\\s*GuiGraphics");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private record Screen(String name, String superClass, boolean overridesRender) {}

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
                            RENDER_OVERRIDE.matcher(source).find()));
                }
            }
        }
        return screens;
    }

    @Test
    void everyContainerScreenRendersTooltips() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Screen screen : containerScreens()) {
            if (!screen.superClass().equals("StationScreen") && !screen.overridesRender()) {
                offenders.add(screen.name() + " extends " + screen.superClass()
                        + " and does not override render(GuiGraphics, ...), so it never calls "
                        + "renderTooltip and shows no item tooltips -- extend StationScreen instead");
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
