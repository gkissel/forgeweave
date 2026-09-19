package dev.gkissel.forgeweave.jei;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.modifier.WorktableRecipe;

/**
 * One Modifier Worktable function for JEI (issue #1057). {@link WorktableRecipe} is a datapack
 * registry value and so carries no id of its own, but JEI needs one per shown recipe, so the
 * registry key travels alongside it -- the same reason {@link EmbossingDisplay} exists.
 *
 * @param id the recipe's registry key, JEI's {@code getRegistryName}
 * @param recipe what the table does and what it costs
 */
record WorktableDisplay(ResourceLocation id, WorktableRecipe recipe) {}
