package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;

/**
 * The Part Builder's crafting table: which pattern produces which part, how many crafting-value
 * units it costs, and how leftover value comes back as {@link ForgeweaveItems#SHARD} of the same
 * material (issue #45, upstream 1.12's second-output behavior).
 *
 * <p><b>Unit (issue #45, re-denominated by parity audit T58 / issue #489):</b> upstream prices
 * everything in "material value" where {@code VALUE_Ingot = 144} and every other size is a fraction
 * of it ({@code Material.java:45-51} of the 1.12 clone): {@code VALUE_Shard = 72}, {@code
 * VALUE_Fragment = 36} (bonemeal, paper, prismarine shards), {@code VALUE_Nugget = 16}. Forgeweave's
 * {@code crafting_items} schema (see {@code Material.CraftingItem}) and every cost below are
 * denominated directly in that same unit, unscaled -- issue #45's coarser "1 unit = 1 shard" scale
 * could not express a nugget or a fragment, which is why the ticket's "nugget-units" is not the
 * unit either (36 is not a multiple of 16):
 *
 * <pre>
 *   item value:   ingot/plank/cobblestone/flint/bone = 144   log = 576   stick/shard = 72
 *                 bonemeal/paper/prismarine shard = 36   nugget = 16
 *   part cost:    head = 288 (2 ingots, matches upstream TinkerTools#pickHead et al.)
 *                 handle/binding = 144 (1 ingot, matches upstream TinkerTools#toolRod/#binding)
 * </pre>
 *
 * A material item's value is per-item; {@link #resolve} consumes the fewest whole items whose
 * combined value covers the part's cost and returns the excess as whole shards ({@link
 * #shardChange}: upstream {@code ToolBuilder#tryBuildToolPart}'s {@code leftover / VALUE_Shard},
 * integer division -- anything under a shard is lost, exactly as upstream).
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
    /**
     * Upstream's {@code Material.VALUE_Ingot} ({@code library/materials/Material.java:47}), the unit
     * everything here and every material JSON's {@code crafting_items[].value} is priced in. Public so
     * the pattern tooltip (issue #379) can quote a cost in ingots the way upstream's {@code
     * Pattern#addInformation} does -- {@code getCost() / (float) Material.VALUE_Ingot}.
     */
    public static final int INGOT_VALUE = 144;
    /** {@code VALUE_Nugget = VALUE_Ingot / 9} -- every {@code addCommonItems} metal's nugget. */
    public static final int NUGGET_VALUE = INGOT_VALUE / 9;
    /** {@code VALUE_Fragment = VALUE_Ingot / 4} -- bonemeal, paper, prismarine shards ({@code TinkerMaterials.java:243,269,276}). */
    public static final int FRAGMENT_VALUE = INGOT_VALUE / 4;
    /** {@code VALUE_Shard = VALUE_Ingot / 2} -- one {@link ForgeweaveItems#SHARD}, and a wood stick. */
    public static final int SHARD_VALUE = INGOT_VALUE / 2;

    // Part costs read straight off the clone's registerToolPart calls (TinkerTools#registerToolParts):
    //   pickHead/shovelHead/axeHead/swordBlade/kamaHead = VALUE_Ingot * 2
    //   toolRod/binding/wide,hand,cross guard/knifeBlade = VALUE_Ingot * 1
    //   signHead/panHead/toughToolRod/toughBinding = VALUE_Ingot * 3
    //   largeSwordBlade/largePlate/hammerHead/excavatorHead/scytheHead/broadAxeHead = VALUE_Ingot * 8
    public static final int HEAD_COST = 2 * INGOT_VALUE;
    public static final int SMALL_PART_COST = INGOT_VALUE;
    public static final int MEDIUM_PART_COST = 3 * INGOT_VALUE;
    public static final int LARGE_HEAD_COST = 8 * INGOT_VALUE;
    /**
     * #677: the 1.20 clone's plating costs ({@code ToolsRecipeProvider#addPartRecipes:451-455},
     * {@code partWithDummy(..., cost, ...)}): helmet 3, chestplate 6, leggings 5, boots 2; maille 2.
     */
    public static final int PLATING_HELMET_COST = 3 * INGOT_VALUE;
    public static final int PLATING_CHESTPLATE_COST = 6 * INGOT_VALUE;
    public static final int PLATING_LEGGINGS_COST = 5 * INGOT_VALUE;
    public static final int PLATING_BOOTS_COST = 2 * INGOT_VALUE;
    public static final int MAILLE_COST = 2 * INGOT_VALUE;

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
            new Entry(ForgeweaveItems.PATTERN_KATANA_BLADE, ForgeweaveItems.PART_KATANA_BLADE, MEDIUM_PART_COST),
            // M3.5 (issue #393). Upstream costs read straight off TinkerTools.java:210-211 --
            // bowLimb is VALUE_Ingot * 3 (MEDIUM_PART_COST, the same tier tough_binding sits in)
            // and bowString is VALUE_Ingot * 1 (SMALL_PART_COST).
            new Entry(ForgeweaveItems.PATTERN_BOW_LIMB, ForgeweaveItems.PART_BOW_LIMB, MEDIUM_PART_COST),
            new Entry(ForgeweaveItems.PATTERN_BOW_STRING, ForgeweaveItems.PART_BOW_STRING, SMALL_PART_COST),
            // #626 (parity audit T17). Upstream costs read straight off TinkerTools.java:213-215 --
            // arrowHead, arrowShaft and fletching are all VALUE_Ingot * 2, the same HEAD_COST tier
            // every plain head part sits in.
            new Entry(ForgeweaveItems.PATTERN_ARROW_HEAD, ForgeweaveItems.PART_ARROW_HEAD, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_ARROW_SHAFT, ForgeweaveItems.PART_ARROW_SHAFT, HEAD_COST),
            new Entry(ForgeweaveItems.PATTERN_FLETCHING, ForgeweaveItems.PART_FLETCHING, HEAD_COST),
            // #677's armor parts.
            new Entry(ForgeweaveItems.PATTERN_PLATING_HELMET, ForgeweaveItems.PART_PLATING_HELMET, PLATING_HELMET_COST),
            new Entry(ForgeweaveItems.PATTERN_PLATING_CHESTPLATE, ForgeweaveItems.PART_PLATING_CHESTPLATE, PLATING_CHESTPLATE_COST),
            new Entry(ForgeweaveItems.PATTERN_PLATING_LEGGINGS, ForgeweaveItems.PART_PLATING_LEGGINGS, PLATING_LEGGINGS_COST),
            new Entry(ForgeweaveItems.PATTERN_PLATING_BOOTS, ForgeweaveItems.PART_PLATING_BOOTS, PLATING_BOOTS_COST),
            new Entry(ForgeweaveItems.PATTERN_MAILLE, ForgeweaveItems.PART_MAILLE, MAILLE_COST),
            // #271's sharpening kit. Upstream cost read straight off its constructor:
            // `SharpeningKit() { super(Material.VALUE_Shard * 4); }` with `VALUE_Shard = VALUE_Ingot / 2`
            // (= 72), so 288 = 2 ingots -- the same HEAD_COST every head part uses.
            new Entry(ForgeweaveItems.PATTERN_SHARPENING_KIT, ForgeweaveItems.PART_SHARPENING_KIT, HEAD_COST),
            // #605: upstream's shard is a tool part in its own right -- {@code Shard extends ToolPart}
            // with {@code super(Material.VALUE_Shard)} and a stencil registration next to the
            // sharpening kit's ({@code TinkerTools#registerItems:154}). It is the roster's only
            // sub-ingot cost, which is what makes shard change reachable from a plain ingot instead
            // of only from an oversized input (a log, a metal block).
            new Entry(ForgeweaveItems.PATTERN_SHARD, ForgeweaveItems.SHARD, SHARD_VALUE));

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

    /**
     * How many whole shards {@code changeUnits} of leftover value comes back as -- upstream {@code
     * ToolBuilder#tryBuildToolPart}'s {@code (amount - cost) / Material.VALUE_Shard}. Sub-shard
     * remainders (a prismarine brick block overpaying a head by one fragment) are lost, as upstream.
     */
    public static int shardChange(int changeUnits) {
        return changeUnits / SHARD_VALUE;
    }

    /** A material identified for the current material-slot stack, and that stack's per-item value. */
    public record MaterialMatch(ResourceLocation id, int unitValue) {}

    /**
     * What one of {@code pattern} costs in value units, or empty if it isn't a part pattern. Public
     * so the Part Builder's info panel (issue #47) can quote the cost without restating the table.
     */
    public static Optional<Integer> patternCost(ItemStack pattern) {
        return findEntry(pattern).map(Entry::cost);
    }

    /**
     * What one part item itself costs in value units -- the mirror of {@link #patternCost} keyed on
     * the part rather than the pattern that stamps it. Public so {@link
     * dev.gkissel.forgeweave.recipe.MeltingRecipe#find} (issue #440, parity audit T8) can price a
     * stone tool part's melt-back off the same cost table the Part Builder itself charges, rather than
     * restating it.
     */
    public static Optional<Integer> partCost(Item part) {
        return ENTRIES.stream().filter(entry -> entry.part().get() == part).findFirst().map(Entry::cost);
    }

    /** The part {@code pattern} makes, or empty if it isn't a part pattern. */
    public static Optional<PartItem> patternPart(ItemStack pattern) {
        return findEntry(pattern).map(entry -> entry.part().get());
    }

    /**
     * Why the loaded slots produce nothing, in the shape the info panel takes over with (issue #378,
     * upstream {@code GuiPartBuilder:143-189}). Two answers, and upstream's own error/warning split:
     *
     * <ul>
     *   <li><b>{@code invalid_pattern}</b> ({@link StationMenu.Rejection#error}) -- the pattern slot
     *       holds something that stamps no part. Upstream throws this from
     *       {@code ToolBuilder#tryBuildToolPart:410} as a {@code TinkerGuiException}, i.e. a craft
     *       that was attempted and refused. The menu's own slot filter turns most of these away, but
     *       the block's inventory is a real {@code Container} -- a hopper feeding the pattern slot a
     *       blank pattern never sees {@code Slot#mayPlace}.
     *   <li><b>{@code useless_tool_part}</b> ({@link StationMenu.Rejection#warning}) -- the part on
     *       the output carries a material this world has no definition for, so no tool can ever be
     *       built from it ({@code GuiPartBuilder:152-157}, which likewise only looks at the slots).
     *       Reachable through {@link #materialValue}'s shard branch, which trusts the id a shard
     *       carries without asking the registry whether it still exists.
     * </ul>
     */
    public static Optional<StationMenu.Rejection> rejection(@Nullable HolderLookup.Provider registries,
            ItemStack pattern, ItemStack output) {
        if (!pattern.isEmpty() && !isPattern(pattern)) {
            return Optional.of(StationMenu.Rejection.error(
                    Component.translatable("gui.forgeweave.part_builder.invalid_pattern")));
        }
        // Content-family toggles ticket: a valid pattern for a part nothing enabled can use. An
        // error rather than a warning, matching invalid_pattern above -- it is a craft that was
        // attempted and refused, not a loadout that merely can never work out.
        if (!ContentFamilies.itemEnabled(pattern)) {
            return Optional.of(StationMenu.Rejection.error(ContentFamilies.disabledMessage()));
        }
        ResourceLocation materialId = output.get(ForgeweaveDataComponents.MATERIAL.get());
        if (materialId != null && PartItem.hasUnusableMaterial(registries, output)) {
            return Optional.of(StationMenu.Rejection.warning(
                    Component.translatable("gui.forgeweave.part_builder.useless_tool_part",
                            MaterialDisplay.plainName(materialId),
                            // The part kind, not this stack: since issue #446 a part stack's own
                            // name already carries the material, and upstream's own call site
                            // (GuiPartBuilder.java:155) passes a fresh componentless part stack
                            // for exactly that reason.
                            new ItemStack(output.getItem()).getHoverName())));
        }
        return Optional.empty();
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
        return findEntry(pattern)
                // Content-family toggles ticket: a part whose every tool is in an off family cannot
                // be stamped. Filtered here rather than at the pattern slot so a pattern already in
                // the world stays storable and shift-clickable -- only the craft stops.
                .filter(entry -> ContentFamilies.itemEnabled(entry.part().get()))
                .flatMap(entry -> combinedMaterialValue(registries, material1, material2)
                .flatMap(matched -> {
                    if (matched.totalValue() < entry.cost()) {
                        return Optional.empty();
                    }
                    // #392, upstream ToolBuilder#tryBuildToolPart's own material check: a material
                    // carries only some of the stat blocks, and stamping a pattern from one that
                    // lacks this part's block would produce a part no tool could ever use.
                    if (!hasStatsFor(registries, matched.id(), entry.part().get().kind())) {
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
                    int shards = shardChange(-remaining);
                    if (shards > 0) {
                        change = new ItemStack(ForgeweaveItems.SHARD.get(), shards);
                        change.set(ForgeweaveDataComponents.MATERIAL.get(), matched.id());
                    }
                    return Optional.of(new Match(result, consumed1, consumed2, change));
                }));
    }

    /**
     * Which material {@code stack} counts as in the material slot, and what one item of it is worth
     * in value units. Public for the same reason as {@link #patternCost}: the info panel needs the
     * answer, and there must be exactly one place that decides it.
     */
    public static Optional<MaterialMatch> materialValue(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.is(ForgeweaveItems.SHARD.get())) {
            ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
            return Optional.ofNullable(materialId)
                    // A shard of a material this world has no definition for keeps matching, which is
                    // what leaves the useless_tool_part warning in #rejection reachable; only a
                    // material that exists and says cast_only is turned away.
                    .filter(id -> lookupMaterial(registries, id)
                            .map(PartBuilderRecipes::craftableInPartBuilder).orElse(true))
                    .map(id -> new MaterialMatch(id, SHARD_VALUE));
        }

        Optional<HolderLookup.RegistryLookup<Material>> lookup = registries.lookup(Material.REGISTRY);
        if (lookup.isEmpty()) {
            return Optional.empty();
        }
        for (var holder : lookup.get().listElements().toList()) {
            if (!craftableInPartBuilder(holder.value())) {
                continue;
            }
            for (Material.CraftingItem craftingItem : holder.value().craftingItems()) {
                if (craftingItem.ingredient().test(stack)) {
                    return Optional.of(new MaterialMatch(holder.key().location(), craftingItem.value()));
                }
            }
        }
        return Optional.empty();
    }

    /** A material identified across both material slots, and the value available in total (issue #306). */
    public record CombinedMaterialMatch(ResourceLocation id, int totalValue) {}

    /**
     * Which material the two material slots count as together, and how many value units are
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

    /**
     * Whether this station will take {@code material} at all -- upstream 1.12's
     * {@code Material#isCraftable}, which {@code ToolBuilder#tryBuildToolPart:423-426} consults
     * before matching any crafting item (issue #435, parity audit T3). A material marked {@code
     * cast_only} keeps its crafting items listed and simply is not offered here until
     * {@link ForgeweaveConfig#craftCastableMaterials()} says so, exactly as upstream's
     * {@code craftCastableMaterials} works.
     *
     * <p>Public because JEI ({@code jei.PartCraftingRecipes}) has to apply the same gate or it
     * advertises a craft the station refuses -- the same reason {@code hasStatsFor}'s question is
     * asked in both places (issue #393).
     */
    public static boolean craftableInPartBuilder(Material material) {
        return !material.castOnly() || ForgeweaveConfig.craftCastableMaterials();
    }

    private static Optional<Material> lookupMaterial(HolderLookup.Provider registries, ResourceLocation materialId) {
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, materialId)))
                .map(Holder::value);
    }

    /** Whether the named material carries the stat block a part of {@code kind} draws from (issue #392). */
    private static boolean hasStatsFor(HolderLookup.Provider registries, ResourceLocation materialId,
            PartItem.Kind kind) {
        return lookupMaterial(registries, materialId)
                .map(material -> material.hasStatsFor(kind))
                .orElse(false);
    }

    /**
     * How many value units one item of {@code stack} is worth against a specific material id, or 0
     * if it doesn't match.
     *
     * <p>Public because repair asks the same question ({@code ToolAssemblyRecipes#resolveRepair},
     * parity audit T30/issue #461): upstream 1.12 has exactly one item-to-material table -- {@code
     * Material extends RecipeMatchRegistry} -- and both the Part Builder ({@code
     * ToolBuilder#tryBuildToolPart}) and repair ({@code TinkersItem#calculateRepairAmount}) match
     * against it. Deliberately <em>not</em> gated on {@link #craftableInPartBuilder}: {@code
     * Material#isCraftable} is consulted by the Part Builder alone, so a {@code cast_only} metal
     * still repairs from its ingots and blocks.
     */
    public static int unitValueAgainst(HolderLookup.Provider registries, ResourceLocation materialId, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(ForgeweaveItems.SHARD.get())) {
            return materialId.equals(stack.get(ForgeweaveDataComponents.MATERIAL.get())) ? SHARD_VALUE : 0;
        }
        return lookupMaterial(registries, materialId)
                .flatMap(material -> material.craftingItems().stream()
                        .filter(craftingItem -> craftingItem.ingredient().test(stack))
                        .map(Material.CraftingItem::value)
                        .findFirst())
                .orElse(0);
    }

    private PartBuilderRecipes() {}
}
