package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One embossing display recipe, for one donor material (see {@link EmbossingRecipes}): every donor
 * part type that material grants traits through, cycling in one slot -- an embossment's traits and
 * name depend only on the donor's material and part kind ({@code modifier.Embossing}), never on
 * which tool receives it -- plus the shared reagent set {@code modifier.EmbossingRecipe} defines.
 *
 * @param donorParts one representative stack per donor part kind this material grants traits
 *     through, all carrying {@code material}
 * @param reagents the station's own cost, straight off the datapack recipe -- always four or fewer,
 *     since embossing spans the Tool Station's five free slots and the donor always claims one
 * @param material the donor material every stack in {@code donorParts} carries
 */
record EmbossingDisplay(List<ItemStack> donorParts, List<Ingredient> reagents, ResourceLocation material) {}
