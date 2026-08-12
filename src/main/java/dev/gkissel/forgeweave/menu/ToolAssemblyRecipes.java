package dev.gkissel.forgeweave.menu;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.Embossing;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolRepair;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Everything the Tool Station can produce from its input slots. Four recipes share those slots, told
 * apart by what is in the first one and what the rest hold:
 *
 * <ul>
 *   <li><b>Assembly</b> -- head part + binding part + handle part. Which head part is used decides
 *       which tool comes out; binding and handle are shared across every M1 tool, because upstream
 *       1.12's pickaxe/shovel/hatchet all use {@code TinkerTools.binding}/{@code toolRod} for those
 *       two slots (see each tool class's constructor, e.g. {@code tools/tools/Pickaxe.java}:
 *       {@code PartMaterialType.handle(toolRod)}, {@code .head(pickHead)}, {@code .extra(binding)}).
 *   <li><b>Repair</b> -- a damaged or Broken tool in the head slot, plus items matching its head
 *       material's {@code repair_item} in the other two. CONTEXT.md puts repair at this station and
 *       makes the head material the one that determines the repair item.
 *   <li><b>Modifier application</b> (issue #105) -- a tool in the head slot plus a
 *       {@code modifier.ModifierRecipe}'s reagents in the other two, tried after repair so the two
 *       never fight over an item that is both.
 *   <li><b>Embossing</b> (issue #154) -- a tool in the head slot plus a donor tool part and an
 *       {@code modifier.EmbossingRecipe}'s reagent set across all four free slots, which is why the
 *       repair tab has four of them.
 * </ul>
 *
 * <p>NOTICE.md cites the tool classes for the part composition, {@code ToolNBT.java} for the stat
 * math in {@code ToolStats}, and {@code TinkersItem.java} for the repair math in
 * {@code ToolRepair}.
 */
public final class ToolAssemblyRecipes {
    /**
     * Public, and {@link #ENTRIES} likewise, so {@code jei.AssemblyRecipes} can enumerate the exact
     * head-part-to-tool table the station itself builds from instead of hand-copying it (issue #79:
     * a hand-copy can update one and miss the other) -- same "expose the table, keep resolution
     * package-private" pattern as {@link PartBuilderRecipes}'s public cost constants.
     */
    public record Entry(Supplier<? extends PartItem> headPart, Supplier<? extends ToolItem> tool) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry(ForgeweaveItems.PART_PICKAXE_HEAD, ForgeweaveItems.TOOL_PICKAXE),
            new Entry(ForgeweaveItems.PART_SHOVEL_HEAD, ForgeweaveItems.TOOL_SHOVEL),
            new Entry(ForgeweaveItems.PART_AXE_HEAD, ForgeweaveItems.TOOL_HATCHET));

    /**
     * The "large tool" classification (docs/SCOPE.md M3 issue #152): tools that can only be assembled
     * at the Tool Forge. Upstream 1.12 draws this line with two registries -- {@code
     * TinkerRegistry.registerToolCrafting} (Tool Station) and {@code registerToolForgeCrafting} (Tool
     * Forge only) -- and its hammer/excavator/lumber axe/scythe/cleaver/vein hammer register into the
     * second one; {@code ContainerToolForge#getBuildableTools} is the whole of the gate there.
     *
     * <p>Here it is a plain item tag, which is the same decision expressed as data: M3's tool issues
     * (#157-#161) add their tool to {@code data/forgeweave/tags/item/large_tools.json} and inherit the
     * gate with no code change. The tag ships empty until then -- no M1/M2 tool is large -- so the
     * GameTest datapack ({@code src/gametest/resources}) puts the hatchet in it to prove the gate.
     */
    public static final TagKey<Item> LARGE_TOOLS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "large_tools"));

    /**
     * The Tool Forge's repair discount (issue #152; a Forgeweave deviation from upstream, maintainer
     * decision 2026-08-12): the same repair costs 5% less material there.
     *
     * <p>Expressed as "each repair item goes {@code 1 / 0.95} as far" rather than as "charge 95% of
     * the items", because repair items are whole items and an M1/M2 repair spends one or two of them
     * -- 5% off a count that small rounds straight back to the same count, so a discount applied to
     * the count would be invisible in every repair a player actually performs. Applied to the
     * per-item durability instead it is exact at every scale and identical in aggregate: a repair
     * that took {@code k} items at the Tool Station takes {@code 0.95k} here.
     */
    private static final double FORGE_REPAIR_DISCOUNT = 0.95;

    /**
     * What the station produces and what taking it costs. {@code slotsUsed} is how many items to
     * spend from each input slot, indexed by slot -- what lets every recipe here share one output
     * slot: assembly always spends one of each part, a repair spends the tool plus only as many
     * repair items as it actually consumed, an embossment spends one of everything it matched.
     */
    record Result(ItemStack output, List<Integer> slotsUsed) {

        static Result of(ItemStack output, int... slotsUsed) {
            return new Result(output, Arrays.stream(slotsUsed).boxed().toList());
        }
    }

    static boolean isHeadPart(ItemStack stack) {
        return ENTRIES.stream().anyMatch(entry -> stack.is(entry.headPart().get()));
    }

    static boolean isBindingPart(ItemStack stack) {
        return stack.is(ForgeweaveItems.PART_TOOL_BINDING.get());
    }

    static boolean isHandlePart(ItemStack stack) {
        return stack.is(ForgeweaveItems.PART_TOOL_HANDLE.get());
    }

    /** Whether the head slot holds something the station can work on at all. */
    static boolean isHeadSlotInput(ItemStack stack) {
        return isHeadPart(stack) || stack.getItem() instanceof ToolItem;
    }

    /** Whether {@code stack} repairs the tool currently in the head slot. */
    static boolean isRepairItemFor(HolderLookup.Provider registries, ItemStack headSlotStack, ItemStack stack) {
        return headMaterialOf(registries, headSlotStack)
                .filter(material -> material.repairItem().test(stack))
                .isPresent();
    }

    /**
     * Resolves what the station should currently produce, or empty if the slots don't form any
     * of the recipes.
     *
     * @param freeSlots every input slot except the first, in slot order. Assembly, repair and
     *     modifier application read the first two of them (the slots M1 and M2 shipped); embossing
     *     (issue #154) needs all four, which is why this takes the list rather than two stacks.
     * @param forge whether the station is a Tool Forge (issue #152): gates large-tool assembly and
     *     applies the repair discount. Every other outcome is identical at both blocks.
     */
    static Optional<Result> resolve(HolderLookup.Provider registries, ItemStack headStack, List<ItemStack> freeSlots,
            boolean forge) {
        ItemStack bindingStack = freeSlots.get(0);
        ItemStack handleStack = freeSlots.get(1);
        if (!(headStack.getItem() instanceof ToolItem)) {
            return resolveAssembly(registries, headStack, bindingStack, handleStack, forge);
        }
        Optional<Result> repair = resolveRepair(registries, headStack, bindingStack, handleStack, forge);
        if (repair.isPresent()) {
            return repair;
        }
        // Embossing before modifier application: a donor tool part is nothing any modifier recipe
        // accepts, so the two can never both match, and trying the more specific one first keeps the
        // modifier path from having to know embossing exists.
        Optional<Result> embossing = resolveEmbossing(registries, headStack, freeSlots);
        return embossing.isPresent()
                ? embossing
                : resolveModifier(registries, headStack, bindingStack, handleStack);
    }

    /**
     * Embossing (issue #154, ADR-0004): a tool in the first slot, a donor part and the reagent set
     * spread across the four free ones. Every matched slot gives up exactly one item -- upstream's
     * {@code RecipeMatch.ItemCombination(1, ...)} -- and a rejected embossment produces no output
     * here, only the message {@link ToolStationMenu#rejection} shows.
     */
    private static Optional<Result> resolveEmbossing(HolderLookup.Provider registries, ItemStack toolStack,
            List<ItemStack> freeSlots) {
        return Embossing.resolve(registries, toolStack, freeSlots)
                .filter(outcome -> !outcome.output().isEmpty())
                .map(outcome -> Result.of(outcome.output(), 1, 1, 1, 1, 1));
    }

    /**
     * Whether the tool the head slot would assemble is a large tool, i.e. one only the Tool Forge can
     * build. Public so {@link ToolStationMenu#rejection} can say so in the info panel and a GameTest
     * can assert on the classification itself rather than only on its effect.
     */
    public static boolean isLargeToolHead(ItemStack headStack) {
        return ENTRIES.stream()
                .filter(entry -> headStack.is(entry.headPart().get()))
                .anyMatch(entry -> entry.tool().get().builtInRegistryHolder().is(LARGE_TOOLS));
    }

    /**
     * Modifier application (issue #105, ADR-0004) rides the same repair-tab slots: a tool in the
     * first one, reagents in the other two. Repair is tried first, so an item that is both a repair
     * item and some modifier's reagent still repairs -- and a rejected application (slots full, level
     * cap) produces no output here, only the message the screen reads from
     * {@link ToolStationMenu#rejection}.
     */
    private static Optional<Result> resolveModifier(HolderLookup.Provider registries, ItemStack toolStack,
            ItemStack bindingStack, ItemStack handleStack) {
        return ModifierApplication.resolve(registries, toolStack, bindingStack, handleStack)
                .filter(outcome -> !outcome.output().isEmpty())
                .map(outcome -> Result.of(
                        grantEnchantments(registries, outcome.output()), 1, outcome.firstUsed(), outcome.secondUsed()));
    }

    /**
     * Silky (issue #107) grants vanilla Silk Touch the moment it's applied rather than through the
     * enchanting table (CONTEXT.md: Forgeweave tools aren't enchantable there by default), upstream
     * {@code ModSilktouch#applyEffect}'s {@code ToolBuilder#addEnchantment}. This is the one modifier
     * effect that lands on a vanilla component and needs registry access to do it, which is exactly
     * why {@link ModifierApplication} -- deliberately registry-free, see its own javadoc -- doesn't do
     * it itself; this caller already threads {@code registries} through for everything else here.
     */
    private static ItemStack grantEnchantments(HolderLookup.Provider registries, ItemStack stack) {
        if (!ForgeweaveModifiers.grantsSilkTouch(stack)) {
            return stack;
        }
        Optional<Holder.Reference<Enchantment>> silkTouch = registries.lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(Enchantments.SILK_TOUCH));
        if (silkTouch.isEmpty()) {
            return stack;
        }
        stack.enchant(silkTouch.get(), 1);
        return stack;
    }

    private static Optional<Result> resolveAssembly(HolderLookup.Provider registries, ItemStack headStack,
            ItemStack bindingStack, ItemStack handleStack, boolean forge) {
        if (!isBindingPart(bindingStack) || !isHandlePart(handleStack)) {
            return Optional.empty();
        }
        if (!forge && isLargeToolHead(headStack)) {
            return Optional.empty(); // a large tool needs the Tool Forge; ToolStationMenu#rejection says so
        }
        Optional<Entry> entry = ENTRIES.stream().filter(candidate -> headStack.is(candidate.headPart().get())).findFirst();
        if (entry.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation headId = headStack.get(ForgeweaveDataComponents.MATERIAL.get());
        ResourceLocation bindingId = bindingStack.get(ForgeweaveDataComponents.MATERIAL.get());
        ResourceLocation handleId = handleStack.get(ForgeweaveDataComponents.MATERIAL.get());
        if (headId == null || bindingId == null || handleId == null) {
            return Optional.empty();
        }

        Optional<Material> head = lookupMaterial(registries, headId);
        Optional<Material> binding = lookupMaterial(registries, bindingId);
        Optional<Material> handle = lookupMaterial(registries, handleId);
        if (head.isEmpty() || binding.isEmpty() || handle.isEmpty()) {
            return Optional.empty();
        }

        ToolStats.Stats stats = ToolStats.compute(head.get(), binding.get(), handle.get());

        ToolItem tool = entry.get().tool().get();
        ItemStack result = new ItemStack(tool);
        result.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(), new ToolMaterials(headId, bindingId, handleId));
        result.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        // Trait ids come along as data so every later trait hook works off the stack alone
        // (ForgeweaveTraits: same id from two parts still counts once, as upstream 1.12 does).
        result.set(ForgeweaveDataComponents.TRAITS.get(),
                ForgeweaveTraits.resolve(head.get(), binding.get(), handle.get()));
        // Mining tier, mining speed, and the durability bar all ride on vanilla components, so
        // vanilla's own block-breaking and rendering paths need no Forgeweave-specific handling.
        result.set(DataComponents.TOOL, tool.toolComponent(head.get(), stats));
        result.set(DataComponents.MAX_DAMAGE, stats.durability());
        result.set(DataComponents.DAMAGE, 0);
        return Optional.of(Result.of(result, 1, 1, 1));
    }

    /**
     * Repairs the tool in the head slot with as many matching items as it takes (or as many as are
     * there), spending the binding slot before the handle slot. Every other component -- materials,
     * stats, the vanilla tool component -- rides along untouched on the copy, so a repaired tool is
     * the same tool.
     */
    private static Optional<Result> resolveRepair(HolderLookup.Provider registries, ItemStack toolStack,
            ItemStack bindingStack, ItemStack handleStack, boolean forge) {
        int damage = toolStack.getDamageValue();
        if (damage <= 0) {
            return Optional.empty(); // undamaged and unbroken: nothing to repair (upstream 1.12 does the same)
        }
        Optional<Material> head = headMaterialOf(registries, toolStack);
        if (head.isEmpty()) {
            return Optional.empty();
        }

        int fromBinding = head.get().repairItem().test(bindingStack) ? bindingStack.getCount() : 0;
        int fromHandle = head.get().repairItem().test(handleStack) ? handleStack.getCount() : 0;
        int available = fromBinding + fromHandle;
        if (available == 0) {
            return Optional.empty();
        }

        int headDurability = head.get().head().durability();
        int maxDamage = toolStack.getMaxDamage();
        int repairCount = toolStack.getOrDefault(ForgeweaveDataComponents.REPAIR_COUNT.get(), 0);
        int used = 0;
        while (damage > 0 && used < available) {
            int increment = repairIncrement(headDurability, maxDamage, repairCount + used, forge);
            // Traits get to top the repair up (upstream 1.12 fires ITrait#onToolHeal on every heal).
            damage -= increment + ForgeweaveTraits.repairBonus(toolStack, increment);
            used++;
        }

        ItemStack result = toolStack.copy();
        result.set(DataComponents.DAMAGE, Math.max(0, damage));
        result.set(ForgeweaveDataComponents.REPAIR_COUNT.get(), repairCount + used);
        // Any repair moves the tool off the Broken threshold, since one repair item is always worth
        // at least 1/64 of the durability pool.
        result.remove(ForgeweaveDataComponents.BROKEN.get());
        return Optional.of(Result.of(result, 1, Math.min(used, fromBinding), Math.max(0, used - fromBinding)));
    }

    /**
     * What one repair item restores, with the Tool Forge's {@link #FORGE_REPAIR_DISCOUNT} folded in.
     * Public so a GameTest can assert the discount arithmetic directly rather than only through a
     * tool's damage value.
     */
    public static int repairIncrement(int headDurability, int maxDamage, int repairCount, boolean forge) {
        int increment = ToolRepair.repairIncrement(headDurability, maxDamage, repairCount);
        return forge ? (int) Math.ceil(increment / FORGE_REPAIR_DISCOUNT) : increment;
    }

    /** The {@code Material} of an assembled tool's head part, which is what a repair needs. */
    private static Optional<Material> headMaterialOf(HolderLookup.Provider registries, ItemStack stack) {
        if (!(stack.getItem() instanceof ToolItem)) {
            return Optional.empty();
        }
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        return materials == null ? Optional.empty() : lookupMaterial(registries, materials.head());
    }

    private static Optional<Material> lookupMaterial(HolderLookup.Provider registries, ResourceLocation id) {
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, id)))
                .map(holder -> holder.value());
    }

    private ToolAssemblyRecipes() {}
}
