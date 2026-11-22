package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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

import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookLayout;
import dev.gkissel.forgeweave.client.book.BookLayout.Slot;
import dev.gkissel.forgeweave.client.book.BookLink;
import dev.gkissel.forgeweave.client.book.BookMaterialOrder;
import dev.gkissel.forgeweave.client.book.BookPage;
import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookSection;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialStage;

/**
 * Issue #846 (M6 UI/schema hardening), pressure point 2: {@code BookContent#withMaterials} builds
 * the guide book's materials section out of {@link IconGridPage}s holding a {@link BookLink} per
 * material -- at the real 216-material roster those pages' blocks (one grid row per
 * {@code BookScreen#iconGridBlocks}) hold far more content than one leaf. This pins two things at
 * the real roster: {@link BookContent#sections(List)} still produces correct grids, every link
 * resolving to its own material's page; and {@link BookLayout}, the block-level paginator issue
 * #428 gave every generated page, actually spreads that many grid rows across several leaves rather
 * than truncating or overflowing one -- the general mechanism
 * {@code BookScreen#blocksOf}/{@code #iconGridBlocks} already routes an oversized grid through
 * (feat/479-book-listing-pages), verified here against real numbers instead of a synthetic case.
 *
 * <p>Issue #1104 split the one grid into one per progression stage, so the roster-wide counts here
 * are the sum across the stage grids and the pagination case is the biggest single stage.
 */
class BookMaterialsScaleTest {

