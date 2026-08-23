package dev.gkissel.forgeweave.client.book;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.material.Material;

/**
 * One page of the guide book -- the Forgeweave equivalents of the four page types the 1.12 book's
 * shipped sections actually use ({@code sections/*.json} "type" values in
 * {@code resources/assets/tconstruct/book/}: {@code text}, {@code "image with text below"},
 * {@code tool}, {@code modifier}, plus the material stat pages its
 * {@code MaterialSectionTransformer} generates). Upstream renders these through Mantle's book
 * engine; Forgeweave's {@link BookScreen} renders them directly, and the registry-driven kinds
 * ({@link ToolPage}, {@link MaterialPage}, {@link ModifierPage}) pull live data instead of static
 * JSON so the book can never drift from what is actually registered.
 *
 * <p>ponytail: no structure/multiblock page type -- the 1.12 smeltery section's rotating 3D
 * structure page needs a schematic renderer nothing else uses; its information is carried by the
 * smeltery section's text pages instead.
 */
public sealed interface BookPage {

    /**
     * A titled page of wrapped body text, optionally with an image between title and text (the
     * 1.12 {@code text} and {@code "image with text below"} page types folded together -- the only
     * difference between them is whether {@code image} is present).
     */
    record TextPage(String titleKey, String textKey, @Nullable ResourceLocation image) implements BookPage {
        public TextPage(String titleKey, String textKey) {
            this(titleKey, textKey, null);
        }
    }

    /**
     * A tool's page, upstream's {@code ContentTool} (issue #651): name, Tool Station blurb, the
     * "Properties:" bullets, and the modify-station diagram of the tool with its parts.
     */
    record ToolPage(Item tool) implements BookPage {}

    /** A material's stat page, rendered live from the datapack {@link Material} registry. */
    record MaterialPage(ResourceLocation id, Material material) implements BookPage {}

    /**
     * A modifier's page, upstream's {@code ContentModifier} (issue #651): the coloured name,
     * description, "Effects:" bullets, and the diagram of a demo pickaxe over the reagent slot.
     */
    record ModifierPage(ResourceLocation id) implements BookPage {}

    /**
     * A section's opening navigation page: one linked text row per page of the section, titled with
     * the section name. Upstream's {@code ContentListing}, inserted at index 0 of the tools and
     * modifiers sections by {@code ContentListingSectionTransformer#transform} (issue #479).
     */
    record ListingPage(String titleKey, List<BookLink> links) implements BookPage {}

    /**
     * A section's opening navigation page as a grid of item icons, each linking to a page and
     * naming it on hover. Upstream's {@code ContentPageIconList}, which
     * {@code AbstractMaterialSectionTransformer#transform} puts in front of the materials section
     * instead of a text listing (issue #479).
     */
    record IconGridPage(String titleKey, List<BookLink> links) implements BookPage {}

    /**
     * One page of the generated index section (issue #651): Mantle's {@code ContentSectionList},
     * a 3x3 grid of 42x42 section buttons -- each drawing its section's icon at 32px with the
     * title underneath ({@code ElementSection}) and jumping to the section's first page. Built by
     * {@code BookTransformer.IndexTranformer}, which names the pages {@code page1}, {@code page2},
     * ... one per nine sections; the name is the page's bookmark segment ({@code index.page1}).
     * A link's {@code targetPage} is book-global here, since the index section starts at page 0.
     */
    record SectionListPage(String name, List<BookLink> links) implements BookPage {}
}
