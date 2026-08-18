package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * One Tool Station repair display recipe, for one material: any tool (cycling every tool type,
 * since repair is the same rule regardless of tool type -- see {@code menu.ToolAssemblyRecipes}'s
 * repair resolution) + any item that material repairs from -> the same tool, repaired.
 */
record RepairRecipe(List<ItemStack> tools, List<ItemStack> repairItems, List<ItemStack> repairedTools) {}
