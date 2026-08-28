package dev.gkissel.forgeweave.client;

/**
 * The Tool Station's tool-selection grid paging (issue #733): the arithmetic of upstream 1.20's
 * {@code SideButtonsWidgetPaged} -- {@code COLUMNS} columns, at most {@code MAX_ROWS} rows a page,
 * previous/next arrows only once the roster overflows one page (NOTICE.md). Pure integers so
 * {@code ToolStationSelectionTest} can pin it without a client; {@link ToolStationScreen} owns the
 * pixels.
 *
 * <p>Upstream caps a page at eight rows. Four here: the station panel is 174px tall and the arrow
 * row plus the side-inventory panel ({@code ToolStationMenu#SIDE_PANEL_Y}) have to fit under the
 * grid, which eight 22px rows never could.
 */
public final class ToolStationSelection {
    public static final int COLUMNS = 6;
    public static final int MAX_ROWS = 4;
    public static final int PAGE_SIZE = COLUMNS * MAX_ROWS;

    private ToolStationSelection() {}

    public static int pageCount(int count) {
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Whether the arrows show at all: upstream hides both while one page holds everything. */
    public static boolean paged(int count) {
        return count > PAGE_SIZE;
    }

    /** The page holding roster position {@code index}. */
    public static int pageOf(int index) {
        return Math.max(0, index) / PAGE_SIZE;
    }

    public static int clampPage(int page, int count) {
        return Math.clamp(page, 0, pageCount(count) - 1);
    }

    /** First roster position on {@code page}. */
    public static int firstIndex(int page) {
        return page * PAGE_SIZE;
    }

    /** One past the last roster position on {@code page}. */
    public static int endIndex(int page, int count) {
        return Math.min(firstIndex(page) + PAGE_SIZE, count);
    }

    /** Rows the grid draws on {@code page}. */
    public static int rowsOn(int page, int count) {
        int shown = endIndex(page, count) - firstIndex(page);
        return shown <= 0 ? 0 : (shown - 1) / COLUMNS + 1;
    }
}
