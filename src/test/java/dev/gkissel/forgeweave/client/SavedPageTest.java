package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookSection;
import dev.gkissel.forgeweave.client.book.SavedPage;
import dev.gkissel.forgeweave.material.Material;

/**
 * Issue #623, the saved-page slice: closing the book bookmarks the open page on the item as
 * upstream 1.12 does ({@code GuiBook#onGuiClosed} writing {@code section.name + "." + page.name}
 * through {@code BookLoader#updateSavedPage}, reopened via {@code BookData#findPageNumber} in the
 * {@code GuiBook} constructor -- Mantle {@code 1.12} @ {@code 340a386}, NOTICE.md). Forgeweave's
 * pages are generated from registries rather than named JSON files, so {@link SavedPage} derives
 * the upstream-shaped {@code section.page} string from the page's own identity; these tests pin
 * that derivation to upstream's contract: every page's bookmark resolves back to exactly that
 * page, lookups are case-insensitive ({@code findPageNumber} lowercases), and a string with no
 * dot or no match means the cover ({@code -1}), never a crash.
 */
class SavedPageTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = SavedPageTest.class.getResourceAsStream(path)) {
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static List<BookSection> sections() {
        return BookContent.sections(List.of(
                Map.entry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron"), shipped("iron")),
                Map.entry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stone"), shipped("stone"))));
    }

    private static int pageCount(List<BookSection> sections) {
        return sections.stream().mapToInt(section -> section.pages().size()).sum();
    }

    /**
     * Upstream's contract in one property: the string {@code onGuiClosed} would save for a page is
     * the string {@code findPageNumber} resolves back to that page -- for every page the book has,
     * with no two pages sharing a bookmark.
     */
    @Test
    void everyPageBookmarkResolvesBackToItsOwnPage() {
        List<BookSection> sections = sections();
        Set<String> seen = new HashSet<>();
        for (int page = 0; page < pageCount(sections); page++) {
            String bookmark = SavedPage.name(sections, page);
            assertTrue(seen.add(bookmark), "duplicate bookmark " + bookmark);
            assertEquals(page, SavedPage.find(sections, bookmark),
                    "bookmark " + bookmark + " does not resolve back to page " + page);
        }
    }

    /** The strings themselves are upstream-shaped {@code section.page} names. */
    @Test
    void bookmarksAreSectionDotPage() {
        List<BookSection> sections = sections();
        assertEquals("intro.welcome", SavedPage.name(sections, 0));
        // The tools section opens with its generated listing page (#479), then repairing, then
        // the tools in BookContent.TOOLS order -- pickaxe first.
        int tools = sections.get(0).pages().size();
        assertEquals("tools.listing", SavedPage.name(sections, tools));
        assertEquals("tools.repairing", SavedPage.name(sections, tools + 1));
        assertEquals("tools.pickaxe", SavedPage.name(sections, tools + 2));
        // A material page is named by the material's registry path.
        assertTrue(SavedPage.find(sections, "materials.iron") >= 0, "no materials.iron page");
    }

    /** {@code findPageNumber} lowercases its argument before matching. */
    @Test
    void findIsCaseInsensitive() {
        List<BookSection> sections = sections();
        assertEquals(SavedPage.find(sections, "tools.pickaxe"), SavedPage.find(sections, "Tools.Pickaxe"));
    }

    /**
     * Upstream returns {@code -1} (the cover) for a dotless string, an unknown section, or an
     * unknown page -- a stale bookmark from a removed material must reopen the cover, not crash.
     */
    @Test
    void unknownBookmarksMeanTheCover() {
        List<BookSection> sections = sections();
        assertEquals(-1, SavedPage.find(sections, ""));
        assertEquals(-1, SavedPage.find(sections, "welcome"));
        assertEquals(-1, SavedPage.find(sections, "tools.no_such_tool"));
        assertEquals(-1, SavedPage.find(sections, "no_such_section.pickaxe"));
        assertEquals(-1, SavedPage.find(sections, SavedPage.INDEX));
    }
}
