package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;

/**
 * Shared chrome for the categories that have no upstream JEI counterpart of their own (issue #785).
 * #753 first gave every category a flat procedurally-drawn bevel because nothing was derived from
 * upstream at all. #785 replaces that bevel with real derived art: alloying, casting, melting and
 * assembly each derive their own background straight from their own upstream analog in the 1.20
 * clone (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT) -- see each class's own
 * {@code BACKGROUND_LOC}. Part crafting, modifier application, repair and embossing have no such
 * analog (upstream's Part Builder/Tool Station JEI panels are laid out for entirely different slot
 * counts and shapes), so per the maintainer's decision on #785 all four instead reuse this one crop
 * -- upstream {@code PartBuilderCategory}'s own plain station panel -- "so the set reads as one
 * family" rather than four more procedural bevels.
 */
final class JeiCategoryChrome {
    /** Margin between the panel edge and the first row/column of slots or drawables. */
    static final int GUTTER = 4;

    /** The derived copy of upstream's `textures/gui/jei/tinker_station.png` (NOTICE.md). */
    static final ResourceLocation TINKER_STATION = JeiCategoryGeometry.TINKER_STATION;

    /** Upstream {@code PartBuilderCategory}'s own background rect inside {@link #TINKER_STATION}. */
    private static final int TILE_U = 0;
    private static final int TILE_V = 117;
    private static final int TILE_WIDTH = 121;

    private JeiCategoryChrome() {}

    /**
     * The Part Builder panel crop, tiled left-to-right to cover {@code width}: {@code
     * EmbossingCategory} and {@code ModifierApplicationCategory} both need a wider panel than the
     * 121px source tile (their five-reagent-slot row), so the tile repeats and relies on JEI's own
     * per-recipe scissor -- every {@code IRecipeCategory} is clipped to its declared {@code
     * getWidth()}/{@code getHeight()} when the recipe list draws it -- to discard whatever repeats
     * past that edge. {@code RepairCategory} and {@code PartCraftingCategory} are narrower than one
     * tile, so for them this degenerates to the single untiled crop.
     */
    static IDrawable stationPanel(IGuiHelper helper, int width, int height) {
        int tileWidth = Math.min(width, TILE_WIDTH);
        IDrawable tile = helper.createDrawable(TINKER_STATION, TILE_U, TILE_V, tileWidth, height);
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
                for (int drawnX = 0; drawnX < width; drawnX += tileWidth) {
                    tile.draw(guiGraphics, x + drawnX, y);
                }
            }
        };
    }
}
