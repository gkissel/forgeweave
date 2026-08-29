package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
 */
final class JeiCategoryChrome {
    /**
     * Upstream {@code ToolBuildingCategory}'s own {@code slotBorder}, `createDrawable(
     * tinker_station.png, 162, 59, 18, 18)`: the 18x18 inventory-slot frame it draws under each of
     * its own slots, since its panel art has none. Drawn at {@code (slotX - 1, slotY - 1)}, because
     * a slot frame is 18x18 around an 16x16 slot -- upstream's own comment on the same call.
     */
    private static final int SLOT_FRAME_U = 162;
    private static final int SLOT_FRAME_V = 59;
    static final int SLOT_FRAME_SIZE = 18;

    private JeiCategoryChrome() {}

    /** The category's whole background, at the exact upstream rect {@code panel} names. */
    static IDrawable panel(IGuiHelper helper, Panel panel) {
        return helper.createDrawable(panel.background(), panel.u(), panel.v(), panel.width(), panel.height());
    }

    /** See {@link #SLOT_FRAME_U}: draw it at {@code (slotX - 1, slotY - 1)}. */
    static IDrawable slotFrame(IGuiHelper helper) {
        return helper.createDrawable(JeiCategoryGeometry.TINKER_STATION,
                SLOT_FRAME_U, SLOT_FRAME_V, SLOT_FRAME_SIZE, SLOT_FRAME_SIZE);
    }

    /** Draws {@link #slotFrame}'s 18x18 frame around the 16x16 slot whose origin is {@code (x, y)}. */
    static void drawSlotFrame(IDrawable frame, GuiGraphics guiGraphics, int x, int y) {
        frame.draw(guiGraphics, x - 1, y - 1);
    }

    /** Draws {@code text} centred on {@code centerX}, the way every upstream category centres its own. */
    static void drawCentered(GuiGraphics guiGraphics, Font font, Component text, int centerX, int y, int colour, boolean shadow) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, colour, shadow);
    }

    /**
     * Issue #804: {@code EmbossingCategory} and {@code ModifierApplicationCategory} both draw a
     * datapack-named modifier/material with plain {@code GuiGraphics#drawString} -- unlike JEI's own
     * ingredient-slot rendering, a raw {@code drawString} call has no scissor of its own, so a name
     * longer than the panel (real examples: the "Projectile Protection" trait, the "Embossment
     * (Purple Slimevine)" material name) drew straight out past the JEI popup's edge. Ellipsizes with
     * vanilla's own {@link Font#plainSubstrByWidth} rather than growing the panel without bound,
     * since a datapack can always add a longer name than whatever fixed width is chosen.
     */
    static Component trimToWidth(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String trimmed = font.plainSubstrByWidth(text.getString(), maxWidth - font.width(ellipsis));
        return Component.literal(trimmed + ellipsis);
    }
}
