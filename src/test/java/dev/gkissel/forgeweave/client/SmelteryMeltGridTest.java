package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.SideInventorySlots;
import dev.gkissel.forgeweave.menu.SmelteryMenu;

/**
 * Where the smeltery's melt grid is and how big it gets: issue #146's "no fragment floating in the
 * panel art's notch" and issue #408's "sized to the smeltery like upstream", which turn out to be
 * the same fix.
 *
 * <p>#146's defect came from putting the grid somewhere upstream does not put it. Upstream's melting
 * inventory ({@code GuiSmelterySideInventory}) is a {@code connected} module built
 * {@code rightSide = false}, which {@code GuiSideInventory#updateSlots} hangs off the <em>left</em>
 * of the parent GUI ({@code slot.xPos -= this.xSize}); the transparent top-right of
 * {@code smeltery.png} is unused space in upstream's art, which is why upstream's own 1.20 rewrite
 * ({@code heating_structure.png}) squared the art off and kept the left-hand module. Forgeweave used
 * to draw the grid inside that notch -- a hole of fixed size -- where a frame sized to a two-slot
 * smeltery floated over the rest of it, which is the "corrupted 2-slot fragment" the alpha.1
 * playtest reported.
 *
 * <p>So this pins both halves: nothing is ever drawn in the notch (the grid's whole rectangle is
 * left of the panel), and the grid is upstream's own size -- {@code ceil(slots / 3)} rows, capped by
 * {@code calcCappedYSize(parentHeight - 10)}, a slider past the cap and a slider's width of extra
 * panel with it.
 *
 * <p>The notch is measured off the real {@code smeltery.png} rather than restated as numbers, in the
 * style of {@code StationSocketAlignmentTest}. The pixels themselves are checked by the screenshot
 * harness's {@code smeltery_empty} (two slots, one row) and {@code smeltery_large} (ten rows in a
 * seven-row window, so a slider) captures.
 */
class SmelteryMeltGridTest {

    private static final String SMELTERY_PNG =
            "src/main/resources/assets/forgeweave/textures/derived/gui/smeltery.png";

    /** The GUI's own rectangle inside the 256x256 sheet; {@code SmelteryScreen.BASE_WIDTH}. */
    private static final int PANEL_WIDTH = 176;

    /** The smallest smeltery there is: a 1x1x2 interior, two melt slots -- issue #146's own case. */
    private static final int MINIMUM_SMELTERY = 2;

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
     * Issue #146. The grid is a module hanging off the panel, so its whole rectangle -- frame
     * included, at every smeltery size -- is left of the panel's own left edge but for the pixel
     * column upstream's {@code connected} overlap deliberately shares with it. Nothing of it can
     * therefore reach the notch, which is the only way a fragment ever appeared in there.
     */
    @Test
    void nothingIsEverDrawnInThePanelArtsNotch() {
        for (int slots : new int[] {1, MINIMUM_SMELTERY, 3, 9, 18, 27, 64}) {
            int right = SmelteryMenu.gridX(slots) + SmelteryMenu.gridWidth(slots);
            assertEquals(SmelteryMenu.GRID_OVERLAP, right,
                    slots + " melt slots: the grid must stop at the panel's edge, lapping it by the "
                            + "connected module's one pixel column");
            assertTrue(SmelteryMenu.gridX(slots) < 0, slots + " melt slots: the grid must hang off the panel");
        }
    }

    /**
     * ...and the notch really is a hole in the art, so "nothing is drawn there" is a statement about
     * the art and not just about the grid's numbers. Sampled where the grid used to be drawn.
     */
    @Test
    void theNotchIsTransparentArtNothingCovers() throws IOException {
        BufferedImage png = panel();
        for (int y = SmelteryMenu.GRID_Y; y < SmelteryMenu.gridHeight(MINIMUM_SMELTERY); y++) {
            assertTrue(transparent(png, PANEL_WIDTH - 1 - SmelteryMenu.GRID_BORDER, y),
                    "smeltery.png is opaque at the top-right corner the melt grid no longer covers");
        }
    }

    /**
     * The scroll clamp. A smeltery that fits in its own grid has nothing above or below the window to
     * scroll to, and must not offer a slider.
     */
    @Test
    void aSmelteryThatFitsItsGridNeverScrolls() {
        int windowSlots = SmelteryMenu.MELT_MAX_ROWS * SmelteryMenu.MELT_COLUMNS;
        for (int slots = 0; slots <= windowSlots; slots++) {
            assertEquals(0, SmelteryMenu.meltScrollRows(slots),
                    "a " + slots + "-slot smeltery fits its own grid, so it must not scroll");
        }
        assertEquals(1, SmelteryMenu.meltScrollRows(windowSlots + 1),
                "one slot past a full window is one scrollable row");
    }

