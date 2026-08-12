package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolRepair;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Everything the Tool Station can produce from its input slots. Four recipes share those slots, told
 * apart by what is in the first one and what the rest hold:
 *
 * <ul>
 *   <li><b>Assembly</b> -- one part per slot, in the selected tool's own part order. Which tool
 *       comes out is decided by the whole set, not by one slot: {@link #ENTRIES} is the table, one
 *       {@link Entry} per assemblable tool, and slot {@code i} accepts the part its
 *       {@link ToolConstants.Entry#parts()} names at that index (issue #155). M1's three all took
 *       the same head/binding/handle triple; M3's roster does not -- its swords each take a
 *       different guard, three of its weapons have no extra part at all, and the Tool Forge tier
 *       takes four parts, two of them in the same role.
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
     * One assemblable tool: its {@link ToolConstants} entry and the item the station produces.
     * Nothing else -- the ordered part list, the slot count, every slot's role and weight and the
     * stat constants all already live on {@link ToolConstants.Entry} (issue #153), so a tool issue
     * registers a row by naming those two things and no third table can drift out of sync with them.
     *
     * <p>Slot {@code i} accepts the {@code PartItem} registered under
     * {@code forgeweave:<constants.parts().get(i).partId()>}, and that slot's material feeds part slot
     * {@code i} of {@link ToolConstants#compute}. Matching is positional, never by part identity: the
     * cleaver takes the same {@code tough_tool_rod} in two different roles, and the hammer takes
     * {@code large_plate} in two separate HEAD slots.
     *
     * <p>Public, and {@link #ENTRIES} likewise, so {@code jei.AssemblyRecipes} and
     * {@link ToolStationTabs} enumerate the exact table the station itself builds from instead of
     * hand-copying it (issue #79: a hand-copy can update one and miss the other) -- same "expose the
     * table, keep resolution package-private" pattern as {@link PartBuilderRecipes}'s public cost
     * constants.
     */
    public record Entry(ToolConstants.Entry constants, Supplier<? extends ToolItem> tool) {

        /** How many of the station's input slots this tool uses (2 to 4 across the M3 roster). */
        public int slotCount() {
            return constants.parts().size();
        }

        /**
         * The part item slot {@code slot} takes, looked up by id. Resolved on call rather than at
         * class-init, because {@link #ENTRIES} is a static table built while the item registry is
         * still being populated.
         */
        public PartItem part(int slot) {
            String id = constants.parts().get(slot).partId();
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, id));
            if (!(item instanceof PartItem partItem)) {
                throw new IllegalStateException(constants.id() + " slot " + slot + " names part '" + id
                        + "', which is not a registered part item");
            }
            return partItem;
        }

        /** Every part item this tool takes, in slot order. */
        public List<PartItem> parts() {
            List<PartItem> items = new ArrayList<>(slotCount());
            for (int i = 0; i < slotCount(); i++) {
                items.add(part(i));
            }
            return List.copyOf(items);
        }

        /** The slot holding the tool's first head part -- the one repair reads its repair item from. */
        public int headSlot() {
            List<ToolConstants.PartSlot> slots = constants.parts();
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i).role() == ToolConstants.Role.HEAD) {
                    return i;
                }
            }
            throw new IllegalStateException(constants.id() + " has no head part");
        }

        /** Whether the given input slots hold exactly this tool's parts, in order. */
        boolean matches(List<ItemStack> inputs) {
            if (inputs.size() < slotCount()) {
                return false;
            }
            for (int i = 0; i < inputs.size(); i++) {
                ItemStack stack = inputs.get(i);
                if (i < slotCount() ? !stack.is(part(i)) : !stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * M1's three tools have no {@link ToolConstants} entry -- that class is M3's roster (issue #153)
     * -- so their compositions live here, in the (head, binding, handle) slot order the station has
     * used since M1 and every existing GameTest and JEI recipe assumes. Every stat field is the
     * identity value, which makes {@link ToolConstants#compute} reproduce {@code ToolStats#compute}
     * exactly for them; the attack speed and damage potential are the ones {@code ForgeweaveItems}
     * already gives the items, repeated here only so the entry reads as a complete tool.
     */
    private static final ToolConstants.Entry PICKAXE = new ToolConstants.Entry("pickaxe",
            List.of(new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "pickaxe_head"),
                    new ToolConstants.PartSlot(ToolConstants.Role.EXTRA, "tool_binding"),
                    new ToolConstants.PartSlot(ToolConstants.Role.HANDLE, "tool_handle")),
            1.2f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    private static final ToolConstants.Entry SHOVEL = new ToolConstants.Entry("shovel",
            List.of(new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "shovel_head"),
                    new ToolConstants.PartSlot(ToolConstants.Role.EXTRA, "tool_binding"),
                    new ToolConstants.PartSlot(ToolConstants.Role.HANDLE, "tool_handle")),
            1.0f, 0.9f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    private static final ToolConstants.Entry HATCHET = new ToolConstants.Entry("hatchet",
            List.of(new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "axe_head"),
                    new ToolConstants.PartSlot(ToolConstants.Role.EXTRA, "tool_binding"),
                    new ToolConstants.PartSlot(ToolConstants.Role.HANDLE, "tool_handle")),
            1.1f, 1.1f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    public static final List<Entry> ENTRIES = List.of(
            new Entry(PICKAXE, ForgeweaveItems.TOOL_PICKAXE),
            new Entry(SHOVEL, ForgeweaveItems.TOOL_SHOVEL),
            new Entry(HATCHET, ForgeweaveItems.TOOL_HATCHET),
            // M3 station weapons (issue #155). Slot order is ToolConstants' own, which is upstream's
            // PartMaterialType order in each tool's constructor -- handle, head, then the guard.
            new Entry(ToolConstants.BROADSWORD, ForgeweaveItems.TOOL_BROADSWORD),
            new Entry(ToolConstants.LONGSWORD, ForgeweaveItems.TOOL_LONGSWORD),
            new Entry(ToolConstants.RAPIER, ForgeweaveItems.TOOL_RAPIER),
            new Entry(ToolConstants.BATTLESIGN, ForgeweaveItems.TOOL_BATTLESIGN),
            new Entry(ToolConstants.FRYING_PAN, ForgeweaveItems.TOOL_FRYING_PAN),
            new Entry(ToolConstants.DAGGER, ForgeweaveItems.TOOL_DAGGER),
            // #161: the Tool Forge tier's warmace -- handle, head, binding, same slot order as
            // ToolConstants#WARMACE's part list.
            new Entry(ToolConstants.WARMACE, ForgeweaveItems.TOOL_WARMACE),
            // M3 station tools (issue #156). The mattock is the one M3 station tool with no binding
            // part at all -- HANDLE, HEAD (axe), HEAD (shovel), upstream tools/tools/Mattock.java --
            // which the positional slot matching above already handles with no special case.
            new Entry(ToolConstants.MATTOCK, ForgeweaveItems.TOOL_MATTOCK),
            new Entry(ToolConstants.KAMA, ForgeweaveItems.TOOL_KAMA),
            // #159. The battleaxe is the first four-slot tool: handle, two broad axe heads, binding,
            // ToolConstants#BATTLEAXE's own order and upstream's own (BattleAxe.java's part list, and
            // its battleaxe.tcon.json layers them handle/backhead/fronthead/binding to match). Its two
            // heads take a slot each and so can be different materials -- #159 landed them sharing one
            // slot only because the pre-#155 station was fixed at three, which this table no longer is.
            new Entry(ToolConstants.BATTLEAXE, ForgeweaveItems.TOOL_BATTLEAXE),
            new Entry(ToolConstants.SCIMITAR, ForgeweaveItems.TOOL_SCIMITAR));

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

    /** The entry that produces {@code tool}, or empty for an item no tab builds. */
    public static Optional<Entry> entryFor(ItemStack tool) {
        return ENTRIES.stream().filter(entry -> tool.is(entry.tool().get())).findFirst();
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
            List<ItemStack> inputs = new ArrayList<>(freeSlots.size() + 1);
            inputs.add(headStack);
            inputs.addAll(freeSlots);
            return resolveAssembly(registries, inputs, forge);
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
                .filter(entry -> headStack.is(entry.part(entry.headSlot())))
                .anyMatch(ToolAssemblyRecipes::isLargeTool);
    }

    /** Whether this tool can only be assembled at the Tool Forge (issue #152's {@link #LARGE_TOOLS}). */
    public static boolean isLargeTool(Entry entry) {
        return entry.tool().get().builtInRegistryHolder().is(LARGE_TOOLS);
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

    private static Optional<Result> resolveAssembly(HolderLookup.Provider registries, List<ItemStack> inputs,
            boolean forge) {
        Optional<Entry> match = ENTRIES.stream().filter(entry -> entry.matches(inputs)).findFirst();
        if (match.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = match.get();
        if (!forge && isLargeTool(entry)) {
            return Optional.empty(); // a large tool needs the Tool Forge; ToolStationMenu#rejection says so
        }

        List<ResourceLocation> materialIds = new ArrayList<>(entry.slotCount());
        for (int i = 0; i < entry.slotCount(); i++) {
            ResourceLocation id = inputs.get(i).get(ForgeweaveDataComponents.MATERIAL.get());
            if (id == null) {
                return Optional.empty();
            }
            materialIds.add(id);
        }

        // One of every part it used, and nothing from a slot this tool doesn't have.
        int[] used = new int[entry.slotCount()];
        Arrays.fill(used, 1);
        return assemble(registries, entry, materialIds).map(output -> Result.of(output, used));
    }

    /**
     * Builds the assembled tool one set of part materials produces, or empty when a material id
     * doesn't resolve. Public so a GameTest and the dev screenshot harness can build the same stack
     * the station does without staging a menu.
     *
     * @param materialIds one per {@link Entry#slotCount()} slot, in that order
     */
    public static Optional<ItemStack> assemble(HolderLookup.Provider registries, Entry entry,
            List<ResourceLocation> materialIds) {
        List<Material> materials = new ArrayList<>(materialIds.size());
        for (ResourceLocation id : materialIds) {
            Optional<Material> material = lookupMaterial(registries, id);
            if (material.isEmpty()) {
                return Optional.empty();
            }
            materials.add(material.get());
        }

        Material head = materials.get(entry.headSlot());
        ToolStats.Stats stats = statsOf(entry, materials, head);

        ToolItem tool = entry.tool().get();
        ItemStack result = new ItemStack(tool);
        result.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                ToolMaterials.of(entry.constants().parts(), materialIds));
        result.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        // Trait ids come along as data so every later trait hook works off the stack alone
        // (ForgeweaveTraits: same id from two parts still counts once, as upstream 1.12 does).
        result.set(ForgeweaveDataComponents.TRAITS.get(), resolveTraits(entry, materials));
        // Mining tier, mining speed, and the durability bar all ride on vanilla components, so
        // vanilla's own block-breaking and rendering paths need no Forgeweave-specific handling.
        result.set(DataComponents.TOOL, tool.toolComponent(head, stats));
        result.set(DataComponents.MAX_DAMAGE, stats.durability());
        result.set(DataComponents.DAMAGE, 0);
        return Optional.of(result);
    }

    /**
     * {@link ToolConstants#compute}'s stored stat block plus the head material's own durability
     * trait. Upstream fires that trait step ({@code TinkerEvent.OnItemBuilding}) after the tool
     * class's {@code buildTagData}, which is exactly this order; {@code ToolStats#compute} applies
     * the same step for M1's three-material shape, and reproduces this one exactly for those three.
     */
    private static ToolStats.Stats statsOf(Entry entry, List<Material> materials, Material head) {
        ToolStats.Stats base = ToolConstants.compute(entry.constants(), materials);
        int durability = ForgeweaveTraits.headDurability(head.traits().forPart(PartItem.Kind.HEAD), base.durability());
        return new ToolStats.Stats(durability, base.miningSpeed(), base.attackDamage());
    }

    /**
     * Every part's traits, deduplicated, in the same head/extra/handle precedence M1 used. A tool
     * with more than one HEAD part contributes each of them, still deduplicated by id -- upstream
     * 1.12 counts a repeated trait once regardless of how many parts granted it.
     */
    private static List<ResourceLocation> resolveTraits(Entry entry, List<Material> materials) {
        List<ToolConstants.PartSlot> slots = entry.constants().parts();
        List<ResourceLocation> traits = new ArrayList<>();
        for (ToolConstants.Role role : ToolConstants.Role.values()) {
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i).role() == role) {
                    traits.addAll(materials.get(i).traits().forPart(kindOf(role)));
                }
            }
        }
        return traits.stream().distinct().toList();
    }

    private static PartItem.Kind kindOf(ToolConstants.Role role) {
        return switch (role) {
            case HEAD -> PartItem.Kind.HEAD;
            case EXTRA -> PartItem.Kind.EXTRA;
            case HANDLE -> PartItem.Kind.HANDLE;
        };
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
