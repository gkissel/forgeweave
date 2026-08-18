package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.menu.ChestMenu;

/**
 * Parity audit T45 (issue #476): the Pattern/Part Chest is upstream's scaling chest, not a vanilla
 * double chest with page arrows.
 *
 * <p>Upstream {@code GuiPatternChest} draws {@code textures/gui/blank.png} -- a plain 176x166
 * station panel whose whole upper half is empty -- and fills that space with a {@code
 * GuiScalingChest}: a {@code GuiDynInventory} at {@code xOffset 7, yOffset 17, xSize 162, ySize 54}
 * whose columns are {@code (xSize - slider.width) / 18} and rows {@code ySize / 18}, with {@code
 * GuiGeneric}'s 12px-wide slider always shown down the right of that box. Forgeweave shipped a
 * vanilla {@code generic_54.png} instead, with a fixed 9x6 grid and {@code &lt; page x/y &gt;}
 * arrows in the title row.
 *
 * <p>Numbers here are upstream's own arithmetic rather than restated magic constants, and the
 * derived art is measured off the file the way {@code SmelteryMeltGridTest} measures
 * {@code smeltery.png}.
 */
class ChestScalingGuiTest {

    /** Upstream {@code GuiDynInventory}: the module's box inside the panel. */
    private static final int MODULE_X = 7;
    private static final int MODULE_Y = 17;
    private static final int MODULE_WIDTH = 162;
    private static final int MODULE_HEIGHT = 54;
    /** Upstream {@code GuiGeneric.sliderBackground}: 12px wide. */
    private static final int SLIDER_WIDTH = 12;
    private static final int SLOT = 18;

