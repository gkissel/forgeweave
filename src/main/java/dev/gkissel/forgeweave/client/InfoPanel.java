package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
 * which is all the state a panel has.
 *
 * <p>Issue #47 shipped mouse-wheel scrolling with no visible scrollbar, recorded here as a
 * deliberate deviation. Issue #376 reverses it (maintainer decision, 2026-08-14): upstream's slider
 * is back, drawn from {@code panel.png}'s own knob/track sprites whenever the text overflows
 * ({@code GuiInfoPanel} lines 47-52, 57, 101-102, 165-188, 372-373), and the wheel still works. A
 * panel with no overflow draws no slider, exactly as upstream's {@code updateSliderParameters}
 * hides it.
 *
 * <p>Issue #376 also ports upstream's grey "?" bubble in the top-right corner
 * ({@code GuiInfoPanel:331-333}), shown only when some line carries hover text, and hovering it
 * explains that the entries are hoverable ({@code GuiInfoPanel:257-262}, {@code gui.general.hover}).
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
     * Which of {@code panel.png}'s frames to draw. Upstream ships three on one sheet and each
     * station picks one: only {@code GuiToolStation} calls {@code wood()}, while {@code
     * GuiPartBuilder} leaves its panel on the default dark frame. Issue #79: this class hardcoded
     * the wood frame, so the Part Builder wore the Tool Station's skin. Issue #152 adds the third,
     * for the Tool Forge -- upstream's {@code GuiInfoPanel#metal()} is
     * {@code shift(resW + 8, resH + 8)}, i.e. the wood frame's column, one frame down.
     *
     * <p>The slider sprites are a separate column of the same sheet and shift by their own amount:
     * {@code wood()} is {@code shiftSlider(6, 0)} and {@code metal()} {@code shiftSlider(12, 0)}
     * (issue #376), i.e. three 6px-wide slider styles side by side rather than the frame's grid.
     */
    public enum Style {
        DEFAULT(0, 0, 0),
        WOOD(CONTENT_WIDTH + 8, 0, 6),
        METAL(CONTENT_WIDTH + 8, CONTENT_HEIGHT + 8, 12);

        private final int u;
        private final int v;
        private final int sliderU;

        Style(int u, int v, int sliderU) {
            this.u = u;
            this.v = v;
            this.sliderU = sliderU;
        }
    }

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
     * What the slider costs the text: upstream's {@code getTotalLines} takes {@code slider.width + 3}
     * off the wrap width while the slider is shown, so an overflowing panel wraps 6px narrower than
     * the same text in a panel that fits.
     */
    private static final int SLIDER_WRAP_INSET = 6;

    // The slider, all of it upstream's own (GuiInfoPanel:47-52 for the sprites, :101-102 for the
    // geometry). The insets fold in GuiWidgetBorder's 7px `border.w`/`border.h` -- the generic
    // border's field initialisers, which GuiInfoPanel never reassigns, the same quirk WRAP_INSET
    // records: x is `guiRight() - border.w - 2`, y is `guiTop + border.h + 12`, and the track is
    // `ySize - border.h * 2 - 2 - 12` tall.
    private static final int SLIDER_W = 3;
    private static final int SLIDER_X_INSET = 9;
    private static final int SLIDER_Y_INSET = 19;
    private static final int SLIDER_HEIGHT_INSET = 28;
    /** {@code sliderNormal} = (0, 83, 3, 5); the knob's own column is the style's base u. */
    private static final int SLIDER_KNOB_V = 83;
    private static final int SLIDER_KNOB_H = 5;
    /** {@code sliderBar} = (0, 88, 3, 8), tiled down the track between the two caps. */
    private static final int SLIDER_BAR_V = 88;
    private static final int SLIDER_BAR_H = 8;
    /** {@code sliderTop} = (3, 88, 3, 4) and {@code sliderBot} = (3, 92, 3, 4) -- one column right of the bar. */
    private static final int SLIDER_CAP_U = 3;
    private static final int SLIDER_CAP_H = 4;
    private static final int SLIDER_BOT_V = 92;

    /**
     * Upstream's hover hint ({@code GuiInfoPanel:331-333} and {@code :257-262}): a grey "?" in the
     * top-right corner whenever some line has hover text, whose own hover says so. Drawn at
     * {@code guiRight() - border.w - charWidth('?') / 2}, unshadowed, in {@code 0xff5f5f5f}.
     */
    private static final String HELP_GLYPH = "?";
    private static final int HELP_COLOR = 0xFF5F5F5F;
    private static final int HELP_INSET = 7;
    private static final net.minecraft.network.chat.Style HELP_STYLE =
            net.minecraft.network.chat.Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.translatable("gui.forgeweave.general.hover")));

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

        if (caption != null) {
            Component underlined = caption.copy().withStyle(ChatFormatting.UNDERLINE);
            graphics.drawString(font, underlined,
                    x + width / 2 - font.width(underlined) / 2, (int) textTop, TEXT_COLOR, true);
            textTop += font.lineHeight + CAPTION_GAP;
        }
        if (hasHover(lines)) {
            graphics.drawString(font, HELP_GLYPH, helpX(font, x, width), y + TEXT_INSET, HELP_COLOR, false);
        }

        Layout layout = layout(font, width, height, caption != null, lines);
        int start = Math.clamp(scroll, 0, layout.maxScroll());
        for (int i = start; i < Math.min(layout.lines().size(), start + layout.visible()); i++) {
            graphics.drawString(font, layout.lines().get(i), textLeft,
                    Math.round(textTop + (i - start) * lineStep(font)), TEXT_COLOR, true);
        }
        if (layout.slider()) {
            renderSlider(graphics, style, x, y, width, height, start, layout.maxScroll());
        }
        return layout.maxScroll();
    }

    /**
     * Upstream's slider: the two 4px end caps of {@code panel.png}'s track column with the 8px bar
     * tiled between them, and the knob positioned along it by {@link #knobY}. Only drawn on
     * overflow -- {@code GuiInfoPanel#updateSliderParameters} hides it otherwise.
     *
     * <p>ponytail: upstream also swaps in a lit knob sprite while the mouse is over it
     * ({@code sliderHover}); on a 3x5 knob that is not worth threading the cursor through
     * {@link #render}. Add it if a playtest asks.
     */
    private static void renderSlider(GuiGraphics graphics, Style style, int x, int y, int width, int height,
            int scroll, int maxScroll) {
        int sliderX = x + width - SLIDER_X_INSET;
        int trackY = y + SLIDER_Y_INSET;
        int trackH = height - SLIDER_HEIGHT_INSET;
        int u = style.sliderU;
        graphics.blit(TEXTURE, sliderX, trackY, u + SLIDER_CAP_U, SLIDER_BAR_V, SLIDER_W, SLIDER_CAP_H, SHEET, SHEET);
        for (int drawn = SLIDER_CAP_H; drawn < trackH - SLIDER_CAP_H; drawn += SLIDER_BAR_H) {
            graphics.blit(TEXTURE, sliderX, trackY + drawn, u, SLIDER_BAR_V, SLIDER_W,
                    Math.min(SLIDER_BAR_H, trackH - SLIDER_CAP_H - drawn), SHEET, SHEET);
        }
        graphics.blit(TEXTURE, sliderX, trackY + trackH - SLIDER_CAP_H, u + SLIDER_CAP_U, SLIDER_BOT_V,
                SLIDER_W, SLIDER_CAP_H, SHEET, SHEET);
        graphics.blit(TEXTURE, sliderX, knobY(trackY, trackH, scroll, maxScroll), u, SLIDER_KNOB_V,
                SLIDER_W, SLIDER_KNOB_H, SHEET, SHEET);
    }

    /**
     * The text {@link net.minecraft.network.chat.Style} of the character under the mouse (fully
     * qualified: this class's own {@link Style} is the frame choice above), or {@code null} over the
     * frame, the caption, a spacer or bare padding -- how a screen finds the hover event a panel
     * line carries (issue #258: the Tool Station's modifier rows). Repeats {@link #render}'s exact
     * wrap, clamp and {@link #lineStep} math so hit-testing can never drift from where the text is
     * drawn.
     */
    @Nullable
    public static net.minecraft.network.chat.Style hoveredStyle(Font font, int x, int y, int width, int height,
            boolean hasCaption, List<Component> lines, int scroll, double mouseX, double mouseY) {
        if (hasHover(lines) && mouseX >= helpX(font, x, width) && mouseX < x + width
                && mouseY > y + TEXT_INSET && mouseY < y + TEXT_INSET + font.lineHeight) {
            return HELP_STYLE;
        }
        int textLeft = x + TEXT_INSET;
        float textTop = y + TEXT_INSET + (hasCaption ? font.lineHeight + CAPTION_GAP : 0);
        if (mouseX < textLeft) {
            return null;
        }
        Layout layout = layout(font, width, height, hasCaption, lines);
        int start = Math.clamp(scroll, 0, layout.maxScroll());
        for (int i = start; i < Math.min(layout.lines().size(), start + layout.visible()); i++) {
            int lineY = Math.round(textTop + (i - start) * lineStep(font));
            if (mouseY >= lineY && mouseY < lineY + font.lineHeight) {
                return font.getSplitter().componentStyleAtWidth(layout.lines().get(i), (int) mouseX - textLeft);
            }
        }
        return null;
    }

    /** How far {@code lines} can scroll in a panel of this size, without drawing anything. */
    public static int maxScroll(Font font, int width, int height, boolean hasCaption, List<Component> lines) {
        return layout(font, width, height, hasCaption, lines).maxScroll();
    }

    /**
     * True while the mouse is on this panel's slider, i.e. while a press there should start a drag
     * rather than fall through to whatever is behind the panel.
     */
    public static boolean overSlider(Font font, int x, int y, int width, int height, boolean hasCaption,
            List<Component> lines, double mouseX, double mouseY) {
        int sliderX = x + width - SLIDER_X_INSET;
        return mouseX >= sliderX && mouseX < sliderX + SLIDER_W
                && mouseY >= y + SLIDER_Y_INSET && mouseY < y + SLIDER_Y_INSET + height - SLIDER_HEIGHT_INSET
                && layout(font, width, height, hasCaption, lines).slider();
    }

    /** The scroll the knob lands on with the cursor at {@code mouseY}, clamped -- a drag may leave the track. */
    public static int sliderScroll(Font font, int y, int width, int height, boolean hasCaption,
            List<Component> lines, double mouseY) {
        return scrollAt(y + SLIDER_Y_INSET, height - SLIDER_HEIGHT_INSET,
                layout(font, width, height, hasCaption, lines).maxScroll(), mouseY);
    }

    /**
     * The wrapped text of a panel plus what the slider does to it, resolved once so
     * {@link #render}, {@link #hoveredStyle} and {@link #maxScroll} can never disagree about where a
     * line is or whether there is a slider next to it.
     *
     * <p>The two-pass shape is upstream's ({@code updateSliderParameters} hides the slider, measures,
     * then shows it and measures again): whether the slider is needed depends on the wrap, and the
     * wrap depends on whether the slider is there, so the wide wrap decides and the narrow one is
     * only used once the answer is yes.
     */
    private static Layout layout(Font font, int width, int height, boolean hasCaption, List<Component> lines) {
        int textHeight = height - TEXT_INSET * 2 - (hasCaption ? font.lineHeight + CAPTION_GAP : 0);
        int visible = visibleLines(font.lineHeight, textHeight);
        List<FormattedCharSequence> wrapped = wrap(font, lines, width - WRAP_INSET);
        if (!needsSlider(font.lineHeight, textHeight, wrapped.size())) {
            return new Layout(wrapped, visible, 0, false);
        }
        wrapped = wrap(font, lines, width - WRAP_INSET - SLIDER_WRAP_INSET);
        return new Layout(wrapped, visible, Math.max(0, wrapped.size() - visible), true);
    }

    private record Layout(List<FormattedCharSequence> lines, int visible, int maxScroll, boolean slider) {}

    /**
     * Whether a panel of this text height overflows {@code totalLines} of wrapped text, which is
     * exactly when upstream shows its slider. Takes a plain line height rather than a {@link Font}
     * so the off-by-one that decides whether the slider appears at all is testable without a client
     * (issue #376), the same seam {@code StationScreen#centreWithTabStrip} uses.
     */
    static boolean needsSlider(int lineHeight, int textHeight, int totalLines) {
        return totalLines > visibleLines(lineHeight, textHeight);
    }

    /** Where the knob sits on a track of this height: hard against the top at scroll 0, the bottom at the end. */
    static int knobY(int trackY, int trackHeight, int scroll, int maxScroll) {
        int span = trackHeight - SLIDER_KNOB_H;
        if (maxScroll <= 0 || span <= 0) {
            return trackY;
        }
        return trackY + span * Math.clamp(scroll, 0, maxScroll) / maxScroll;
    }

    /** {@link #knobY} inverted: the scroll that puts the knob's middle under {@code mouseY}. */
    static int scrollAt(int trackY, int trackHeight, int maxScroll, double mouseY) {
        int span = trackHeight - SLIDER_KNOB_H;
        if (maxScroll <= 0 || span <= 0) {
            return 0;
        }
        return (int) Math.clamp(Math.round((mouseY - trackY - SLIDER_KNOB_H / 2.0) * maxScroll / span), 0, maxScroll);
    }

    /** Whether any line carries hover text, which is what upstream's {@code hasTooltips} gates the "?" on. */
    private static boolean hasHover(List<Component> lines) {
        for (Component line : lines) {
            if (line != null && line.getStyle().getHoverEvent() != null) {
                return true;
            }
        }
        return false;
    }

    private static int helpX(Font font, int x, int width) {
        return x + width - HELP_INSET - font.width(HELP_GLYPH) / 2;
    }

    /**
     * Upstream's line advance is {@code fontRenderer.FONT_HEIGHT * textScale + 0.5f} -- 9.5px at
     * our scale, not the flat 9 this used to step by (issue #79). Over a full panel the half-pixels
     * accumulate into a whole extra line of drift. {@code GuiGraphics} only draws text at integer
     * coordinates, so each line rounds to the nearest pixel rather than sitting at upstream's exact
     * subpixel y; the line <em>positions</em> match, the glyph antialiasing at 1x GUI scale doesn't.
     */
    private static float lineStep(Font font) {
        return lineStep(font.lineHeight);
    }

    private static float lineStep(int lineHeight) {
        return lineHeight + 0.5F;
    }

    /**
     * Upstream keeps drawing while {@code y + textHeight - 0.5f <= lowerBound}, i.e. while the
     * <em>glyph box</em> still fits under {@code guiTop + ySize - 5} -- the trailing half-pixel of
     * leading is allowed to overhang. So the last line fits whenever there is {@code FONT_HEIGHT}
     * left, not a whole {@link #lineStep}, which is why this is not a plain division.
     */
    private static int visibleLines(int lineHeight, int textHeight) {
        return textHeight < lineHeight ? 0 : (int) ((textHeight - lineHeight) / lineStep(lineHeight)) + 1;
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
        int v = style.v;
        int innerW = width - BORDER * 2;
        int innerH = height - BORDER * 2;
        int right = x + width - BORDER;
        int bottom = y + height - BORDER;
        int sheetRight = u + CONTENT_WIDTH + BORDER;
        int sheetBottom = v + CONTENT_HEIGHT + BORDER;

        blit(graphics, x, y, BORDER, BORDER, u, v, BORDER, BORDER);
        blit(graphics, x + BORDER, y, innerW, BORDER, u + BORDER, v, CONTENT_WIDTH, BORDER);
        blit(graphics, right, y, BORDER, BORDER, sheetRight, v, BORDER, BORDER);

        tileY(graphics, x, y + BORDER, innerH, u, v + BORDER);
        blit(graphics, x + BORDER, y + BORDER, innerW, innerH, u + BORDER, v + BORDER, CONTENT_WIDTH, CONTENT_HEIGHT);
        tileY(graphics, right, y + BORDER, innerH, sheetRight, v + BORDER);

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
