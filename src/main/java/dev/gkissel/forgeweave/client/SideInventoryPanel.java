package dev.gkissel.forgeweave.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.SideInventorySlots;
import dev.gkissel.forgeweave.menu.SideInventorySlots.SideSlot;

/**
 * Shared side-inventory panel rendering for the three stations that show a neighboring block's items
 * in a GUI side panel (docs/SCOPE.md issue #40, extended from the Crafting Station to the Part
 * Builder and Tool Station in the same issue's follow-up). Which side it hangs off is the calling
 * menu's {@code SIDE_PANEL_X}, negative for upstream's left-hand placement.
 *
 * <p>Issue #68 fix 3 rebuilt this as upstream 1.12's {@code GuiSideInventory} module: a nine-sliced
 * border from {@code textures/gui/generic.png} (NOTICE.md) around a six-column grid of that same
 * sheet's slot tile, capped to as many rows as fit next to the parent GUI and scrolled with the
 * mouse wheel. It previously drew a translucent rectangle behind a nine-column grid of whatever slot
 * sprite each screen happened to have lying around, which with a double chest adjacent produced the
 * unstyled 9x6 sprawl the maintainer screenshotted. Geometry constants are upstream's own: 7px
 * border ({@code GuiWidgetBorder}), 18px slots, at most 10 rows and never taller than the parent GUI
 * less 10px ({@code GuiSideInventory#calcCappedYSize}).
 *
 * <p>Stateful, one instance per screen, because the scroll offset (and, since issue #376, whether a
 * slider drag is in progress) is per-open-GUI state.
 *
 * <p>Issue #68 shipped mouse-wheel scrolling with no visible scrollbar, recorded here as a
 * deliberate deviation alongside {@link InfoPanel}'s. Issue #376 reverses both (maintainer decision,
 * 2026-08-14): upstream's slider is back, drawn from {@code generic.png}'s own knob/track sprites
 * whenever the rows overflow ({@code GuiSideInventory:334-340}), and the wheel still works. As
 * upstream does ({@code GuiSideInventory:140}), the panel grows by the slider's width when it
 * appears -- leftwards here, because these panels hang off their station's left edge, so the slots
 * move with it and the slider ends up in the column next to the station.
 */
final class SideInventoryPanel {
    /** Upstream's {@code GuiGeneric} sheet: 7px border pieces, an 18px slot tile and an 18px "no slot here" tile. */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/generic.png");
    private static final int SHEET = 64;
    private static final int BORDER = SideInventorySlots.BORDER;
    private static final int SLOT = SideInventorySlots.SLOT_SIZE;
    private static final int COLUMNS = SideInventorySlots.COLUMNS;
    private static final int SLOT_U = 7;
    private static final int SLOT_V = 7;
    private static final int SLOT_EMPTY_U = 7 + 18;

    /** Upstream {@code GuiSideInventory}: at most ten rows, and at most the parent's height less 10px. */
    private static final int MAX_ROWS = 10;
    private static final int PARENT_MARGIN = 10;

    // Upstream GuiGeneric's slider (issue #376): a 12px track -- 1px caps at (43, 7) and (43, 38)
    // around a 30px bar at (43, 8) -- with a 10x15 knob at (7, 25), centred in the track.
    static final int SLIDER_WIDTH = 12;
    private static final int SLIDER_TRACK_U = 43;
    private static final int SLIDER_TOP_V = 7;
    private static final int SLIDER_BAR_V = 8;
    private static final int SLIDER_BAR_H = 30;
    private static final int SLIDER_BOTTOM_V = 38;
    private static final int SLIDER_CAP_H = 1;
    private static final int SLIDER_KNOB_U = 7;
    private static final int SLIDER_KNOB_V = 25;
    private static final int SLIDER_KNOB_W = 10;
    private static final int SLIDER_KNOB_H = 15;

