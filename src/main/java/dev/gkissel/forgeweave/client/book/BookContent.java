package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.ListingPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookPage.ModifierPage;
import dev.gkissel.forgeweave.client.book.BookPage.TextPage;
import dev.gkissel.forgeweave.client.book.BookPage.ToolPage;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * The guide book's section and page structure, following the 1.12 book's shipped organization
 * ({@code resources/assets/tconstruct/book/index.json} and its {@code sections/*.json}, NOTICE.md):
 * intro, tools, materials, modifiers, smeltery, in that order, with the same index icons. The
 * static pages' text lives in {@code book.forgeweave.*} lang keys ({@code ForgeweaveLanguageProvider});
 * the tool, material and modifier listings are generated from Forgeweave's registries at open time
 * -- upstream's {@code ToolSectionTransformer}/{@code MaterialSectionTransformer}/
 * {@code ModifierSectionTransformer} did the same from its registries, so a newly registered
 * material or modifier shows up here without touching this class.
 *
 * <p>{@code BookLangCoverageTest} walks {@link #staticLangKeys()} and {@link #TOOLS} against the
 * generated {@code en_us.json}, so a page added here without its lang lines fails the build.
 */
public final class BookContent {

    public static final String TITLE = "book.forgeweave.title";
    public static final String SUBTITLE = "book.forgeweave.subtitle";
    public static final String INDEX_TITLE = "book.forgeweave.index.title";

    /**
     * Every assembled tool the book lists, in the 1.12 tools section's harvest-then-weapons order.
     * Each entry's page reuses the tool's registered name and its {@code item.forgeweave.<id>.description}
     * Tool Station blurb, so the book and the station tab can never disagree about a tool.
     */
    public static final List<Supplier<? extends Item>> TOOLS = List.of(
            ForgeweaveItems.TOOL_PICKAXE,
            ForgeweaveItems.TOOL_SHOVEL,
            ForgeweaveItems.TOOL_HATCHET,
            ForgeweaveItems.TOOL_MATTOCK,
            ForgeweaveItems.TOOL_KAMA,
            // parity audit 2026-08-18 T77: upstream tools.json:21-124 puts scythe right after
            // kama, before hammer/excavator/lumberaxe -- it had drifted to the end of the
            // harvest run.
            ForgeweaveItems.TOOL_SCYTHE,
            ForgeweaveItems.TOOL_HAMMER,
            ForgeweaveItems.TOOL_EXCAVATOR,
            ForgeweaveItems.TOOL_LUMBERAXE,
            ForgeweaveItems.TOOL_VEIN_HAMMER,
            ForgeweaveItems.TOOL_BROADSWORD,
            ForgeweaveItems.TOOL_LONGSWORD,
            ForgeweaveItems.TOOL_RAPIER,
            ForgeweaveItems.TOOL_DAGGER,
            ForgeweaveItems.TOOL_BATTLESIGN,
            ForgeweaveItems.TOOL_FRYING_PAN,
            ForgeweaveItems.TOOL_BATTLEAXE,
            ForgeweaveItems.TOOL_SCIMITAR,
            ForgeweaveItems.TOOL_KATANA,
            ForgeweaveItems.TOOL_WARMACE,
            ForgeweaveItems.TOOL_CLEAVER,
            ForgeweaveItems.TOOL_SHORTBOW, // M3.5 #394
            ForgeweaveItems.TOOL_LONGBOW, // M3.5 #395
            ForgeweaveItems.TOOL_CROSSBOW); // M3.5 #395

    private static final ResourceLocation SMELTERY_IMAGE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/book/smeltery.png");

    private BookContent() {
    }

    /** Builds the full section list; needs registry access for the material listing. */
    public static List<BookSection> sections(RegistryAccess registries) {
        return sections(registries.registryOrThrow(Material.REGISTRY).entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().getPath()))
                .map((Map.Entry<ResourceKey<Material>, Material> entry) ->
                        Map.entry(entry.getKey().location(), entry.getValue()))
                .toList());
    }

    /**
     * The registry-free half of {@link #sections(RegistryAccess)}: everything but the material list
     * is fixed, so handing the materials in keeps the whole page tree unit-testable
     * ({@code BookListingTest}) without standing up a datapack registry.
     *
     * @param materials every material to give a page, already in the order the book shows them
     */
    public static List<BookSection> sections(List<Map.Entry<ResourceLocation, Material>> materials) {
        List<BookSection> sections = new ArrayList<>();

        sections.add(new BookSection("book.forgeweave.section.intro",
                () -> new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()),
                List.of(
                        new TextPage("book.forgeweave.intro.welcome.title", "book.forgeweave.intro.welcome.text"),
                        new TextPage("book.forgeweave.intro.workshop.title", "book.forgeweave.intro.workshop.text"))));

        List<BookPage> toolPages = new ArrayList<>();
        toolPages.add(new TextPage("book.forgeweave.tools.repairing.title", "book.forgeweave.tools.repairing.text"));
        for (Supplier<? extends Item> tool : TOOLS) {
            toolPages.add(new ToolPage(tool.get()));
        }
        sections.add(new BookSection("book.forgeweave.section.tools",
                () -> new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()),
                withListing("book.forgeweave.section.tools", toolPages)));

        List<BookPage> materialPages = new ArrayList<>();
        materialPages.add(new TextPage("book.forgeweave.materials.intro.title", "book.forgeweave.materials.intro.text"));
        List<BookLink> grid = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Material> entry : materials) {
            ResourceLocation id = entry.getKey();
            Material material = entry.getValue();
            grid.add(new BookLink("material." + id.getNamespace() + "." + id.getPath(), material.color(),
                    () -> representativeItem(material), 1 + materialPages.size()));
            materialPages.add(new MaterialPage(id, material));
        }
        List<BookPage> materialSection = new ArrayList<>();
        materialSection.add(new IconGridPage("book.forgeweave.section.materials", List.copyOf(grid)));
        materialSection.addAll(materialPages);
        sections.add(new BookSection("book.forgeweave.section.materials",
                () -> new ItemStack(Items.IRON_INGOT), List.copyOf(materialSection)));

        List<BookPage> modifierPages = new ArrayList<>();
        modifierPages.add(new TextPage("book.forgeweave.modifiers.intro.title", "book.forgeweave.modifiers.intro.text"));
        ForgeweaveModifiers.ids().stream()
                .sorted(Comparator.comparing(ResourceLocation::getPath))
                .forEach(id -> modifierPages.add(new ModifierPage(id)));
        sections.add(new BookSection("book.forgeweave.section.modifiers",
                () -> new ItemStack(Items.REDSTONE),
                withListing("book.forgeweave.section.modifiers", modifierPages)));

        sections.add(new BookSection("book.forgeweave.section.smeltery",
                () -> new ItemStack(ForgeweaveBlocks.STANDARD_CORE.get()),
                List.of(
                        new TextPage("book.forgeweave.smeltery.intro.title", "book.forgeweave.smeltery.intro.text",
                                SMELTERY_IMAGE),
                        new TextPage("book.forgeweave.smeltery.structure.title", "book.forgeweave.smeltery.structure.text"),
                        new TextPage("book.forgeweave.smeltery.working.title", "book.forgeweave.smeltery.working.text"))));

        return List.copyOf(sections);
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
            case ListingPage listing -> listing.titleKey();
            case IconGridPage listing -> listing.titleKey();
        };
    }

    /**
     * The item a material's grid icon shows -- upstream's {@code Material#getRepresentativeItem()},
     * which is the item the material is repaired and built with. Forgeweave has no separate
     * representative field, so the repair ingredient stands in for it; an ingredient with no items
     * (a tag no pack fills) falls back to the blank pattern rather than rendering nothing.
     */
    private static ItemStack representativeItem(Material material) {
        Ingredient repair = material.repairItem();
        ItemStack[] items = repair.getItems();
        return items.length > 0 ? items[0] : new ItemStack(ForgeweaveItems.PATTERN_BLANK.get());
    }

    /**
     * Every fixed lang key the book structure references -- the section titles, chrome, and each
     * static page's title/text pair. {@code BookLangCoverageTest}'s manifest; keep in sync with
     * {@link #sections}.
     */
    public static List<String> staticLangKeys() {
        return List.of(
                TITLE, SUBTITLE, INDEX_TITLE,
                "book.forgeweave.section.intro",
                "book.forgeweave.section.tools",
                "book.forgeweave.section.materials",
                "book.forgeweave.section.modifiers",
                "book.forgeweave.section.smeltery",
                "book.forgeweave.intro.welcome.title", "book.forgeweave.intro.welcome.text",
                "book.forgeweave.intro.workshop.title", "book.forgeweave.intro.workshop.text",
                "book.forgeweave.tools.repairing.title", "book.forgeweave.tools.repairing.text",
                "book.forgeweave.materials.intro.title", "book.forgeweave.materials.intro.text",
                "book.forgeweave.modifiers.intro.title", "book.forgeweave.modifiers.intro.text",
                "book.forgeweave.smeltery.intro.title", "book.forgeweave.smeltery.intro.text",
                "book.forgeweave.smeltery.structure.title", "book.forgeweave.smeltery.structure.text",
                "book.forgeweave.smeltery.working.title", "book.forgeweave.smeltery.working.text");
    }
}
