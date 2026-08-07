package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;

/**
 * Builds one {@link PartCraftingRecipe} per (part type, material) pair -- cheap to enumerate
 * explicitly (5 part types x however many materials a modpack ships), unlike tool assembly's head x
 * binding x handle combinatorics (see {@link AssemblyRecipes}). Costs mirror
 * {@code menu.PartBuilderRecipes} (2 material items for a head, 1 for binding/handle); re-declared
 * here rather than reused so this optional JEI-only package stays a one-way dependency on the mod.
 * No NOTICE.md row: fresh integer constants already documented in {@code PartBuilderRecipes}, not
 * copied text or assets.
 */
final class PartCraftingRecipes {
    private static final int HEAD_COST = 2;
    private static final int SMALL_PART_COST = 1;

    private record Entry(Supplier<? extends Item> pattern, Supplier<? extends PartItem> part, int cost) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PATTERN_PICKAXE_HEAD, ForgeweaveItems.PART_PICKAXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SHOVEL_HEAD, ForgeweaveItems.PART_SHOVEL_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_AXE_HEAD, ForgeweaveItems.PART_AXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_BINDING, ForgeweaveItems.PART_TOOL_BINDING, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_HANDLE, ForgeweaveItems.PART_TOOL_HANDLE, SMALL_PART_COST));

    static List<PartCraftingRecipe> build(Map<ResourceLocation, Material> materials) {
        List<PartCraftingRecipe> recipes = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            for (Map.Entry<ResourceLocation, Material> material : materials.entrySet()) {
                ItemStack[] representatives = material.getValue().repairItem().getItems();
                if (representatives.length == 0) {
                    continue; // an ingredient with no matching items can't show a display recipe
                }

                ItemStack result = new ItemStack(entry.part().get());
                result.set(ForgeweaveDataComponents.MATERIAL.get(), material.getKey());

                recipes.add(new PartCraftingRecipe(
                        new ItemStack(entry.pattern().get()),
                        representatives[0].copyWithCount(entry.cost()),
                        result));
            }
        }
        return recipes;
    }

    private PartCraftingRecipes() {}
}