    private final int slotX;
    private final int slotY;
    private int scrollRow;
    private Rect2i bounds = new Rect2i(0, 0, 0, 0);
    /** The slider's track after the last {@link #render}, empty while there is nothing to scroll. */
    private Rect2i sliderTrack = new Rect2i(0, 0, 0, 0);
    private int maxScrollRow;
    private boolean draggingSlider;

    /** @param slotX,slotY where the menu put the panel's <em>first slot</em>, relative to the screen's top-left. */
    SideInventoryPanel(int slotX, int slotY) {
        this.slotX = slotX;
        this.slotY = slotY;
    }

    /** The panel's on-screen rectangle after the last {@link #render}; empty while there is no side inventory. */
    Rect2i bounds() {
        return bounds;
    }

    /**
     * Draws the panel and (re)places the slots it shows, hiding the ones scrolled out of view.
     *
     * @param parentHeight the parent screen's {@code imageHeight}, which caps how tall the panel gets
     */
    void render(GuiGraphics graphics, AbstractContainerMenu menu, int leftPos, int topPos, int parentHeight,
            List<SideSlot> slots) {
        if (slots.isEmpty()) {
            bounds = new Rect2i(0, 0, 0, 0);
            sliderTrack = new Rect2i(0, 0, 0, 0);
            maxScrollRow = 0;
            return;
        }
        int totalRows = SideInventorySlots.rows(slots.size());
        int visibleRows = visibleRows(totalRows, parentHeight, slotY);
        maxScrollRow = totalRows - visibleRows;
        scrollRow = Math.clamp(scrollRow, 0, maxScrollRow);

        // Upstream GuiSideInventory:140 widens the panel by the slider when it is needed, always
        // putting the slider column right of the slots. Which way the panel grows is which edge is
        // pinned to the station: a left-hand panel keeps its right edge there and so moves left
        // (slots included, exactly as upstream's `slot.xPos -= xSize` does), a right-hand one keeps
        // its left edge and does not move at all.
        int sliderWidth = maxScrollRow > 0 ? SLIDER_WIDTH : 0;
        int sliderShift = slotX < 0 ? sliderWidth : 0;
        int width = SideInventorySlots.PANEL_WIDTH + sliderWidth;
        int height = visibleRows * SLOT + BORDER * 2;
        // Issue #79: the panel corner is SLOT_INSET (= BORDER + 1) out from the first slot, not
        // BORDER. generic.png's 18x18 slot tile is a 1px bevel wrapped around a 16x16 socket, so
        // laying tiles flush with the slots left every item one pixel up and left of its socket.
        int x = onScreen(leftPos + slotX - SideInventorySlots.SLOT_INSET - sliderShift, width, graphics.guiWidth());
        int y = topPos + slotY - SideInventorySlots.SLOT_INSET;
        bounds = new Rect2i(x, y, width, height);
        sliderTrack = sliderWidth == 0
                ? new Rect2i(0, 0, 0, 0)
                : new Rect2i(x + BORDER + COLUMNS * SLOT, y + BORDER, SLIDER_WIDTH, height - BORDER * 2);
        // The slots follow the (possibly shifted) panel, so items stay in their sockets.
        int laidOutSlotX = x - leftPos + SideInventorySlots.SLOT_INSET;

        renderBorder(graphics, x, y, width, height);

        int firstSlot = scrollRow * COLUMNS;
        int lastSlot = Math.min(slots.size(), firstSlot + visibleRows * COLUMNS);
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                // Upstream draws its "slotEmpty" tile past the end of a partial last row, so the
                // panel stays a full rectangle instead of ending mid-row.
                boolean filled = firstSlot + row * COLUMNS + col < lastSlot;
                graphics.blit(TEXTURE, x + BORDER + col * SLOT, y + BORDER + row * SLOT,
                        filled ? SLOT_U : SLOT_EMPTY_U, SLOT_V, SLOT, SLOT, SHEET, SHEET);
            }
        }

        if (sliderWidth > 0) {
            renderSlider(graphics);
        }

        SideInventorySlots.layout(menu, slots, firstSlot, lastSlot, laidOutSlotX, slotY);
    }

    /** Upstream's slider: {@code generic.png}'s two 1px caps around its tiled bar, knob centred on top. */
    private void renderSlider(GuiGraphics graphics) {
        int x = sliderTrack.getX();
        int y = sliderTrack.getY();
        int trackH = sliderTrack.getHeight();
        graphics.blit(TEXTURE, x, y, SLIDER_TRACK_U, SLIDER_TOP_V, SLIDER_WIDTH, SLIDER_CAP_H, SHEET, SHEET);
        for (int drawn = SLIDER_CAP_H; drawn < trackH - SLIDER_CAP_H; drawn += SLIDER_BAR_H) {
            graphics.blit(TEXTURE, x, y + drawn, SLIDER_TRACK_U, SLIDER_BAR_V, SLIDER_WIDTH,
                    Math.min(SLIDER_BAR_H, trackH - SLIDER_CAP_H - drawn), SHEET, SHEET);
        }
        graphics.blit(TEXTURE, x, y + trackH - SLIDER_CAP_H, SLIDER_TRACK_U, SLIDER_BOTTOM_V,
                SLIDER_WIDTH, SLIDER_CAP_H, SHEET, SHEET);
        graphics.blit(TEXTURE, x + (SLIDER_WIDTH - SLIDER_KNOB_W) / 2,
                knobY(y, trackH, scrollRow, maxScrollRow), SLIDER_KNOB_U, SLIDER_KNOB_V,
                SLIDER_KNOB_W, SLIDER_KNOB_H, SHEET, SHEET);
    }

    /** Where the knob sits on a track of this height: against the top at row 0, the bottom at the last row. */
    static int knobY(int trackY, int trackHeight, int scrollRow, int maxScrollRow) {
        int span = trackHeight - SLIDER_KNOB_H;
        if (maxScrollRow <= 0 || span <= 0) {
            return trackY;
        }
        return trackY + span * Math.clamp(scrollRow, 0, maxScrollRow) / maxScrollRow;
    }

    /** {@link #knobY} inverted: the row that puts the knob's middle under {@code mouseY}. */
    static int scrollRowAt(int trackY, int trackHeight, int maxScrollRow, double mouseY) {
        int span = trackHeight - SLIDER_KNOB_H;
        if (maxScrollRow <= 0 || span <= 0) {
            return 0;
        }
        return (int) Math.clamp(Math.round((mouseY - trackY - SLIDER_KNOB_H / 2.0) * maxScrollRow / span),
                0, maxScrollRow);
    }

    /**
     * Grabs the slider if the press landed on it. Hit-tested against the track the last {@link
     * #render} placed, so this needs none of that call's arguments -- the panel is drawn every frame
     * before any input reaches it.
     *
     * @return true when the press was the slider's, so the screen shouldn't also act on it
     */
    boolean sliderClicked(double mouseX, double mouseY) {
        if (maxScrollRow <= 0 || !sliderTrack.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        draggingSlider = true;
        scrollRow = scrollRowAt(sliderTrack.getY(), sliderTrack.getHeight(), maxScrollRow, mouseY);
        return true;
    }

    /**
     * Follows a grabbed slider, wherever the cursor has since gone. Gated on the press having landed
     * on the track: a bare "is the cursor over the slider" test would hijack any item drag that
     * happens to cross the column between the panel's slots and its station.
     */
    boolean sliderDragged(double mouseY) {
        if (!draggingSlider) {
            return false;
        }
        scrollRow = scrollRowAt(sliderTrack.getY(), sliderTrack.getHeight(), maxScrollRow, mouseY);
        return true;
    }

    void sliderReleased() {
        draggingSlider = false;
    }

    /**
     * Keeps the panel inside the window (issue #88). A station's chrome is laid out relative to its
     * own panel, which vanilla centres without knowing the chrome exists, so at small window widths
     * a side panel can end up partly or wholly past an edge. Shifting it back is strictly better
     * than losing it: the worst case is that it overlaps its own station's other chrome, which is
     * still readable and still clickable.
     *
     * <p>The caller re-derives the slot coordinates from the returned x, so tiles and items shift
     * together -- moving one without the other is the issue #79 misalignment all over again.
     */
    static int onScreen(int x, int width, int screenWidth) {
        return Math.clamp(x, 0, Math.max(0, screenWidth - width));
    }

    /** @return true when the wheel was over this panel and consumed, so the screen shouldn't scroll anything else. */
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY, int parentHeight, List<SideSlot> slots) {
        if (slots.isEmpty() || !bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        int totalRows = SideInventorySlots.rows(slots.size());
        int visibleRows = visibleRows(totalRows, parentHeight, slotY);
        if (visibleRows >= totalRows) {
            return false;
        }
        scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, totalRows - visibleRows);
        return true;
    }

    /**
     * Upstream {@code GuiSideInventory#calcCappedYSize}: shed whole rows until the panel fits.
     *
     * <p>The space a panel has is what is left of the parent <em>below its own top edge</em>, not
     * the whole parent (issue #88). Upstream never had to say so because its side inventories always
     * start at {@code yOffset = 0}; the Tool Station's starts below the tool-tab column, and without
     * this a 54-slot chest next to it drew nine rows out through the bottom of the GUI.
     */
    static int visibleRows(int totalRows, int parentHeight, int slotY) {
        int rows = Math.min(totalRows, MAX_ROWS);
        int available = parentHeight - PARENT_MARGIN - (slotY - SideInventorySlots.SLOT_INSET);
        while (rows > 1 && rows * SLOT + BORDER * 2 > available) {
            rows--;
        }
        return rows;
    }

    /**
     * The nine-sliced {@code generic.png} frame upstream wraps every side inventory in. Shared with
     * {@link SmelteryScreen}'s melt grid (issue #101), which is the same upstream widget
     * ({@code GuiWidgetBorder}) around a different grid.
     */
    static void renderBorder(GuiGraphics graphics, int x, int y, int width, int height) {
        int innerW = width - BORDER * 2;
        int innerH = height - BORDER * 2;
        int right = x + width - BORDER;
        int bottom = y + height - BORDER;
        int sheetRight = SHEET - BORDER;
        int edgeW = SHEET - BORDER * 2;

        blit(graphics, x, y, BORDER, BORDER, 0, 0, BORDER, BORDER);
        blit(graphics, x + BORDER, y, innerW, BORDER, BORDER, 0, edgeW, BORDER);
        blit(graphics, right, y, BORDER, BORDER, sheetRight, 0, BORDER, BORDER);

        blit(graphics, x, y + BORDER, BORDER, innerH, 0, BORDER, BORDER, edgeW);
        blit(graphics, right, y + BORDER, BORDER, innerH, sheetRight, BORDER, BORDER, edgeW);

        blit(graphics, x, bottom, BORDER, BORDER, 0, sheetRight, BORDER, BORDER);
        blit(graphics, x + BORDER, bottom, innerW, BORDER, BORDER, sheetRight, edgeW, BORDER);
        blit(graphics, right, bottom, BORDER, BORDER, sheetRight, sheetRight, BORDER, BORDER);
    }

    /**
     * Stretching blit ({@code (uWidth, vHeight)} of the sheet drawn into {@code (width, height)}),
     * same helper {@link InfoPanel} uses. The border strips are uniform, so stretching them reads
     * identically to upstream's tiling and costs one draw call instead of a loop.
     */
    private static void blit(GuiGraphics graphics, int x, int y, int width, int height,
            int u, int v, int uWidth, int vHeight) {
        graphics.blit(TEXTURE, x, y, width, height, u, v, uWidth, vHeight, SHEET, SHEET);
    }
}
