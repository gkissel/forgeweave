package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.ListingPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookPage.ModifierPage;
import dev.gkissel.forgeweave.client.book.BookPage.TextPage;
import dev.gkissel.forgeweave.client.book.BookPage.ToolPage;
import dev.gkissel.forgeweave.client.book.MaterialPageContent.Icon;
import dev.gkissel.forgeweave.client.book.MaterialPageContent.StatGroup;
import dev.gkissel.forgeweave.item.GuideBookItem;
import dev.gkissel.forgeweave.item.SavedBookPagePayload;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

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

    /**
     * A listing row's bullet, and the whole row while hovered -- upstream {@code ElementListingLeft}
     * colours its prefix, and its text while hovered, "dark red" (vanilla {@code DARK_RED}, 0xAA0000).
     */
    private static final int LINK_COLOR = 0xFFAA0000;

    /**
     * The wash behind a hovered grid icon: {@code ElementPageIconLink.draw} fills
     * {@code appearance.hoverColor | (0x77 << 24)}, and Mantle's {@code AppearanceData.hoverColor}
     * default -- which Tinkers' {@code appearance.json} does not override -- is already
     * {@code 0x77EE541C}, so the or-in is a no-op and this is upstream's exact fill.
     */
    private static final int HOVER_COLOR = 0x77EE541C;

    /** {@code ContentPageIconList}'s default 20px icon cell and the 15px page inset it builds at. */
    private static final int GRID_CELL = 20;
    private static final int GRID_MARGIN = 15;

    /** Upstream's {@code oldPage} idle value: -1 is the (valid) cover, so "none" is -2. */
    private static final int NO_BACK_SPREAD = -2;

    /** {@code ElementItem.ITEM_SIZE_HARDCODED}: the cell one display-bar or slot icon occupies. */
    private static final int ITEM_SIZE = 16;

    /**
     * A {@link Region} that only carries hover text: upstream's {@code ElementItem}/{@code TextData}
     * tooltips, which are not links. Any non-negative target is a {@code go-to-page-rtn} jump.
     */
    private static final int NO_TARGET = -1;

    /** Draws one already-measured piece of a page at the origin it was laid out at. */
    @FunctionalInterface
    private interface Drawer {
        void draw(GuiGraphics graphics, int x, int y);
    }

    /**
     * A clickable area inside one block, positioned relative to the block's origin: upstream's
     * {@code go-to-page-rtn:} action, plus the hover text an icon link carries. Compared by
     * identity, so a drawer can ask "am I the hovered one?" without two identical rows shadowing
     * each other.
     */
    private record Region(int dx, int dy, int w, int h, int targetPage, @Nullable Component tooltip) {}

    /**
     * The smallest piece of a page that must not be split across leaves: one wrapped body line, a
     * whole title, an image, a tool icon, one row of a listing or icon grid. {@link BookLayout}
     * sees only the height.
     */
    private record Block(int height, Drawer drawer, List<Region> regions) {
        Block(int height, Drawer drawer) {
            this(height, drawer, List.of());
        }
    }

    /** One rendered leaf. The index is the screen's own page, not a {@link BookPage}, so it has no blocks. */
    private record PageSlot(List<Block> blocks, boolean index) {}

    private final List<BookSection> sections;
    private final List<PageSlot> slots = new ArrayList<>();
    private final int[] sectionStartSlot;

    /** -1 is the closed cover; spread s shows slots {@code 2s-1} (none for s=0) and {@code 2s}. */
    private int spread = -1;
    /** Where the returner arrow goes back to after an index jump; {@link #NO_BACK_SPREAD} = hidden. */
    private int backSpread = NO_BACK_SPREAD;

    /** For each source page, the slot its first leaf became -- what a listing link jumps to. */
    private int[] pageFirstSlot = new int[0];

    /** For each slot, the source page it renders ({@code -1} for the index leaf) -- what a bookmark names. */
    private int[] slotPage = new int[0];

    /**
     * The hand holding the book this screen was opened from, so closing can bookmark the open page
     * on it (issue #623); {@code null} when the screen was opened without an item (the screenshot
     * harness), which saves nothing -- upstream {@code GuiBook} likewise takes a nullable item.
     */
    @Nullable
    private final InteractionHand hand;

    /**
     * The bookmark read off the item, applied once the first {@link #init} has laid the pages out
     * -- upstream's constructor {@code openPage(book.findPageNumber(...))} call. Consumed on use so
     * a window resize re-runs {@code init} without yanking the reader back.
     */
    @Nullable
    private String pendingBookmark;

    /** The link under the cursor as of the last {@link #render}; drives hover styling and tooltips. */
    @Nullable
    private Region hovered;

    /**
     * Frames drawn since the screen opened. Mantle's {@code ElementItem} advances its cycling stack
     * every {@code ITEM_SWITCH_TICKS} draws, not every game tick, so a cycling icon reads this.
     */
    private int frame;

    public BookScreen(List<BookSection> sections) {
        this(sections, "", null);
    }

    /**
     * @param bookmark the {@code section.page} saved on the book item, or {@code ""} for the cover
     * @param hand     the hand holding the book, or {@code null} when no item backs this screen
     */
    public BookScreen(List<BookSection> sections, String bookmark, @Nullable InteractionHand hand) {
        super(Component.translatable(BookContent.TITLE));
        this.sections = sections;
        this.sectionStartSlot = new int[sections.size()];
        this.pendingBookmark = bookmark.isEmpty() ? null : bookmark;
        this.hand = hand;
    }

    /**
     * Measures every page into blocks and lays them out into slots (issue #428). Runs here rather
     * than in the constructor because measuring needs {@link #font}, which {@link Screen#init} sets;
     * the result depends only on the content, so a resize rebuilds an identical layout.
     */
    @Override
    protected void init() {
        super.init();
        List<List<Block>> blocks = new ArrayList<>();
        int[] sectionStartPage = new int[this.sections.size()];
        for (int i = 0; i < this.sections.size(); i++) {
            sectionStartPage[i] = blocks.size();
            for (BookPage page : this.sections.get(i).pages()) {
                blocks.add(blocksOf(page, sectionStartPage[i]));
            }
        }

        List<BookLayout.Slot> laid = BookLayout.paginate(
                blocks.stream().map(page -> page.stream().map(Block::height).toList()).toList(),
                PAGE_TEXT_H);

        this.slots.clear();
        this.slots.add(new PageSlot(List.of(), true));
        this.slotPage = new int[1 + laid.size()];
        this.slotPage[0] = -1;
        for (BookLayout.Slot slot : laid) {
            this.slotPage[this.slots.size()] = slot.page();
            this.slots.add(new PageSlot(blocks.get(slot.page())
                    .subList(slot.firstBlock(), slot.firstBlock() + slot.blockCount()), false));
        }
        this.pageFirstSlot = new int[blocks.size()];
        for (int page = 0; page < blocks.size(); page++) {
            this.pageFirstSlot[page] = 1 + BookLayout.firstSlotOf(laid, page);
        }
        for (int i = 0; i < this.sections.size(); i++) {
            this.sectionStartSlot[i] = this.pageFirstSlot[sectionStartPage[i]];
        }
        this.spread = Math.min(this.spread, lastSpread());

        // Upstream's constructor ends with openPage(book.findPageNumber(savedPage)) -- applied
        // here instead because resolving a bookmark to a spread needs the layout above (#623).
        if (this.pendingBookmark != null) {
            String bookmark = this.pendingBookmark;
            this.pendingBookmark = null;
            if (SavedPage.INDEX.equals(bookmark)) {
                this.spread = 0;
            } else {
                int page = SavedPage.find(this.sections, bookmark);
                if (page >= 0) {
                    this.spread = BookGeometry.spreadOf(this.pageFirstSlot[page]);
                }
            }
        }
    }

    /** Opens the book straight onto a spread -- the screenshot harness's entry point. */
    public void openSpread(int spread) {
        this.spread = Math.min(spread, lastSpread());
    }

    /**
     * Upstream {@code GuiBook#onGuiClosed} (issue #623): bookmark the page left open on the book
     * item -- {@code ""} from the cover, so the bookmark clears, else the left leaf's page (the
     * index leaf as {@link SavedPage#INDEX}). {@code SavedBookPagePayload#apply} writes the
     * client's copy (upstream's {@code BookHelper.writeSavedPage}) and the payload has the server
     * write the authoritative one (upstream's {@code PacketUpdateSavedPage}); both only ever touch
     * a held guide book, so nothing is sent when the hand holds something else by now.
     */
    @Override
    public void removed() {
        if (this.hand != null && this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.getItemInHand(this.hand).getItem() instanceof GuideBookItem) {
            String bookmark = bookmark();
            SavedBookPagePayload.apply(this.minecraft.player, this.hand, bookmark);
            PacketDistributor.sendToServer(new SavedBookPagePayload(this.hand, bookmark));
        }
        super.removed();
    }

    /** The bookmark for the current spread -- what upstream saves for {@code this.page}. */
    private String bookmark() {
        if (this.spread < 0) {
            return "";
        }
        if (this.spread == 0) {
            return SavedPage.INDEX;
        }
        int leftSlot = BookGeometry.leftSlot(this.spread);
        if (leftSlot < 0 || leftSlot >= this.slotPage.length) {
            return "";
        }
        return SavedPage.name(this.sections, this.slotPage[leftSlot]);
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
        this.frame++;
        this.hovered = regionAt(mouseX, mouseY);
        if (this.spread < 0) {
            renderCover(graphics);
        } else {
            renderSpread(graphics);
        }
        renderArrows(graphics, mouseX, mouseY);
        if (this.hovered != null && this.hovered.tooltip() != null) {
            graphics.renderTooltip(this.font, this.hovered.tooltip(), mouseX, mouseY);
        }
    }

    /**
     * The link under the cursor, or {@code null} -- {@code BookElement.isHovered} over the two
     * visible leaves. Walks the same cursor arithmetic {@link #renderSlot} draws with, so
     * hit-testing carries no state over from the last frame, and a region the page scissor clipped
     * away is not clickable either.
     */
    @Nullable
    private Region regionAt(double mouseX, double mouseY) {
        if (this.spread < 0) {
            return null;
        }
        int top = BookGeometry.pageY(this.height);
        Region left = regionIn(BookGeometry.leftSlot(this.spread), BookGeometry.leftPageX(this.width),
                top, mouseX, mouseY);
        return left != null ? left
                : regionIn(BookGeometry.rightSlot(this.spread), BookGeometry.rightPageX(this.width),
                        top, mouseX, mouseY);
    }

    @Nullable
    private Region regionIn(int slotIndex, int x, int top, double mouseX, double mouseY) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return null;
        }
        int cursor = top;
        for (Block block : this.slots.get(slotIndex).blocks()) {
            for (Region region : block.regions()) {
                int rx = x + region.dx();
                int ry = cursor + region.dy();
                if (mouseX >= rx && mouseX < rx + region.w() && mouseY >= ry && mouseY < ry + region.h()
                        && ry >= top && ry + region.h() <= top + PAGE_TEXT_H) {
                    return region;
                }
            }
            cursor += block.height();
        }
        return null;
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
    private List<Block> blocksOf(BookPage page, int sectionStartPage) {
        List<Block> blocks = new ArrayList<>();
        if (page instanceof ListingPage listing) {
            listingBlocks(blocks, listing.titleKey(), listing.links(), sectionStartPage);
        } else if (page instanceof IconGridPage grid) {
            iconGridBlocks(blocks, grid.titleKey(), grid.links(), sectionStartPage);
        } else if (page instanceof TextPage text) {
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

    /**
     * Upstream {@code ContentListing#build}: a titled page of {@code "- "}-bulleted rows, each a
     * link to one page of the section, the bullet turning into {@code " > "} and the row into dark
     * red under the cursor ({@code ElementListingLeft#draw}).
     *
     * <p>Deviation: past fourteen rows upstream halves the row width and fills a second column,
     * with no third -- a 31-row listing like Forgeweave's modifiers section would draw its last
     * three rows off the leaf. This engine paginates instead, so a long listing continues onto the
     * next leaf, the same answer issue #428 already gave for every other overlong page.
     */
    private void listingBlocks(List<Block> blocks, String titleKey, List<BookLink> links, int sectionStartPage) {
        titleBlock(blocks, Component.translatable(titleKey));
        for (BookLink link : links) {
            Component label = Component.translatable(link.labelKey());
            Region region = new Region(0, 0, PAGE_TEXT_W, this.font.lineHeight,
                    sectionStartPage + link.targetPage(), null);
            blocks.add(new Block(this.font.lineHeight + 1, (graphics, x, y) -> {
                boolean over = this.hovered == region;
                String bullet = over ? " > " : "- ";
                graphics.drawString(this.font, bullet, x, y, LINK_COLOR, false);
                graphics.drawString(this.font, label, x + this.font.width(bullet), y,
                        over ? LINK_COLOR : TEXT_COLOR, false);
            }, List.of(region)));
        }
    }

    /**
     * Upstream {@code ContentPageIconList#build}: a titled grid of item icons inset 15px from the
     * page edge in 20px cells, filled left to right and wrapped, each icon linking to a page and
     * naming it on hover behind a hover wash ({@code ElementPageIconLink}). With #430's real
     * 182px leaf the row is upstream's own {@code (PAGE_WIDTH - 30) / 20} = 7 columns.
     *
     * <p>Deviations: one grid row is one block, so an overlong grid continues onto the next leaf
     * rather than onto upstream's hand-counted {@code getPagesNeededForItemCount} overview pages,
     * and the cells stay at scale 1 -- which is what upstream's own scale search settles on the
     * moment a grid needs more than one page, as Forgeweave's material roster does. Upstream also
     * dims an unhovered icon to 50% alpha; {@code GuiGraphics#renderItem} takes no colour
     * multiplier in 1.21, so only the hover wash survives.
     */
    private void iconGridBlocks(List<Block> blocks, String titleKey, List<BookLink> links, int sectionStartPage) {
        titleBlock(blocks, Component.translatable(titleKey));
        int columns = Math.max(1, (PAGE_TEXT_W - 2 * GRID_MARGIN) / GRID_CELL);
        for (int first = 0; first < links.size(); first += columns) {
            List<ItemStack> icons = new ArrayList<>();
            List<Region> regions = new ArrayList<>();
            for (BookLink link : links.subList(first, Math.min(first + columns, links.size()))) {
                icons.add(link.icon() == null ? ItemStack.EMPTY : link.icon().get());
                Component name = Component.translatable(link.labelKey());
                regions.add(new Region(GRID_MARGIN + regions.size() * GRID_CELL, 0, GRID_CELL, GRID_CELL,
                        sectionStartPage + link.targetPage(),
                        link.color() == null ? name : name.copy().withStyle(Style.EMPTY.withColor(link.color()))));
            }
            List<Region> row = List.copyOf(regions);
            List<ItemStack> stacks = List.copyOf(icons);
            blocks.add(new Block(GRID_CELL, (graphics, x, y) -> {
                for (int i = 0; i < row.size(); i++) {
                    int cellX = x + row.get(i).dx();
                    if (this.hovered == row.get(i)) {
                        graphics.fill(cellX, y, cellX + GRID_CELL, y + GRID_CELL, HOVER_COLOR);
                    }
                    graphics.renderItem(stacks.get(i), cellX + 2, y + 2);
                }
            }, row));
        }
    }

    /**
     * Upstream {@code ContentMaterial#build} (issue #633): the material's coloured name, the display
     * bar of the stations and demo tools that use it, one block per stat type it carries -- the
     * parts that read that stat as a cycling icon, the stat type's name underlined beside them, then
     * its hover-explained stat and trait lines -- and the italic flavour quote if the language file
     * has one. {@link MaterialPageContent} decides all of that; this measures and draws it.
     *
     * <p>Deviation: upstream lays the page out absolutely -- the display bar as a vertical column
     * pinned to the outer edge of the leaf, the stat blocks as two columns with EXTRA and the quote
     * on a hand-computed second row. This engine paginates a single column (issue #428), which is
     * what keeps a many-trait material's blocks from running off the leaf, so the bar becomes a row
     * under the title and the blocks stack.
     */
    private void materialBlocks(List<Block> blocks, MaterialPage page) {
        Material material = page.material();
        titleBlock(blocks, Component
                .translatable("material." + page.id().getNamespace() + "." + page.id().getPath())
                .withStyle(Style.EMPTY.withColor(material.color())));

        displayBarBlock(blocks, page);

        for (StatGroup group : MaterialPageContent.statGroups(material)) {
            statGroupBlocks(blocks, page, group);
        }

        flavourBlocks(blocks, page.id());
    }

    /**
     * Upstream {@code ContentMaterial#addDisplayItems}: the representative item, the stations that
     * turn the material into parts, then demo tools built wholly out of it, capped at nine icons in
     * total and each naming itself on hover.
     */
    private void displayBarBlock(List<Block> blocks, MaterialPage page) {
        List<Icon> icons = new ArrayList<>(MaterialPageContent.craftIcons(page.id(), page.material()));
        HolderLookup.Provider registries = registries();
        if (registries != null) {
            for (ToolAssemblyRecipes.Entry entry :
                    MaterialPageContent.demoTools(page.material(), MaterialPageContent.DISPLAY_ITEMS - icons.size())) {
                ToolAssemblyRecipes.assemble(registries, entry, Collections.nCopies(entry.slotCount(), page.id()))
                        .ifPresent(tool -> icons.add(new Icon(tool, null)));
            }
        }
        if (icons.isEmpty()) {
            return;
        }

        List<Icon> bar = List.copyOf(icons.subList(0, Math.min(icons.size(), MaterialPageContent.DISPLAY_ITEMS)));
        List<Region> regions = new ArrayList<>();
        for (int i = 0; i < bar.size(); i++) {
            Icon icon = bar.get(i);
            regions.add(new Region(i * ITEM_SIZE, 0, ITEM_SIZE, ITEM_SIZE, NO_TARGET,
                    icon.tooltip() == null ? icon.stack().getHoverName() : icon.tooltip()));
        }
        blocks.add(new Block(ITEM_SIZE + 4, (graphics, x, y) -> {
            for (int i = 0; i < bar.size(); i++) {
                graphics.renderItem(bar.get(i).stack(), x + i * ITEM_SIZE, y);
            }
        }, List.copyOf(regions)));
    }

    /**
     * Upstream {@code ContentMaterial#addStatsDisplay}: the parts that have a use for the stat drawn
     * at half scale, the stat type's own name underlined beside them, then the block's stat lines
     * and the traits a part of that kind grants -- every one of which explains itself on hover.
     */
    private void statGroupBlocks(List<Block> blocks, MaterialPage page, StatGroup group) {
        List<ItemStack> parts = MaterialPageContent.partsFor(group.kind(), page.id());
        Component name = Component.translatable(group.nameKey()).withStyle(ChatFormatting.UNDERLINE);
        blocks.add(new Block(this.font.lineHeight + 4, (graphics, x, y) -> {
            if (!parts.isEmpty()) {
                ItemStack part = parts.get((this.frame / MaterialPageContent.ITEM_SWITCH_TICKS) % parts.size());
                graphics.pose().pushPose();
                graphics.pose().translate(x, y + 1.0F, 0.0F);
                graphics.pose().scale(0.5F, 0.5F, 1.0F);
                graphics.renderItem(part, 0, 0);
                graphics.pose().popPose();
            }
            graphics.drawString(this.font, name, x + 10, y + 1, TITLE_COLOR, false);
        }));
        for (Component stat : group.stats()) {
            blocks.add(hoverLineBlock(stat, TEXT_COLOR));
        }
        for (Component trait : group.traits()) {
            blocks.add(hoverLineBlock(trait, TEXT_COLOR));
        }
    }

    /**
     * Upstream's inspirational quote: {@code "<material>.flavour"} in italics, wrapped in quotation
     * marks, and nothing at all when the language file defines no such string -- which upstream's own
     * book does for every material but wood and stone.
     */
    private void flavourBlocks(List<Block> blocks, ResourceLocation id) {
        String key = MaterialPageContent.flavourKey(id);
        if (!I18n.exists(key)) {
            return;
        }
        bodyBlocks(blocks, Component.literal("\"").append(Component.translatable(key)).append("\"")
                .withStyle(ChatFormatting.ITALIC));
    }

    /** The registries a demo tool is assembled against, or {@code null} outside a loaded world. */
    @Nullable
    private HolderLookup.Provider registries() {
        return this.minecraft == null || this.minecraft.level == null
                ? null : this.minecraft.level.registryAccess();
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

    /**
     * A body line that explains itself on hover, which is what upstream's material page gives every
     * stat line ({@code IMaterialStats#getLocalizedDesc}) and every trait line. The hover area is
     * the drawn text, not the whole leaf width, exactly as a {@code TextData} tooltip's is.
     */
    private Block hoverLineBlock(Component line, int color) {
        Component tooltip = line.getStyle().getHoverEvent() == null ? null
                : line.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
        Block block = lineBlock(line, color);
        return tooltip == null ? block : new Block(block.height(), block.drawer(), List.of(new Region(
                0, 0, this.font.width(line), this.font.lineHeight, NO_TARGET, tooltip)));
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
                Region region = regionAt(mouseX, mouseY);
                if (region != null) {
                    goToPageReturning(BookGeometry.spreadOf(this.pageFirstSlot[region.targetPage()]));
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
                goToPageReturning(BookGeometry.spreadOf(this.sectionStartSlot[i]));
                return true;
            }
        }
        return false;
    }

    /**
     * Upstream's {@code go-to-page-rtn}: the jump every index row, listing row and grid icon makes,
     * which remembers where it came from so the returner arrow can undo it.
     */
    private void goToPageReturning(int spread) {
        int from = this.spread;
        this.spread = spread;
        this.backSpread = from;
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
