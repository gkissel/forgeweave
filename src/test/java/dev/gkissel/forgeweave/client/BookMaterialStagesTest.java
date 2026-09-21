package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookLink;
import dev.gkissel.forgeweave.client.book.BookPage;
import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.ListingPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookPage.TraitPage;
import dev.gkissel.forgeweave.client.book.BookSection;
import dev.gkissel.forgeweave.client.book.BookTraits;
import dev.gkissel.forgeweave.client.book.MaterialPageContent;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialStage;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #1104: the guide book's materials chapter was one alphabetical run of 216 pages, so nothing
 * a player could see said which materials were near their current tier or what a material cost to
 * obtain (review 05-book.md &sect;1, 06-progression.md &sect;3). It is a ladder now: seven
 * {@link MaterialStage}s derived from the harvest rung and the alloy depth, each with its own index
 * grid, and a traits reference the material pages link into.
 *
 * <p>Everything here runs against the real shipped data (216 material JSONs, 32 alloy recipes) and
 * needs no client, the same seam {@code BookListingTest} and {@code BookMaterialsScaleTest} use.
 */
class BookMaterialStagesTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
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

    private static <T> List<T> shipped(String registry, com.mojang.serialization.Codec<T> codec) throws Exception {
        Path dir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave").resolve(registry);
        List<T> parsed = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                parsed.add(codec.parse(ops, json).getOrThrow());
            }
        }
        return List.copyOf(parsed);
    }

    private static List<Map.Entry<ResourceLocation, Material>> shippedMaterials() throws Exception {
        Path dir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        List<Map.Entry<ResourceLocation, Material>> materials = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                materials.add(Map.entry(ResourceLocation.fromNamespaceAndPath("forgeweave", name),
                        Material.CODEC.parse(ops, json).getOrThrow()));
            }
        }
        materials.sort(Comparator.comparing(entry -> entry.getKey().getPath()));
        return List.copyOf(materials);
    }

    private static MaterialStage.Lookup shippedLookup() throws Exception {
        return MaterialStage.Lookup.derive(shipped("alloy_recipe", AlloyRecipe.CODEC));
    }

    private static BookSection section(List<BookSection> sections, String name) {
        return sections.stream().filter(s -> s.titleKey().equals("book.forgeweave.section." + name)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + name + " section"));
    }

    private static List<BookSection> shippedBook() throws Exception {
        return BookContent.sections(shippedMaterials(), shippedLookup());
    }

    /** The ladder page's stage rows, i.e. every row but the last one (the traits reference). */
    private static List<BookLink> stageRows(BookSection chapter, ListingPage ladder) {
        return ladder.links().stream()
                .filter(row -> chapter.pages().get(row.targetPage()) instanceof IconGridPage)
                .toList();
    }

    @Test
    void everyShippedMaterialLandsInExactlyOneStage() throws Exception {
        List<Map.Entry<ResourceLocation, Material>> materials = shippedMaterials();
        assertTrue(materials.size() >= 200, "non-vacuity: expected the real roster, saw " + materials.size());

        BookSection chapter = section(shippedBook(), "materials");
        ListingPage ladder = assertInstanceOf(ListingPage.class, chapter.pages().get(0));

        Set<ResourceLocation> seen = new HashSet<>();
        for (BookLink row : stageRows(chapter, ladder)) {
            for (BookLink icon : ((IconGridPage) chapter.pages().get(row.targetPage())).links()) {
                MaterialPage page = (MaterialPage) chapter.pages().get(icon.targetPage());
                assertTrue(seen.add(page.id()), page.id() + " appears in more than one stage grid");
            }
        }
        assertEquals(materials.stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()), seen,
                "every material gets exactly one stage and no stage invents one");
    }

    /**
     * The rule the issue asked for: the harvest rung's band, one stage further along for an alloy.
     * These four are the corners of it, so a change to the bands or to the alloy bump fails here
     * with the reason rather than as a page-count drift somewhere else.
     */
    @Test
    void theStageRuleReadsTheRungAndTheAlloyDepth() throws Exception {
        MaterialStage.Lookup lookup = shippedLookup();
        Map<ResourceLocation, Material> materials = new java.util.HashMap<>();
        shippedMaterials().forEach(entry -> materials.put(entry.getKey(), entry.getValue()));

        assertEquals(MaterialStage.FIRST_DAY, stageOf(lookup, materials, "wood"),
                "wood is the wooden rung and needs no smeltery");
        assertEquals(MaterialStage.FIRST_SMELTERY, stageOf(lookup, materials, "iron"),
                "iron is the iron rung, melted and cast, no alloy");
        assertEquals(MaterialStage.ENDGAME, stageOf(lookup, materials, "truesteel"),
                "truesteel is the top rung and four alloying steps deep");

        // The netherite rung held 64 materials, a third of the roster, which is the split the alloy
        // bump exists for: cobalt is mined and poured, manyullyn is a second load on top of it.
        assertEquals(0, lookup.depthOf(ResourceLocation.fromNamespaceAndPath("forgeweave", "cobalt")),
                "cobalt is an ore, not an alloy");
        assertFalse(stageOf(lookup, materials, "cobalt") == stageOf(lookup, materials, "manyullyn"),
                "a mined netherite-rung ore and an alloy made out of it must not share a stage");
        assertTrue(lookup.depthOf(ResourceLocation.fromNamespaceAndPath("forgeweave", "glowveil")) >= 2,
                "glowveil is an alloy of alloys (dreadalloy + sparkalloy + brimspar)");
    }

    private static MaterialStage stageOf(MaterialStage.Lookup lookup, Map<ResourceLocation, Material> materials,
            String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forgeweave", name);
        Material material = materials.get(id);
        assertNotNull(material, "missing shipped material " + name);
        return lookup.of(id, material);
    }

    /**
     * Issue #1113 item 6: atomic matter alloy keeps its stats and has to read as the last thing you
     * make. Its own data cannot say so -- it sits on the resonite rung and has no alloy recipe at
     * all, because a nucleosynthesis run on Mekanism's own deepest machine is the only thing that
     * makes the ingot -- so the derivation lands it at {@link MaterialStage#DEEP_ALLOYS} next to
     * mined resonite. The shipped {@code stage/endgame} material tag is what moves it, and this
     * reads that tag out of the shipped datapack rather than restating it.
     */
    @Test
    void theShippedStageTagPutsAtomicMatterAlloyLast() throws Exception {
        Map<ResourceLocation, MaterialStage> overrides = shippedStageTags();
        assertFalse(overrides.isEmpty(), "no shipped stage tag found under data/forgeweave/tags/forgeweave/material");

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forgeweave", "atomic_matter_alloy");
        Map<ResourceLocation, Material> materials = new java.util.HashMap<>();
        shippedMaterials().forEach(entry -> materials.put(entry.getKey(), entry.getValue()));
        MaterialStage.Lookup derived = shippedLookup();

        assertEquals(MaterialStage.DEEP_ALLOYS, derived.of(id, materials.get(id)),
                "the derivation reads only the rung and the alloy depth, so it lands a stage early");
        assertEquals(MaterialStage.ENDGAME,
                new MaterialStage.Lookup(overrides, Map.of()).of(id, materials.get(id)),
                "the shipped stage/endgame tag has to move it to the last stage");
    }

    /** Every {@code forgeweave:stage/<id>} material tag the mod's own datapack ships. */
    private static Map<ResourceLocation, MaterialStage> shippedStageTags() throws Exception {
        Path dir = projectRoot().resolve("src/main/resources/data/forgeweave/tags/forgeweave/material/stage");
        Map<ResourceLocation, MaterialStage> overrides = new java.util.HashMap<>();
        if (!Files.isDirectory(dir)) {
            return overrides;
        }
        for (MaterialStage stage : MaterialStage.values()) {
            Path file = dir.resolve(stage.id() + ".json");
            if (!Files.exists(file)) {
                continue;
            }
            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("values")
                    .forEach(value -> overrides.put(ResourceLocation.parse(value.getAsString()), stage));
        }
        return overrides;
    }

    @Test
    void aStageTagOverridesTheDerivedStage() throws Exception {
        Map.Entry<ResourceLocation, Material> wood = shippedMaterials().stream()
                .filter(entry -> entry.getKey().getPath().equals("wood")).findFirst().orElseThrow();
        MaterialStage.Lookup overridden =
                new MaterialStage.Lookup(Map.of(wood.getKey(), MaterialStage.ENDGAME), Map.of());

        assertEquals(MaterialStage.ENDGAME, overridden.of(wood.getKey(), wood.getValue()),
                "a pack that puts a material in forgeweave:stage/endgame wins over the derivation");
    }

    @Test
    void theChapterOrderIsDeterministicAndGroupsCompatMaterialsLast() throws Exception {
        List<ResourceLocation> once = chapterOrder();
        assertEquals(once, chapterOrder(), "the same data must produce the same chapter twice");

        MaterialStage.Lookup lookup = shippedLookup();
        Map<ResourceLocation, Material> byId = new java.util.HashMap<>();
        shippedMaterials().forEach(entry -> byId.put(entry.getKey(), entry.getValue()));

        // The documented key order: stage, then ranged-only last, then source mod ("" first), then
        // the id path. Walking consecutive pairs proves the whole run without restating it.
        for (int i = 1; i < once.size(); i++) {
            List<Comparable<?>> before = sortKey(lookup, byId, once.get(i - 1));
            List<Comparable<?>> after = sortKey(lookup, byId, once.get(i));
            assertTrue(compare(before, after) <= 0,
                    once.get(i - 1) + " " + before + " must not sort after " + once.get(i) + " " + after);
        }
    }

    private static List<ResourceLocation> chapterOrder() throws Exception {
        return section(shippedBook(), "materials").pages().stream()
                .filter(MaterialPage.class::isInstance)
                .map(page -> ((MaterialPage) page).id())
                .toList();
    }

    private static List<Comparable<?>> sortKey(MaterialStage.Lookup lookup,
            Map<ResourceLocation, Material> byId, ResourceLocation id) {
        Material material = byId.get(id);
        return List.of(lookup.of(id, material).ordinal(),
                dev.gkissel.forgeweave.client.book.BookMaterialOrder.rangedOnly(material) ? 1 : 0,
                dev.gkissel.forgeweave.client.book.BookMaterialOrder.sourceMod(material),
                id.getPath());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(List<Comparable<?>> left, List<Comparable<?>> right) {
        for (int i = 0; i < left.size(); i++) {
            int order = ((Comparable) left.get(i)).compareTo(right.get(i));
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    /**
     * Iron melts from nineteen things, most of them scrap (a chain, an anvil, a crossbow, four
     * pieces of chainmail). The page shows {@link MaterialPageContent#MELTING_SOURCES} of them and
     * leads with the form a player holds, so the list says how the material is made rather than
     * what can be recycled into it.
     */
    @Test
    void theMeltsFromListLeadsWithTheRepairItemThenTheOre() {
        ItemStack ingot = new ItemStack(Items.IRON_INGOT);
        MeltingRecipe fromIngot = new MeltingRecipe(Ingredient.of(Items.IRON_INGOT), Fluids.LAVA, 144, 1200, false);
        MeltingRecipe fromOre = new MeltingRecipe(Ingredient.of(Items.IRON_ORE), Fluids.LAVA, 144, 1200, true);
        MeltingRecipe fromScrap = new MeltingRecipe(Ingredient.of(Items.CROSSBOW), Fluids.LAVA, 144, 1200, false);

        assertEquals(0, MaterialPageContent.meltingRank(fromIngot, ingot));
        assertEquals(1, MaterialPageContent.meltingRank(fromOre, ingot));
        assertEquals(2, MaterialPageContent.meltingRank(fromScrap, ingot));
        assertEquals(1, MaterialPageContent.meltingRank(fromOre, ItemStack.EMPTY),
                "with no repair item to lead with, the ore still sorts ahead of the scrap");
        assertEquals(3, MaterialPageContent.MELTING_SOURCES,
                "three rows: the repair item, the ore, and one more");
    }

    @Test
    void everyTraitAMaterialNamesGetsExactlyOneReferenceEntry() throws Exception {
        List<Map.Entry<ResourceLocation, Material>> materials = shippedMaterials();
        Set<ResourceLocation> named = new HashSet<>();
        materials.forEach(entry -> named.addAll(entry.getValue().traits().all()));
        assertTrue(named.size() >= 100, "non-vacuity: expected the real trait roster, saw " + named.size());

        Set<ResourceLocation> covered = new HashSet<>();
        for (BookTraits.Family family : BookTraits.families(materials)) {
            for (BookTraits.Rung rung : family.rungs()) {
                assertTrue(covered.add(rung.id()), rung.id() + " appears in more than one family");
                assertFalse(rung.materials().isEmpty(), rung.id() + " is listed with no material");
                for (ResourceLocation material : rung.materials()) {
                    assertTrue(materials.stream().anyMatch(entry -> entry.getKey().equals(material)
                                    && entry.getValue().traits().all().contains(rung.id())),
                            material + " is listed under " + rung.id() + " but does not grant it");
                }
            }
            assertEquals(family.rungs().stream().map(BookTraits.Rung::level).sorted().toList(),
                    family.rungs().stream().map(BookTraits.Rung::level).toList(),
                    family.path() + "'s levels must come out in order");
        }
        assertEquals(named, covered, "the reference covers exactly the traits the materials name");
    }

    /**
     * The graded ids the tree ships today ({@code evolved}/{@code evolved2}/{@code evolved3} and
     * eleven more) have to come out as one entry with its levels, which is what the reference is
     * for. Issue #1103 is merging the duplicate ids into declared families; when it lands, more of
     * them collapse into entries like this one without a change here.
     */
    @Test
    void aGradedTraitComesOutAsOneFamilyWithItsLevels() throws Exception {
        BookTraits.Family evolved = BookTraits.families(shippedMaterials()).stream()
                .filter(family -> family.path().equals("evolved")).findFirst()
                .orElseThrow(() -> new AssertionError("the evolved family is not in the reference"));

        assertEquals(List.of(1, 2, 3), evolved.rungs().stream().map(BookTraits.Rung::level).toList());
        assertEquals(3, evolved.maxLevel());
        assertEquals("trait.forgeweave.evolved.name", evolved.nameKey());
    }

    @Test
    void everyCrossReferenceInTheChapterResolves() throws Exception {
        BookSection chapter = section(shippedBook(), "materials");
        ListingPage ladder = assertInstanceOf(ListingPage.class, chapter.pages().get(0));

        // The ladder's last row opens the traits reference, whose listing reaches every trait page.
        BookLink traitsRow = ladder.links().get(ladder.links().size() - 1);
        assertEquals(BookContent.TRAITS_TITLE, traitsRow.labelKey());
        assertEquals(BookContent.TRAITS_INDEX_TEXT, traitsRow.descriptionKey());
        ListingPage traitsIndex = assertInstanceOf(ListingPage.class, chapter.pages().get(traitsRow.targetPage()),
                "the traits reference opens on its own listing");
        assertFalse(traitsIndex.links().isEmpty());
        for (BookLink row : traitsIndex.links()) {
            TraitPage target = assertInstanceOf(TraitPage.class, chapter.pages().get(row.targetPage()),
                    "a traits listing row must open a trait page");
            assertEquals(target.family().nameKey(), row.labelKey());
        }

        // Every trait a material page shows has a reference entry to jump to, which is what
        // BookScreen resolves through BookTraits#familyOf.
        Set<String> families = chapter.pages().stream()
                .filter(TraitPage.class::isInstance)
                .map(page -> ((TraitPage) page).family().path())
                .collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> pages = chapter.pages().stream()
                .filter(MaterialPage.class::isInstance)
                .map(page -> ((MaterialPage) page).id())
                .collect(java.util.stream.Collectors.toSet());
        for (BookPage page : chapter.pages()) {
            if (page instanceof MaterialPage material) {
                for (ResourceLocation trait : material.material().traits().all()) {
                    assertTrue(families.contains(BookTraits.familyOf(trait)),
                            trait + " on " + material.id() + "'s page has no reference entry to link to");
                }
            }
            // And every material a trait entry lists has a page to jump back to.
            if (page instanceof TraitPage trait) {
                trait.family().rungs().forEach(rung -> rung.materials().forEach(material ->
                        assertTrue(pages.contains(material),
                                material + " is listed under " + rung.id() + " with no page of its own")));
            }
        }
    }
}
