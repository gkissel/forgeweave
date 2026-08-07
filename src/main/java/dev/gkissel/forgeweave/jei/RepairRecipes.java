package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;

/**
 * Builds one {@link RepairRecipe} per registered material -- repair only depends on a tool's head
 * material (CONTEXT.md; {@code menu.ToolAssemblyRecipes#resolveRepair}), not its tool type, so one
 * recipe per material covers every tool rather than one per (tool type, material) pair.
 */
final class RepairRecipes {
    private static final List<ItemStack> ANY_TOOL = List.of(
            new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()),
            new ItemStack(ForgeweaveItems.TOOL_SHOVEL.get()),
            new ItemStack(ForgeweaveItems.TOOL_HATCHET.get()));

    static List<RepairRecipe> build(Map<ResourceLocation, Material> materials) {
        List<RepairRecipe> recipes = new ArrayList<>();
        for (Material material : materials.values()) {
            ItemStack[] representatives = material.repairItem().getItems();
            if (representatives.length == 0) {
                continue; // an ingredient with no matching items can't show a display recipe
            }
            recipes.add(new RepairRecipe(ANY_TOOL, representatives[0].copyWithCount(1), ANY_TOOL));
        }
        return recipes;
    }

    private RepairRecipes() {}
}
