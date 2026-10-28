package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #84: the Tool Station's repair slots shipped as solid black squares.
 *
 * <p>Two halves, because the defect needed both to be true at once.
 *
 * <p><b>The state.</b> {@code StationScreen#extractBackground} draws the station-group tab row
 * before the panel, and that row draws item stacks. On 1.21.1 drawing an item flushed through the
 * item render types, whose {@code clearRenderState} ended in {@code RenderSystem.disableBlend()}, so
 * every translucent blit in {@code renderPanel} afterwards landed at full opacity unless blending
 * was turned back on in between (upstream {@code GuiToolStation} does the same: "reset state after
 * item drawing"). 26.x took that global state away: a GUI draw is recorded with its own render
 * pipeline and colour and drawn later, so nothing an item does can leak into the next blit. What
 * the slot plate needs now is that its own blit asks for it: a blending pipeline and its 28% alpha
 * in the colour. Nothing in a unit test can see a GL state machine, so this is a scan over the
 * source, matching {@code StationScreenTooltipTest} and {@code StationSocketAlignmentTest}'s
 * approach -- the test classpath has no Minecraft client.
 *
 * <p><b>The sprite.</b> Why losing the alpha is catastrophic rather than merely wrong: upstream's
 * {@code SlotBackground} is an <em>opaque black mask</em>, only ever drawn at 28%. If a future
 * derived sheet made it a real translucent socket plate, the alpha would stop being load-bearing
 * and this guard's rationale would be stale -- so the mask is asserted too.
 */
class StationScreenBlendStateTest {

    /** {@code tool_station.png}'s {@code SlotBackground}, upstream's {@code GuiElement(176, 0, 18, 18)}. */
    private static final int SLOT_BACKGROUND_U = 176;
    private static final int SLOT_BACKGROUND_V = 0;

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
    void theTabRowIsDrawnBeforeThePanel() throws IOException {
        String source = Files.readString(
                projectRoot().resolve("src/main/java/dev/gkissel/forgeweave/client/StationScreen.java"),
                StandardCharsets.UTF_8);

        int extractBackground = source.indexOf("public final void extractBackground(");
        assertTrue(extractBackground >= 0, "StationScreen no longer declares extractBackground -- update this guard");
        // Comments stripped, or commenting the call out would still satisfy the scan below.
        String body = stripComments(source.substring(extractBackground, source.indexOf("\n    }", extractBackground)));

        int tabs = body.indexOf("renderTabs(graphics, false)");
        int panel = body.indexOf("renderPanel(");

        assertTrue(tabs >= 0, "extractBackground no longer draws the tab row first -- update this guard");
        assertTrue(panel >= 0, "extractBackground no longer calls renderPanel -- update this guard");
        assertTrue(tabs < panel, "the tab row's item stacks come before the panel, the order issue #84 was about");
    }

    /**
     * 26.x's half of issue #84: the slot plate is blended because its own blit says so. It goes
     * through {@code RenderPipelines.GUI_TEXTURED} (translucent blending) with a white colour at
     * {@code SLOT_BACKGROUND_ALPHA}, and that constant is still upstream's 28%. An opaque pipeline
     * or a colour without the alpha gives back the black squares.
     */
    @Test
    void theSlotPlateIsBlittedThroughABlendingPipelineAtTwentyEightPercent() throws IOException {
        String source = stripComments(Files.readString(
                projectRoot().resolve("src/main/java/dev/gkissel/forgeweave/client/ToolStationScreen.java"),
                StandardCharsets.UTF_8));

        int plate = source.indexOf("SLOT_BACKGROUND_U, ");
        assertTrue(plate >= 0, "ToolStationScreen no longer blits SLOT_BACKGROUND_U -- update this guard");
        String call = source.substring(source.lastIndexOf("graphics.blit(", plate), source.indexOf(");", plate));
        assertTrue(call.contains("RenderPipelines.GUI_TEXTURED,"),
                "the slot plate must be drawn through GUI_TEXTURED, which blends; got: " + call);
        assertTrue(call.contains("ARGB.white(SLOT_BACKGROUND_ALPHA)"),
                "the slot plate's colour must carry SLOT_BACKGROUND_ALPHA, or it lands opaque (issue #84); got: " + call);
        assertTrue(source.contains("SLOT_BACKGROUND_ALPHA = 0.28F"),
                "upstream draws SlotBackground at 28%");
    }

    /** Drops {@code //} line comments and {@code /* *}{@code /} blocks so commented-out code doesn't match. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void theSlotBackgroundSpriteIsAnOpaqueMask() throws IOException {
        Path png = projectRoot()
                .resolve("src/main/resources/assets/forgeweave/textures/derived/gui/tool_station.png");
        assertTrue(Files.exists(png), png + " is missing");
        BufferedImage sheet = ImageIO.read(png.toFile());

        // The 16x16 interior of the 18x18 sprite: solid black at full alpha, which is only legible
        // as a slot plate when the 28% alpha survives to the framebuffer.
        for (int dy = 1; dy < 17; dy++) {
            for (int dx = 1; dx < 17; dx++) {
                assertEquals(0xFF000000, sheet.getRGB(SLOT_BACKGROUND_U + dx, SLOT_BACKGROUND_V + dy),
                        "SlotBackground is no longer an opaque black mask at +(" + dx + ", " + dy
                                + "). If it became a real translucent plate, the blend guard above "
                                + "still matters for the other alpha blits, but say so there.");
            }
        }
    }
}
