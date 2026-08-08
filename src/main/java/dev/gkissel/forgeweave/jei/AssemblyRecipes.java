package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Builds one {@link AssemblyRecipe} per tool type -- not per material combination. Head x binding x
 * handle would explode combinatorially with the material count, so each of the three slots instead
 * carries every registered material's part as a cycling ingredient list ({@code IIngredientAcceptor
 * #addItemStacks}, JEI's built-in slot rotation); binding and handle are shared across every tool
 * type, same as at the real station -- the head-part-to-tool table itself is {@link
 * ToolAssemblyRecipes#ENTRIES}, reused directly rather than hand-copied here (issue #79) so the two
 * can't drift apart.
 *
 * <p>The representative/cycling material set, instead of enumerating every combination, follows
 * Tinker's JEI's {@code StatsWrapper.java}/{@code StatsCategory.java} (docs/SCOPE.md M1 source
 * policy) -- NOTICE.md row. Upstream cycled by hand-rendering off {@code System.currentTimeMillis()}
 * inside a 1.12 {@code IRecipeWrapper#drawInfo}; JEI's modern API does the equivalent rotation
 * itself once a slot is given a {@code List} of ingredients, so no timer code is ported.
 */
final class AssemblyRecipes {
    static List<AssemblyRecipe> build(Map<ResourceLocation, Material> materials) {
        List<ItemStack> bindings = partStacks(ForgeweaveItems.PART_TOOL_BINDING, materials);
        List<ItemStack> handles = partStacks(ForgeweaveItems.PART_TOOL_HANDLE, materials);

        List<AssemblyRecipe> recipes = new ArrayList<>();
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            List<ItemStack> heads = partStacks(entry.headPart(), materials);
            if (heads.isEmpty() || bindings.isEmpty() || handles.isEmpty()) {
                continue; // no materials loaded yet (e.g. title screen) -- nothing to display
            }
            recipes.add(new AssemblyRecipe(heads, bindings, handles, new ItemStack(entry.tool().get())));
        }
        return recipes;
    }

    private static List<ItemStack> partStacks(Supplier<? extends PartItem> part, Map<ResourceLocation, Material> materials) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ResourceLocation materialId : materials.keySet()) {
            ItemStack stack = new ItemStack(part.get());
            stack.set(ForgeweaveDataComponents.MATERIAL.get(), materialId);
            stacks.add(stack);
        }
        return stacks;
    }

    private AssemblyRecipes() {}
}
