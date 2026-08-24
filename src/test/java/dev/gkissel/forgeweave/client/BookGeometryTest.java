package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.client.book.BookGeometry;

/**
 * Issue #430: the guide book must lay out exactly like the 1.12 book. The 1.12 engine is Mantle's
 * {@code GuiBook} (branch {@code 1.12}, commit {@code 340a386af51a97efaac0e71a3f1ff87fb267efe9}),
 * and every expected value below is that class's arithmetic evaluated by hand -- the constants at
 * its top, {@code drawerTransform}/{@code leftOffset}/{@code topOffset}, the page-number formulas
 * in {@code drawScreen}, and {@code initGui}/{@code updateScreen}'s arrow placement -- so a drift
 * in {@link BookGeometry} is a drift from upstream, not from an earlier version of itself.
 */
class BookGeometryTest {

    // GuiBook.init(): PAGE_WIDTH = 206 - (8 + 0 + 8 + 8), PAGE_HEIGHT = 200 - (4 + 4 + 8 + 8).
    @Test
    void pageSizeMatchesUpstreamsPaddingArithmetic() {
        assertEquals(206, BookGeometry.PAGE_WIDTH_UNSCALED);
        assertEquals(200, BookGeometry.PAGE_HEIGHT_UNSCALED);
        assertEquals(182, BookGeometry.PAGE_WIDTH);
        assertEquals(176, BookGeometry.PAGE_HEIGHT);
    }

    /** For a 427x240 window (the 854x480 default at GUI scale 2), upstream's transforms. */
    @Test
    void pageOriginsMatchUpstreamsDrawerTransform() {
        int width = 427;
        int height = 240;
        // drawerTransform(false): width/2 - 206 + 8 + 8; drawerTransform(true): width/2 + 0 + 8.
        assertEquals(width / 2 - 190, BookGeometry.leftPageX(width));
        assertEquals(width / 2 + 8, BookGeometry.rightPageX(width));
        // height/2 - 100 + 4 + 8.
        assertEquals(height / 2 - 88, BookGeometry.pageY(height));
        // The spread's blit origin: width/2 - PAGE_WIDTH_UNSCALED, height/2 - PAGE_HEIGHT_UNSCALED/2.
        assertEquals(width / 2 - 206, BookGeometry.spreadLeft(width));
        assertEquals(height / 2 - 100, BookGeometry.spreadTop(height));
    }

    /**
     * Upstream's page model: book page 0 is the lone right-hand leaf after the cover, book page n
     * shows reading pages 2n-1 and 2n. Its number strings are {@code (page-1)*2+2} on the left and
     * {@code (page-1)*2+3} on the right.
     */
    @Test
    void spreadToSlotMappingMatchesUpstreamsPageModel() {
        assertEquals(-1, BookGeometry.leftSlot(0), "the first spread has no left leaf");
        assertEquals(0, BookGeometry.rightSlot(0));
        assertEquals(1, BookGeometry.leftSlot(1));
        assertEquals(2, BookGeometry.rightSlot(1));
        assertEquals(3, BookGeometry.leftSlot(2));

        assertEquals(0, BookGeometry.spreadOf(0));
        assertEquals(1, BookGeometry.spreadOf(1));
        assertEquals(1, BookGeometry.spreadOf(2));
        assertEquals(2, BookGeometry.spreadOf(3));

        assertEquals(1, BookGeometry.pageNumber(0), "the index leaf is page 1");
        assertEquals(2, BookGeometry.pageNumber(1));
        assertEquals(3, BookGeometry.pageNumber(2));

        assertEquals(0, BookGeometry.lastSpread(1));
        assertEquals(1, BookGeometry.lastSpread(2));
        assertEquals(1, BookGeometry.lastSpread(3));
        assertEquals(2, BookGeometry.lastSpread(4));
    }

    /** initGui/updateScreen: prev at width/2-184, next at width/2+165 (width/2+80 on the cover), y height/2+75. */
    @Test
    void arrowPlacementMatchesUpstream() {
        int width = 427;
        int height = 240;
        assertEquals(width / 2 - 184, BookGeometry.prevArrowX(width));
        assertEquals(width / 2 + 165, BookGeometry.nextArrowX(width, false));
        assertEquals(width / 2 + 80, BookGeometry.nextArrowX(width, true));
        assertEquals(height / 2 + 75, BookGeometry.arrowY(height));
        // backArrow: width/2 - WIDTH/2, height/2 + HEIGHT/2 + PAGE_HEIGHT/2 = height/2 + 5 + 88.
        assertEquals(width / 2 - 9, BookGeometry.backArrowX(width));
        assertEquals(height / 2 + 93, BookGeometry.backArrowY(height));
        // indexArrow: width/2 - PAGE_WIDTH_UNSCALED - WIDTH/2, height/2 - PAGE_HEIGHT_UNSCALED/2.
        assertEquals(width / 2 - 215, BookGeometry.indexArrowX(width));
        assertEquals(height / 2 - 100, BookGeometry.indexArrowY(height));
    }

    /**
     * The structure page's animate-toggle button (issue #651): {@code GuiArrow.ArrowType.REFRESH}
     * is the 18x18 sprite at (412, 76) on book.png, tinted with {@code AppearanceData}'s
     * structure-button defaults -- Tinkers' {@code appearance.json} overrides none of the three.
     */
    @Test
    void refreshArrowSpriteAndTintsMatchUpstream() {
        assertEquals(412, BookGeometry.REFRESH_U);
        assertEquals(76, BookGeometry.REFRESH_V);
        assertEquals(18, BookGeometry.REFRESH_SIZE);
        assertEquals(0xe3E3BC, BookGeometry.STRUCTURE_BUTTON_COLOR);
        assertEquals(0x76D1E8, BookGeometry.STRUCTURE_BUTTON_HOVER_COLOR);
        assertEquals(0x67C768, BookGeometry.STRUCTURE_BUTTON_TOGGLED_COLOR);
    }
}
