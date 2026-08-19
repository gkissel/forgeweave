package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookLink;
import dev.gkissel.forgeweave.client.book.BookPage;
import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.ListingPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookPage.ModifierPage;
import dev.gkissel.forgeweave.client.book.BookPage.TextPage;
import dev.gkissel.forgeweave.client.book.BookPage.ToolPage;
import dev.gkissel.forgeweave.client.book.BookSection;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * Parity audit 2026-08-18, T48 (issue #479): upstream's book opens every generated section with a
 * navigation page and Forgeweave's opened straight onto content, so a player looking for one of the
 * ~60 material pages or ~30 modifier pages had to page through the lot.
 *
 * <p>Upstream {@code ContentListingSectionTransformer#transform} inserts a {@code ContentListing}
 * page at index 0 of the tools and modifiers sections, one linked row per page; upstream
 * {@code AbstractMaterialSectionTransformer#transform} instead opens the materials section with
 * {@code ContentPageIconList} overview pages, one linked item icon per material with the material's
 * coloured name as its hover text. The intro and smeltery sections get no transformer, so they keep
 * opening on their first content page.
 *
 * <p>Everything asserted here is the pure structure, so it needs no client: {@link
 * BookContent#sections(List)} is the registry-free seam {@link
 * BookContent#sections(RegistryAccess)} delegates to.
 */
class BookListingTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = BookListingTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped material JSON: " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static ResourceLocation fw(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** The three materials the book's grid is checked against, in the order the book sorts them. */
    private static List<Map.Entry<ResourceLocation, Material>> materials() {
        return List.of(
                Map.entry(fw("iron"), shipped("iron")),
                Map.entry(fw("stone"), shipped("stone")),
                Map.entry(fw("wood"), shipped("wood")));
    }

    private static List<BookSection> sections() {
        return BookContent.sections(materials());
    }

    private static BookSection section(String titleKey) {
        return sections().stream().filter(s -> s.titleKey().equals(titleKey)).findFirst()
                .orElseThrow(() -> new AssertionError("no section " + titleKey));
    }

    /** Every link must land on a real page of its own section, never on the listing page itself. */
    private static void assertTargetsAreInSection(BookSection section, List<BookLink> links) {
        for (BookLink link : links) {
            assertTrue(link.targetPage() >= 1 && link.targetPage() < section.pages().size(),
                    "link " + link.labelKey() + " targets page " + link.targetPage()
                            + ", outside section " + section.titleKey() + " (" + section.pages().size() + " pages)");
        }
    }

    @Test
    void theToolsSectionOpensOnAListingOfEveryToolPage() {
        BookSection tools = section("book.forgeweave.section.tools");

        ListingPage listing = assertInstanceOf(ListingPage.class, tools.pages().get(0),
                "upstream ContentListingSectionTransformer puts the listing at index 0 of the tools section");
        assertEquals("book.forgeweave.section.tools", listing.titleKey(),
                "upstream titles the listing with the section name (book.translate(sectionName))");
        assertEquals(tools.pages().size() - 1, listing.links().size(),
                "every page of the section but the listing itself gets a row");
        assertTargetsAreInSection(tools, listing.links());

        List<String> labels = listing.links().stream().map(BookLink::labelKey).toList();
        assertEquals("book.forgeweave.tools.repairing.title", labels.get(0),
                "the static repairing page keeps its own title as its row");
        for (Supplier<? extends Item> tool : BookContent.TOOLS) {
            assertTrue(labels.contains(tool.get().getDescriptionId()),
                    "no listing row for " + tool.get().getDescriptionId());
        }
        for (BookLink link : listing.links()) {
            assertNull(link.icon(), "upstream's ContentListing rows are text, not icons");
        }
    }

    @Test
    void everyToolsListingRowPointsAtItsOwnPage() {
        BookSection tools = section("book.forgeweave.section.tools");
        ListingPage listing = (ListingPage) tools.pages().get(0);

        for (BookLink link : listing.links()) {
            BookPage target = tools.pages().get(link.targetPage());
            String label = target instanceof ToolPage tool
                    ? tool.tool().getDescriptionId()
                    : ((TextPage) target).titleKey();
            assertEquals(label, link.labelKey(),
                    "row " + link.labelKey() + " jumps to a page titled " + label);
        }
    }

    @Test
    void theModifiersSectionOpensOnAListing() {
        BookSection modifiers = section("book.forgeweave.section.modifiers");

        ListingPage listing = assertInstanceOf(ListingPage.class, modifiers.pages().get(0),
                "upstream ModifierSectionTransformer extends ContentListingSectionTransformer");
        assertEquals(modifiers.pages().size() - 1, listing.links().size());
        assertTargetsAreInSection(modifiers, listing.links());
        assertTrue(ForgeweaveModifiers.ids().size() >= 20,
                "non-vacuity: expected the shipped modifier roster, saw " + ForgeweaveModifiers.ids().size());

        List<String> labels = listing.links().stream().map(BookLink::labelKey).toList();
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            assertTrue(labels.contains("modifier." + id.getNamespace() + "." + id.getPath() + ".name"),
                    "no listing row for modifier " + id);
        }
        for (BookLink link : listing.links()) {
            BookPage target = modifiers.pages().get(link.targetPage());
            if (target instanceof ModifierPage modifier) {
                assertEquals("modifier." + modifier.id().getNamespace() + "." + modifier.id().getPath() + ".name",
                        link.labelKey());
            }
        }
    }

    @Test
    void theMaterialsSectionOpensOnAnIconGridOfEveryMaterial() {
        BookSection materials = section("book.forgeweave.section.materials");

        IconGridPage grid = assertInstanceOf(IconGridPage.class, materials.pages().get(0),
                "upstream AbstractMaterialSectionTransformer opens the materials section with "
                        + "ContentPageIconList overview pages, not with a text page");
        assertEquals("book.forgeweave.section.materials", grid.titleKey());
        assertEquals(3, grid.links().size(), "one icon per material");
        assertTargetsAreInSection(materials, grid.links());

        for (BookLink link : grid.links()) {
            assertNotNull(link.icon(), "every grid entry needs the material's representative item");
            assertNotNull(link.color(), "the hover name is the material's coloured name upstream");
            MaterialPage target = assertInstanceOf(MaterialPage.class, materials.pages().get(link.targetPage()),
                    "a grid icon links to that material's own page");
            assertEquals("material." + target.id().getNamespace() + "." + target.id().getPath(), link.labelKey());
            assertEquals(target.material().color(), link.color());
        }
    }

    /** The intro text page survives the grid going in front of it -- it is content, not chrome. */
    @Test
    void theMaterialsIntroPageIsStillInTheSection() {
        BookSection materials = section("book.forgeweave.section.materials");
        List<String> titles = new ArrayList<>();
        for (BookPage page : materials.pages()) {
            if (page instanceof TextPage text) {
                titles.add(text.titleKey());
            }
        }

        assertTrue(titles.contains("book.forgeweave.materials.intro.title"), "saw only " + titles);
    }

    @Test
    void theMaterialGridFollowsTheSectionsMaterialOrder() {
        BookSection materials = section("book.forgeweave.section.materials");
        IconGridPage grid = (IconGridPage) materials.pages().get(0);

        List<ResourceLocation> pageOrder = materials.pages().stream()
                .filter(MaterialPage.class::isInstance).map(page -> ((MaterialPage) page).id()).toList();
        List<ResourceLocation> gridOrder = grid.links().stream()
                .map(link -> ((MaterialPage) materials.pages().get(link.targetPage())).id()).toList();

        assertEquals(pageOrder, gridOrder);
        assertEquals(pageOrder.stream().sorted(Comparator.comparing(ResourceLocation::getPath)).toList(), pageOrder,
                "the book sorts materials by path; the grid must not reorder them");
    }

    /** Upstream registers a transformer for tools/materials/modifiers only (TinkerBook:36-39). */
    @Test
    void sectionsWithNoUpstreamTransformerKeepOpeningOnContent() {
        for (String titleKey : List.of("book.forgeweave.section.intro", "book.forgeweave.section.smeltery")) {
            BookPage first = section(titleKey).pages().get(0);
            assertFalse(first instanceof ListingPage || first instanceof IconGridPage,
                    titleKey + " has no upstream section transformer, so it must open on its first page");
        }
    }

    /** The registry-backed entry point must produce the same tree the pure seam does. */
    @Test
    void everySectionStillHasPages() {
        for (BookSection section : sections()) {
            assertFalse(section.pages().isEmpty(), section.titleKey() + " has no pages");
        }
    }
}
