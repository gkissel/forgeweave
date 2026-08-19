package dev.gkissel.forgeweave.client.book;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

/**
 * One clickable row or icon on a {@link BookPage.ListingPage} or {@link BookPage.IconGridPage}:
 * a label, an optional icon, and the page it jumps to.
 *
 * <p>Upstream 1.12 splits this in two -- {@code ContentListing#addEntry} builds a {@code TextData}
 * carrying a {@code go-to-page-rtn:} action, and {@code ContentPageIconList#addLink} builds an
 * {@code ElementPageIconLink} carrying the same action plus a display element and a hover name.
 * Both are the same three facts, so Forgeweave keeps one record: a text row leaves {@link #icon}
 * and {@link #color} null, an icon entry sets both (upstream's icon hover text is the material's
 * *coloured* name, {@code material.getLocalizedNameColored()}).
 *
 * @param labelKey   lang key of the row text / icon hover text
 * @param color      colour for that text, or {@code null} for the page's default
 * @param icon       the item to draw, or {@code null} for a text row; a supplier because a stack
 *                   built from an {@code Ingredient} needs bound tags, i.e. a loaded world
 * @param targetPage index into the owning {@link BookSection}'s page list of the page to jump to
 */
public record BookLink(String labelKey, @Nullable TextColor color, @Nullable Supplier<ItemStack> icon,
        int targetPage) {

    /** A plain text row: no icon, the listing page's own colours. */
    public BookLink(String labelKey, int targetPage) {
        this(labelKey, null, null, targetPage);
    }
}
