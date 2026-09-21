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

    /**
     * Issue #1105: the chapter order is the tutorial order, not upstream's index.json order any
     * more. A chapter may only assume what the chapters before it taught, which puts the Smeltery
     * second (metal is the first wall a new player hits), the new Casting and Alloys chapters
     * straight after it, and the two reference chapters -- Materials' roster and Tools' catalogue --
     * behind the lessons. Nine sections is also exactly what one generated index page holds
     * ({@code BookContent.SECTIONS_PER_INDEX_PAGE}), so the index stays a single leaf.
     */
    @Test
    void theIndexJsonListsTheShippedSectionsInTutorialOrder() {
        List<String> names = structure().sections().stream().map(SectionDef::name).toList();

        assertEquals(
                List.of("intro", "smeltery", "casting", "alloys", "materials", "modifiers", "armor",
                        "leveling", "tools"),
                names,
                "the #1105 tutorial spine: learn the workshop, then melting, then shaping a melt, "
                        + "then mixing melts, then the material ladder those metals climb, then "
                        + "modifiers, armor and leveling, with the Tools catalogue last");
        assertEquals(9, names.size(),
                "nine sections fit one generated index page; a tenth would split the index in two");
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

        assertEquals(List.of("intro", "ranged"),
                defs.stream().filter(def -> def.type().equals("text")).map(PageDef::name).toList(),
                "#1105: repairing moved to the Introduction (a first tool breaks on day one) and the "
                        + "chapter opens on what it is for, then the ranged system");
        List<PageDef> tools = defs.stream().filter(def -> def.type().equals("tool")).toList();
        assertEquals(defs.size() - 2, tools.size(), "every page after the two text pages is a tool");
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
     * welcome/workshop pair (#273/#663) and appends those eight station pages, every one a station
     * Forgeweave actually ships. Upstream's own en_us bodies for these pages are unshipped
     * {@code "Text Goes Here"} placeholders, so only the roster and titles are upstream's; the body
     * text is Forgeweave's own.
     */
    @Test
    void theIntroSectionCarriesThePerStationPages() {
        List<PageDef> pages = section("intro").pages();

        assertEquals(List.of("welcome", "progression", "workshop", "blank_pattern", "stencil_table",
                "part_builder", "tool_station", "first_pickaxe", "repairing", "part_exchange",
                "growing", "crafting_station", "pattern_chest", "part_chest", "tool_forge"),
                pages.stream().map(PageDef::name).toList(),
                "#1105 orders the chapter as the first hour actually runs: the welcome pair (now "
                        + "welcome plus the progression ladder), the four stations a first tool needs "
                        + "in the order it needs them, the worked example, then repair, part exchange "
                        + "and the leveling teaser, then the convenience blocks and the forge."
                        + " Issue #782's Armor Station page went with the block in #1006");
        for (PageDef def : pages) {
            assertEquals("text", def.type(), "intro page " + def.name() + " is a plain text page");
        }
    }

    /**
     * M4-7 (issue #682, docs/SCOPE.md D21): the armor section -- the plating/maille intro, the cast
     * bootstrap, one {@code tool} page per piece (the same page kind the tools section uses, so the
     * piece renders with its parts diagram), then the ARMOR traits and the armor modifiers. The
     * piece pages are not in {@link BookContent#TOOLS} (that roster is the tools section's own), so
     * {@code BookLangCoverageTest} walks them separately.
     */
    @Test
    void theArmorSectionListsThePiecesBetweenItsTextPages() {
        List<PageDef> pages = section("armor").pages();

        assertEquals(List.of("intro", "parts", "first_set", "casting", "helmet", "chestplate",
                "leggings", "boots", "heavy_helmet", "heavy_chestplate", "heavy_leggings",
                "heavy_boots", "traits", "modifiers", "overslime"),
                pages.stream().map(PageDef::name).toList(),
                "#1105 adds first_set (the obsidian way in, previously a clause inside casting) and "
                        + "overslime, which had no page at all");
        assertEquals("forgeweave:chestplate", section("armor").iconItem());
        for (PageDef def : pages) {
            if (def.type().equals("tool")) {
                assertEquals("forgeweave:" + def.name(), def.item(), "armor page " + def.name());
            } else {
                assertEquals("text", def.type(), "armor page " + def.name());
            }
        }
        assertEquals(8, pages.stream().filter(def -> def.type().equals("tool")).count());
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

    /**
     * The smeltery section carries upstream's {@code structure} page (issue #651): the rotating 3D
     * schematic, whose def carries a {@code data} reference to the structure file instead of lang
     * text -- upstream's own page has no title and no text, so it contributes no lang keys. Issue
     * #1105 moves it up to sit directly behind the structure rules it illustrates, and appends the
     * five pages the chapter previously only had Ponder scenes for.
     */
    @Test
    void theSmelterySectionCarriesTheStructurePageBehindItsRules() {
        List<PageDef> pages = section("smeltery").pages();

        assertEquals(List.of("intro", "grout", "structure", "multiblock", "working", "fuel", "cores",
                "entity_melting", "energized", "furnace"),
                pages.stream().map(PageDef::name).toList(),
                "#1105: bricks, then the rules, then the picture, then melting, heat, cores, what "
                        + "walks in, energy, and the two non-smeltery seared multiblocks");
        PageDef multiblock = pages.get(3);

        assertEquals("multiblock", multiblock.name());
        assertEquals("structure", multiblock.type(), "upstream smeltery.json's structure page type");
        assertEquals("structure/smeltery.json", multiblock.data(),
                "the structure page must name its block-span data file");
        List<String> manifest = BookContent.staticLangKeys();
        assertFalse(manifest.contains("book.forgeweave.smeltery.multiblock.title"),
                "upstream's structure page has no title, so the manifest must not demand one");
        assertFalse(manifest.contains("book.forgeweave.smeltery.multiblock.text"));
    }

    /**
     * M7-7 (issue #924, epic #917): the leveling chapter -- three plain text pages, no upstream
     * counterpart to follow (Tool Leveling ships no book, and TAIGA/PlusTiC/Moar Tinkers/Tinkers'
     * Evolution are inspiration-only, so nothing here derives from anywhere).
     */
    @Test
    void theLevelingSectionCarriesItsThreeTextPages() {
        List<PageDef> pages = section("leveling").pages();

        assertEquals(List.of("intro", "earning", "curve"), pages.stream().map(PageDef::name).toList());
        assertEquals("minecraft:experience_bottle", section("leveling").iconItem());
        for (PageDef def : pages) {
            assertEquals("text", def.type(), "leveling page " + def.name());
        }
    }

    /** A JSON page def with no lang lines must fail {@code BookLangCoverageTest}, not render raw keys. */
    @Test
    void everyAuthoredTextPageHasItsLangKeysInTheManifest() {
        List<String> manifest = BookContent.staticLangKeys();
        for (SectionDef sectionDef : structure().sections()) {
            for (PageDef def : sectionDef.pages()) {
                if (def.type().equals("tool") || def.type().equals("structure")) {
                    continue; // these page kinds carry no authored title/text lang keys
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
                () -> BookStructure.parseSection("bogus", "[{\"name\": \"x\", \"type\": \"hologram\"}]"));
        assertTrue(thrown.getMessage().contains("hologram"), thrown.getMessage());
    }
}
