package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The stations' side information panel (issue #47): upstream 1.12's {@code GuiInfoPanel}, which is a
 * nine-sliced frame from {@code textures/gui/panel.png} with an underlined, centred caption and
 * word-wrapped body text under it (NOTICE.md). Upstream's own geometry is kept verbatim -- a 4px
 * border around a 118x75 content area, so the natural panel is 126x83 -- and so is its choice of
 * frame, which each caller picks per {@link Style}.
 *
 * <p>Stateless on purpose: each screen owns its panels' text and scroll offset and passes them in,
 * which is all the state a panel has. Upstream's scrollbar widget is replaced by plain mouse-wheel
 * scrolling ({@link #maxScroll}) -- fewer moving parts for the same job, and the only visible
 * difference is the missing slider track.
 */
public final class InfoPanel {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/info_panel.png");

    /** Upstream's content area; a panel is this plus {@link #BORDER} on each side. */
    public static final int CONTENT_WIDTH = 118;
    public static final int CONTENT_HEIGHT = 75;
    public static final int BORDER = 4;
    public static final int WIDTH = CONTENT_WIDTH + BORDER * 2;
    public static final int HEIGHT = CONTENT_HEIGHT + BORDER * 2;

    /**
     * Which of {@code panel.png}'s frames to draw. Upstream ships three side by side on one sheet
     * and each station picks one: only {@code GuiToolStation} calls {@code wood()}, while {@code
     * GuiPartBuilder} leaves its panel on the default dark frame. Issue #79: this class hardcoded
     * the wood frame, so the Part Builder wore the Tool Station's skin. ({@code metal()} exists
     * upstream too; nothing here uses it, so it isn't listed.)
     */
    public enum Style {
        DEFAULT(0),
        WOOD(CONTENT_WIDTH + 8);

        private final int u;

        Style(int u) {
            this.u = u;
        }
    }

    private static final int V = 0;
    private static final int SHEET = 256;

    /** Upstream's text inset and colour ({@code GuiInfoPanel#drawGuiContainerForegroundLayer}). */
    private static final int TEXT_INSET = 5;
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private static final int CAPTION_GAP = 3;

    /**
     * How much narrower than the panel the wrapped text is. Upstream's {@code getTotalLines} uses
     * {@code xSize - border.w * 2 + 2}, and {@code border.w} there is {@code GuiWidgetBorder}'s
     * field initialiser -- which reads the <em>generic</em> 7px border and is never reassigned when
     * {@code GuiInfoPanel} swaps in its own 4px edges. So upstream wraps at {@code width - 12},
     * where this used to wrap at {@code width - TEXT_INSET * 2} = {@code width - 10} (issue #79) --
     * two pixels of slack, enough to keep a word on a line upstream would have broken.
     */
    private static final int WRAP_INSET = 12;

    /**
     * Draws the frame and as much of {@code lines} as fits, starting {@code scroll} lines in.
     *
     * @param style which of the sheet's frames to draw around it
     * @param caption the underlined, centred heading, or {@code null} for a panel with only body text
     * @return how many wrapped lines had to be skipped to fit, i.e. the maximum useful {@code scroll}
     */
    public static int render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            Style style, @Nullable Component caption, List<Component> lines, int scroll) {
        renderFrame(graphics, x, y, width, height, style);

        int textLeft = x + TEXT_INSET;
        float textTop = y + TEXT_INSET;
        int textHeight = height - TEXT_INSET * 2;

        if (caption != null) {
            Component underlined = caption.copy().withStyle(ChatFormatting.UNDERLINE);
            graphics.drawString(font, underlined,
                    x + width / 2 - font.width(underlined) / 2, (int) textTop, TEXT_COLOR, true);
            textTop += font.lineHeight + CAPTION_GAP;
            textHeight -= font.lineHeight + CAPTION_GAP;
        }

        List<FormattedCharSequence> wrapped = wrap(font, lines, width - WRAP_INSET);
        int visibleLines = visibleLines(font, textHeight);
        int maxScroll = Math.max(0, wrapped.size() - visibleLines);
        int start = Math.clamp(scroll, 0, maxScroll);

        for (int i = start; i < Math.min(wrapped.size(), start + visibleLines); i++) {
            graphics.drawString(font, wrapped.get(i), textLeft,
                    Math.round(textTop + (i - start) * lineStep(font)), TEXT_COLOR, true);
        }
        return maxScroll;
    }

    /** How far {@code lines} can scroll in a panel of this size, without drawing anything. */
    public static int maxScroll(Font font, int width, int height, boolean hasCaption, List<Component> lines) {
        int textHeight = height - TEXT_INSET * 2 - (hasCaption ? font.lineHeight + CAPTION_GAP : 0);
        return Math.max(0, wrap(font, lines, width - WRAP_INSET).size() - visibleLines(font, textHeight));
    }

    /**
     * Upstream's line advance is {@code fontRenderer.FONT_HEIGHT * textScale + 0.5f} -- 9.5px at
     * our scale, not the flat 9 this used to step by (issue #79). Over a full panel the half-pixels
     * accumulate into a whole extra line of drift. {@code GuiGraphics} only draws text at integer
     * coordinates, so each line rounds to the nearest pixel rather than sitting at upstream's exact
     * subpixel y; the line <em>positions</em> match, the glyph antialiasing at 1x GUI scale doesn't.
     */
    private static float lineStep(Font font) {
        return font.lineHeight + 0.5F;
    }

    /**
     * Upstream keeps drawing while {@code y + textHeight - 0.5f <= lowerBound}, i.e. while the
     * glyph box itself still fits under {@code guiTop + ySize - 5} -- so the last line may overhang
     * the half-pixel of leading, and a panel fits one more line than {@code textHeight / 9.5} would
     * suggest.
     */
    private static int visibleLines(Font font, int textHeight) {
        return textHeight < font.lineHeight ? 0 : (int) ((textHeight - font.lineHeight) / lineStep(font)) + 1;
    }

    /** A {@code null} entry is a blank spacer line, as upstream's panel text uses. */
    private static List<FormattedCharSequence> wrap(Font font, List<Component> lines, int width) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            if (line == null) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(font.split(line, width));
            }
        }
        return wrapped;
    }

    private static void renderFrame(GuiGraphics graphics, int x, int y, int width, int height, Style style) {
        int u = style.u;
        int innerW = width - BORDER * 2;
        int innerH = height - BORDER * 2;
        int right = x + width - BORDER;
        int bottom = y + height - BORDER;
        int sheetRight = u + CONTENT_WIDTH + BORDER;
        int sheetBottom = V + CONTENT_HEIGHT + BORDER;

        blit(graphics, x, y, BORDER, BORDER, u, V, BORDER, BORDER);
        blit(graphics, x + BORDER, y, innerW, BORDER, u + BORDER, V, CONTENT_WIDTH, BORDER);
        blit(graphics, right, y, BORDER, BORDER, sheetRight, V, BORDER, BORDER);

        tileY(graphics, x, y + BORDER, innerH, u, V + BORDER);
        blit(graphics, x + BORDER, y + BORDER, innerW, innerH, u + BORDER, V + BORDER, CONTENT_WIDTH, CONTENT_HEIGHT);
        tileY(graphics, right, y + BORDER, innerH, sheetRight, V + BORDER);

        blit(graphics, x, bottom, BORDER, BORDER, u, sheetBottom, BORDER, BORDER);
        blit(graphics, x + BORDER, bottom, innerW, BORDER, u + BORDER, sheetBottom, CONTENT_WIDTH, BORDER);
        blit(graphics, right, bottom, BORDER, BORDER, sheetRight, sheetBottom, BORDER, BORDER);
    }

    /**
     * A vertical edge strip, repeated down {@code height} rather than stretched to it (issue #79).
     * The wood frame's side strips are wood grain -- 46 distinct rows in the 75px source -- so
     * stretching them into the Part Builder's 166px-tall panel smears single rows across several
     * pixels. The corners are 1:1 anyway; the top/bottom strips and the centre fill stay stretched,
     * because no panel is ever wider than the natural 126 and the centre fill is one flat colour.
     */
    private static void tileY(GuiGraphics graphics, int x, int y, int height, int u, int v) {
        for (int drawn = 0; drawn < height; drawn += CONTENT_HEIGHT) {
            graphics.blit(TEXTURE, x, y + drawn, u, v, BORDER, Math.min(CONTENT_HEIGHT, height - drawn), SHEET, SHEET);
        }
    }

    /** Stretching blit: {@code (uWidth, vHeight)} of the sheet drawn into {@code (width, height)}. */
    private static void blit(GuiGraphics graphics, int x, int y, int width, int height,
            int u, int v, int uWidth, int vHeight) {
        graphics.blit(TEXTURE, x, y, width, height, u, v, uWidth, vHeight, SHEET, SHEET);
    }

    private InfoPanel() {}
}
