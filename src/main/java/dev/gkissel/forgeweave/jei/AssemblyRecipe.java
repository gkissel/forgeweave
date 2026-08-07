package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * One tool assembly display recipe, for one tool type: head/binding/handle part slots each cycle
 * through every registered material (see {@link AssemblyRecipes}) rather than enumerating every
 * material combination.
 */
record AssemblyRecipe(List<ItemStack> heads, List<ItemStack> bindings, List<ItemStack> handles, ItemStack result) {}
