package dev.gkissel.forgeweave.jei;

import net.minecraft.world.item.ItemStack;

/** One Part Builder display recipe, for one part type and one material: pattern + material items -> part. */
record PartCraftingRecipe(ItemStack pattern, ItemStack material, ItemStack result) {}
