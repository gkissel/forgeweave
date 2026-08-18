package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.client.book.BookLayout;
import dev.gkissel.forgeweave.client.book.BookLayout.Slot;

/**
 * Issue #428: guide book pages drew straight past the bottom of the parchment, over the page number
 * and off the leaf, because {@code BookScreen} only word-wrapped to the page width and advanced its
 * cursor with no bound at all. {@link BookLayout} is the fix's pure half -- it splits a page that
 * does not fit onto continuation slots, 1.12-style -- so the guarantee "nothing is ever laid out
 * below the page body" is checkable here, with no client.
 */
class BookPaginationTest {

    private static final int LINE = 10; // the vanilla font's 9px line plus BookScreen's 1px lead
    private static final int TITLE = 14; // one wrapped title line plus its 5px gap

    /**
     * A conservative lower bound on the default font's advance for Latin text: 'i'/'l' are the only
     * common glyphs narrower than this, and most are 6px. Wrapping the real page text at this width
     * therefore *under*counts lines, so an overflow measured with it is an overflow for certain.
     */
    private static final int NARROW_CHAR = 5;

    private static List<Integer> blocks(int count, int height) {
        return Collections.nCopies(count, height);
    }

    /** The shipped {@code book.forgeweave.*} strings, i.e. what the player actually reads. */
    private static JsonObject lang() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        for (Path root = dir; root != null; root = root.getParent()) {
            if (Files.exists(root.resolve("settings.gradle"))) {
                return JsonParser.parseString(Files.readString(
                        root.resolve("src/generated/resources/assets/forgeweave/lang/en_us.json"),
                        StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        throw new AssertionError("could not locate project root above " + dir);
    }

    /** Greedy word wrap at {@link #NARROW_CHAR} per character, counting blank paragraph lines. */
    private static int wrappedLines(String text, int width) {
        int lines = 0;
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isBlank()) {
                lines++;
                continue;
            }
            int used = 0;
            for (String word : paragraph.split(" ")) {
                int wordWidth = word.length() * NARROW_CHAR;
                if (used == 0) {
                    used = wordWidth;
                } else if (used + NARROW_CHAR + wordWidth <= width) {
                    used += NARROW_CHAR + wordWidth;
                } else {
                    lines++;
                    used = wordWidth;
                }
            }
            lines++;
        }
        return lines;
    }

    @Test
    void aPageThatFitsStaysOnOneSlot() {
        List<Slot> slots = BookLayout.paginate(List.of(blocks(6, 10)), BookLayout.PAGE_TEXT_H);

        assertEquals(List.of(new Slot(0, 0, 6)), slots);
    }

    @Test
    void aPageTallerThanTheLeafSpillsOntoContinuationSlots() {
        // 40 lines of 10px against a 164px body: 16 fit, so 40 lines need three slots.
        List<Slot> slots = BookLayout.paginate(List.of(blocks(40, LINE)), BookLayout.PAGE_TEXT_H);

        assertEquals(3, slots.size(), "40 body lines should need ceil(40 / 16) slots, got " + slots);
        assertEquals(0, slots.get(0).firstBlock());
        assertEquals(40, slots.stream().mapToInt(Slot::blockCount).sum(), "no block may be dropped");
        assertTrue(slots.stream().allMatch(slot -> slot.page() == 0));
    }

    @Test
    void onlyTheFirstSlotOfAPageCarriesItsTitle() {
        // Block 0 is the title (BookScreen lays a page's title, image and tool icon out first).
        List<Integer> page = new ArrayList<>();
        page.add(TITLE);
        page.addAll(blocks(30, LINE));

        List<Slot> slots = BookLayout.paginate(List.of(page), BookLayout.PAGE_TEXT_H);

        assertTrue(slots.size() > 1, "the page should have split, got " + slots);
        assertEquals(0, slots.get(0).firstBlock(), "the title belongs on the first slot");
        assertTrue(slots.get(1).firstBlock() > 0, "a continuation slot must not repeat the title");
    }

    @Test
    void noSlotIsEverLaidOutBelowThePageBody() {
        List<List<Integer>> pages = List.of(
                blocks(1, 9),
                blocks(40, LINE),
                blocks(17, LINE),
                List.of(TITLE, 70, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10),
                blocks(200, 3));

        List<Slot> slots = BookLayout.paginate(pages, BookLayout.PAGE_TEXT_H);

        for (Slot slot : slots) {
            int height = pages.get(slot.page())
                    .subList(slot.firstBlock(), slot.firstBlock() + slot.blockCount())
                    .stream().mapToInt(Integer::intValue).sum();
            assertTrue(height <= BookLayout.PAGE_TEXT_H,
                    "slot " + slot + " lays out " + height + "px into a " + BookLayout.PAGE_TEXT_H + "px page");
        }
    }

    /** The hard lower bound: an unsplittable block never loops forever and is never dropped. */
    @Test
    void aBlockTallerThanTheLeafStillGetsASlotOfItsOwn() {
        List<Slot> slots = BookLayout.paginate(List.of(List.of(400, LINE)), BookLayout.PAGE_TEXT_H);

        assertEquals(2, slots.size(), "the oversized block should not share a slot, got " + slots);
        assertEquals(new Slot(0, 0, 1), slots.get(0));
        assertEquals(new Slot(0, 1, 1), slots.get(1));
    }

    @Test
    void indexReferencesPointAtTheFirstSlotOfTheirPage() {
        List<List<Integer>> pages = List.of(blocks(40, LINE), blocks(2, LINE), blocks(40, LINE));

        List<Slot> slots = BookLayout.paginate(pages, BookLayout.PAGE_TEXT_H);

        assertEquals(0, BookLayout.firstSlotOf(slots, 0));
        assertEquals(3, BookLayout.firstSlotOf(slots, 1), "page 1 sits after page 0's three slots");
        assertEquals(4, BookLayout.firstSlotOf(slots, 2));
        for (int page = 0; page < pages.size(); page++) {
            assertEquals(0, slots.get(BookLayout.firstSlotOf(slots, page)).firstBlock(),
                    "an index row must land on the start of the page, not mid-way through it");
        }
    }

    /** A material with a long trait list -- the registry-driven page kind that grows without notice. */
    @Test
    void aMaterialPageWithTwelveTraitsSplits() {
        List<Integer> page = new ArrayList<>();
        page.add(TITLE);
        page.addAll(blocks(7, LINE)); // head, handle, extra, bow and bowstring stat rows
        page.add(4 + 9 + 2); // the traits header and its gap
        page.addAll(blocks(12, LINE));

        List<Slot> slots = BookLayout.paginate(List.of(page), BookLayout.PAGE_TEXT_H);

        assertTrue(page.stream().mapToInt(Integer::intValue).sum() > BookLayout.PAGE_TEXT_H,
                "non-vacuity: a 12-trait material page must be taller than a leaf to be worth testing");
        assertTrue(slots.size() > 1, "a 12-trait material page should paginate, got " + slots);
    }

    /**
     * The reported page. "Surviving the First Day" is {@code book.forgeweave.intro.welcome}, and its
     * shipped text alone is far taller than a leaf -- this is the playtest regression, measured
     * against the real string rather than a stand-in so shortening the copy retires it honestly.
     */
    @Test
    void theSurvivingTheFirstDayPageDoesNotFitOnOneLeaf() throws IOException {
        String text = lang().get("book.forgeweave.intro.welcome.text").getAsString();
        assertTrue(text.length() >= 300,
                "non-vacuity: expected the shipped welcome copy, saw " + text.length() + " chars");

        int bodyLines = wrappedLines(text, BookLayout.PAGE_TEXT_W);
        int height = TITLE + bodyLines * LINE;
        assertTrue(height > BookLayout.PAGE_TEXT_H, "the welcome page measures " + height
                + "px, which already fits a " + BookLayout.PAGE_TEXT_H + "px leaf -- retire this test");

        List<Integer> page = new ArrayList<>();
        page.add(TITLE);
        page.addAll(blocks(bodyLines, LINE));
        List<Slot> slots = BookLayout.paginate(List.of(page), BookLayout.PAGE_TEXT_H);

        assertTrue(slots.size() > 1,
                "the welcome page must continue onto a second leaf instead of drawing off the book");
    }
}
