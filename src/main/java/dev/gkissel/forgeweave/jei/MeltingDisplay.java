package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * One Melting display recipe: {@code inputs} -- already resolved to whichever items actually win
 * this {@code recipe.MeltingRecipe} entry under most-specific-wins ({@code
 * recipe.MeltingRecipe#resolve}, see {@link MeltingRecipes}) -- melt into {@code amount} mB of
 * {@code fluid} at {@code temperature}. {@code amount} is the recipe's own base value; {@code ore}
 * says whether {@code SmelteryCore}'s tier multiplier (issue #99) applies to it, which {@link
 * MeltingCategory} explains via a tooltip rather than baking a specific multiplier into the number
 * shown (docs/SCOPE.md M2: "melting recipes hold base amounts, the core multiplies").
 */
record MeltingDisplay(List<ItemStack> inputs, Fluid fluid, int amount, int temperature, boolean ore) {}
