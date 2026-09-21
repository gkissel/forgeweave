package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import dev.gkissel.forgeweave.client.book.BookPage;
import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookSection;
import dev.gkissel.forgeweave.material.Material;

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

    /** Every icon of every stage grid, in chapter order (issue #1104 split the one grid per stage). */
    private static List<BookLink> stageGridIcons(BookSection materials) {
        return ((BookPage.ListingPage) materials.pages().get(0)).links().stream()
                .map(row -> (IconGridPage) materials.pages().get(row.targetPage()))
                .flatMap(grid -> grid.links().stream())
                .toList();
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
        int materialCount = ((BookPage.ListingPage) materials.pages().get(0)).links().stream()
                .map(row -> (IconGridPage) materials.pages().get(row.targetPage()))
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
}
