package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * One Part Builder display recipe, for one part type and one material: pattern + material input ->
 * part, with the shard change the Part Builder actually pays out on overpayment (issue #45).
 *
 * <p>{@code materialInputs} and {@code changeOutputs} are parallel, same-length lists -- one entry
 * per crafting-item option the material accepts (planks, logs, sticks, shards, ...), cycled together
 * by JEI so each material stack shown always pairs with the change it actually produces (a log
 * overpays a head and returns shards; a shard pays it exactly and returns none). A {@code null}
 * entry in {@code changeOutputs} means that option leaves no change.
 */
record PartCraftingRecipe(ItemStack pattern, List<ItemStack> materialInputs, ItemStack result, List<ItemStack> changeOutputs) {}
