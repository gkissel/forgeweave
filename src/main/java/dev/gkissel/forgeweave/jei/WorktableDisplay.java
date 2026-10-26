package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.WorktableRecipe;

/**
 * One Modifier Worktable function for JEI (issue #1057). {@link WorktableRecipe} is a datapack
 * registry value and so carries no id of its own, but JEI needs one per shown recipe, so the
 * registry key travels alongside it -- the same reason {@link EmbossingDisplay} exists.
 *
 * @param id the recipe's registry key, JEI's {@code getRegistryName}
 * @param recipe what the table does and what it costs
 */
public record WorktableDisplay(ResourceLocation id, WorktableRecipe recipe) {
    /** What the table can be shown working on: one harvest tool and one armor piece, cycling. */
    public static final List<ItemStack> TOOLS = List.of(
            new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()),
            new ItemStack(ForgeweaveItems.ARMOR_HELMET.get()));
}
