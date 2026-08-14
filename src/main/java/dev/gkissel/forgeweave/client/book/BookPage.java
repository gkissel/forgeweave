package dev.gkissel.forgeweave.client.book;

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

    /** A tool's page: its icon, registered name, and its Tool Station tab blurb. */
    record ToolPage(Item tool) implements BookPage {}

    /** A material's stat page, rendered live from the datapack {@link Material} registry. */
    record MaterialPage(ResourceLocation id, Material material) implements BookPage {}

    /** A modifier's page: its {@code modifier.forgeweave.<id>.name}/{@code .description} pair. */
    record ModifierPage(ResourceLocation id) implements BookPage {}
}