    private static final String BLANK_PNG =
            "src/main/resources/assets/forgeweave/textures/derived/gui/blank.png";

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("no settings.gradle above " + Path.of("").toAbsolutePath());
    }

    private static BufferedImage blank() throws IOException {
        return ImageIO.read(projectRoot().resolve(BLANK_PNG).toFile());
    }

    @Test
    void theGridIsUpstreamsEightByThreeScrollWindow() {
        assertEquals((MODULE_WIDTH - SLIDER_WIDTH) / SLOT, ChestMenu.COLUMNS,
                "columns must be upstream's (xSize - slider.width) / slot.w");
        assertEquals(MODULE_HEIGHT / SLOT, ChestMenu.ROWS, "rows must be upstream's ySize / slot.h");
        assertEquals(24, ChestMenu.VISIBLE_SLOTS, "a scaling chest shows 24 slots at a time, not a page of 54");
    }

    @Test
    void theFirstSlotSitsInsideTheModulesOwnBevel() {
        // Upstream GuiDynInventory#updateSlots: slot.xPos = xOffset + x + 1, yPos = yOffset + y + 1 --
        // generic.png's slot tile is a 1px bevel drawn around its 16x16 socket (SideInventorySlots.SLOT_INSET).
        assertEquals(MODULE_X + 1, ChestMenu.slotX(0), "first slot x");
        assertEquals(MODULE_Y + 1, ChestMenu.slotY(0), "first slot y");
        assertEquals(MODULE_X + 1 + (ChestMenu.COLUMNS - 1) * SLOT, ChestMenu.slotX(ChestMenu.COLUMNS - 1),
                "last column's x");
        assertEquals(MODULE_Y + 1 + SLOT, ChestMenu.slotY(ChestMenu.COLUMNS), "the second row's y");
    }

    @Test
    void theSliderRunsDownTheRightOfTheModule() {
        assertEquals(MODULE_X + MODULE_WIDTH - SLIDER_WIDTH, ChestScreen.SLIDER_X, "slider x inside the panel");
        assertEquals(MODULE_Y, ChestScreen.SLIDER_Y, "slider y inside the panel");
        assertEquals(MODULE_HEIGHT, ChestScreen.SLIDER_HEIGHT, "the slider is as tall as the module");
    }

    @Test
    void scrollingOnlyStartsOnceTheChestOutgrowsTheWindow() {
        assertEquals(0, ChestMenu.maxScrollRow(1), "a fresh chest has nothing to scroll");
        assertEquals(0, ChestMenu.maxScrollRow(ChestMenu.VISIBLE_SLOTS), "a full window still has nothing to scroll");
        assertEquals(1, ChestMenu.maxScrollRow(ChestMenu.VISIBLE_SLOTS + 1), "one slot over is one row of scroll");
        assertEquals(1, ChestMenu.maxScrollRow(ChestMenu.VISIBLE_SLOTS + ChestMenu.COLUMNS),
                "a whole extra row is still one row of scroll");
        assertEquals(2, ChestMenu.maxScrollRow(ChestMenu.VISIBLE_SLOTS + ChestMenu.COLUMNS + 1), "and then two");
        assertEquals(ChestBlockEntity.MAX_SLOTS / ChestMenu.COLUMNS - ChestMenu.ROWS,
                ChestMenu.maxScrollRow(ChestBlockEntity.MAX_SLOTS), "a full 256-slot chest");
    }

    @Test
    void thePanelIsUpstreamsBlankStationBackground() throws IOException {
        BufferedImage blank = blank();
        assertEquals(256, blank.getWidth(), "the derived sheet is upstream's own 256x256 file");
        assertEquals(256, blank.getHeight(), "the derived sheet is upstream's own 256x256 file");
        assertEquals(blank.getWidth(), ChestScreen.SHEET, "the screen must blit against the real sheet size");

        // The panel is the sheet's opaque top-left corner; everything past it is transparent.
        // Its four literal corner pixels are cut away (vanilla's own rounded GUI corner), so the
        // last drawn pixel of the bottom edge is what pins the panel's size.
        assertTrue(opaque(blank, ChestScreen.PANEL_WIDTH / 2, ChestScreen.PANEL_HEIGHT - 1),
                "the panel's bottom edge must reach " + ChestScreen.PANEL_HEIGHT + "px");
        assertTrue(opaque(blank, ChestScreen.PANEL_WIDTH - 1, ChestScreen.PANEL_HEIGHT / 2),
                "the panel's right edge must reach " + ChestScreen.PANEL_WIDTH + "px");
        assertTrue(!opaque(blank, ChestScreen.PANEL_WIDTH, 0) && !opaque(blank, 0, ChestScreen.PANEL_HEIGHT),
                "the panel must be exactly " + ChestScreen.PANEL_WIDTH + "x" + ChestScreen.PANEL_HEIGHT);
    }

    /**
     * The whole point of {@code blank.png}: the module's box is bare background, so the scaling
     * chest's own slot tiles are what the player sees there. A sheet with slots baked in (vanilla's
     * {@code generic_54.png}, which this replaces) would show a fixed grid underneath them.
     */
    @Test
    void theModulesBoxIsEmptyBackgroundForTheScalingGridToDrawInto() throws IOException {
        BufferedImage blank = blank();
        int background = blank.getRGB(MODULE_X, MODULE_Y);
        for (int y = MODULE_Y; y < MODULE_Y + MODULE_HEIGHT; y++) {
            for (int x = MODULE_X; x < MODULE_X + MODULE_WIDTH; x++) {
                assertEquals(background, blank.getRGB(x, y),
                        "blank.png must have no art at " + x + "," + y + " -- the grid is drawn there");
            }
        }
    }

    /** Upstream {@code ContainerPatternChest}: {@code addPlayerInventory(playerInventory, 8, 84)}. */
    @Test
    void thePlayerInventorySitsWhereBlankPngDrawsIt() {
        assertEquals(84, ChestMenu.PLAYER_INVENTORY_Y, "upstream's own player inventory offset");
        assertEquals(166, ChestScreen.PANEL_HEIGHT, "a scaling chest is a normal-height station panel");
    }

    private static boolean opaque(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) != 0;
    }
}
