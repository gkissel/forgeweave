package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

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
            List<ItemStack> accepted = acceptedBy(material);
            if (accepted.isEmpty()) {
                continue; // ingredients with no matching items can't show a display recipe
            }
            recipes.add(new RepairRecipe(tools, accepted, tools));
        }
        return recipes;
    }

    /**
     * Every item that repairs this material's tools, in the order the station tries them (parity
     * audit T30, issue #461): its {@code crafting_items} -- each worth its own value -- then its
     * {@code repair_item}, which for most materials is one of the crafting items again. JEI cycles
     * the input slot through the lot, so a log shows up as a wood repair item the way it now is one.
     * Deduplicated by item so the common "plank is both" case shows one entry, not two.
     */
    private static List<ItemStack> acceptedBy(Material material) {
        Set<Item> seen = new LinkedHashSet<>();
        List<ItemStack> accepted = new ArrayList<>();
        List<Ingredient> ingredients = new ArrayList<>(material.craftingItems().stream()
                .map(Material.CraftingItem::ingredient)
                .toList());
        ingredients.add(material.repairItem());
        for (Ingredient ingredient : ingredients) {
            for (ItemStack stack : ingredient.getItems()) {
                if (seen.add(stack.getItem())) {
                    accepted.add(stack.copyWithCount(1));
                }
            }
        }
        return List.copyOf(accepted);
    }

    private RepairRecipes() {}
}
