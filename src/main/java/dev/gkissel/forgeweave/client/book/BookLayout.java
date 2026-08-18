package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.List;

/**
 * The guide book's page-to-slot layout: how much of a page fits on one rendered leaf, and where the
 * remainder continues. Deliberately free of every {@code net.minecraft} type so it can be unit
 * tested without a client ({@code BookPaginationTest}); {@link BookScreen} owns the measuring (it
 * needs a {@code Font}) and the drawing, this owns only the arithmetic.
 *
 * <p>Upstream 1.12 never scrolls or resizes a page: its book art is fixed and long content is split
 * across pages by hand in {@code book/en_us/**.json} -- {@code intro/welcome.json} runs on into
 * {@code intro/welcome2.json}, whose {@code "title"} is deliberately {@code ""} because a
 * continuation page carries no title (pinned commit c01173c, MIT). Forgeweave's pages are built
 * from lang keys and live registries rather than hand-split JSON, so the same split happens here
 * automatically at layout time instead (issue #428).
 */
public final class BookLayout {

    /** Body width of one page, in pixels. */
    public static final int PAGE_TEXT_W = 118;

    /** Body height of one page, in pixels. Nothing may ever be drawn below it (issue #428). */
    public static final int PAGE_TEXT_H = 164;

    private BookLayout() {
    }

    /**
     * One rendered leaf of the book: the blocks {@code [firstBlock, firstBlock + blockCount)} of
     * source page {@code page}. A page whose blocks all fit produces exactly one slot; a longer one
     * produces a first slot plus continuation slots, and since a page's title/image/icon are its
     * leading blocks, only the first slot ever shows them.
     */
    public record Slot(int page, int firstBlock, int blockCount) {}

    /**
     * Fills slots greedily, in reading order.
     *
     * @param pages    for each source page, the pixel height of each of its drawable blocks in
     *                 order; a block is the smallest unit that must not be split (one wrapped text
     *                 line, a whole title, an image, a tool icon)
     * @param capacity the body height of one slot, in pixels
     * @return every slot needed to render every page with nothing drawn past {@code capacity}
     */
    public static List<Slot> paginate(List<? extends List<Integer>> pages, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("a page slot needs a positive height, got " + capacity);
        }
        List<Slot> slots = new ArrayList<>();
        for (int page = 0; page < pages.size(); page++) {
            List<Integer> blocks = pages.get(page);
            int first = 0;
            int used = 0;
            for (int i = 0; i < blocks.size(); i++) {
                // The hard lower bound: a block taller than a whole leaf (an oversized image, a
                // wrapped title with nothing under it) still takes a slot of its own rather than
                // being dropped or looping forever -- it is the one case that can still overhang,
                // and BookScreen scissors the page rect so even that stays inside the parchment.
                if (i > first && used + blocks.get(i) > capacity) {
                    slots.add(new Slot(page, first, i - first));
                    first = i;
                    used = 0;
                }
                used += blocks.get(i);
            }
            slots.add(new Slot(page, first, blocks.size() - first));
        }
        return List.copyOf(slots);
    }

    /**
     * The slot a page starts on -- what an index row has to jump to, since a section's first page
     * may now sit several slots further in than its position in the page list suggests.
     */
    public static int firstSlotOf(List<Slot> slots, int page) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).page() == page) {
                return i;
            }
        }
        throw new IllegalArgumentException("no slot renders page " + page);
    }
}
