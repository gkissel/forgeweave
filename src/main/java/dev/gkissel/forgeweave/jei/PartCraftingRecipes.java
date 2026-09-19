package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * Builds one {@link PartCraftingRecipe} per (part type, material) pair -- cheap to enumerate
 * explicitly (5 part types x however many materials a modpack ships), unlike tool assembly's head x
 * binding x handle combinatorics (see {@link AssemblyRecipes}). The material input/change slots
 * within each recipe then cycle through every crafting-item option that material accepts (issue
 * #45), reusing {@code menu.PartBuilderRecipes}' own table, cost constants and {@code computeCost}
 * math rather than re-deriving any of them.
 *
 * <p>Issue #1066 deleted the hand-copy of the pattern/part wiring that used to sit here. It existed
 * only because {@code PartBuilderRecipes} kept that association package-private, and a copy of a
 * table is a copy that drifts -- it also had no way to show a part another mod registered. JEI now
 * reads the one table the station stamps from.
 */
final class PartCraftingRecipes {
    /** One crafting-item option a material accepts, and the value (upstream VALUE_* units) one of it pays off. */
    private record Option(ItemStack representative, int value) {}

    static List<PartCraftingRecipe> build(Map<ResourceLocation, Material> materials) {
        List<PartCraftingRecipe> recipes = new ArrayList<>();
        for (PartBuilderRecipes.Entry entry : PartBuilderRecipes.ENTRIES) {
            for (Map.Entry<ResourceLocation, Material> material : materials.entrySet()) {
                // #435: and the same craftable gate (PartBuilderRecipes#craftableInPartBuilder) -- a
                // cast-only material's crafting items are inert at the station until the config says
                // otherwise, so advertising them here would send a player to a craft that refuses.
                if (!PartBuilderRecipes.craftableInPartBuilder(material.getValue())) {
                    continue;
                }
                // #393: the same gate the station itself applies (PartBuilderRecipes#resolve, added
                // by #392) -- a material with no stat block for this part's kind can never stamp it,
                // so JEI must not advertise the craft. Invisible until #392 shipped `string` and
                // `vine`: every material before them carried every block.
                if (!material.getValue().hasStatsFor(entry.part().get().kind())) {
                    continue;
                }
                List<Option> options = craftingOptions(material.getKey(), material.getValue());
                if (options.isEmpty()) {
                    continue; // no usable crafting items or shard for this material -- nothing to display
                }

                List<ItemStack> inputs = new ArrayList<>();
                List<ItemStack> changes = new ArrayList<>();
                for (Option option : options) {
                    PartBuilderRecipes.CostResult cost = PartBuilderRecipes.computeCost(entry.cost(), option.value());
                    inputs.add(option.representative().copyWithCount(cost.itemsNeeded()));
                    int shards = PartBuilderRecipes.shardChange(cost.changeUnits());
                    changes.add(shards > 0 ? shardStack(material.getKey(), shards) : null);
                }

                ItemStack result = new ItemStack(entry.part().get());
                result.set(ForgeweaveDataComponents.MATERIAL.get(), material.getKey());

                recipes.add(new PartCraftingRecipe(new ItemStack(entry.pattern().get()), inputs, result, changes));
            }
        }
        return recipes;
    }

    /** Every crafting item {@code material} accepts, plus the shard itself (matches {@code PartBuilderRecipes#matchMaterial}'s special-casing of shards). */
    private static List<Option> craftingOptions(ResourceLocation materialId, Material material) {
        List<Option> options = new ArrayList<>();
        for (Material.CraftingItem craftingItem : material.craftingItems()) {
            ItemStack[] representatives = craftingItem.ingredient().getItems();
            if (representatives.length > 0) {
                options.add(new Option(representatives[0], craftingItem.value()));
            }
        }
        options.add(new Option(shardStack(materialId, 1), PartBuilderRecipes.SHARD_VALUE));
        return options;
    }

    private static ItemStack shardStack(ResourceLocation materialId, int count) {
        ItemStack stack = new ItemStack(ForgeweaveItems.SHARD.get(), count);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), materialId);
        return stack;
    }

    private PartCraftingRecipes() {}
}
