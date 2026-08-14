package dev.gkissel.forgeweave.client.book;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;

/**
 * A section of the guide book: its index-entry title, the item icon the index shows beside it
 * (upstream's {@code index.json} icon entries), and its pages.
 */
public record BookSection(String titleKey, Supplier<ItemStack> icon, List<BookPage> pages) {}
