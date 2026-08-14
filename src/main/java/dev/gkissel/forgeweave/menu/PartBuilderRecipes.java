package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
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

    // M3 part costs (docs/SCOPE.md M3 issue #151), read off the clone's own registerToolPart calls
    // (TinkerTools#registerToolParts) and normalized the same way as the constants above: upstream's
    // {@code Material.VALUE_Ingot} is 144, exactly 2 of this class's shard-units, so a cost of
    // {@code VALUE_Ingot * n} becomes {@code n * 2} shard-units here.
    //   sign_head/pan_head/tough_tool_rod/tough_binding = VALUE_Ingot * 3 -> 6
    //   large_sword_blade/large_plate/hammer_head/excavator_head/scythe_head/broad_axe_head = VALUE_Ingot * 8 -> 16
    // sword_blade and kama_head are VALUE_Ingot * 2, same as HEAD_COST; wide/hand/cross guard and
    // knife_blade are VALUE_Ingot * 1, same as SMALL_PART_COST -- both reuse the constants above.
    public static final int MEDIUM_PART_COST = 6;
    public static final int LARGE_HEAD_COST = 16;

    /** The value of one shard item, and the atomic unit every other value above is denominated in. */
    public static final int SHARD_VALUE = 1;

    private record Entry(Supplier<? extends Item> pattern, Supplier<? extends PartItem> part, int cost) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PATTERN_PICKAXE_HEAD, ForgeweaveItems.PART_PICKAXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SHOVEL_HEAD, ForgeweaveItems.PART_SHOVEL_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_AXE_HEAD, ForgeweaveItems.PART_AXE_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_BINDING, ForgeweaveItems.PART_TOOL_BINDING, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOOL_HANDLE, ForgeweaveItems.PART_TOOL_HANDLE, SMALL_PART_COST),

            // M3 roster (docs/SCOPE.md issue #151).
            new Entry(ForgeweaveItems.PATTERN_SWORD_BLADE, ForgeweaveItems.PART_SWORD_BLADE, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_WIDE_GUARD, ForgeweaveItems.PART_WIDE_GUARD, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_HAND_GUARD, ForgeweaveItems.PART_HAND_GUARD, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_CROSS_GUARD, ForgeweaveItems.PART_CROSS_GUARD, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_SIGN_PLATE, ForgeweaveItems.PART_SIGN_PLATE, MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_PAN, ForgeweaveItems.PART_PAN, MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_KNIFE_BLADE, ForgeweaveItems.PART_KNIFE_BLADE, SMALL_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE, ForgeweaveItems.PART_LARGE_SWORD_BLADE, LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD, ForgeweaveItems.PART_TOUGH_TOOL_ROD, MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_TOUGH_BINDING, ForgeweaveItems.PART_TOUGH_BINDING, MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_LARGE_PLATE, ForgeweaveItems.PART_LARGE_PLATE, LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_HAMMER_HEAD, ForgeweaveItems.PART_HAMMER_HEAD, LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_EXCAVATOR_HEAD, ForgeweaveItems.PART_EXCAVATOR_HEAD, LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_SCYTHE_HEAD, ForgeweaveItems.PART_SCYTHE_HEAD, LARGE_HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_KAMA_HEAD, ForgeweaveItems.PART_KAMA_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_BROAD_AXE_HEAD, ForgeweaveItems.PART_BROAD_AXE_HEAD, LARGE_HEAD_COST),
            // No upstream cost to read (no 1.12/1.20 counterpart) -- priced like hammer_head, the
            // large-tool head part it's functionally closest to.
            new Entry(ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD, ForgeweaveItems.PART_VEIN_HAMMER_HEAD, LARGE_HEAD_COST),
            // #161: likewise no upstream cost to read -- priced like the other large-tool heads,
            // which is the tier the warmace assembles at (ToolAssemblyRecipes#LARGE_TOOLS).
            new Entry(ForgeweaveItems.PATTERN_WAR_MACE_HEAD, ForgeweaveItems.PART_WAR_MACE_HEAD, LARGE_HEAD_COST),
            // #159's new part. No upstream cost to read either (Forgeweave's own shape) -- priced
            // like sword_blade, the one-handed sword blade it stands in for.
            new Entry(ForgeweaveItems.PATTERN_CURVED_BLADE, ForgeweaveItems.PART_CURVED_BLADE, HEAD_COST),
            // #160's katana blade, also with no upstream cost to read. Priced at MEDIUM_PART_COST:
            // a longer single-edged blade than the plain sword_blade (HEAD_COST) but nothing like
            // the two-handed large_sword_blade (LARGE_HEAD_COST), which is exactly the gap upstream
            // itself prices sign_plate/pan into.
            new Entry(ForgeweaveItems.PATTERN_KATANA_BLADE, ForgeweaveItems.PART_KATANA_BLADE, MEDIUM_PART_COST));

    /**
     * Whether the pattern slot should accept this stack at all (the five part patterns only -- not
     * {@link ForgeweaveItems#PATTERN_BLANK}, which has no {@link Entry}). Public so {@link
     * dev.gkissel.forgeweave.block.ChestKind#PATTERN} (issue #66) can reuse the same check for the
     * Pattern Chest's slot filter instead of re-deriving the pattern-to-part table.
     */
    public static boolean isPattern(ItemStack stack) {
        return findEntry(stack).isPresent();
    }

    private static Optional<Entry> findEntry(ItemStack pattern) {
        return ENTRIES.stream().filter(entry -> pattern.is(entry.pattern().get())).findFirst();
    }

    /**
     * A successful pattern+material match: the crafted part, how many items each material slot
     * contributes (issue #306: upstream's second material input, {@code ToolBuilder#tryBuildToolPart}
     * consuming from a combined item list), and the shard change (possibly {@link ItemStack#EMPTY})
     * for leftover value.
     */
    record Match(ItemStack result, int material1ItemsConsumed, int material2ItemsConsumed, ItemStack change) {}

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
     * Resolves what the part builder should produce for the current pattern and material slots
     * (issue #306: upstream's second material input at (48, 44) -- {@code
     * ContainerPartBuilder#input2}/{@code ToolBuilder#tryBuildToolPart}), or empty if the pattern is
     * missing/unrecognized, both material slots are empty, neither matches any known material's
     * crafting items (or a shard with no material set), or the combined value doesn't cover the cost.
     */
    static Optional<Match> resolve(HolderLookup.Provider registries, ItemStack pattern, ItemStack material1, ItemStack material2) {
        if (pattern.isEmpty() || (material1.isEmpty() && material2.isEmpty())) {
            return Optional.empty();
        }
        return findEntry(pattern).flatMap(entry -> combinedMaterialValue(registries, material1, material2)
                .flatMap(matched -> {
                    if (matched.totalValue() < entry.cost()) {
                        return Optional.empty();
                    }

                    // Whole items only, cheapest-first slot order (upstream's own input1-then-input2
                    // combined list, ListUtil.getListFrom(input1, input2)) -- the same "consume as
                    // few whole items as the cost needs" rule the single-slot version used.
                    int unitValue1 = unitValueAgainst(registries, matched.id(), material1);
                    int unitValue2 = unitValueAgainst(registries, matched.id(), material2);
                    int remaining = entry.cost();
                    int consumed1 = 0;
                    while (remaining > 0 && consumed1 < material1.getCount() && unitValue1 > 0) {
                        consumed1++;
                        remaining -= unitValue1;
                    }
                    int consumed2 = 0;
                    while (remaining > 0 && consumed2 < material2.getCount() && unitValue2 > 0) {
                        consumed2++;
                        remaining -= unitValue2;
                    }

                    ItemStack result = new ItemStack(entry.part().get());
                    result.set(ForgeweaveDataComponents.MATERIAL.get(), matched.id());

                    ItemStack change = ItemStack.EMPTY;
                    int changeUnits = -remaining;
                    if (changeUnits > 0) {
                        change = new ItemStack(ForgeweaveItems.SHARD.get(), changeUnits / SHARD_VALUE);
                        change.set(ForgeweaveDataComponents.MATERIAL.get(), matched.id());
                    }
                    return Optional.of(new Match(result, consumed1, consumed2, change));
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

    /** A material identified across both material slots, and the shard-unit value available in total (issue #306). */
    public record CombinedMaterialMatch(ResourceLocation id, int totalValue) {}

    /**
     * Which material the two material slots count as together, and how many shard-units are
     * available across both -- upstream {@code GuiPartBuilder#getMaterial}/{@code
     * Material#matchesRecursively}'s combined-slot read, tried material1-then-material2 (upstream's
     * own order) for identification, then summing whichever of the two stacks actually matches that
     * material (an unrelated item sitting in the other slot contributes nothing, same as upstream's
     * {@code RecipeMatch} skipping non-matching entries in the combined list).
     */
    public static Optional<CombinedMaterialMatch> combinedMaterialValue(HolderLookup.Provider registries,
            ItemStack material1, ItemStack material2) {
        return materialValue(registries, material1).or(() -> materialValue(registries, material2))
                .map(primary -> new CombinedMaterialMatch(primary.id(),
                        unitValueAgainst(registries, primary.id(), material1) * material1.getCount()
                                + unitValueAgainst(registries, primary.id(), material2) * material2.getCount()));
    }

    /** How many shard-units one item of {@code stack} is worth against a specific material id, or 0 if it doesn't match. */
    private static int unitValueAgainst(HolderLookup.Provider registries, ResourceLocation materialId, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(ForgeweaveItems.SHARD.get())) {
            return materialId.equals(stack.get(ForgeweaveDataComponents.MATERIAL.get())) ? SHARD_VALUE : 0;
        }
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, materialId)))
                .map(Holder::value)
                .flatMap(material -> material.craftingItems().stream()
                        .filter(craftingItem -> craftingItem.ingredient().test(stack))
                        .map(Material.CraftingItem::value)
                        .findFirst())
                .orElse(0);
    }

    private PartBuilderRecipes() {}
}
