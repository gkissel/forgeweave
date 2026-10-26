package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;

import dev.gkissel.forgeweave.jei.JeiCategoryGeometry.Panel;

/**
 * The two pieces of chrome every category shares: its background drawable, and the helpers for
 * drawing on top of one.
 *
 * <p>#753 first gave every category a flat procedurally-drawn bevel because nothing was derived from
 * upstream at all. #785 replaced that with real derived art from the 1.20 clone
 * (`~/development/minecraft/references/tinkers-1.20` @ de26560d, MIT -- NOTICE.md), but for the four
 * categories it judged to have no upstream counterpart it tiled one 121px Part Builder crop across a
 * panel up to 274px wide. Issue #804: that tiling repeated the crop's own baked slot frames and
 * arrow across the row -- the ghost slots in the maintainer's embossing and modifier-application
 * screenshots -- and the widths it was tiled to pushed those categories past the JEI popup's edge.
 * Every category now draws one whole upstream panel at its real size ({@link JeiCategoryGeometry}),
 * so there is nothing left to tile.
 *
 * <p>Issue #971 moved the slot-frame rect and the two text helpers into {@link JeiCategoryGeometry},
 * which has no JEI import, so the EMI plugin can draw the same chrome with no JEI installed.
 */
final class JeiCategoryChrome {
    private JeiCategoryChrome() {}

    /** The category's whole background, at the exact upstream rect {@code panel} names. */
    static IDrawable panel(IGuiHelper helper, Panel panel) {
        return helper.createDrawable(panel.background(), panel.u(), panel.v(), panel.width(), panel.height());
    }

    /** See {@link JeiCategoryGeometry#SLOT_FRAME_U}: draw it at {@code (slotX - 1, slotY - 1)}. */
    static IDrawable slotFrame(IGuiHelper helper) {
        return helper.createDrawable(JeiCategoryGeometry.TINKER_STATION, JeiCategoryGeometry.SLOT_FRAME_U,
                JeiCategoryGeometry.SLOT_FRAME_V, JeiCategoryGeometry.SLOT_FRAME_SIZE, JeiCategoryGeometry.SLOT_FRAME_SIZE);
    }

    /** Draws {@link #slotFrame}'s 18x18 frame around the 16x16 slot whose origin is {@code (x, y)}. */
    static void drawSlotFrame(IDrawable frame, GuiGraphics guiGraphics, int x, int y) {
        frame.draw(guiGraphics, x - 1, y - 1);
    }
}