    /** {@code BookScreen}'s private grid geometry: {@code (PAGE_TEXT_W - 2*GRID_MARGIN) / GRID_CELL}. */
    private static final int GRID_CELL = 20;
    private static final int GRID_MARGIN = 15;
    private static final int COLUMNS = Math.max(1, (BookLayout.PAGE_TEXT_W - 2 * GRID_MARGIN) / GRID_CELL);
    private static final int TITLE_HEIGHT = 14; // one wrapped title line plus its 5px gap (BookPaginationTest)

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Map.Entry<ResourceLocation, Material>> shippedMaterials() throws Exception {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Path materialDir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        List<Map.Entry<ResourceLocation, Material>> materials = new ArrayList<>();
        try (Stream<Path> files = Files.list(materialDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                Material material = Material.CODEC.parse(ops, json).getOrThrow();
                materials.add(Map.entry(ResourceLocation.fromNamespaceAndPath("forgeweave", name), material));
            }
        }
        // BookContent#sections(RegistryAccess) sorts by path; the book-listing/pagination guarantees
        // below assume the same order.
        materials.sort(Comparator.comparing(entry -> entry.getKey().getPath()));
        return materials;
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static BookSection materialsSection(List<BookSection> sections) {
        return sections.stream().filter(s -> s.titleKey().equals("book.forgeweave.section.materials")).findFirst()
                .orElseThrow(() -> new AssertionError("no materials section"));
    }

    /** Every stage grid of the chapter, in ladder order (issue #1104 split the one grid per stage). */
    private static List<IconGridPage> stageGrids(BookSection materials) {
        return ((BookPage.ListingPage) materials.pages().get(0)).links().stream()
                .map(row -> materials.pages().get(row.targetPage()))
                // The ladder's last row opens the traits reference's listing, not a grid.
                .filter(IconGridPage.class::isInstance)
                .map(IconGridPage.class::cast)
                // Issue #1225: the A to Z index is a grid too, but not a stage's.
                .filter(grid -> !grid.titleKey().equals(BookContent.MATERIALS_AZ_TITLE))
                .toList();
    }

    private static List<BookLink> stageGridIcons(BookSection materials) {
        return stageGrids(materials).stream().flatMap(grid -> grid.links().stream()).toList();
    }

    @Test
    void theStageGridsHoldEveryShippedMaterialAtRealRosterScale() throws Exception {
        List<Map.Entry<ResourceLocation, Material>> shipped = shippedMaterials();
        assertTrue(shipped.size() >= 100, "non-vacuity: expected the real M6 roster, saw only " + shipped.size());

        BookSection materials = materialsSection(BookContent.sections(shipped));
        assertInstanceOf(BookPage.ListingPage.class, materials.pages().get(0),
                "the materials chapter opens on the stage ladder at real roster scale");
        List<BookLink> icons = stageGridIcons(materials);

        assertEquals(shipped.size(), icons.size(), "one grid icon per shipped material, across all stages");
        for (BookLink link : icons) {
            assertTrue(link.targetPage() >= 1 && link.targetPage() < materials.pages().size(),
                    "link " + link.labelKey() + " targets page " + link.targetPage() + " outside the section");
            MaterialPage target = assertInstanceOf(MaterialPage.class, materials.pages().get(link.targetPage()),
                    "a grid icon must link to that material's own page");
            assertEquals("material." + target.id().getNamespace() + "." + target.id().getPath(), link.labelKey());
        }
    }

    @Test
    void theBiggestStageGridSpansSeveralLeavesAndDropsNothingAtRealRosterScale() throws Exception {
        BookSection materials = materialsSection(BookContent.sections(shippedMaterials()));
        int materialCount = stageGrids(materials).stream()
                .mapToInt(grid -> grid.links().size())
                .max()
                .orElseThrow();
        int rows = (materialCount + COLUMNS - 1) / COLUMNS;
        assertTrue(rows > 1, "non-vacuity: the real roster must need more than one grid row to be worth testing");

        List<Integer> gridPageBlocks = new ArrayList<>();
        gridPageBlocks.add(TITLE_HEIGHT);
        for (int i = 0; i < rows; i++) {
            gridPageBlocks.add(GRID_CELL);
        }

        List<Slot> slots = BookLayout.paginate(List.of(gridPageBlocks), BookLayout.PAGE_TEXT_H);

        System.out.println("[#846/#1104] biggest guide book stage grid: " + materialCount + " materials / " + COLUMNS
                + " columns = " + rows + " rows -> " + slots.size() + " leaves");

        assertTrue(slots.size() > 1,
                "the biggest stage's grid (" + rows + " rows) should need more than one leaf, got "
                        + slots.size());
        int blocksLaidOut = slots.stream().mapToInt(Slot::blockCount).sum();
        assertEquals(gridPageBlocks.size(), blocksLaidOut, "no grid row may be dropped while paginating");
        assertEquals(0, slots.get(0).firstBlock(), "the title stays on the grid's first leaf");
        assertTrue(slots.stream().allMatch(slot -> slot.page() == 0), "every leaf still belongs to the one grid page");
    }

    /** The generated English lang file, the names a player on the default language reads. */
    private static Map<String, String> englishNames() throws Exception {
        Path lang = projectRoot().resolve("src/generated/resources/assets/forgeweave/lang/en_us.json");
        Map<String, String> names = new HashMap<>();
        JsonParser.parseString(Files.readString(lang, StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(entry -> names.put(entry.getKey(), entry.getValue().getAsString()));
        return names;
    }

    /**
     * Issue #1225: the A to Z index sits right behind the ladder page, the ladder's first row opens
     * it, and it holds every material the chapter has a page for exactly once, sorted by English
     * name, each icon opening that material's own page.
     */
    @Test
    void theAToZIndexListsEveryMaterialOnceByNameAndEveryLinkResolves() throws Exception {
        Map<String, String> english = englishNames();
        Comparator<Map.Entry<ResourceLocation, Material>> byName =
                BookMaterialOrder.byName(key -> english.getOrDefault(key, key), Locale.ROOT);
        BookSection materials = materialsSection(
                BookContent.sections(shippedMaterials(), MaterialStage.Lookup.EMPTY, byName));

        BookLink row = ((BookPage.ListingPage) materials.pages().get(0)).links().get(0);
        assertEquals(BookContent.MATERIALS_AZ_TITLE, row.labelKey(), "the ladder's first row is the A to Z index");
        assertEquals(1, row.targetPage(), "the A to Z index is the page right after the ladder");
        IconGridPage az = assertInstanceOf(IconGridPage.class, materials.pages().get(row.targetPage()));
        assertEquals(BookContent.MATERIALS_AZ_TITLE, az.titleKey());

        Set<ResourceLocation> chapter = new HashSet<>();
        materials.pages().stream().filter(MaterialPage.class::isInstance)
                .forEach(page -> chapter.add(((MaterialPage) page).id()));
        assertTrue(chapter.size() >= 100, "non-vacuity: expected the real roster, saw " + chapter.size());

        List<Map.Entry<ResourceLocation, Material>> listed = new ArrayList<>();
        for (BookLink link : az.links()) {
            assertTrue(link.targetPage() >= 1 && link.targetPage() < materials.pages().size(),
                    "link " + link.labelKey() + " targets page " + link.targetPage() + " outside the section");
            MaterialPage target = assertInstanceOf(MaterialPage.class, materials.pages().get(link.targetPage()),
                    "an A to Z icon must link to a material page");
            assertEquals(BookMaterialOrder.nameKey(target.id()), link.labelKey(),
                    "an A to Z icon must link to its own material's page");
            listed.add(Map.entry(target.id(), target.material()));
        }
        assertEquals(chapter.size(), listed.size(), "one A to Z icon per material page, no duplicates");
        assertEquals(chapter, new HashSet<>(listed.stream().map(Map.Entry::getKey).toList()),
                "the A to Z index lists exactly the chapter's materials");
        for (int i = 1; i < listed.size(); i++) {
            assertTrue(byName.compare(listed.get(i - 1), listed.get(i)) < 0,
                    "out of order: " + listed.get(i - 1).getKey() + " before " + listed.get(i).getKey());
        }
    }

    /** Issue #1225: case never decides the A to Z order, and equal names fall back to the id. */
    @Test
    void theAToZOrderIgnoresCaseAndBreaksTiesById() throws Exception {
        Material any = shippedMaterials().get(0).getValue();
        Map<String, String> names = Map.of(
                "material.forgeweave.b", "banana", "material.forgeweave.a", "Cherry",
                "material.forgeweave.c", "Apple", "material.forgeweave.d", "apple");
        List<ResourceLocation> sorted = names.keySet().stream()
                .map(key -> Map.entry(ResourceLocation.fromNamespaceAndPath("forgeweave",
                        key.substring(key.lastIndexOf('.') + 1)), any))
                .sorted(BookMaterialOrder.byName(names::get, Locale.ROOT))
                .map(Map.Entry::getKey)
                .toList();
        assertEquals(List.of("c", "d", "b", "a"), sorted.stream().map(ResourceLocation::getPath).toList());
    }
}
