package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.ListingPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookPage.ModifierPage;
import dev.gkissel.forgeweave.client.book.BookPage.SectionListPage;
import dev.gkissel.forgeweave.client.book.BookPage.TextPage;
import dev.gkissel.forgeweave.client.book.BookPage.ToolPage;
import dev.gkissel.forgeweave.client.book.BookStructure.PageDef;
import dev.gkissel.forgeweave.client.book.BookStructure.SectionDef;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialStage;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;

/**
 * Builds the guide book's page tree from the data-driven structure in
 * {@code assets/forgeweave/book/} ({@link BookStructure}, issue #651) plus the registry-generated
 * sections, following the 1.12 book's shipped organization
 * ({@code resources/assets/tconstruct/book/index.json} and its {@code sections/*.json}, NOTICE.md).
 * The static pages' text lives in {@code book.forgeweave.*} lang keys
 * ({@code ForgeweaveLanguageProvider}); the tool, material and modifier listings are generated from
 * Forgeweave's registries at open time -- upstream's {@code ToolSectionTransformer}/
 * {@code MaterialSectionTransformer}/{@code ModifierSectionTransformer} did the same from its
 * registries, so a newly registered material or modifier shows up here without touching this class.
 * The whole book then opens with the generated index section
 * ({@code BookTransformer.IndexTranformer}): one {@code ContentSectionList} page per nine sections,
 * each button jumping to its section's first page.
 *
 * <p>{@code BookLangCoverageTest} walks {@link #staticLangKeys()} and {@link #TOOLS} against the
 * generated {@code en_us.json}, so a page added to the JSON without its lang lines fails the build.
 */
public final class BookContent {

    public static final String TITLE = "book.forgeweave.title";
    public static final String SUBTITLE = "book.forgeweave.subtitle";

    /** The materials chapter's opening page: the progression ladder, one row per stage (#1104). */
    public static final String MATERIAL_STAGES_TITLE = "book.forgeweave.materials.stages";

    /** The traits reference at the back of the materials chapter, and its row on the ladder page. */
    public static final String TRAITS_TITLE = "book.forgeweave.materials.traits";
    public static final String TRAITS_INDEX_TEXT = "book.forgeweave.materials.traits_index";

    /** {@code IndexTranformer}: one {@code ContentSectionList} page holds at most nine sections. */
    private static final int SECTIONS_PER_INDEX_PAGE = 9;

    /**
     * Every assembled tool the book lists, in {@code book/sections/tools.json}'s order -- the 1.12
     * tools section's harvest-then-weapons order ({@code BookToolsOrderTest} pins it). Each entry's
     * page reuses the tool's registered name and its {@code item.forgeweave.<id>.description}
     * Tool Station blurb, so the book and the station tab can never disagree about a tool.
     */
    public static final List<Supplier<? extends Item>> TOOLS = BookStructure.load().sections().stream()
            .filter(section -> section.name().equals("tools"))
            .flatMap(section -> section.pages().stream())
            .filter(page -> page.type().equals("tool"))
            .<Supplier<? extends Item>>map(page -> {
                ResourceLocation id = ResourceLocation.parse(page.item());
                return () -> BuiltInRegistries.ITEM.get(id);
            })
            .toList();

    private BookContent() {
    }

