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
 * border around a 118x75 content area, so the natural panel is 126x83 -- and so is its wood-style
 * variant, which is the same sheet shifted 126px right.
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

    /** Upstream's {@code wood()} style: the same nine-slice, shifted one panel-width right. */
    private static final int U = CONTENT_WIDTH + 8;
    private static final int V = 0;
    private static final int SHEET = 256;

    /** Upstream's text inset and colour ({@code GuiInfoPanel#drawGuiContainerForegroundLayer}). */
    private static final int TEXT_INSET = 5;
    private static final int TEXT_COLOR = 0xFFF0F0F0;
    private static final int CAPTION_GAP = 3;

    /**
     * Draws the frame and as much of {@code lines} as fits, starting {@code scroll} lines in.
     *
     * @param caption the underlined, centred heading, or {@code null} for a panel with only body text
     * @return how many wrapped lines had to be skipped to fit, i.e. the maximum useful {@code scroll}
     */
    public static int render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
            @Nullable Component caption, List<Component> lines, int scroll) {
        renderFrame(graphics, x, y, width, height);

        int textLeft = x + TEXT_INSET;
        int textTop = y + TEXT_INSET;
        int textWidth = width - TEXT_INSET * 2;
        int textHeight = height - TEXT_INSET * 2;

        if (caption != null) {
            Component underlined = caption.copy().withStyle(ChatFormatting.UNDERLINE);
            graphics.drawString(font, underlined,
                    x + width / 2 - font.width(underlined) / 2, textTop, TEXT_COLOR, true);
            textTop += font.lineHeight + CAPTION_GAP;
            textHeight -= font.lineHeight + CAPTION_GAP;
        }

        List<FormattedCharSequence> wrapped = wrap(font, lines, textWidth);
        int visibleLines = Math.max(0, textHeight / font.lineHeight);
        int maxScroll = Math.max(0, wrapped.size() - visibleLines);
        int start = Math.clamp(scroll, 0, maxScroll);

        for (int i = start; i < Math.min(wrapped.size(), start + visibleLines); i++) {
            graphics.drawString(font, wrapped.get(i), textLeft,
                    textTop + (i - start) * font.lineHeight, TEXT_COLOR, true);
        }
        return maxScroll;
    }

    /** How far {@code lines} can scroll in a panel of this size, without drawing anything. */
    public static int maxScroll(Font font, int width, int height, boolean hasCaption, List<Component> lines) {
        int textHeight = height - TEXT_INSET * 2 - (hasCaption ? font.lineHeight + CAPTION_GAP : 0);
        int visibleLines = Math.max(0, textHeight / font.lineHeight);
        return Math.max(0, wrap(font, lines, width - TEXT_INSET * 2).size() - visibleLines);
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

    private static void renderFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        int innerW = width - BORDER * 2;
        int innerH = height - BORDER * 2;
        int right = x + width - BORDER;
        int bottom = y + height - BORDER;
        int sheetRight = U + CONTENT_WIDTH + BORDER;
        int sheetBottom = V + CONTENT_HEIGHT + BORDER;

        blit(graphics, x, y, BORDER, BORDER, U, V, BORDER, BORDER);
        blit(graphics, x + BORDER, y, innerW, BORDER, U + BORDER, V, CONTENT_WIDTH, BORDER);
        blit(graphics, right, y, BORDER, BORDER, sheetRight, V, BORDER, BORDER);

        blit(graphics, x, y + BORDER, BORDER, innerH, U, V + BORDER, BORDER, CONTENT_HEIGHT);
        blit(graphics, x + BORDER, y + BORDER, innerW, innerH, U + BORDER, V + BORDER, CONTENT_WIDTH, CONTENT_HEIGHT);
        blit(graphics, right, y + BORDER, BORDER, innerH, sheetRight, V + BORDER, BORDER, CONTENT_HEIGHT);

        blit(graphics, x, bottom, BORDER, BORDER, U, sheetBottom, BORDER, BORDER);
        blit(graphics, x + BORDER, bottom, innerW, BORDER, U + BORDER, sheetBottom, CONTENT_WIDTH, BORDER);
        blit(graphics, right, bottom, BORDER, BORDER, sheetRight, sheetBottom, BORDER, BORDER);
    }

    /** Stretching blit: {@code (uWidth, vHeight)} of the sheet drawn into {@code (width, height)}. */
    private static void blit(GuiGraphics graphics, int x, int y, int width, int height,
            int u, int v, int uWidth, int vHeight) {
        graphics.blit(TEXTURE, x, y, width, height, u, v, uWidth, vHeight, SHEET, SHEET);
    }

    private InfoPanel() {}
}
