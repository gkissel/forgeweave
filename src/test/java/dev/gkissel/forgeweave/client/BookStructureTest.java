package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookGeometry;
import dev.gkissel.forgeweave.client.book.BookStructure;
import dev.gkissel.forgeweave.client.book.BookStructure.PageDef;
import dev.gkissel.forgeweave.client.book.BookStructure.SectionDef;

/**
 * Issue #651, the data-driven slice: the book's section and page structure moves out of hardcoded
 * Java into {@code assets/forgeweave/book/} -- {@code appearance.json}, {@code index.json} and
 * {@code sections/*.json}, the same file shapes the 1.12 book ships in
 * {@code resources/assets/tconstruct/book/} (pinned commit c01173c, MIT, NOTICE.md) read through
 * Mantle's {@code BookLoader}/{@code SectionData} semantics (Mantle {@code 1.12} @ {@code 340a386}).
 * Upstream's per-language content dirs collapse into the lang system: a page def carries no data
 * file, its title/text live in {@code book.forgeweave.<section>.<page>.title|.text} lang keys
 * ({@code ForgeweaveLanguageProvider}), which is this repo's one localization channel.
 *
 * <p>These tests pin the parse and the shipped structure: the appearance values are upstream's, the
 * section order is the shipped book's, and every authored page's lang keys are in the
 * {@link BookContent#staticLangKeys()} manifest {@code BookLangCoverageTest} walks -- so a page
 * added to the JSON without its lang lines still fails the build.
 */
class BookStructureTest {

    private static BookStructure structure() {
        return BookStructure.load();
    }

    private static SectionDef section(String name) {
        return structure().sections().stream().filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no section " + name));
    }

    /** Tinkers' {@code appearance.json} sets coverColor 0xffce85 and drawSectionListText true. */
    @Test
    void theAppearanceJsonCarriesUpstreamsValues() {
        BookStructure.Appearance appearance = structure().appearance();

        assertEquals(0xffce85, appearance.coverColor(), "Tinkers' appearance.json coverColor");
        assertEquals(BookGeometry.COVER_COLOR, appearance.coverColor(),
                "the chrome tint and the appearance data must agree");
        assertTrue(appearance.drawSectionListText(),
                "Tinkers' appearance.json draws the section titles under the index grid icons");
        assertEquals(BookContent.TITLE, appearance.title(),
                "upstream's appearance title becomes the title lang key");
        assertEquals(BookContent.SUBTITLE, appearance.subtitle());
    }

    /** Upstream {@code index.json} order for the sections Forgeweave has, with an icon each. */
    @Test
    void theIndexJsonListsTheShippedSectionsInUpstreamOrder() {
        List<String> names = structure().sections().stream().map(SectionDef::name).toList();

        assertEquals(List.of("intro", "tools", "materials", "modifiers", "smeltery"), names,
                "upstream index.json: intro, tools, materials, modifiers, smeltery");
        for (SectionDef def : structure().sections()) {
            assertNotNull(def.iconItem(), "index entry " + def.name() + " needs its icon item");
            assertFalse(def.pages().isEmpty(),
                    "section " + def.name() + " lists no authored pages");
        }
    }

    /** {@code sections/tools.json} is now the tools roster's source of truth, upstream's order. */
    @Test
    void theToolsSectionDataDrivesTheToolRoster() {
        List<PageDef> defs = section("tools").pages();

        assertEquals("repairing", defs.get(0).name(), "upstream tools.json opens with repairing");
        assertEquals("text", defs.get(0).type());
        List<PageDef> tools = defs.stream().filter(def -> def.type().equals("tool")).toList();
        assertEquals(defs.size() - 1, tools.size(), "every page after repairing is a tool page");
        assertTrue(tools.size() >= 21, "expected the full tool roster, saw " + tools.size());
        for (PageDef def : tools) {
            assertNotNull(def.item(), "tool page " + def.name() + " names no item");
            assertTrue(def.item().startsWith("forgeweave:"), def.item());
        }
        assertEquals(BookContent.TOOLS.size(), tools.size(),
                "BookContent.TOOLS must be built from the JSON roster");
    }

    /**
     * The #651 content tail: upstream's {@code sections/intro.json} follows its welcome pages with
     * one text page per workshop station (blank pattern, crafting station, stencil table, pattern
     * chest, part builder, part chest, tool station, tool forge) -- Forgeweave keeps its condensed
     * welcome/workshop pair (#273/#663) and appends the eight station pages, every one a station
     * Forgeweave actually ships. Upstream's own en_us bodies for these pages are unshipped
     * {@code "Text Goes Here"} placeholders, so only the roster and titles are upstream's; the body
     * text is Forgeweave's own.
     */
    @Test
    void theIntroSectionCarriesThePerStationPages() {
        List<PageDef> pages = section("intro").pages();

        assertEquals(List.of("welcome", "workshop", "blank_pattern", "crafting_station",
                "stencil_table", "pattern_chest", "part_builder", "part_chest", "tool_station",
                "tool_forge"), pages.stream().map(PageDef::name).toList(),
                "the intro section: the condensed welcome pair, then upstream's station pages");
        for (PageDef def : pages) {
            assertEquals("text", def.type(), "intro page " + def.name() + " is a plain text page");
        }
    }

    /** The smeltery intro is upstream's {@code "image with text below"} page type. */
    @Test
    void theSmelteryIntroCarriesItsImage() {
        PageDef intro = section("smeltery").pages().get(0);

        assertEquals("image with text below", intro.type(),
                "upstream smeltery.json's intro page type");
        assertNotNull(intro.image(), "the intro page must name the smeltery scene image");
        assertTrue(intro.image().startsWith("forgeweave:textures/derived/"), intro.image());
    }

    /** A JSON page def with no lang lines must fail {@code BookLangCoverageTest}, not render raw keys. */
    @Test
    void everyAuthoredTextPageHasItsLangKeysInTheManifest() {
        List<String> manifest = BookContent.staticLangKeys();
        for (SectionDef sectionDef : structure().sections()) {
            for (PageDef def : sectionDef.pages()) {
                if (def.type().equals("tool")) {
                    continue; // tool pages take their keys from the item, covered separately
                }
                String base = "book.forgeweave." + sectionDef.name() + "." + def.name();
                assertTrue(manifest.contains(base + ".title"),
                        base + ".title missing from staticLangKeys()");
                assertTrue(manifest.contains(base + ".text"),
                        base + ".text missing from staticLangKeys()");
            }
        }
    }

    /** Unknown page types are authoring errors and must fail loudly, not render nothing. */
    @Test
    void anUnknownPageTypeFailsTheParse() {
        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> BookStructure.parseSection("bogus", "[{\"name\": \"x\", \"type\": \"structure\"}]"));
        assertTrue(thrown.getMessage().contains("structure"), thrown.getMessage());
    }
}
