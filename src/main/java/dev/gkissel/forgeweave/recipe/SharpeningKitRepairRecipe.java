package dev.gkissel.forgeweave.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Repairs a tool with sharpening kits in a crafting table, without a Tool Station (parity audit T32,
 * issue #463). Ported from upstream 1.12's {@code tools/common/RepairRecipe} (tinkers-1.12, pinned
 * commit in NOTICE.md), which is exactly this: a hidden special recipe whose accepted set of repair
 * items is the sharpening kit alone -- one tool, any number of kits, anything else and it is not
 * this recipe.
 *
 * <p>Upstream's three structural rules are kept as they are:
 *
 * <ul>
 *   <li>{@code canFit(width, height) == width >= 3 && height >= 3}: the crafting table only, never
 *       the player's 2x2 grid;
 *   <li>{@code isHidden()}: it advertises no result, so it stays out of the recipe book and out of
 *       recipe viewers. In 1.21.1 that falls out of {@link CustomRecipe} itself -- {@code isSpecial}
 *       is true and {@code getResultItem} is empty;
 *   <li>the "check if all items were used" bail from {@code TinkersItem#repair}: the grid consumes
 *       one of every stack in it regardless of what the repair actually needed, so a loadout the
 *       repair would not spend in full has to not match at all rather than eat the surplus. That is
 *       {@link ToolAssemblyRecipes#repairOutsideStation}'s filter.
 * </ul>
 *
 * <p>The durability arithmetic itself is not duplicated here: it is the Tool Station's, which since
 * this issue also accepts kits.
 */
public class SharpeningKitRepairRecipe extends CustomRecipe {

    public SharpeningKitRepairRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !repair(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return repair(input, registries);
    }

    /** Upstream {@code RepairRecipe#canFit}: a crafting table, never the inventory's 2x2 grid. */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ForgeweaveRecipeSerializers.SHARPENING_KIT_REPAIR.get();
    }

    /** The repaired tool, or empty if the grid holds anything but one tool and its sharpening kits. */
    private static ItemStack repair(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack tool = ItemStack.EMPTY;
        List<ItemStack> kits = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ToolItem) {
                if (!tool.isEmpty()) {
                    return ItemStack.EMPTY; // upstream: a second tool makes this no recipe
                }
                tool = stack;
            } else if (stack.is(ForgeweaveItems.PART_SHARPENING_KIT.get())) {
                kits.add(stack.copyWithCount(1));
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (tool.isEmpty() || kits.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ToolAssemblyRecipes.repairOutsideStation(registries, tool.copy(), kits);
    }
}
