package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookPage.ModifierPage;
import dev.gkissel.forgeweave.client.book.BookPage.TextPage;
import dev.gkissel.forgeweave.client.book.BookPage.ToolPage;

/**
 * The guide book's screen: a closed cover, then two-page spreads with arrow navigation and a
 * clickable index -- the flow of the 1.12 book (Mantle's {@code GuiBook}: cover, index page,
 * section pages, page-turn arrows), rendered by Forgeweave's own minimal engine. Mantle's book
 * chrome art is not part of the pinned Tinkers' clone, so the spread/cover/arrow art in
 * {@code textures/gui/book.png} is freshly authored ({@code scripts/generate_book_gui.py}) in the
 * same brown-leather-and-parchment look rather than derived.
 *
 * <p>ponytail: navigation is cover -> index -> spreads with the index rows and two arrows as the
 * only interactive elements, hit-tested directly instead of through widget subclasses; the 1.12
 * book's extra chrome (bookmark tabs, back-arrow history, search) is out of scope for #273.
 */
public class BookScreen extends Screen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/gui/book.png");
    private static final int TEX_SIZE = 512;

    // Sheet regions (kept in sync with scripts/generate_book_gui.py).
    private static final int BOOK_W = 320;
    private static final int BOOK_H = 200;
    private static final int COVER_W = 130;
    private static final int COVER_H = 180;
    private static final int COVER_U = 322;
    private static final int COVER_V = 0;
    private static final int ARROW_W = 18;
    private static final int ARROW_H = 10;
    private static final int ARROW_PREV_U = 322;
    private static final int ARROW_NEXT_U = 342;
    private static final int ARROW_V = 182;
    private static final int ARROW_HOVER_V = 194;

    // Per-page content layout, in book-local coordinates.
    private static final int PAGE_TEXT_X_LEFT = 24;
    private static final int PAGE_TEXT_X_RIGHT = 178;
    private static final int PAGE_TEXT_Y = 14;
    private static final int PAGE_TEXT_W = 118;
    private static final int PAGE_TEXT_H = 164;

    private static final int TEXT_COLOR = 0xFF3F3F3F;
    private static final int TITLE_COLOR = 0xFF542D0B;

    /** A blank slot has no page; the index is the screen's own, not a {@link BookPage}. */
    private record PageSlot(@Nullable BookPage page, boolean index) {}

    private final List<BookSection> sections;
    private final List<PageSlot> slots = new ArrayList<>();
    private final int[] sectionStartSlot;

    /** -1 is the closed cover; otherwise the spread showing slots {@code 2n} and {@code 2n+1}. */
    private int spread = -1;

    public BookScreen(List<BookSection> sections) {
        super(Component.translatable(BookContent.TITLE));
        this.sections = sections;
        this.sectionStartSlot = new int[sections.size()];
        this.slots.add(new PageSlot(null, true));
        for (int i = 0; i < sections.size(); i++) {
            this.sectionStartSlot[i] = this.slots.size();
            for (BookPage page : sections.get(i).pages()) {
                this.slots.add(new PageSlot(page, false));
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int bookLeft() {
        return (this.width - BOOK_W) / 2;
    }

    private int bookTop() {
        return (this.height - BOOK_H) / 2;
    }

    private int lastSpread() {
        return (this.slots.size() - 1) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.spread < 0) {
            renderCover(graphics);
            return;
        }
        int left = bookLeft();
        int top = bookTop();
        graphics.blit(TEXTURE, left, top, 0, 0, BOOK_W, BOOK_H, TEX_SIZE, TEX_SIZE);

        renderSlot(graphics, this.spread * 2, left + PAGE_TEXT_X_LEFT, top + PAGE_TEXT_Y);
        renderSlot(graphics, this.spread * 2 + 1, left + PAGE_TEXT_X_RIGHT, top + PAGE_TEXT_Y);

        if (hasPrev()) {
            boolean hover = overPrev(mouseX, mouseY);
            graphics.blit(TEXTURE, prevX(), arrowY(), ARROW_PREV_U, hover ? ARROW_HOVER_V : ARROW_V,
                    ARROW_W, ARROW_H, TEX_SIZE, TEX_SIZE);
        }
        if (hasNext()) {
            boolean hover = overNext(mouseX, mouseY);
            graphics.blit(TEXTURE, nextX(), arrowY(), ARROW_NEXT_U, hover ? ARROW_HOVER_V : ARROW_V,
                    ARROW_W, ARROW_H, TEX_SIZE, TEX_SIZE);
        }
    }

    private void renderCover(GuiGraphics graphics) {
        int left = (this.width - COVER_W) / 2;
        int top = (this.height - COVER_H) / 2;
        graphics.blit(TEXTURE, left, top, COVER_U, COVER_V, COVER_W, COVER_H, TEX_SIZE, TEX_SIZE);
        drawCentredWrapped(graphics, Component.translatable(BookContent.TITLE), left + COVER_W / 2,
                top + 44, COVER_W - 34, 0xFFE8D8A0);
        drawCentredWrapped(graphics, Component.translatable(BookContent.SUBTITLE).withStyle(ChatFormatting.ITALIC),
                left + COVER_W / 2, top + 96, COVER_W - 34, 0xFFC8B880);
    }

    private void renderSlot(GuiGraphics graphics, int slotIndex, int x, int y) {
        if (slotIndex >= this.slots.size()) {
            return;
        }
        PageSlot slot = this.slots.get(slotIndex);
        if (slot.index()) {
            renderIndex(graphics, x, y);
        } else if (slot.page() instanceof TextPage page) {
            renderText(graphics, page, x, y);
        } else if (slot.page() instanceof ToolPage page) {
            renderTool(graphics, page, x, y);
        } else if (slot.page() instanceof MaterialPage page) {
            renderMaterial(graphics, page, x, y);
        } else if (slot.page() instanceof ModifierPage page) {
            renderModifier(graphics, page, x, y);
        }
        // Page number, bottom-centre of the page (a numeral, not translatable text).
        graphics.drawString(this.font, String.valueOf(slotIndex + 1),
                x + (PAGE_TEXT_W - this.font.width(String.valueOf(slotIndex + 1))) / 2,
                y + PAGE_TEXT_H + 4, TEXT_COLOR, false);
    }

    private void renderIndex(GuiGraphics graphics, int x, int y) {
        graphics.drawString(this.font, Component.translatable(BookContent.INDEX_TITLE),
                x + (PAGE_TEXT_W - this.font.width(Component.translatable(BookContent.INDEX_TITLE))) / 2, y,
                TITLE_COLOR, false);
        for (int i = 0; i < this.sections.size(); i++) {
            int rowY = indexRowY(y, i);
            ItemStack icon = this.sections.get(i).icon().get();
            graphics.renderItem(icon, x, rowY);
            graphics.drawString(this.font, Component.translatable(this.sections.get(i).titleKey()),
                    x + 20, rowY + 4, TEXT_COLOR, false);
        }
    }

    private void renderText(GuiGraphics graphics, TextPage page, int x, int y) {
        int cursor = drawTitle(graphics, Component.translatable(page.titleKey()), x, y);
        if (page.image() != null) {
            // Fit the image to the text column, preserving the source's aspect only approximately:
            // the one shipped image (the smeltery scene) is 854x480, close enough to 16:9.
            int imageH = PAGE_TEXT_W * 9 / 16;
            graphics.blit(page.image(), x, cursor, PAGE_TEXT_W, imageH, 0, 0, 854, 480, 854, 480);
            cursor += imageH + 4;
        }
        drawWrapped(graphics, Component.translatable(page.textKey()), x, cursor);
    }

    private void renderTool(GuiGraphics graphics, ToolPage page, int x, int y) {
        ItemStack stack = new ItemStack(page.tool());
        graphics.pose().pushPose();
        graphics.pose().translate(x + PAGE_TEXT_W / 2.0F - 16.0F, y, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
        int cursor = y + 36;
        cursor = drawTitle(graphics, page.tool().getDescription(), x, cursor);
        String descriptionKey = page.tool().getDescriptionId() + ".description";
        drawWrapped(graphics, Component.translatable(descriptionKey), x, cursor);
    }

    private void renderMaterial(GuiGraphics graphics, MaterialPage page, int x, int y) {
        Component name = Component.translatable("material." + page.id().getNamespace() + "." + page.id().getPath())
                .withStyle(Style.EMPTY.withColor(page.material().color()));
        int cursor = drawTitle(graphics, name, x, y);
        // The three stat groups run together, not StationText#materialStats: that one is the info
        // panel's shape (issue #376's underlined headings and null spacers), and a book page has its
        // own headings, no room for five more lines, and a drawString that would NPE on a spacer.
        List<Component> stats = new ArrayList<>(StationText.headStats(page.material()));
        stats.addAll(StationText.handleStats(page.material()));
        stats.addAll(StationText.extraStats(page.material()));
        for (Component line : stats) {
            graphics.drawString(this.font, line, x, cursor, TEXT_COLOR, false);
            cursor += this.font.lineHeight + 1;
        }
        cursor += 4;
        List<ResourceLocation> traitIds = page.material().traits().all();
        Component traitsHeader = Component.translatable("gui.forgeweave.tool_station.traits");
        graphics.drawString(this.font, traitsHeader, x, cursor, TITLE_COLOR, false);
        cursor += this.font.lineHeight + 2;
        if (traitIds.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.forgeweave.tool_station.no_traits"),
                    x, cursor, TEXT_COLOR, false);
            return;
        }
        for (ResourceLocation traitId : traitIds) {
            Component traitName = Component
                    .translatable("trait." + traitId.getNamespace() + "." + traitId.getPath() + ".name")
                    .withStyle(Style.EMPTY.withColor(page.material().color()));
            graphics.drawString(this.font, traitName, x, cursor, TEXT_COLOR, false);
            cursor += this.font.lineHeight + 1;
        }
    }

    private void renderModifier(GuiGraphics graphics, ModifierPage page, int x, int y) {
        String base = "modifier." + page.id().getNamespace() + "." + page.id().getPath();
        int cursor = drawTitle(graphics, Component.translatable(base + ".name"), x, y);
        drawWrapped(graphics, Component.translatable(base + ".description"), x, cursor);
    }

    /** Draws a centred title and returns the y just below it. */
    private int drawTitle(GuiGraphics graphics, Component title, int x, int y) {
        List<FormattedCharSequence> lines = this.font.split(title, PAGE_TEXT_W);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x + (PAGE_TEXT_W - this.font.width(line)) / 2, y,
                    TITLE_COLOR, false);
            y += this.font.lineHeight;
        }
        return y + 5;
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y) {
        for (FormattedCharSequence line : this.font.split(text, PAGE_TEXT_W)) {
            graphics.drawString(this.font, line, x, y, TEXT_COLOR, false);
            y += this.font.lineHeight + 1;
        }
    }

    private void drawCentredWrapped(GuiGraphics graphics, Component text, int centreX, int y, int width, int color) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            graphics.drawString(this.font, line, centreX - this.font.width(line) / 2, y, color, false);
            y += this.font.lineHeight + 1;
        }
    }

    private int indexRowY(int pageY, int row) {
        return pageY + 16 + row * 20;
    }

    private boolean hasPrev() {
        return this.spread >= 0;
    }

    private boolean hasNext() {
        return this.spread < lastSpread();
    }

    private int prevX() {
        return bookLeft() + 14;
    }

    private int nextX() {
        return bookLeft() + BOOK_W - 14 - ARROW_W;
    }

    private int arrowY() {
        return bookTop() + BOOK_H - 20;
    }

    private boolean overPrev(double mouseX, double mouseY) {
        return mouseX >= prevX() && mouseX < prevX() + ARROW_W && mouseY >= arrowY() && mouseY < arrowY() + ARROW_H;
    }

    private boolean overNext(double mouseX, double mouseY) {
        return mouseX >= nextX() && mouseX < nextX() + ARROW_W && mouseY >= arrowY() && mouseY < arrowY() + ARROW_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.spread < 0) {
                this.spread = 0; // any click opens the cover
                return true;
            }
            if (hasPrev() && overPrev(mouseX, mouseY)) {
                this.spread--;
                return true;
            }
            if (hasNext() && overNext(mouseX, mouseY)) {
                this.spread++;
                return true;
            }
            if (this.spread == 0 && indexRowClicked(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean indexRowClicked(double mouseX, double mouseY) {
        int x = bookLeft() + PAGE_TEXT_X_LEFT;
        int y = bookTop() + PAGE_TEXT_Y;
        for (int i = 0; i < this.sections.size(); i++) {
            int rowY = indexRowY(y, i);
            if (mouseX >= x && mouseX < x + PAGE_TEXT_W && mouseY >= rowY && mouseY < rowY + 18) {
                this.spread = this.sectionStartSlot[i] / 2;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT && hasNext()) {
            this.spread++;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT && hasPrev()) {
            this.spread--;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
