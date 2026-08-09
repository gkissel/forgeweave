package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.SideInventorySlots;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * Guards issue #88: the Tool Station's side-inventory panel rendered far off the right of the
 * window.
 *
 * <p>The station lays its chrome out relative to its own 176px panel, which vanilla centres knowing
 * nothing about the chrome. Hanging the side panel past both info panels put the total span at
 * 536px against a window vanilla only guarantees to be 320 wide -- still 124px overboard at 427 and
 * 18px at 640. Moving it under the tool-tab column on the left costs nothing horizontally, because
 * the tab column already reaches further left than the panel needs to.
 *
 * <p>All coordinates here are GUI pixels relative to the station panel's top-left, which is what the
 * screen's own constants are in.
 */
class ToolStationChromeLayoutTest {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 174;

    /** {@code ToolStationScreen}: 5-column GuiSideButtons grid, 2px gap, 2px beam end-cap. */
    private static final int TAB_COLUMN_LEFT = -(5 * 18 + 4 * 4) - 2 - 2;
    /** Base panel + 2px gap + InfoPanel.WIDTH + the beam's 2px end-cap. */
    private static final int INFO_PANEL_RIGHT = PANEL_WIDTH + 2 + InfoPanel.WIDTH + 2;

    private static int sidePanelLeft() {
        return ToolStationMenu.SIDE_PANEL_X - SideInventorySlots.SLOT_INSET;
    }

    private static int sidePanelRight() {
        return sidePanelLeft() + SideInventorySlots.PANEL_WIDTH;
    }

    private static int chromeWidth() {
        return Math.max(INFO_PANEL_RIGHT, sidePanelRight()) - Math.min(TAB_COLUMN_LEFT, sidePanelLeft());
    }

    @Test
    void theSidePanelIsOnTheLeftEdge() {
        assertTrue(ToolStationMenu.SIDE_PANEL_X < 0,
                "the side panel belongs on the station's left edge, like the Crafting Station's and "
                        + "Part Builder's; got SIDE_PANEL_X = " + ToolStationMenu.SIDE_PANEL_X);
        assertEquals(SideInventorySlots.LEFT_SLOT_X, ToolStationMenu.SIDE_PANEL_X,
                "left-hand side panels should all share SideInventorySlots.LEFT_SLOT_X");
    }

    @Test
    void theSidePanelCostsAlmostNothingHorizontally() {
        // The tab column already reaches -110; the panel needs -122. That 12px is the entire price
        // of showing a neighbour's inventory, and is what makes the left edge the right answer.
        int withoutSidePanel = INFO_PANEL_RIGHT - TAB_COLUMN_LEFT;
        assertTrue(chromeWidth() - withoutSidePanel <= 16,
                "the side panel should add almost nothing to the station's span: " + withoutSidePanel
                        + "px without it, " + chromeWidth() + "px with it");
    }

    @Test
    void theWholeStationFitsATypicalWindow() {
        // 480x270 is 1920x1080 at GUI scale 4 -- the common case the old placement failed.
        int window = 480;
        int left = (window - PANEL_WIDTH) / 2;
        assertTrue(left + Math.min(TAB_COLUMN_LEFT, sidePanelLeft()) >= 0,
                "station chrome runs off the left of a " + window + "px window");
        assertTrue(left + Math.max(INFO_PANEL_RIGHT, sidePanelRight()) <= window,
                "station chrome runs off the right of a " + window + "px window");
    }

    @Test
    void theSidePanelStartsBelowTheTabColumnAndStaysInsideTheGui() {
        int tabColumnBottom = 9 + 18; // one row of tool tabs
        assertTrue(ToolStationMenu.SIDE_PANEL_Y - SideInventorySlots.SLOT_INSET > tabColumnBottom,
                "the side panel must start below the tab column it shares an edge with");

        // A double-chest neighbour is 54 slots = 9 rows; the panel must shed rows rather than draw
        // out through the bottom of a 174px GUI.
        int rows = SideInventoryPanel.visibleRows(
                SideInventorySlots.rows(54), PANEL_HEIGHT, ToolStationMenu.SIDE_PANEL_Y);
        int top = ToolStationMenu.SIDE_PANEL_Y - SideInventorySlots.SLOT_INSET;
        int bottom = top + rows * SideInventorySlots.SLOT_SIZE + SideInventorySlots.BORDER * 2;
        assertTrue(rows >= 1, "the panel should always show at least one row");
        assertTrue(bottom <= PANEL_HEIGHT,
                "a 54-slot neighbour draws to y " + bottom + ", past the " + PANEL_HEIGHT + "px GUI");
    }

    @Test
    void thePanelIsShiftedBackOnScreenRatherThanLost() {
        int width = SideInventorySlots.PANEL_WIDTH;
        assertEquals(0, SideInventoryPanel.onScreen(-50, width, 320), "off the left edge should clamp to 0");
        assertEquals(320 - width, SideInventoryPanel.onScreen(400, width, 320), "off the right edge should clamp in");
        assertEquals(30, SideInventoryPanel.onScreen(30, width, 480), "a panel that already fits must not move");
    }
}
