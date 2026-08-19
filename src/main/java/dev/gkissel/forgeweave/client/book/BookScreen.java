package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

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
 * The guide book's screen, a 1:1 port of the 1.12 engine's chrome and flow (issue #430): Mantle's
 * {@code GuiBook} (branch {@code 1.12}, commit {@code 340a386af51a97efaac0e71a3f1ff87fb267efe9},
 * MIT -- NOTICE.md rows for the two derived sheets). The cover is {@code bookfront.png} tinted with
 * the appearance cover color under an untinted title plate; a spread is {@code book.png}'s 412x200
 * back tinted the same way with an untinted parchment leaf blitted per visible page; the page-turn,
 * returner and back-to-index arrows are {@code GuiArrow}'s sprites, tinted with the appearance
 * arrow colors and hit-tested at upstream's positions ({@link BookGeometry}). Navigation is
 * upstream's: the next arrow opens the cover onto the lone index leaf, arrows/left-right (or A/D)
 * keys/mouse wheel turn spreads, an index-row jump shows the returner arrow ({@code
 * go-to-page-rtn}), and every spread past the first carries the corner arrow back to the index.
 *
 * <p>Pages are fixed-size and never scroll, so a page whose content is taller than a leaf continues
 * onto further leaves: {@link #blocksOf} measures a page into indivisible blocks and
 * {@link BookLayout} fills leaves with them (issue #428). Upstream instead hand-splits its authored
 * content; Forgeweave's pages are generated from live registries, so the split is computed.
 *
 * <p>ponytail: this ports the chrome, geometry and navigation; Mantle's element/content-type
 * system ({@code BookElement}, {@code ContentText}...) and Tinkers' authored book data are the
 * #430 follow-up, so the page kinds below still render through the minimal block model.
 */
public class BookScreen extends Screen {

    private static final ResourceLocation TEX_BOOK = ResourceLocation.fromNamespaceAndPath(
            Forgeweave.MODID, "textures/derived/gui/book/book.png");
    private static final ResourceLocation TEX_COVER = ResourceLocation.fromNamespaceAndPath(
            Forgeweave.MODID, "textures/derived/gui/book/bookfront.png");

    private static final int PAGE_TEXT_W = BookLayout.PAGE_TEXT_W;
    private static final int PAGE_TEXT_H = BookLayout.PAGE_TEXT_H;

    private static final int TEXT_COLOR = 0xFF3F3F3F;
    private static final int TITLE_COLOR = 0xFF542D0B;

    /** Upstream's {@code oldPage} idle value: -1 is the (valid) cover, so "none" is -2. */
    private static final int NO_BACK_SPREAD = -2;

    /** Draws one already-measured piece of a page at the origin it was laid out at. */
    @FunctionalInterface
    private interface Drawer {
        void draw(GuiGraphics graphics, int x, int y);
    }

    /**
     * The smallest piece of a page that must not be split across leaves: one wrapped body line, a
     * whole title, an image, a tool icon. {@link BookLayout} sees only the height.
     */
    private record Block(int height, Drawer drawer) {}

    /** One rendered leaf. The index is the screen's own page, not a {@link BookPage}, so it has no blocks. */
    private record PageSlot(List<Block> blocks, boolean index) {}

    private final List<BookSection> sections;
    private final List<PageSlot> slots = new ArrayList<>();
    private final int[] sectionStartSlot;

    /** -1 is the closed cover; spread s shows slots {@code 2s-1} (none for s=0) and {@code 2s}. */
    private int spread = -1;
    /** Where the returner arrow goes back to after an index jump; {@link #NO_BACK_SPREAD} = hidden. */
    private int backSpread = NO_BACK_SPREAD;

    public BookScreen(List<BookSection> sections) {
        super(Component.translatable(BookContent.TITLE));
        this.sections = sections;
        this.sectionStartSlot = new int[sections.size()];
    }

    /**
     * Measures every page into blocks and lays them out into slots (issue #428). Runs here rather
     * than in the constructor because measuring needs {@link #font}, which {@link Screen#init} sets;
     * the result depends only on the content, so a resize rebuilds an identical layout.
     */
    @Override
    protected void init() {
        super.init();
        List<BookPage> pages = new ArrayList<>();
        int[] sectionStartPage = new int[this.sections.size()];
        for (int i = 0; i < this.sections.size(); i++) {
            sectionStartPage[i] = pages.size();
            pages.addAll(this.sections.get(i).pages());
        }

        List<List<Block>> blocks = pages.stream().map(this::blocksOf).toList();
        List<BookLayout.Slot> laid = BookLayout.paginate(
                blocks.stream().map(page -> page.stream().map(Block::height).toList()).toList(),
                PAGE_TEXT_H);

        this.slots.clear();
        this.slots.add(new PageSlot(List.of(), true));
        for (BookLayout.Slot slot : laid) {
            this.slots.add(new PageSlot(blocks.get(slot.page())
                    .subList(slot.firstBlock(), slot.firstBlock() + slot.blockCount()), false));
        }
        for (int i = 0; i < this.sections.size(); i++) {
            this.sectionStartSlot[i] = 1 + BookLayout.firstSlotOf(laid, sectionStartPage[i]);
        }
        this.spread = Math.min(this.spread, lastSpread());
    }

    /** Opens the book straight onto a spread -- the screenshot harness's entry point. */
    public void openSpread(int spread) {
        this.spread = Math.min(spread, lastSpread());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int lastSpread() {
        return BookGeometry.lastSpread(this.slots.size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.spread < 0) {
            renderCover(graphics);
        } else {
            renderSpread(graphics);
        }
        renderArrows(graphics, mouseX, mouseY);
    }

    /**
     * Upstream's cover: {@code bookfront.png}'s base tinted with the cover color, the title plate
     * untinted over it, then the title at 2.5x (2x once it outgrows 67px) and the subtitle at 1.5x,
     * both gold with a shadow, at {@code GuiBook.drawScreen}'s exact offsets.
     */
    private void renderCover(GuiGraphics graphics) {
        int x = this.width / 2 - BookGeometry.PAGE_WIDTH_UNSCALED / 2;
        int y = BookGeometry.spreadTop(this.height);

        setColor(graphics, BookGeometry.COVER_COLOR);
        chromeBlit(graphics, TEX_COVER, x, y, 0, 0,
                BookGeometry.PAGE_WIDTH_UNSCALED, BookGeometry.PAGE_HEIGHT_UNSCALED);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        chromeBlit(graphics, TEX_COVER, x, y, 0, BookGeometry.PAGE_HEIGHT_UNSCALED,
                BookGeometry.PAGE_WIDTH_UNSCALED, BookGeometry.PAGE_HEIGHT_UNSCALED);

        Component title = Component.translatable(BookContent.TITLE);
        float scale = this.font.width(title) <= 67 ? 2.5F : 2F;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1F);
        graphics.drawString(this.font, title,
                (int) ((this.width / 2) / scale + 3 - this.font.width(title) / 2),
                (int) ((this.height / 2 - this.font.lineHeight / 2) / scale - 4),
                BookGeometry.COVER_TEXT_COLOR, true);
        graphics.pose().popPose();

        Component subtitle = Component.translatable(BookContent.SUBTITLE);
        graphics.pose().pushPose();
        graphics.pose().scale(1.5F, 1.5F, 1F);
        graphics.drawString(this.font, subtitle,
                (int) ((this.width / 2) / 1.5F + 7 - this.font.width(subtitle) / 2),
                (int) ((this.height / 2 + 100 - this.font.lineHeight * 2) / 1.5F),
                BookGeometry.COVER_TEXT_COLOR, true);
        graphics.pose().popPose();
    }

    /** Upstream's spread: the cover-tinted 412x200 back, then one untinted parchment leaf per visible slot. */
    private void renderSpread(GuiGraphics graphics) {
        int left = BookGeometry.spreadLeft(this.width);
        int top = BookGeometry.spreadTop(this.height);

        setColor(graphics, BookGeometry.COVER_COLOR);
        chromeBlit(graphics, TEX_BOOK, left, top, 0, 0,
                BookGeometry.PAGE_WIDTH_UNSCALED * 2, BookGeometry.PAGE_HEIGHT_UNSCALED);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int leftSlot = BookGeometry.leftSlot(this.spread);
        if (leftSlot >= 0) {
            chromeBlit(graphics, TEX_BOOK, left, top, 0, BookGeometry.PAGE_HEIGHT_UNSCALED,
                    BookGeometry.PAGE_WIDTH_UNSCALED, BookGeometry.PAGE_HEIGHT_UNSCALED);
            renderSlot(graphics, leftSlot, BookGeometry.leftPageX(this.width), BookGeometry.pageY(this.height));
        }
        int rightSlot = BookGeometry.rightSlot(this.spread);
        if (rightSlot < this.slots.size()) {
            chromeBlit(graphics, TEX_BOOK, this.width / 2, top, BookGeometry.PAGE_WIDTH_UNSCALED,
                    BookGeometry.PAGE_HEIGHT_UNSCALED, BookGeometry.PAGE_WIDTH_UNSCALED,
                    BookGeometry.PAGE_HEIGHT_UNSCALED);
            renderSlot(graphics, rightSlot, BookGeometry.rightPageX(this.width), BookGeometry.pageY(this.height));
        }
    }

    private void renderSlot(GuiGraphics graphics, int slotIndex, int x, int y) {
        PageSlot slot = this.slots.get(slotIndex);
        graphics.enableScissor(x, y, x + PAGE_TEXT_W, y + PAGE_TEXT_H);
        if (slot.index()) {
            renderIndex(graphics, x, y);
        } else {
            int cursor = y;
            for (Block block : slot.blocks()) {
                block.drawer().draw(graphics, x, cursor);
                cursor += block.height();
            }
        }
        graphics.disableScissor();
        // Upstream: pNum centred at PAGE_WIDTH/2, PAGE_HEIGHT - 10, 0xFFAAAAAA, no shadow.
        String number = String.valueOf(BookGeometry.pageNumber(slotIndex));
        graphics.drawString(this.font, number,
                x + BookGeometry.PAGE_WIDTH / 2 - this.font.width(number) / 2,
                y + BookGeometry.PAGE_HEIGHT - 10, BookGeometry.PAGE_NUMBER_COLOR, false);
    }

    private void renderIndex(GuiGraphics graphics, int x, int y) {
        Component title = Component.translatable(BookContent.INDEX_TITLE);
        graphics.drawString(this.font, title, x + (PAGE_TEXT_W - this.font.width(title)) / 2, y,
                TITLE_COLOR, false);
        for (int i = 0; i < this.sections.size(); i++) {
            int rowY = indexRowY(y, i);
            ItemStack icon = this.sections.get(i).icon().get();
            graphics.renderItem(icon, x, rowY);
            graphics.drawString(this.font, Component.translatable(this.sections.get(i).titleKey()),
                    x + 20, rowY + 4, TEXT_COLOR, false);
        }
    }

    private void renderArrows(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean cover = this.spread < 0;
        if (hasNext()) {
            drawArrow(graphics, BookGeometry.nextArrowX(this.width, cover), BookGeometry.arrowY(this.height),
                    BookGeometry.ARROW_NEXT_U, BookGeometry.ARROW_NEXT_V,
                    BookGeometry.ARROW_W, BookGeometry.ARROW_H, mouseX, mouseY);
        }
        if (cover) {
            return;
        }
        if (hasPrev()) {
            drawArrow(graphics, BookGeometry.prevArrowX(this.width), BookGeometry.arrowY(this.height),
                    BookGeometry.ARROW_PREV_U, BookGeometry.ARROW_PREV_V,
                    BookGeometry.ARROW_W, BookGeometry.ARROW_H, mouseX, mouseY);
        }
        if (hasBack()) {
            drawArrow(graphics, BookGeometry.backArrowX(this.width), BookGeometry.backArrowY(this.height),
                    BookGeometry.ARROW_BACK_U, BookGeometry.ARROW_BACK_V,
                    BookGeometry.ARROW_W, BookGeometry.ARROW_H, mouseX, mouseY);
        }
        if (hasIndexArrow()) {
            drawArrow(graphics, BookGeometry.indexArrowX(this.width), BookGeometry.indexArrowY(this.height),
                    BookGeometry.ARROW_INDEX_U, BookGeometry.ARROW_INDEX_V,
                    BookGeometry.ARROW_INDEX_SIZE, BookGeometry.ARROW_INDEX_SIZE, mouseX, mouseY);
        }
    }

    /** {@code GuiArrow.drawButton}: the sprite from book.png, tinted with the arrow (or hover) color. */
    private void drawArrow(GuiGraphics graphics, int x, int y, int u, int v, int w, int h,
            int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        setColor(graphics, hovered ? BookGeometry.ARROW_HOVER_COLOR : BookGeometry.ARROW_COLOR);
        chromeBlit(graphics, TEX_BOOK, x, y, u, v, w, h);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * A chrome-sheet blit with blending guaranteed on -- {@code GuiBook.drawScreen} leads with
     * {@code enableAlpha()/enableBlend()} and re-establishes GL state before each page. The sheets'
     * anti-aliased edge pixels need real alpha blending, and the GL blend state at blit time is
     * whatever the last draw (text rendering included) left behind.
     */
    private static void chromeBlit(GuiGraphics graphics, ResourceLocation tex, int x, int y,
            int u, int v, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(tex, x, y, u, v, w, h, BookGeometry.TEX_SIZE, BookGeometry.TEX_SIZE);
    }

    private static void setColor(GuiGraphics graphics, int rgb) {
        graphics.setColor(((rgb >> 16) & 0xff) / 255.0F, ((rgb >> 8) & 0xff) / 255.0F,
                (rgb & 0xff) / 255.0F, 1.0F);
    }

    /**
     * Measures one page into the blocks {@link BookLayout} then spreads across leaves. A page's
     * title, image and tool icon are its leading blocks, so a continuation leaf -- which starts
     * part-way down the list -- never repeats them, exactly as upstream's hand-split
     * {@code welcome2.json} carries an empty title.
     */
    private List<Block> blocksOf(BookPage page) {
        List<Block> blocks = new ArrayList<>();
        if (page instanceof TextPage text) {
            titleBlock(blocks, Component.translatable(text.titleKey()));
            ResourceLocation image = text.image();
            if (image != null) {
                int imageH = PAGE_TEXT_W * 9 / 16;
                blocks.add(new Block(imageH + 4, (graphics, x, y) ->
                        graphics.blit(image, x, y, PAGE_TEXT_W, imageH, 0, 0, 854, 480, 854, 480)));
            }
            bodyBlocks(blocks, Component.translatable(text.textKey()));
        } else if (page instanceof ToolPage tool) {
            ItemStack stack = new ItemStack(tool.tool());
            blocks.add(new Block(36, (graphics, x, y) -> {
                graphics.pose().pushPose();
                graphics.pose().translate(x + PAGE_TEXT_W / 2.0F - 16.0F, y, 0.0F);
                graphics.pose().scale(2.0F, 2.0F, 1.0F);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
            }));
            titleBlock(blocks, tool.tool().getDescription());
            bodyBlocks(blocks, Component.translatable(tool.tool().getDescriptionId() + ".description"));
        } else if (page instanceof MaterialPage material) {
            materialBlocks(blocks, material);
        } else if (page instanceof ModifierPage modifier) {
            String base = "modifier." + modifier.id().getNamespace() + "." + modifier.id().getPath();
            titleBlock(blocks, Component.translatable(base + ".name"));
            bodyBlocks(blocks, Component.translatable(base + ".description"));
        }
        return List.copyOf(blocks);
    }

    private void materialBlocks(List<Block> blocks, MaterialPage page) {
        titleBlock(blocks, Component
                .translatable("material." + page.id().getNamespace() + "." + page.id().getPath())
                .withStyle(Style.EMPTY.withColor(page.material().color())));

        List<Component> stats = new ArrayList<>(StationText.headStats(page.material()));
        stats.addAll(StationText.handleStats(page.material()));
        stats.addAll(StationText.extraStats(page.material()));
        stats.addAll(StationText.bowStats(page.material()));
        stats.addAll(StationText.bowstringStats(page.material()));
        for (Component line : stats) {
            blocks.add(lineBlock(line, TEXT_COLOR));
        }

        Component traitsHeader = Component.translatable("gui.forgeweave.tool_station.traits");
        blocks.add(new Block(4 + this.font.lineHeight + 2, (graphics, x, y) ->
                graphics.drawString(this.font, traitsHeader, x, y + 4, TITLE_COLOR, false)));

        List<ResourceLocation> traitIds = page.material().traits().all();
        if (traitIds.isEmpty()) {
            blocks.add(lineBlock(Component.translatable("gui.forgeweave.tool_station.no_traits"), TEXT_COLOR));
            return;
        }
        for (ResourceLocation traitId : traitIds) {
            blocks.add(lineBlock(Component
                    .translatable("trait." + traitId.getNamespace() + "." + traitId.getPath() + ".name")
                    .withStyle(Style.EMPTY.withColor(page.material().color())), TEXT_COLOR));
        }
    }

    /** A whole centred title is one block: it wraps, but it never splits across leaves. */
    private void titleBlock(List<Block> blocks, Component title) {
        List<FormattedCharSequence> lines = this.font.split(title, PAGE_TEXT_W);
        blocks.add(new Block(lines.size() * this.font.lineHeight + 5, (graphics, x, y) -> {
            int cursor = y;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(this.font, line, x + (PAGE_TEXT_W - this.font.width(line)) / 2, cursor,
                        TITLE_COLOR, false);
                cursor += this.font.lineHeight;
            }
        }));
    }

    /** One block per wrapped line, so body text continues onto the next leaf a line at a time. */
    private void bodyBlocks(List<Block> blocks, Component text) {
        for (FormattedCharSequence line : this.font.split(text, PAGE_TEXT_W)) {
            blocks.add(new Block(this.font.lineHeight + 1,
                    (graphics, x, y) -> graphics.drawString(this.font, line, x, y, TEXT_COLOR, false)));
        }
    }

    private Block lineBlock(Component line, int color) {
        return new Block(this.font.lineHeight + 1,
                (graphics, x, y) -> graphics.drawString(this.font, line, x, y, color, false));
    }

    private int indexRowY(int pageY, int row) {
        return pageY + 16 + row * 20;
    }

    private boolean hasPrev() {
        return this.spread >= 0; // upstream: visible on every spread; from the first it re-closes the cover
    }

    private boolean hasNext() {
        return this.spread < lastSpread();
    }

    private boolean hasBack() {
        return this.backSpread >= -1;
    }

    private boolean hasIndexArrow() {
        return this.spread >= 1; // upstream: visible once the shown pages are past the index section
    }

    private boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Upstream's {@code actionPerformed}: every arrow press also retires the returner arrow. */
    private void turnTo(int spread) {
        this.spread = spread;
        this.backSpread = NO_BACK_SPREAD;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolean cover = this.spread < 0;
            if (hasNext() && over(mouseX, mouseY, BookGeometry.nextArrowX(this.width, cover),
                    BookGeometry.arrowY(this.height), BookGeometry.ARROW_W, BookGeometry.ARROW_H)) {
                turnTo(this.spread + 1);
                return true;
            }
            if (!cover) {
                if (hasPrev() && over(mouseX, mouseY, BookGeometry.prevArrowX(this.width),
                        BookGeometry.arrowY(this.height), BookGeometry.ARROW_W, BookGeometry.ARROW_H)) {
                    turnTo(this.spread - 1);
                    return true;
                }
                if (hasBack() && over(mouseX, mouseY, BookGeometry.backArrowX(this.width),
                        BookGeometry.backArrowY(this.height), BookGeometry.ARROW_W, BookGeometry.ARROW_H)) {
                    turnTo(this.backSpread);
                    return true;
                }
                if (hasIndexArrow() && over(mouseX, mouseY, BookGeometry.indexArrowX(this.width),
                        BookGeometry.indexArrowY(this.height), BookGeometry.ARROW_INDEX_SIZE,
                        BookGeometry.ARROW_INDEX_SIZE)) {
                    turnTo(0);
                    return true;
                }
                if (this.spread == 0 && indexRowClicked(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** An index jump is upstream's {@code go-to-page-rtn}: it remembers where to return to. */
    private boolean indexRowClicked(double mouseX, double mouseY) {
        int x = BookGeometry.rightPageX(this.width);
        int y = BookGeometry.pageY(this.height);
        for (int i = 0; i < this.sections.size(); i++) {
            int rowY = indexRowY(y, i);
            if (mouseX >= x && mouseX < x + PAGE_TEXT_W && mouseY >= rowY && mouseY < rowY + 18) {
                int from = this.spread;
                this.spread = BookGeometry.spreadOf(this.sectionStartSlot[i]);
                this.backSpread = from;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Upstream binds A/D alongside the arrow keys (GuiBook.keyTyped).
        if ((keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) && hasNext()) {
            turnTo(this.spread + 1);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) && hasPrev()) {
            turnTo(this.spread - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Upstream turns pages on the wheel: scroll down is next, scroll up is previous. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0 && hasNext()) {
            turnTo(this.spread + 1);
            return true;
        }
        if (scrollY > 0 && hasPrev()) {
            turnTo(this.spread - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