    /** Builds the full section list; needs registry access for the material listing. */
    public static List<BookSection> sections(RegistryAccess registries) {
        return sections(registries.registryOrThrow(Material.REGISTRY).entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().getPath()))
                .map((Map.Entry<ResourceKey<Material>, Material> entry) ->
                        Map.entry(entry.getKey().location(), entry.getValue()))
                .toList(),
                MaterialStage.Lookup.of(registries.registryOrThrow(Material.REGISTRY),
                        registries.registryOrThrow(AlloyRecipe.REGISTRY)));
    }

    /**
     * The registry-free half of {@link #sections(RegistryAccess)}: everything but the material list
     * comes from the structure JSONs and the code registries, so handing the materials in keeps the
     * whole page tree unit-testable ({@code BookListingTest}) without standing up a datapack
     * registry.
     *
     * @param materials every material to give a page, in the order the id sort left them; the
     *                  chapter's own order is {@link BookMaterialOrder}'s, applied here
     */
    public static List<BookSection> sections(List<Map.Entry<ResourceLocation, Material>> materials) {
        return sections(materials, MaterialStage.Lookup.EMPTY);
    }

    /**
     * As {@link #sections(List)}, with the stage lookup the materials chapter's ladder needs
     * (issue #1104). {@link MaterialStage.Lookup#EMPTY} derives every stage from the material's
     * own harvest rung alone, which is all a caller with no alloy registry can know.
     */
    public static List<BookSection> sections(List<Map.Entry<ResourceLocation, Material>> materials,
            MaterialStage.Lookup stages) {
        List<BookMaterialOrder.Stage> ladder = BookMaterialOrder.byStage(materials, stages);
        List<BookTraits.Family> traits = BookTraits.families(materials);
        List<BookSection> sections = new ArrayList<>();
        for (SectionDef def : BookStructure.load().sections()) {
            String titleKey = "book.forgeweave.section." + def.name();
            List<BookPage> authored = new ArrayList<>();
            for (PageDef page : def.pages()) {
                authored.add(pageOf(def.name(), page));
            }
            // The generated halves, hooked by section name exactly as upstream hooks its
            // SectionTransformers ("tools"/"materials"/"modifiers", TinkerBook:37-40).
            List<BookPage> pages = switch (def.name()) {
                case "tools" -> withListing(titleKey, authored);
                case "materials" -> withMaterials(authored, ladder, traits);
                case "modifiers" -> {
                    ForgeweaveModifiers.ids().stream()
                            .sorted(Comparator.comparing(ResourceLocation::getPath))
                            .forEach(id -> authored.add(new ModifierPage(id)));
                    yield withListing(titleKey, authored);
                }
                default -> authored;
            };
            sections.add(new BookSection(titleKey, iconOf(def.iconItem()), List.copyOf(pages)));
        }
        sections.add(0, indexSection(sections));
        return List.copyOf(sections);
    }

    /** One authored page def becomes a page; the tool kind reads the registry, text reads lang. */
    private static BookPage pageOf(String sectionName, PageDef def) {
        if (def.type().equals("tool")) {
            ResourceLocation id = ResourceLocation.parse(def.item());
            return new ToolPage(BuiltInRegistries.ITEM.get(id));
        }
        if (def.type().equals("structure")) {
            return new BookPage.StructurePage(def.name(), StructureInfo.load(def.data()));
        }
        String base = "book.forgeweave." + sectionName + "." + def.name();
        return new TextPage(base + ".title", base + ".text", imageOf(def.image()));
    }

    @Nullable
    private static ResourceLocation imageOf(@Nullable String image) {
        if (image == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.parse(image);
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath());
    }

    private static Supplier<ItemStack> iconOf(String itemId) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        return () -> new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    /**
     * Upstream {@code BookTransformer.IndexTranformer}: the index becomes the first section, named
     * {@code index}, its pages named {@code page1}, {@code page2}, ... -- one
     * {@code ContentSectionList} of up to nine section buttons per page, every section but the
     * index itself getting a button that jumps to its first page. The buttons' targets are
     * book-global page indices (the index section starts at page 0, so the screen's
     * section-relative arithmetic still holds).
     */
    private static BookSection indexSection(List<BookSection> others) {
        int indexPages = Math.max(1,
                (others.size() + SECTIONS_PER_INDEX_PAGE - 1) / SECTIONS_PER_INDEX_PAGE);
        List<BookPage> pages = new ArrayList<>();
        int firstPage = indexPages;
        int section = 0;
        for (int page = 0; page < indexPages; page++) {
            List<BookLink> links = new ArrayList<>();
            for (int i = 0; i < SECTIONS_PER_INDEX_PAGE && section < others.size(); i++, section++) {
                BookSection target = others.get(section);
                links.add(new BookLink(target.titleKey(), null, target.icon(), firstPage));
                firstPage += target.pages().size();
            }
            pages.add(new SectionListPage("page" + (page + 1), List.copyOf(links)));
        }
        return new BookSection("book.forgeweave.section.index", () -> ItemStack.EMPTY, List.copyOf(pages));
    }

    /**
     * The materials chapter, stage by stage (issue #1104). Upstream
     * {@code AbstractMaterialSectionTransformer#transform} opens the chapter with
     * {@code ContentPageIconList} grids of one linked item icon per material (issue #479); 1.20
     * splits the chapter into tier-scoped sections that each get their own grid. This keeps one
     * section and gives each stage its own grid inside it:
     *
     * <ol>
     *   <li>the chapter's opening page: one row per stage, with the sentence saying what unlocks
     *       it, jumping to that stage's grid, and a last row for the traits reference;
     *   <li>the authored intro pages;
     *   <li>per stage, in ladder order: the stage's icon grid, then its material pages in
     *       {@link BookMaterialOrder}'s order;
     *   <li>the traits reference: its own listing page, then one page per trait family.
     * </ol>
     *
     * <p>A link is section-relative, so the page indices are counted as the list is built -- the
     * same arithmetic the old single grid did, once per stage.
     *
     * <p>The traits reference sits at the back of this chapter rather than in a section of its own
     * because the chapter list is the tutorial order (issue #1105) and holds exactly the nine
     * sections one generated index page fits. It is the same material data read the other way
     * round, so this is where it belongs anyway.
     */
    private static List<BookPage> withMaterials(List<BookPage> authored,
            List<BookMaterialOrder.Stage> ladder, List<BookTraits.Family> families) {
        // Page 0 is the stage listing; the authored pages follow, then one grid plus its material
        // pages per stage, then the traits reference.
        int firstStagePage = 1 + authored.size();
        List<BookLink> stageRows = new ArrayList<>();
        List<BookPage> stagePages = new ArrayList<>();
        for (BookMaterialOrder.Stage stage : ladder) {
            int gridPage = firstStagePage + stagePages.size();
            stageRows.add(new BookLink(stage.stage().nameKey(), null, null, gridPage,
                    stage.stage().unlockKey()));
            List<BookLink> grid = new ArrayList<>();
            List<BookPage> materialPages = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Material> entry : stage.materials()) {
                ResourceLocation id = entry.getKey();
                Material material = entry.getValue();
                grid.add(new BookLink("material." + id.getNamespace() + "." + id.getPath(), material.color(),
                        () -> representativeItem(material), gridPage + 1 + materialPages.size()));
                materialPages.add(new MaterialPage(id, material, stage.stage()));
            }
            stagePages.add(new IconGridPage(stage.stage().nameKey(), List.copyOf(grid)));
            stagePages.addAll(materialPages);
        }

        // One page per family rather than one long roster page, so a trait name on a material page
        // jumps to that trait and nothing else. Its listing page leads, the way every other
        // generated listing in this book does (upstream's ContentListing shape).
        int traitsIndexPage = firstStagePage + stagePages.size();
        stageRows.add(new BookLink(TRAITS_TITLE, null, null, traitsIndexPage, TRAITS_INDEX_TEXT));
        List<BookLink> traitRows = new ArrayList<>();
        List<BookPage> traitPages = new ArrayList<>();
        for (BookTraits.Family family : families) {
            traitRows.add(new BookLink(family.nameKey(), traitsIndexPage + 1 + traitPages.size()));
            traitPages.add(new BookPage.TraitPage(family));
        }

        List<BookPage> pages = new ArrayList<>();
        pages.add(new ListingPage(MATERIAL_STAGES_TITLE, List.copyOf(stageRows)));
        pages.addAll(authored);
        pages.addAll(stagePages);
        pages.add(new ListingPage(TRAITS_TITLE, List.copyOf(traitRows)));
        pages.addAll(traitPages);
        return List.copyOf(pages);
    }

    /**
     * Upstream {@code ContentListingSectionTransformer#transform}: a generated section opens with a
     * listing page of one linked row per page, titled with the section name, and every content page
     * shifts one along behind it (issue #479). Upstream skips a page whose title is literally
     * {@code "hidden"}; Forgeweave has no hidden pages, so every page gets a row.
     *
     * <p>Deviation from upstream's {@code ModifierSectionTransformer}, which overrides
     * {@code processPage} to drop everything that is not a {@code ContentModifier}: upstream's
     * modifiers section is nothing *but* modifier pages, so the override changes nothing there,
     * while Forgeweave's opens with a written introduction that is worth linking.
     */
    private static List<BookPage> withListing(String titleKey, List<BookPage> content) {
        List<BookLink> links = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            links.add(new BookLink(rowLabelKey(content.get(i)), 1 + i));
        }
        List<BookPage> pages = new ArrayList<>();
        pages.add(new ListingPage(titleKey, List.copyOf(links)));
        pages.addAll(content);
        return List.copyOf(pages);
    }

    /**
     * The lang key a listing row shows for one page -- upstream's {@code page.getTitle()}, except
     * for the page kinds whose title is not authored text: a tool row is the tool's own name
     * ({@code ToolSectionTransformer#processPage}) and a modifier row the modifier's
     * ({@code ModifierSectionTransformer#processPage}), so the listing can never disagree with the
     * page it opens.
     */
    private static String rowLabelKey(BookPage page) {
        return switch (page) {
            case TextPage text -> text.titleKey();
            case ToolPage tool -> tool.tool().getDescriptionId();
            case ModifierPage modifier ->
                    "modifier." + modifier.id().getNamespace() + "." + modifier.id().getPath() + ".name";
            case MaterialPage material ->
                    "material." + material.id().getNamespace() + "." + material.id().getPath();
            case BookPage.TraitPage trait -> trait.family().nameKey();
            case ListingPage listing -> listing.titleKey();
            case IconGridPage listing -> listing.titleKey();
            case SectionListPage sectionList -> sectionList.name(); // never listed; exhaustiveness
            case BookPage.StructurePage structure -> structure.name(); // never listed; exhaustiveness
        };
    }

    /**
     * The item a material's grid icon shows -- upstream's {@code Material#getRepresentativeItem()},
     * which is the item the material is repaired and built with. Forgeweave has no separate
     * representative field, so the repair ingredient stands in for it; an ingredient with no items
     * (a tag no pack fills) falls back to the blank pattern rather than rendering nothing.
     */
    private static ItemStack representativeItem(Material material) {
        ItemStack[] items = material.repairItem().getItems();
        return items.length > 0 ? items[0] : new ItemStack(ForgeweaveItems.PATTERN_BLANK.get());
    }

    /**
     * Every fixed lang key the book structure references -- the cover, the section titles, and each
     * authored page's title/text pair, derived from the structure JSONs so a page added there
     * without its lang lines fails {@code BookLangCoverageTest}. (The generated index section's
     * title key is a bookmark identifier, never rendered, so it is not in the manifest.)
     */
    public static List<String> staticLangKeys() {
        List<String> keys = new ArrayList<>(List.of(
                TITLE, SUBTITLE,
                // Issue #651: the tool/modifier pages' bullet-list headers (upstream's
                // tool.properties / modifier.effect book strings).
                ModifyPageContent.TOOL_PROPERTIES_TITLE,
                ModifyPageContent.MODIFIER_EFFECTS_TITLE,
                // Issue #1104: the materials chapter's ladder page and the per-material-page lines
                // that say which stage it sits in and how it is made.
                MATERIAL_STAGES_TITLE,
                TRAITS_TITLE,
                TRAITS_INDEX_TEXT,
                MaterialPageContent.STAGE_LINE,
                MaterialPageContent.MADE_BY_MELTING,
                MaterialPageContent.MADE_BY_ALLOYING,
                MaterialPageContent.ALLOY_INPUT,
                MaterialPageContent.TRAIT_GRANTED_BY,
                MaterialPageContent.TRAIT_LEVEL));
        for (MaterialStage stage : MaterialStage.values()) {
            keys.add(stage.nameKey());
            keys.add(stage.unlockKey());
        }
        for (SectionDef def : BookStructure.load().sections()) {
            keys.add("book.forgeweave.section." + def.name());
            for (PageDef page : def.pages()) {
                // Tool pages take their keys from the item; a structure page has no title or text
                // (upstream's multiblock.json carries neither).
                if (!page.type().equals("tool") && !page.type().equals("structure")) {
                    String base = "book.forgeweave." + def.name() + "." + page.name();
                    keys.add(base + ".title");
                    keys.add(base + ".text");
                }
            }
        }
        return List.copyOf(keys);
    }
}
