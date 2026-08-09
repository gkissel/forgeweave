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
 * <p><b>The state.</b> {@code StationScreen#renderBg} draws the station-group tab row before the
 * panel, and that row renders item stacks. {@code GuiGraphics#renderItem} flushes through the item
 * render types, whose {@code clearRenderState} ends in {@code RenderSystem.disableBlend()} -- so
 * every translucent blit in {@code renderPanel} afterwards lands at full opacity unless blending is
 * turned back on in between. Upstream {@code GuiToolStation} does the same thing explicitly ("reset
 * state after item drawing"). Nothing in a unit test can see a GL state machine, so this is an
 * ordering scan over the source, matching {@code StationScreenTooltipTest} and {@code
 * StationSocketAlignmentTest}'s approach -- the test classpath has no Minecraft client.
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
    void renderBgRestoresBlendingBetweenTheTabRowAndThePanel() throws IOException {
        String source = Files.readString(
                projectRoot().resolve("src/main/java/dev/gkissel/forgeweave/client/StationScreen.java"),
                StandardCharsets.UTF_8);

        int renderBg = source.indexOf("protected final void renderBg(");
        assertTrue(renderBg >= 0, "StationScreen no longer declares renderBg -- update this guard");
        // Comments stripped, or commenting the call out would still satisfy the scan below.
        String body = stripComments(source.substring(renderBg, source.indexOf("\n    }", renderBg)));

        int tabs = body.indexOf("renderTabs(graphics, false)");
        int enableBlend = body.indexOf("restoreBlending()");
        int panel = body.indexOf("renderPanel(");

        assertTrue(tabs >= 0, "renderBg no longer draws the tab row first -- update this guard");
        assertTrue(enableBlend >= 0,
                "renderBg must call restoreBlending() after the tab row: renderTabs draws item stacks, "
                        + "which leaves RenderSystem blending disabled, and every translucent blit in "
                        + "renderPanel then lands opaque (issue #84 -- black repair slots)");
        assertTrue(panel >= 0, "renderBg no longer calls renderPanel -- update this guard");
        assertTrue(tabs < enableBlend && enableBlend < panel,
                "restoreBlending() must sit between the tab row and renderPanel; it is the tab row's "
                        + "item rendering that clears the blend state renderPanel depends on");

        assertTrue(stripComments(source).contains("RenderSystem.enableBlend()"),
                "restoreBlending() must actually enable blending");
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
