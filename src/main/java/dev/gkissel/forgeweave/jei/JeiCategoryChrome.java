package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.api.gui.drawable.IDrawable;

/**
 * Shared chrome for every recipe category (issue #753): none of them drew anything behind their
 * slots, so every category rendered as bare slots floating with no panel at all -- unlike vanilla's
 * own JEI categories, which always frame their slots in the source block's GUI texture. Each
 * category now draws one of these panels as the first thing in its {@code draw(...)} (not via the
 * {@code getBackground()} default method, which this JEI version marks for removal) -- a flat,
 * vanilla-styled beveled panel (light top/left edge, dark bottom/right edge, matching the vanilla
 * inventory panel palette) instead of a bespoke texture per station, plus a shared {@link #GUTTER}
 * margin so slots stop sitting flush against that panel's edge.
 */
final class JeiCategoryChrome {
    /** Margin between the panel edge and the first row/column of slots or drawables. */
    static final int GUTTER = 4;

    private static final int FILL_COLOR = 0xFFC6C6C6;
    private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int SHADOW_COLOR = 0xFF8B8B8B;

    private JeiCategoryChrome() {}

    /** A flat beveled panel sized to the category's full (gutter-inclusive) width/height. */
    static IDrawable panel(int width, int height) {
        return new IDrawable() {
            @Override
            public int getWidth() {
                return width;
            }

            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public void draw(GuiGraphics guiGraphics, int x, int y) {
                guiGraphics.fill(x, y, x + width, y + height, FILL_COLOR);
                guiGraphics.fill(x, y, x + width, y + 1, HIGHLIGHT_COLOR);
                guiGraphics.fill(x, y, x + 1, y + height, HIGHLIGHT_COLOR);
                guiGraphics.fill(x, y + height - 1, x + width, y + height, SHADOW_COLOR);
                guiGraphics.fill(x + width - 1, y, x + width, y + height, SHADOW_COLOR);
            }
        };
    }
}
