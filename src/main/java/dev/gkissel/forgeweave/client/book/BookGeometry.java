package dev.gkissel.forgeweave.client.book;

/**
 * The 1.12 book's layout arithmetic, ported 1:1 from Mantle's {@code GuiBook}/{@code GuiArrow}
 * (branch {@code 1.12}, commit {@code 340a386af51a97efaac0e71a3f1ff87fb267efe9}, MIT) for issue
 * #430. Upstream draws a fixed 412x200 two-leaf spread centred in GUI coordinates -- it scales with
 * the screen only the way every vanilla screen does, through the GUI scale; {@code PAGE_SCALE} is
 * 1 and there is no fit-to-window scale factor (the ticket's {@code min(width/.., height/..)}
 * formula is later Mantle, not 1.12). Deliberately free of every {@code net.minecraft} type so
 * {@code BookGeometryTest} checks it against upstream's numbers without a client.
 *
 * <p>Slot mapping: upstream's book page -1 is the closed cover, book page 0 shows only the right
 * leaf (reading page 1), and book page n shows reading pages 2n and 2n+1... in its own terms,
 * {@code (page-1)*2+2} and {@code (page-1)*2+3}. Forgeweave calls a reading page a "slot"
 * (0-based) and a book page a "spread", so slot = reading page - 1 and the formulas below are
 * upstream's, shifted by one.
 */
public final class BookGeometry {

    /** Both chrome sheets are 512x512, {@code GuiBook.TEX_SIZE}. */
    public static final int TEX_SIZE = 512;

    // GuiBook's page metrics, PAGE_SCALE = 1.
    public static final int PAGE_MARGIN = 8;
    public static final int PAGE_PADDING_TOP = 4;
    public static final int PAGE_PADDING_BOT = 4;
    public static final int PAGE_PADDING_LEFT = 8;
    public static final int PAGE_PADDING_RIGHT = 0;
    public static final int PAGE_WIDTH_UNSCALED = 206;
    public static final int PAGE_HEIGHT_UNSCALED = 200;

    /** {@code GuiBook.init()}: the drawable page area inside one leaf. 182x176. */
    public static final int PAGE_WIDTH =
            PAGE_WIDTH_UNSCALED - (PAGE_PADDING_LEFT + PAGE_PADDING_RIGHT + PAGE_MARGIN + PAGE_MARGIN);
    public static final int PAGE_HEIGHT =
            PAGE_HEIGHT_UNSCALED - (PAGE_PADDING_TOP + PAGE_PADDING_BOT + PAGE_MARGIN + PAGE_MARGIN);

    /**
     * Upstream draws the page number at {@code PAGE_HEIGHT - 10} and hand-authors content to stay
     * above it; Forgeweave's content is generated, so its paginator stops this many pixels short of
     * {@link #PAGE_HEIGHT} to keep the number row clear (issue #428's guarantee, kept under #430).
     */
    public static final int PAGE_NUMBER_CLEARANCE = 12;

    // GuiArrow: sprite regions on book.png and the appearance-default tints.
    public static final int ARROW_W = 18;
    public static final int ARROW_H = 10;
    public static final int ARROW_NEXT_U = 412;
    public static final int ARROW_NEXT_V = 0;
    public static final int ARROW_PREV_U = 412;
    public static final int ARROW_PREV_V = 10;
    public static final int ARROW_BACK_U = 412;
    public static final int ARROW_BACK_V = 30; // ArrowType.LEFT, the returner arrow
    public static final int ARROW_INDEX_U = 412;
    public static final int ARROW_INDEX_V = 40; // ArrowType.BACK_UP
    public static final int ARROW_INDEX_SIZE = 18;

    // AppearanceData defaults (Tinkers' appearance.json overrides only coverColor) and GuiBook's
    // hardcoded text colors.
    public static final int COVER_COLOR = 0xffce85; // tconstruct book/appearance.json
    public static final int ARROW_COLOR = 0xFFFFD3;
    public static final int ARROW_HOVER_COLOR = 0xFF541C;
    public static final int COVER_TEXT_COLOR = 0xAE8000;
    public static final int PAGE_NUMBER_COLOR = 0xFFAAAAAA;

    private BookGeometry() {
    }

    /** X of the spread blit (and the cover-tinted backdrop): {@code width/2 - PAGE_WIDTH_UNSCALED}. */
    public static int spreadLeft(int width) {
        return width / 2 - PAGE_WIDTH_UNSCALED;
    }

    /** Y of the spread blit: {@code height/2 - PAGE_HEIGHT_UNSCALED/2}. */
    public static int spreadTop(int height) {
        return height / 2 - PAGE_HEIGHT_UNSCALED / 2;
    }

    /** {@code drawerTransform(false)}: where the left leaf's page area starts. */
    public static int leftPageX(int width) {
        return width / 2 - PAGE_WIDTH_UNSCALED + PAGE_PADDING_LEFT + PAGE_MARGIN;
    }

    /** {@code drawerTransform(true)}: where the right leaf's page area starts. */
    public static int rightPageX(int width) {
        return width / 2 + PAGE_PADDING_RIGHT + PAGE_MARGIN;
    }

    /** {@code topOffset()}: where either leaf's page area starts vertically. */
    public static int pageY(int height) {
        return height / 2 - PAGE_HEIGHT_UNSCALED / 2 + PAGE_PADDING_TOP + PAGE_MARGIN;
    }

    /** The slot on a spread's left leaf, or -1: the first spread has no left leaf. */
    public static int leftSlot(int spread) {
        return spread == 0 ? -1 : 2 * spread - 1;
    }

    /** The slot on a spread's right leaf (may be past the end of the slot list). */
    public static int rightSlot(int spread) {
        return 2 * spread;
    }

    /** The spread a slot is shown on -- what a jump to a slot has to open. */
    public static int spreadOf(int slot) {
        return (slot + 1) / 2;
    }

    /** The printed page number: upstream numbers reading pages from 1 on the index leaf. */
    public static int pageNumber(int slot) {
        return slot + 1;
    }

    /** The last spread that still shows a slot. */
    public static int lastSpread(int slotCount) {
        return spreadOf(slotCount - 1);
    }

    /** {@code updateScreen}: {@code previousArrow.x = width/2 - 184}. */
    public static int prevArrowX(int width) {
        return width / 2 - 184;
    }

    /** {@code updateScreen}: {@code width/2 + 165} on a spread, {@code width/2 + 80} on the cover. */
    public static int nextArrowX(int width, boolean cover) {
        return cover ? width / 2 + 80 : width / 2 + 165;
    }

    /** {@code updateScreen}: both page-turn arrows sit at {@code height/2 + 75}. */
    public static int arrowY(int height) {
        return height / 2 + 75;
    }

    /** {@code initGui}: the returner arrow, centred under the spread. */
    public static int backArrowX(int width) {
        return width / 2 - ARROW_W / 2;
    }

    public static int backArrowY(int height) {
        return height / 2 + ARROW_H / 2 + PAGE_HEIGHT / 2;
    }

    /** {@code initGui}: the back-to-index arrow, hanging off the spread's top-left corner. */
    public static int indexArrowX(int width) {
        return width / 2 - PAGE_WIDTH_UNSCALED - ARROW_W / 2;
    }

    public static int indexArrowY(int height) {
        return height / 2 - PAGE_HEIGHT_UNSCALED / 2;
    }
}
