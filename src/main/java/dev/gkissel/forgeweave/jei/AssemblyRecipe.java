package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One tool assembly display recipe, for one tool type: one slot per part, in the station's own slot
 * order, each cycling through every registered material (see {@link AssemblyRecipes}) rather than
 * enumerating every material combination.
 *
 * @param parts one cycling ingredient list per input slot -- two entries for M3's two-part weapons,
 *     three for everything else (issue #155)
 */
record AssemblyRecipe(List<List<ItemStack>> parts, Item tool, ItemStack result) {}
