package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The screenshot harness's log scans and missing-texture test (checklist lines 3, 8 and 16), on
 * fixed input: the harness only runs by hand, so a scan that quietly matches nothing would never
 * be noticed there.
 */
class HarnessChecksTest {
    private static final String OWN_WARN =
            "[26Oct2026 14:21:59.001] [Render thread/WARN] [dev.gkissel.forgeweave.client.Foo/]: a forgeweave warning";
    private static final String MODEL_WARN = "[26Oct2026 14:21:59.002] [Worker-Main-3/WARN] "
            + "[net.minecraft.client.resources.model.BlockStateModelLoader/]: Exception loading blockstate definition: "
            + "'forgeweave:blockstates/molten_iron.json' missing model for variant: 'forgeweave:molten_iron#level=5'";
    private static final String OTHER_WARN =
            "[26Oct2026 14:21:59.003] [Render thread/WARN] [mezz.jei.Foo/]: something about jei only";
    private static final String INFO =
            "[26Oct2026 14:21:59.004] [Render thread/INFO] [dev.gkissel.forgeweave.Forgeweave/]: hello";
    private static final String HARNESS_REPORT = "[26Oct2026 14:21:59.005] [Render thread/ERROR] "
            + "[dev.gkissel.forgeweave.client.ScreenshotHarness/]: [screenshot harness] #200 scene check FAILED: x";
    private static final String THIRD_PARTY_TRACE_HEADER =
            "[26Oct2026 14:21:59.006] [Render thread/ERROR] [mezz.jei.Plugin/]: plugin failed";
    private static final String TRACE_LINE = "\tat dev.gkissel.forgeweave.jei.ForgeweaveJeiPlugin.register(ForgeweaveJeiPlugin.java:10)";

    private static String log(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    void theScanFindsForgeweaveWarningsByLoggerByTextAndByStackTrace() {
        List<HarnessChecks.Entry> problems = HarnessChecks.forgeweaveProblems(
                log(OWN_WARN, MODEL_WARN, OTHER_WARN, INFO, THIRD_PARTY_TRACE_HEADER, TRACE_LINE), List.of());

        assertEquals(List.of(OWN_WARN, MODEL_WARN, THIRD_PARTY_TRACE_HEADER),
                problems.stream().map(HarnessChecks.Entry::header).toList());
    }

    @Test
    void theHarnessOwnReportsAreNotCountedTwice() {
        assertTrue(HarnessChecks.forgeweaveProblems(log(HARNESS_REPORT), List.of()).isEmpty());
    }

    @Test
    void anAllowlistEntryHidesOnlyTheLinesItMatches() {
        List<Pattern> allowlist = HarnessChecks.parseAllowlist(List.of(
                "# comment", "", "  BlockStateModelLoader.*forgeweave:blockstates/molten_  "));

        List<HarnessChecks.Entry> problems = HarnessChecks.forgeweaveProblems(log(OWN_WARN, MODEL_WARN), allowlist);

        assertEquals(List.of(OWN_WARN), problems.stream().map(HarnessChecks.Entry::header).toList());
    }

    @Test
    void theReloadScanFailsOnTheConfigMessageAtAnyLevelAndOnAnyLoggedException() {
        String configInfo = "[26Oct2026 14:21:59.007] [Render thread/INFO] [some.Mod/]: "
                + HarnessChecks.CONFIG_NOT_LOADED + ". Requested key: x";
        String plainWarn = "[26Oct2026 14:21:59.008] [Render thread/WARN] [some.Mod/]: nothing thrown";

        List<HarnessChecks.Entry> problems = HarnessChecks.reloadProblems(
                log(configInfo, plainWarn, THIRD_PARTY_TRACE_HEADER, TRACE_LINE, INFO), List.of());

        assertEquals(List.of(configInfo, THIRD_PARTY_TRACE_HEADER),
                problems.stream().map(HarnessChecks.Entry::header).toList());
    }

    /** The real allowlist parses, so a typo in it fails here rather than at the end of a harness run. */
    @Test
    void theShippedAllowlistParses() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        HarnessChecks.parseAllowlist(Files.readAllLines(root.resolve("scripts/harness-log-allowlist.txt")));
    }

    @Test
    void missingTextureMagentaSurvivesShadingAndTintButNothingElseCounts() {
        assertTrue(HarnessChecks.isMissingTextureMagenta(0xF8, 0x00, 0xF8), "the raw checker colour");
        assertTrue(HarnessChecks.isMissingTextureMagenta(0x7C, 0x00, 0x7C), "half-shaded");
        assertTrue(HarnessChecks.isMissingTextureMagenta(0x40, 0x00, 0xC0), "under a blue tint");
        assertFalse(HarnessChecks.isMissingTextureMagenta(0x00, 0x00, 0x00), "the checker's black half");
        assertFalse(HarnessChecks.isMissingTextureMagenta(0x80, 0x50, 0x90), "a purple skin or obsidian");
        assertFalse(HarnessChecks.isMissingTextureMagenta(0xC8, 0xC8, 0xC8), "iron grey");
    }
}
