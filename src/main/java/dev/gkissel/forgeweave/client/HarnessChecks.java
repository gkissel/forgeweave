package dev.gkissel.forgeweave.client;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.CreativeModeTab;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The screenshot harness's programmatic self-checks: the parts of the release checklist a reviewer
 * used to judge by eye that a program can answer outright. Each one reports through the {@code fail}
 * sink {@link ScreenshotHarness} hands it, so a failure here fails the harness's exit code the same
 * way a frame that never opened does.
 *
 * <ul>
 *   <li>The log scan (checklist line 8): no Forgeweave WARN or ERROR line in the harness's own
 *       {@code logs/latest.log} by the time the world is joined and the creative tabs were drawn.
 *   <li>The title-screen reload (line 16): a resource reload with no world loaded finishes without
 *       an exception and without "Cannot get config value before config is loaded".
 *   <li>The missing-texture count (line 3): no missing-texture magenta in the worn-armor frames.
 * </ul>
 *
 * <p>Known third-party noise goes in the allowlist file named by {@value #ALLOWLIST_PROPERTY}
 * ({@code scripts/harness-log-allowlist.txt}), one regular expression per line.
 */
final class HarnessChecks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[screenshot harness] ";
    static final String ALLOWLIST_PROPERTY = "forgeweave.harness_log_allowlist";
    static final String CONFIG_NOT_LOADED = "Cannot get config value before config is loaded";

    /**
     * One {@code logs/latest.log} entry header, in FML's file pattern {@code [date] [thread/LEVEL]
     * [logger/marker]: message}. Lines that do not match (stack traces, multi-line messages) belong to
     * the entry above them.
     */
    private static final Pattern ENTRY = Pattern.compile(
            "^\\[[^\\]]*\\] \\[.*?/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\] \\[([^/\\]]*)/[^\\]]*\\]: .*");

    /** One log entry: its level, its logger, and its text with every continuation line attached. */
    record Entry(String level, String logger, String header, String text) {
        boolean isWarnOrWorse() {
            return level.equals("WARN") || level.equals("ERROR") || level.equals("FATAL");
        }

        boolean namesForgeweave() {
            return logger.startsWith("dev.gkissel.forgeweave")
                    || text.toLowerCase(Locale.ROOT).contains(Forgeweave.MODID);
        }

        /** The harness's own scene-check lines already fail the run where they are logged. */
        boolean isHarnessReport() {
            return header.contains(LOG_PREFIX.trim());
        }

        boolean looksLikeAnException() {
            return text.contains(CONFIG_NOT_LOADED) || text.contains("Exception") || text.contains("\tat ");
        }
    }

    private HarnessChecks() {}

    // ------------------------------------------------------------------ log scans (lines 8 and 16)

    /** Splits raw log text into entries. */
    static List<Entry> entries(String log) {
        List<Entry> entries = new ArrayList<>();
        String level = null;
        String logger = null;
        String header = null;
        StringBuilder text = new StringBuilder();
        for (String line : log.split("\\R")) {
            Matcher m = ENTRY.matcher(line);
            if (m.matches()) {
                if (header != null) {
                    entries.add(new Entry(level, logger, header, text.toString()));
                }
                level = m.group(1);
                logger = m.group(2);
                header = line;
                text.setLength(0);
                text.append(line);
            } else if (header != null) {
                text.append('\n').append(line);
            }
        }
        if (header != null) {
            entries.add(new Entry(level, logger, header, text.toString()));
        }
        return entries;
    }

    /** Line 8: every WARN-or-worse entry naming Forgeweave that no allowlist pattern covers. */
    static List<Entry> forgeweaveProblems(String log, List<Pattern> allowlist) {
        return entries(log).stream()
                .filter(e -> e.isWarnOrWorse() && e.namesForgeweave() && !e.isHarnessReport())
                .filter(e -> !allowed(e, allowlist))
                .toList();
    }

    /**
     * Line 16: every entry logged during the title-screen reload that carries an exception or the
     * config-not-loaded message, from any mod, that no allowlist pattern covers. The config message
     * fails at any level, since a caller that catches it may well log it at INFO.
     */
    static List<Entry> reloadProblems(String log, List<Pattern> allowlist) {
        return entries(log).stream()
                .filter(e -> e.text().contains(CONFIG_NOT_LOADED) || (e.isWarnOrWorse() && e.looksLikeAnException()))
                .filter(e -> !e.isHarnessReport())
                .filter(e -> !allowed(e, allowlist))
                .toList();
    }

    private static boolean allowed(Entry entry, List<Pattern> allowlist) {
        return allowlist.stream().anyMatch(p -> p.matcher(entry.header()).find());
    }

    /** Parses the allowlist format: one regex per line, blank lines and {@code #} comments skipped. */
    static List<Pattern> parseAllowlist(List<String> lines) {
        return lines.stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(Pattern::compile)
                .toList();
    }

    private static List<Pattern> allowlist() {
        String path = System.getProperty(ALLOWLIST_PROPERTY);
        if (path == null) {
            return List.of();
        }
        try {
            return parseAllowlist(Files.readAllLines(Path.of(path), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the harness log allowlist " + path, e);
        }
    }

    private static Path latestLog(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("logs").resolve("latest.log");
    }

    /** The current length of {@code latest.log}, so a later read can start where this one stopped. */
    static long logLength(Minecraft mc) {
        try {
            return Files.size(latestLog(mc));
        } catch (IOException e) {
            return 0L;
        }
    }

    /** {@code latest.log} from byte {@code from} to its current end. */
    private static String readLog(Minecraft mc, long from) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(latestLog(mc).toFile(), "r")) {
            long length = file.length();
            byte[] bytes = new byte[(int) Math.max(0, length - from)];
            file.seek(Math.min(from, length));
            file.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** Line 8, after world join and the creative tabs: fails on every Forgeweave WARN/ERROR so far. */
    static void scanLogForForgeweaveProblems(Minecraft mc, Consumer<String> fail) {
        report(mc, "log scan", 0L, HarnessChecks::forgeweaveProblems, fail);
    }

    private static void report(Minecraft mc, String check, long from,
            java.util.function.BiFunction<String, List<Pattern>, List<Entry>> scan, Consumer<String> fail) {
        List<Entry> problems;
        try {
            problems = scan.apply(readLog(mc, from), allowlist());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("{}{} FAILED: could not read {}", LOG_PREFIX, check, latestLog(mc), e);
            fail.accept(check);
            return;
        }
        LOGGER.info("{}{}: {} problem line(s)", LOG_PREFIX, check, problems.size());
        for (Entry problem : problems) {
            // Logged with the prefix, so a later scan skips the report instead of counting it again.
            LOGGER.error("{}{} FAILED: {}", LOG_PREFIX, check, problem.header());
            fail.accept(check + ": " + problem.header());
        }
    }

    // ------------------------------------------------------------------ title-screen reload (line 16)

    @Nullable
    private static CompletableFuture<Void> titleReload;
    private static long titleReloadLogStart;

    /** Starts a resource reload from the title screen, with no world loaded. */
    static void startTitleReload(Minecraft mc) {
        LOGGER.info("{}reloading resource packs on the title screen", LOG_PREFIX);
        titleReloadLogStart = logLength(mc);
        titleReload = mc.reloadResourcePacks();
    }

    /** Whether the reload has finished and its loading overlay is gone. */
    static boolean titleReloadDone(Minecraft mc) {
        return titleReload != null && titleReload.isDone() && mc.getOverlay() == null;
    }

    static void checkTitleReload(Minecraft mc, Consumer<String> fail) {
        if (titleReload == null || titleReload.isCompletedExceptionally()) {
            LOGGER.error("{}title-screen reload FAILED: the reload future completed exceptionally", LOG_PREFIX);
            fail.accept("title-screen reload");
        }
        report(mc, "title-screen reload", titleReloadLogStart, HarnessChecks::reloadProblems, fail);
    }

    // ------------------------------------------------------------------ creative tabs (line 8)

    /** Ticks each Forgeweave creative tab stays open, long enough for every item in it to draw. */
    static final int CREATIVE_TAB_TICKS = 10;

    private static final Field SELECTED_TAB = selectedTabField();

    private static Field selectedTabField() {
        try {
            Field field = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CreativeModeInventoryScreen has no selectedTab field", e);
        }
    }

    /** Every creative tab Forgeweave registers, in registry order. */
    static List<CreativeModeTab> forgeweaveTabs() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.entrySet().stream()
                .filter(e -> e.getKey().location().getNamespace().equals(Forgeweave.MODID))
                .map(java.util.Map.Entry::getValue)
                .toList();
    }

    /**
     * Shows tab {@code index} of {@link #forgeweaveTabs()} in the real creative inventory, the screen a
     * player gets pressing E; returns false once every tab has been shown and the screen closed.
     * The screen's own init picks its static {@code selectedTab}, so setting that first is the same
     * as the player having clicked the tab.
     */
    static boolean showCreativeTab(Minecraft mc, int index) {
        List<CreativeModeTab> tabs = forgeweaveTabs();
        if (index >= tabs.size()) {
            // Escape's path, which also puts the player's containerMenu back on its inventory. A bare
            // setScreen(null) leaves it on the creative picker menu, whose container id is the
            // inventory's own 0, so later armor-slot updates from the server land in the picker
            // and the client never sees the armor the #679 scenes put on.
            if (mc.screen != null) {
                mc.screen.onClose();
            }
            return false;
        }
        try {
            SELECTED_TAB.set(null, tabs.get(index));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        LOGGER.info("{}showing creative tab {}", LOG_PREFIX, tabs.get(index).getDisplayName().getString());
        mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                mc.options.operatorItemsTab().get()));
        return true;
    }

    // ------------------------------------------------------------------ missing textures (line 3)

    /**
     * Whether a pixel is the magenta half of the missing-texture checker ({@code #F800F8}) after the
     * renderer's shading and any material tint multiplied it: green stays at zero, red and blue stay
     * clearly above it. Nothing Forgeweave draws has that colour. The checker's black half is not
     * counted, since shadows and outlines are black too, but it never appears without the magenta.
     */
    static boolean isMissingTextureMagenta(int red, int green, int blue) {
        return green <= 8 && red >= 32 && blue >= 32;
    }

    static int missingTexturePixels(NativeImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                if (isMissingTextureMagenta(FastColor.ABGR32.red(abgr), FastColor.ABGR32.green(abgr),
                        FastColor.ABGR32.blue(abgr))) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Line 3: a worn-armor frame must hold no missing-texture pixel at all. */
    static void checkNoMissingTexture(NativeImage image, String fileName, Consumer<String> fail) {
        int count = missingTexturePixels(image);
        if (count > 0) {
            LOGGER.error("{}missing-texture check FAILED: {} has {} missing-texture magenta pixels",
                    LOG_PREFIX, fileName, count);
            fail.accept(fileName + " (missing texture)");
        } else {
            LOGGER.info("{}missing-texture check: {} has none", LOG_PREFIX, fileName);
        }
    }
}
