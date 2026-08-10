package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.SmelteryMenu;

/**
 * Guards issue #146: the smeltery's melt grid must be one fixed frame, sized to the hole in the
 * panel art it sits in, and never to the smeltery's slot count.
 *
 * <p>The defect it pins came from copying upstream's sizing without its layout. Upstream's melting
 * inventory ({@code GuiSmelterySideInventory}) is a module hanging off the <em>side</em> of a
 * rectangular GUI, so sizing it to the slot count costs nothing: a short panel just sits next to the
 * main window. Forgeweave puts the same grid inside the notch of the L-shaped panel art -- a hole of
 * fixed size -- where a short frame leaves the rest of that hole see-through. A minimum-size
 * smeltery (1x1x2 interior, two melt slots) therefore drew a single-row frame floating over a
 * transparent gap, which is the "corrupted 2-slot fragment" the alpha.1 playtest reported.
 *
 * <p>So the two assertions below are: the frame fills the notch (nothing that fits is left showing
 * through), and it is the largest whole grid that fits inside it. Both are measured off the real
 * {@code smeltery.png} rather than restated as numbers, in the style of
 * {@code StationSocketAlignmentTest} -- the constants and the art have to agree, and neither can
 * drift alone. The pixels themselves are checked by the screenshot harness's {@code smeltery_empty}
 * capture, which builds exactly the smeltery that broke.
 */
class SmelteryMeltGridTest {

    private static final String SMELTERY_PNG =
            "src/main/resources/assets/forgeweave/textures/derived/gui/smeltery.png";

    /** The GUI's own rectangle inside the 256x256 sheet; {@code SmelteryScreen.BASE_WIDTH}. */
    private static final int PANEL_WIDTH = 176;

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static BufferedImage panel() throws IOException {
        Path png = projectRoot().resolve(SMELTERY_PNG);
        assertTrue(Files.exists(png), png + " is missing");
        return ImageIO.read(png.toFile());
    }

    private static boolean transparent(BufferedImage png, int x, int y) {
        return (png.getRGB(x, y) >>> 24) == 0;
    }

    /**
     * How far the notch runs right of {@code x} along {@code y}, i.e. the hole's width at that row.
     * Stopped at the panel's own right edge: the sheet is 256px wide and everything past 176 is
     * either blank or one of the sheet's other regions (the scale overlay, the heat bars), none of
     * which is part of the GUI's rectangle.
     */
    private static int transparentRunRight(BufferedImage png, int x, int y) {
        int end = x;
        while (end < PANEL_WIDTH && transparent(png, end, y)) {
            end++;
        }
        return end - x;
    }

    /** How far the notch runs down from {@code y} along {@code x}, i.e. the hole's height at that column. */
    private static int transparentRunDown(BufferedImage png, int x, int y) {
        int end = y;
        while (end < png.getHeight() && transparent(png, x, end)) {
            end++;
        }
        return end - y;
    }

    /**
     * Measured through the middle of the grid rather than at its edges: the frame deliberately
     * overlaps the panel's own edge by a pixel (so the two read as one window) and the notch's
     * corners are rounded, so only the hole's interior is a straight answer.
     */
    private static int notchWidth(BufferedImage png) {
        int y = SmelteryMenu.GRID_Y + SmelteryMenu.GRID_BORDER;
        int x = SmelteryMenu.GRID_X + SmelteryMenu.GRID_BORDER;
        assertTrue(transparent(png, x, y), "smeltery.png has no notch at the melt grid's origin");
        return x + transparentRunRight(png, x, y) - SmelteryMenu.GRID_X;
    }

    private static int notchHeight(BufferedImage png) {
        int x = SmelteryMenu.GRID_X + SmelteryScreen.gridWidth() / 2;
        int y = SmelteryMenu.GRID_Y;
        return y + transparentRunDown(png, x, y) - SmelteryMenu.GRID_Y;
    }

    @Test
    void theGridFillsThePanelArtsNotch() throws IOException {
        BufferedImage png = panel();
        assertTrue(SmelteryScreen.gridWidth() <= notchWidth(png),
                "the melt grid is wider than the hole it is drawn in");
        assertTrue(SmelteryScreen.gridHeight() <= notchHeight(png),
                "the melt grid is taller than the hole it is drawn in");
        assertTrue(notchWidth(png) - SmelteryScreen.gridWidth() < SmelteryMenu.CELL_WIDTH,
                "another column of melt slots fits in the notch; the grid is leaving art-sized holes");
        assertTrue(notchHeight(png) - SmelteryScreen.gridHeight() < SmelteryMenu.SLOT_SIZE,
                "another row of melt slots fits in the notch; the grid is leaving art-sized holes");
    }

    /**
     * The scroll clamp's half of the same invariant. A smeltery smaller than the drawn window has
     * fewer content rows than the grid has cells -- issue #146's own case -- and the grid must
     * neither shrink to those rows (it is a hole-sized frame, see the class javadoc) nor scroll,
     * because there is nothing above or below the window to scroll to.
     */
    @Test
    void aSmelterySmallerThanTheWindowNeverScrolls() {
        int windowSlots = SmelteryMenu.MELT_VISIBLE_ROWS * SmelteryMenu.MELT_COLUMNS;
        for (int slots = 0; slots <= windowSlots; slots++) {
            assertEquals(0, Math.max(0, SmelteryMenu.meltRows(slots) - SmelteryMenu.MELT_VISIBLE_ROWS),
                    "a " + slots + "-slot smeltery fits the window, so its grid must not scroll");
        }
        assertEquals(1, Math.max(0, SmelteryMenu.meltRows(windowSlots + 1) - SmelteryMenu.MELT_VISIBLE_ROWS),
                "one slot past a full window is one scrollable row");
    }
}