    /**
     * Upstream {@code GuiSideInventory#getTotalRows}: a row per {@link SmelteryMenu#MELT_COLUMNS}
     * slots, bounded above by {@link SmelteryMenu#MELT_MAX_ROWS}. The counts are the ones issue #408
     * names, plus the 1x1x2 minimum: one row for a tiny smeltery, seven for a 3x3x3 or 4x4x4 one.
     */
    @Test
    void theGridSizesItselfToTheSmeltery() {
        assertEquals(1, SmelteryMenu.visibleMeltRows(1));
        assertEquals(1, SmelteryMenu.visibleMeltRows(MINIMUM_SMELTERY));
        assertEquals(1, SmelteryMenu.visibleMeltRows(3));
        assertEquals(3, SmelteryMenu.visibleMeltRows(9));
        assertEquals(4, SmelteryMenu.visibleMeltRows(10));
        assertEquals(6, SmelteryMenu.visibleMeltRows(18));
        assertEquals(7, SmelteryMenu.visibleMeltRows(27));
        assertEquals(7, SmelteryMenu.visibleMeltRows(64));
    }

    /**
     * Upstream {@code GuiSideInventory#calcCappedYSize(parentSizeY - 10)}: shed whole rows until the
     * framed grid fits in the parent's height less 10px. Asserted as the property rather than as the
     * number 7, so the cap follows the art if the panel ever changes height.
     */
    @Test
    void theCapIsUpstreamsCalcCappedYSize() {
        int max = SmelteryMenu.PANEL_HEIGHT - SmelteryMenu.PANEL_MARGIN;
        assertTrue(SmelteryMenu.gridHeight(64) <= max,
                "the tallest melt grid does not fit in the panel less upstream's 10px margin");
        assertTrue(SmelteryMenu.gridHeight(64) + SmelteryMenu.SLOT_SIZE > max,
                "another row of melt slots fits inside the cap; the grid is shorter than upstream's");
    }

    /**
     * Upstream {@code GuiSideInventory#updatePosition}: the slider is enabled exactly when the
     * displayed rows are fewer than the total, and while it is, {@code xSize} gains its width -- so
     * for a left-hung module the whole grid, slots included, shifts that much further left.
     */
    @Test
    void theSliderAppearsOnlyWhenTheRowsOverflow() {
        int bare = SmelteryMenu.MELT_COLUMNS * SmelteryMenu.CELL_WIDTH + SmelteryMenu.GRID_BORDER * 2;
        for (int slots : new int[] {1, 3, 9, 18, SmelteryMenu.MELT_MAX_ROWS * SmelteryMenu.MELT_COLUMNS}) {
            assertEquals(0, SmelteryMenu.meltScrollRows(slots), slots + " melt slots must not scroll");
            assertEquals(bare, SmelteryMenu.gridWidth(slots), slots + " melt slots must not widen the grid");
        }
        assertEquals(2, SmelteryMenu.meltScrollRows(27), "a 3x3x3 interior is nine rows in a seven-row window");
        assertEquals(15, SmelteryMenu.meltScrollRows(64), "a 4x4x4 interior is 22 rows in a seven-row window");
        for (int slots : new int[] {27, 64}) {
            assertEquals(bare + SideInventorySlots.SLIDER_WIDTH, SmelteryMenu.gridWidth(slots));
            assertEquals(SmelteryMenu.gridX(9) - SideInventorySlots.SLIDER_WIDTH, SmelteryMenu.gridX(slots),
                    slots + " melt slots: the slider must push the whole module left, not overlap the panel");
        }
    }

    /**
     * Upstream {@code GuiSmelterySideInventory#updateSlots}' {@code xOffset += 4}: the slot sits past
     * its cell's heat bar, and the whole row sits inside the frame. Pins that the slots moved with
     * the frame when it moved off the panel -- a frame and slots that disagree is issue #79.
     */
    @Test
    void theSlotsSitInTheCellsTheGridDraws() {
        for (int slots : new int[] {1, 9, 27}) {
            int inner = SmelteryMenu.gridX(slots) + SmelteryMenu.GRID_BORDER;
            for (int index = 0; index < SmelteryMenu.MELT_COLUMNS; index++) {
                int cell = inner + index * SmelteryMenu.CELL_WIDTH;
                assertEquals(cell + SmelteryMenu.HEAT_BAR_WIDTH + 1, SmelteryMenu.meltSlotX(index, slots),
                        slots + " melt slots, column " + index);
            }
        }
    }
}
