package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.client.book.ModifyPageContent;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;

/**
 * Every {@link ModifierRecipe} the Tool Station's modify tab accepts, one JEI recipe each
 * (docs/SCOPE.md M2 issues #105-#108, JEI category per issue #109). No dedup or cycling is needed:
 * each recipe's reagent {@code Ingredient} is its own display input, and two recipes are free to
 * target the same modifier id with different reagents (extra_slot's crafted item and netherite
 * ingot) -- both simply show up as their own row, which is the desired "show both" behavior rather
 * than something to collapse.
 */
public final class ModifierApplicationRecipes {
    /**
     * Bare tool icons, same set as {@code RepairRecipes}' -- repair (unlike modifier application) has
     * no per-recipe restriction on which tool it targets. {@link EmbossingCategory}, the repair tab's
     * third render-only tool mechanic, which likewise applies to any tool, uses it as is (issue #165).
     *
     * <p>Issue #794: this catalog is <b>not</b> "any tool accepts any modifier" -- the three harvest
     * tools here are all {@code Category.HARVEST}, so before that issue's fix they were the row shown
     * for every modifier recipe regardless of restriction, including armor-only and projectile-only
     * ones no harvest tool could ever take. {@link #tools} asks {@link
     * ModifyPageContent#compatibleEntries} per recipe instead.
     */
    public static final List<ItemStack> ANY_TOOL = List.of(
            new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()),
            new ItemStack(ForgeweaveItems.TOOL_SHOVEL.get()),
            new ItemStack(ForgeweaveItems.TOOL_HATCHET.get()));

    static List<ModifierRecipe> build(Map<Identifier, ModifierRecipe> recipes) {
        return new ArrayList<>(recipes.values());
    }

    /**
     * The render-only tool slot's cycling catalog for {@code recipe} (issue #794): every tool this
     * recipe's modifier actually accepts ({@link ModifyPageContent#compatibleEntries}), the same set
     * the guide book's modifier page picks its single illustration from -- {@link #ANY_TOOL} is not a
     * safe default here since it is all {@code Category.HARVEST} and an armor-only or projectile-only
     * modifier accepts none of them. Falls back to {@link #ANY_TOOL} only when the recipe's modifier
     * id isn't registered at all (a stale/malformed recipe JSON), so the slot still shows something
     * rather than an empty cycle.
     *
     * @param registries the session's registries, or {@code null} before a world is joined
     */
    public static List<ItemStack> tools(ModifierRecipe recipe, HolderLookup.Provider registries) {
        Modifier modifier = ForgeweaveModifiers.get(recipe.modifier());
        if (modifier == null) {
            return ANY_TOOL;
        }
        return ModifyPageContent.compatibleEntries(registries, modifier).stream()
                .map(entry -> new ItemStack(entry.tool().get()))
                .toList();
    }

    private ModifierApplicationRecipes() {}
}
