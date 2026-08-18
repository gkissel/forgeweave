package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * Builds one {@link PartCraftingRecipe} per (part type, material) pair -- cheap to enumerate
 * explicitly (5 part types x however many materials a modpack ships), unlike tool assembly's head x
 * binding x handle combinatorics (see {@link AssemblyRecipes}). The material input/change slots
 * within each recipe then cycle through every crafting-item option that material accepts (issue
 * #45), reusing {@code menu.PartBuilderRecipes}' own cost constants and {@code computeCost} math
 * rather than re-deriving them -- only the pattern/part wiring is re-declared here, since
 * {@code PartBuilderRecipes} keeps that association package-private to {@code menu}.
 */
final class PartCraftingRecipes {
    private record Entry(Supplier<? extends Item> pattern, Supplier<? extends PartItem> part, int cost) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PATTERN_PICKAXE_HEAD, ForgeweaveItems.PART_PICKAXE_HEAD, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SHOVEL_HEAD, ForgeweaveItems.PART_SHOVEL_HEAD, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_AXE_HEAD, ForgeweaveItems.PART_AXE_HEAD, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_BINDING, ForgeweaveItems.PART_TOOL_BINDING, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_HANDLE, ForgeweaveItems.PART_TOOL_HANDLE, PartBuilderRecipes.SMALL_PART_COST),

            // M3 roster (docs/SCOPE.md issue #151) -- mirrors menu.PartBuilderRecipes#ENTRIES.
            new Entry(ForgeweaveItems.PATTERN_SWORD_BLADE, ForgeweaveItems.PART_SWORD_BLADE, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_WIDE_GUARD, ForgeweaveItems.PART_WIDE_GUARD, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_HAND_GUARD, ForgeweaveItems.PART_HAND_GUARD, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_CROSS_GUARD, ForgeweaveItems.PART_CROSS_GUARD, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_SIGN_PLATE, ForgeweaveItems.PART_SIGN_PLATE, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_PAN, ForgeweaveItems.PART_PAN, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_KNIFE_BLADE, ForgeweaveItems.PART_KNIFE_BLADE, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE, ForgeweaveItems.PART_LARGE_SWORD_BLADE, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD, ForgeweaveItems.PART_TOUGH_TOOL_ROD, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOUGH_BINDING, ForgeweaveItems.PART_TOUGH_BINDING, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_LARGE_PLATE, ForgeweaveItems.PART_LARGE_PLATE, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_HAMMER_HEAD, ForgeweaveItems.PART_HAMMER_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_EXCAVATOR_HEAD, ForgeweaveItems.PART_EXCAVATOR_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SCYTHE_HEAD, ForgeweaveItems.PART_SCYTHE_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_KAMA_HEAD, ForgeweaveItems.PART_KAMA_HEAD, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_BROAD_AXE_HEAD, ForgeweaveItems.PART_BROAD_AXE_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD, ForgeweaveItems.PART_VEIN_HAMMER_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_WAR_MACE_HEAD, ForgeweaveItems.PART_WAR_MACE_HEAD, PartBuilderRecipes.LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_CURVED_BLADE, ForgeweaveItems.PART_CURVED_BLADE, PartBuilderRecipes.HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_KATANA_BLADE, ForgeweaveItems.PART_KATANA_BLADE, PartBuilderRecipes.MEDIUM_PART_COST),
            // M3.5's bow parts (issue #393).
            new Entry(ForgeweaveItems.PATTERN_BOW_LIMB, ForgeweaveItems.PART_BOW_LIMB, PartBuilderRecipes.MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_BOW_STRING, ForgeweaveItems.PART_BOW_STRING, PartBuilderRecipes.SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_SHARPENING_KIT, ForgeweaveItems.PART_SHARPENING_KIT, PartBuilderRecipes.HEAD_COST));

    /** One crafting-item option a material accepts, and the shard-unit value one of it pays off. */
    private record Option(ItemStack representative, int value) {}

    static List<PartCraftingRecipe> build(Map<ResourceLocation, Material> materials) {
        List<PartCraftingRecipe> recipes = new ArrayList<>();
        for (Entry entry : ENTRIES) {
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
                    changes.add(cost.changeUnits() > 0 ? shardChange(material.getKey(), cost.changeUnits()) : null);
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

    private static ItemStack shardChange(ResourceLocation materialId, int changeUnits) {
        return shardStack(materialId, changeUnits / PartBuilderRecipes.SHARD_VALUE);
    }

    private static ItemStack shardStack(ResourceLocation materialId, int count) {
        ItemStack stack = new ItemStack(ForgeweaveItems.SHARD.get(), count);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), materialId);
        return stack;
    }

    private PartCraftingRecipes() {}
}
