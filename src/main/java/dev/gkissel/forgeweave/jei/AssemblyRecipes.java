package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Builds one {@link AssemblyRecipe} per tool type -- not per material combination. Every slot's
 * material choice multiplied out would explode combinatorially with the material count, so each slot
 * instead carries every registered material's part as a cycling ingredient list
 * ({@code IIngredientAcceptor#addItemStacks}, JEI's built-in slot rotation). The part-to-slot table
 * itself is {@link ToolAssemblyRecipes#ENTRIES}, reused directly rather than hand-copied here
 * (issue #79) so the two can't drift apart -- which since issue #155 also covers the slot
 * <em>count</em>, because three M3 weapons have no extra part.
 *
 * <p>The representative/cycling material set, instead of enumerating every combination, follows
 * Tinker's JEI's {@code StatsWrapper.java}/{@code StatsCategory.java} (docs/SCOPE.md M1 source
 * policy) -- NOTICE.md row. Upstream cycled by hand-rendering off {@code System.currentTimeMillis()}
 * inside a 1.12 {@code IRecipeWrapper#drawInfo}; JEI's modern API does the equivalent rotation
 * itself once a slot is given a {@code List} of ingredients, so no timer code is ported.
 */
final class AssemblyRecipes {
    static List<AssemblyRecipe> build(Map<ResourceLocation, Material> materials) {
        List<AssemblyRecipe> recipes = new ArrayList<>();
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            List<List<ItemStack>> parts = new ArrayList<>(entry.slotCount());
            boolean complete = true;
            for (PartItem part : entry.parts()) {
                List<ItemStack> stacks = partStacks(part, materials);
                complete &= !stacks.isEmpty();
                parts.add(stacks);
            }
            if (!complete) {
                continue; // no materials loaded yet (e.g. title screen) -- nothing to display
            }
            recipes.add(new AssemblyRecipe(List.copyOf(parts), entry.tool().get(),
                    new ItemStack(entry.tool().get())));
        }

        return recipes;
    }

    private static List<ItemStack> partStacks(PartItem part, Map<ResourceLocation, Material> materials) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Material> material : materials.entrySet()) {
            ResourceLocation materialId = material.getKey();
            // M3.5 #394: the first assembly whose slots read different stat blocks. A material with
            // no block for this slot is a part the Part Builder refuses to make (PartBuilderRecipes),
            // so it is not offered here either -- string never cycles through a limb slot.
            if (!material.getValue().hasStatsFor(part.kind())) {
                continue;
            }
            ItemStack stack = new ItemStack(part);
            stack.set(ForgeweaveDataComponents.MATERIAL.get(), materialId);
            stacks.add(stack);
        }
        return stacks;
    }

    /**
     * Whether {@code recipe} is one of {@code #forgeweave:large_tools} -- the split {@link
     * ForgeweaveJeiPlugin} uses to route a recipe into {@link AssemblyCategory#TYPE} (Tool Station)
     * or {@link AssemblyCategory#LARGE_TYPE} (Tool Forge only) (issue #165). Item tags aren't bound
     * outside a running server, so this is proven correct against the real tag by {@code
     * gametest.ToolForgeGameTests#exactlySevenToolsAreForgeOnly} rather than a plain unit test here.
     */
    static boolean isLarge(AssemblyRecipe recipe) {
        return ToolAssemblyRecipes.entryFor(recipe.result()).map(ToolAssemblyRecipes::isLargeTool).orElse(false);
    }

    /**
     * Whether {@code recipe} is a {@code Category.ARMOR} entry -- the same three-way split
     * {@link #isLarge} documents, extended by docs/SCOPE.md M4 issue #782: {@link
     * AssemblyCategory#ARMOR_TYPE} (Armor Station) instead of {@link AssemblyCategory#TYPE} (Tool
     * Station/Tool Forge). No entry is ever both large and armor (armor pieces carry no
     * {@code #forgeweave:large_tools} tag), so the two splits never conflict.
     */
    static boolean isArmor(AssemblyRecipe recipe) {
        return ToolAssemblyRecipes.entryFor(recipe.result()).map(ToolAssemblyRecipes::isArmorEntry).orElse(false);
    }

    private AssemblyRecipes() {}
}
