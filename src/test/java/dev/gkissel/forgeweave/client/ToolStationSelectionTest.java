package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Issue #733: the selection grid's paging, upstream 1.20 {@code SideButtonsWidgetPaged}'s rules. */
class ToolStationSelectionTest {

    @Test
    void oneShortPageHidesTheArrows() {
        assertEquals(1, ToolStationSelection.pageCount(ToolStationSelection.PAGE_SIZE));
        assertFalse(ToolStationSelection.paged(ToolStationSelection.PAGE_SIZE));
        assertEquals(0, ToolStationSelection.endIndex(0, 0));
        assertEquals(0, ToolStationSelection.rowsOn(0, 0));
    }

    @Test
    void anOverflowingRosterPages() {
        int count = ToolStationSelection.PAGE_SIZE + 7;
        assertTrue(ToolStationSelection.paged(count));
        assertEquals(2, ToolStationSelection.pageCount(count));
        assertEquals(0, ToolStationSelection.firstIndex(0));
        assertEquals(ToolStationSelection.PAGE_SIZE, ToolStationSelection.endIndex(0, count));
        assertEquals(ToolStationSelection.PAGE_SIZE, ToolStationSelection.firstIndex(1));
        assertEquals(count, ToolStationSelection.endIndex(1, count));
        assertEquals(ToolStationSelection.MAX_ROWS, ToolStationSelection.rowsOn(0, count));
        assertEquals(2, ToolStationSelection.rowsOn(1, count), "7 buttons over 6 columns is two rows");
    }

    @Test
    void theSelectedTabsPageIsFoundAndClamped() {
        int count = ToolStationSelection.PAGE_SIZE + 7;
        assertEquals(0, ToolStationSelection.pageOf(ToolStationSelection.PAGE_SIZE - 1));
        assertEquals(1, ToolStationSelection.pageOf(ToolStationSelection.PAGE_SIZE));
        assertEquals(0, ToolStationSelection.pageOf(-1), "an unlisted tab lands on the first page");
        assertEquals(1, ToolStationSelection.clampPage(5, count));
        assertEquals(0, ToolStationSelection.clampPage(-1, count));
        assertEquals(0, ToolStationSelection.clampPage(3, 2));
    }
}
