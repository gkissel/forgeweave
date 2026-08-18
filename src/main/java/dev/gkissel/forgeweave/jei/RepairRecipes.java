package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Builds one {@link RepairRecipe} per registered material -- repair depends on the materials of a
 * tool's repair parts ({@code menu.ToolAssemblyRecipes#resolveRepair}), not on its tool type, so one
 * recipe per material covers every tool rather than one per (tool type, material) pair.
 */
final class RepairRecipes {
    /** Every assemblable tool, off the station's own table so a new tool can't be forgotten here. */
    private static List<ItemStack> anyTool() {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .map(entry -> new ItemStack(entry.tool().get()))
                .toList();
    }

    static List<RepairRecipe> build(Map<ResourceLocation, Material> materials) {
        // Built once and shared by both slots of every recipe: the "before" and "after" of a repair
        // are the same set of tools, and JEI cycles the two slots in step only if they are the same
        // list instances.
        List<ItemStack> tools = anyTool();
        List<RepairRecipe> recipes = new ArrayList<>();
        for (Material material : materials.values()) {
            ItemStack[] representatives = material.repairItem().getItems();
            if (representatives.length == 0) {
                continue; // an ingredient with no matching items can't show a display recipe
            }
            recipes.add(new RepairRecipe(tools, representatives[0].copyWithCount(1), tools));
        }
        return recipes;
    }

    private RepairRecipes() {}
}
