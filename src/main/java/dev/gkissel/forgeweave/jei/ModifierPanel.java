package dev.gkissel.forgeweave.jei;

/**
 * Upstream {@code ModifierRecipeCategory}'s own panel geometry (`~/development/minecraft/references/
 * tinkers-1.20` @ de26560d, MIT -- NOTICE.md), shared by the two Forgeweave categories that draw the
 * same picture: {@link ModifierApplicationCategory} and {@link EmbossingCategory}.
 *
 * <p>Issue #804 replaced both categories' old layout -- a 274px row of five reagent slots followed by
 * an arrow and a 120px text column, on a Part Builder crop tiled to reach that width -- with this
 * one. That row was wider than JEI's recipe popup, so the maintainer's screenshots show it running
 * off the popup's right edge into the item list, with the tiled crop's own baked slot frames
 * repeating behind it as ghost slots and the name/level text drawn across them. Upstream's panel is
 * exactly the shape both categories need and was there all along: a ring of five reagent slots
 * around the tool, the resulting tool to the right of an arrow, the modifier's name across the top
 * and its level line centred under that.
 *
 * <p>#785's note that "modifier application has no upstream counterpart" was wrong -- it read
 * upstream's five ring slots as "a six-slot upgrade-socket layout". The five slots are the recipe's
 * five inputs, which is exactly {@code menu.ToolStationMenu#INPUT_SLOTS}' own count.
 */
final class ModifierPanel {
    private ModifierPanel() {}

    static final JeiCategoryGeometry.Panel PANEL = JeiCategoryGeometry.MODIFIER_APPLICATION;
    static final int WIDTH = PANEL.width();
    static final int HEIGHT = PANEL.height();

    /**
     * Upstream's five input slots, in ring order: `addSlot(INPUT, 25, 15)` top, `(3, 33)` left,
     * `(47, 33)` right, `(7, 58)` bottom-left, `(43, 58)` bottom-right. Upstream declares them
     * 0..4 as (3,33), (25,15), (47,33), (43,58), (7,58); the order here is only the order slots get
     * filled as a recipe needs more of them, and reads outward from the top.
     */
    static final int[][] INPUT_SLOTS = {{25, 15}, {3, 33}, {47, 33}, {7, 58}, {43, 58}};

    /** Upstream's own tool slot -- `addSlot(CATALYST, 25, 38)`, the middle of the ring. */
    static final int TOOL_X = 25;
    static final int TOOL_Y = 38;
    /** Upstream's own result slot -- `addSlot(CATALYST, 105, 34)`, right of the baked arrow. */
    static final int RESULT_X = 105;
    static final int RESULT_Y = 34;

    /**
     * Upstream draws the modifier's name through a {@code ModifierIngredientRenderer(124, 10)} bound
     * to a slot at (3,3): centred inside that 124px box, one pixel down, white with a shadow.
     */
    static final int NAME_CENTER_X = 3 + 124 / 2;
    static final int NAME_Y = 4;
    static final int NAME_WIDTH = 124;
    static final int NAME_COLOR = 0xFFFFFF;

    /** Upstream's own level line -- `drawString(.., 86 - font.width(levelText) / 2, 16, GRAY, false)`. */
    static final int LEVEL_CENTER_X = 86;
    static final int LEVEL_Y = 16;
    /** Widest a string centred on {@link #LEVEL_CENTER_X} can be without leaving the panel. */
    static final int LEVEL_WIDTH = 2 * (WIDTH - LEVEL_CENTER_X) - 4;
    static final int TEXT_COLOR = 0x404040;
}
