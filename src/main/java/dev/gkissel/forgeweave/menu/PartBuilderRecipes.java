package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;

/**
 * The Part Builder's crafting table: which pattern produces which part, how many crafting-value
 * units it costs, and how leftover value comes back as {@link ForgeweaveItems#SHARD} of the same
 * material (issue #45, upstream 1.12's second-output behavior).
 *
 * <p><b>Normalization (issue #45):</b> upstream prices everything in "material value" where {@code
 * VALUE_Ingot = 144} and every other size is a fraction of it -- {@code VALUE_Shard = VALUE_Ingot /
 * 2 = 72}, the finest granularity any crafting item actually uses (a wood stick and a shard are
 * both priced at exactly one {@code VALUE_Shard}; nothing upstream prices any finer). Forgeweave's
 * {@code crafting_items} schema (see {@code Material.CraftingItem}) is denominated directly in that
 * unit -- 1 Forgeweave "shard-unit" = 1 upstream {@code VALUE_Shard} -- so every value in the
 * shipped material JSONs and below is an integer with no fractional loss:
 *
 * <pre>
 *   item value:   plank/cobblestone/stone/flint/bone = 2 (1 ingot)   log = 8 (4 ingots)   stick/shard = 1
 *   part cost:    head = 4 (2 ingots, matches upstream TinkerTools#pickHead et al.)
 *                 handle/binding = 2 (1 ingot, matches upstream TinkerTools#toolRod/#binding)
 * </pre>
 *
 * A material item's value is per-item (all crafting-item stacks in the material slot are a single
 * item type, matching Forgeweave's one-material-slot design); {@link #resolve} consumes the fewest
 * whole items whose combined value covers the part's cost and returns the excess as shards.
 *
 * <p>Material identification for a raw crafting item is "first datapack material (registry order)
 * whose {@code crafting_items} list has an ingredient matching the input stack" (mirrors {@code
 * repair_item}'s matching rule elsewhere; ADR-0002). A {@link ForgeweaveItems#SHARD} stack instead
 * carries its material directly via {@link ForgeweaveDataComponents#MATERIAL}.
 *
 * <p>The cost constants and {@link #computeCost} are {@code public} so the JEI plugin
 * ({@code jei.PartCraftingRecipes}) can enumerate the same crafting-item-to-part math the station
 * actually uses instead of re-deriving it; everything else here (pattern/part wiring, live-slot
 * resolution) stays package-private since only the menu needs it.
 */
public final class PartBuilderRecipes {
    public static final int HEAD_COST = 4;
    public static final int SMALL_PART_COST = 2;

    /** The value of one shard item, and the atomic unit every other value above is denominated in. */
    public static final int SHARD_VALUE = 1;

    private record Entry(Supplier<? extends Item> pattern, Supplier<? extends PartItem> part, int cost) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PATTERN_PICKAXE_HEAD, ForgeweaveItems.PART_PICKAXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SHOVEL_HEAD, ForgeweaveItems.PART_SHOVEL_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_AXE_HEAD, ForgeweaveItems.PART_AXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_BINDING, ForgeweaveItems.PART_TOOL_BINDING, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_HANDLE, ForgeweaveItems.PART_TOOL_HANDLE, SMALL_PART_COST));

    /** Whether the pattern slot should accept this stack at all. */
    static boolean isPattern(ItemStack stack) {
        return findEntry(stack).isPresent();
    }

    private static Optional<Entry> findEntry(ItemStack pattern) {
        return ENTRIES.stream().filter(entry -> pattern.is(entry.pattern().get())).findFirst();
    }

    /**
     * A successful pattern+material match: the crafted part, how many material-slot items it
     * consumes, and the shard change (possibly {@link ItemStack#EMPTY}) for leftover value.
     */
    record Match(ItemStack result, int materialItemsConsumed, ItemStack change) {}

    /** Pure value math: how many whole items of {@code unitValue} it takes to cover {@code cost}, and the leftover. */
    public record CostResult(int itemsNeeded, int changeUnits) {}

    public static CostResult computeCost(int cost, int unitValue) {
        int itemsNeeded = Math.ceilDiv(cost, unitValue);
        return new CostResult(itemsNeeded, itemsNeeded * unitValue - cost);
    }

    /** A material identified for the current material-slot stack, and that stack's per-item value. */
    public record MaterialMatch(ResourceLocation id, int unitValue) {}

    /**
     * What one of {@code pattern} costs in shard-units, or empty if it isn't a part pattern. Public
     * so the Part Builder's info panel (issue #47) can quote the cost without restating the table.
     */
    public static Optional<Integer> patternCost(ItemStack pattern) {
        return findEntry(pattern).map(Entry::cost);
    }

    /** The part {@code pattern} makes, or empty if it isn't a part pattern. */
    public static Optional<PartItem> patternPart(ItemStack pattern) {
        return findEntry(pattern).map(entry -> entry.part().get());
    }

    /**
     * Resolves what the part builder should produce for the current pattern and material slots, or
     * empty if the pattern is missing/unrecognized, the material doesn't match any known material's
     * crafting items (or a shard with no material set), or there isn't enough of it.
     */
    static Optional<Match> resolve(HolderLookup.Provider registries, ItemStack pattern, ItemStack material) {
        if (pattern.isEmpty() || material.isEmpty()) {
            return Optional.empty();
        }
        return findEntry(pattern).flatMap(entry -> materialValue(registries, material).flatMap(matched -> {
            CostResult cost = computeCost(entry.cost(), matched.unitValue());
            if (material.getCount() < cost.itemsNeeded()) {
                return Optional.empty();
            }

            ItemStack result = new ItemStack(entry.part().get());
            result.set(ForgeweaveDataComponents.MATERIAL.get(), matched.id());

            ItemStack change = ItemStack.EMPTY;
            if (cost.changeUnits() > 0) {
                change = new ItemStack(ForgeweaveItems.SHARD.get(), cost.changeUnits() / SHARD_VALUE);
                change.set(ForgeweaveDataComponents.MATERIAL.get(), matched.id());
            }
            return Optional.of(new Match(result, cost.itemsNeeded(), change));
        }));
    }

    /**
     * Which material {@code stack} counts as in the material slot, and what one item of it is worth
     * in shard-units. Public for the same reason as {@link #patternCost}: the info panel needs the
     * answer, and there must be exactly one place that decides it.
     */
    public static Optional<MaterialMatch> materialValue(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.is(ForgeweaveItems.SHARD.get())) {
            ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
            return Optional.ofNullable(materialId).map(id -> new MaterialMatch(id, SHARD_VALUE));
        }

        Optional<HolderLookup.RegistryLookup<Material>> lookup = registries.lookup(Material.REGISTRY);
        if (lookup.isEmpty()) {
            return Optional.empty();
        }
        for (var holder : lookup.get().listElements().toList()) {
            for (Material.CraftingItem craftingItem : holder.value().craftingItems()) {
                if (craftingItem.ingredient().test(stack)) {
                    return Optional.of(new MaterialMatch(holder.key().location(), craftingItem.value()));
                }
            }
        }
        return Optional.empty();
    }

    private PartBuilderRecipes() {}
}
