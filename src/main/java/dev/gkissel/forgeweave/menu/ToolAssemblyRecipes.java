package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.Embossing;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Fortification;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.LauncherStats;
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
 *   <li><b>Repair</b> -- a damaged or Broken tool in the head slot, plus items belonging to one of
 *       its repair slots' materials in any of the five free slots (#434): any of that material's
 *       {@code crafting_items} at its own value, its shard, or its {@code repair_item} at one ingot
 *       (#461). CONTEXT.md puts repair at this station and makes the head material the one that
 *       determines the repair item.
 *   <li><b>Modifier application</b> (issue #105) -- a tool in the head slot plus
 *       {@code modifier.ModifierRecipe} reagents in any of the five free slots (#434), tried after
 *       repair so the two never fight over an item that is both.
 *   <li><b>Embossing</b> (issue #154; parity cost per issue #248) -- a tool in the head slot plus a
 *       donor tool part and an {@code modifier.EmbossingRecipe}'s reagent set across all five free
 *       slots, which is why the repair tab has five of them.
 *   <li><b>Fortification</b> (issue #271) -- a tool in the head slot plus a sharpening kit and a
 *       flint, which sets the tool's mining tier to the kit material's
 *       ({@code modifier.Fortification}). Tried after embossing and before modifier application; the
 *       flint half of its cost is a modifier recipe that the generic path deliberately skips.
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
     * exactly for them, except the hatchet's {@code flatAttackBonus} -- upstream
     * {@code Hatchet#buildTagData}'s {@code data.attack += 0.5f} (parity audit 2026-08-18 T65, issue
     * #496); the attack speed and damage potential are the ones {@code HatchetItem} already gives the
     * item, repeated here only so the entry reads as a complete tool. Package-visible, not
     * {@code private}: {@code ToolAssemblyRecipesTest} pins the assembled attack bonus against this
     * exact entry.
     */
    private static final ToolConstants.Entry PICKAXE = new ToolConstants.Entry("pickaxe", ToolConstants.Category.HARVEST,
            List.of(new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "pickaxe_head"),
                    new ToolConstants.PartSlot(ToolConstants.Role.EXTRA, "tool_binding"),
                    new ToolConstants.PartSlot(ToolConstants.Role.HANDLE, "tool_handle")),
            1.2f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    private static final ToolConstants.Entry SHOVEL = new ToolConstants.Entry("shovel", ToolConstants.Category.HARVEST,
            List.of(new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "shovel_head"),
                    new ToolConstants.PartSlot(ToolConstants.Role.EXTRA, "tool_binding"),
                    new ToolConstants.PartSlot(ToolConstants.Role.HANDLE, "tool_handle")),
            1.0f, 0.9f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    static final ToolConstants.Entry HATCHET = new ToolConstants.Entry("hatchet", ToolConstants.Category.HARVEST,
            List.of(new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "axe_head"),
                    new ToolConstants.PartSlot(ToolConstants.Role.EXTRA, "tool_binding"),
                    new ToolConstants.PartSlot(ToolConstants.Role.HANDLE, "tool_handle")),
            // flatAttackBonus 0.5f: upstream Hatchet#buildTagData's `data.attack += 0.5f`
            // (parity audit 2026-08-18 T65, issue #496) -- every other field here is still identity.
            1.1f, 1.1f, 1.0f, 0.5f, 1.0f, 1.0f, false, false);

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
            new Entry(ToolConstants.SCIMITAR, ForgeweaveItems.TOOL_SCIMITAR),
            // #160: handle, katana blade, hand guard -- ToolConstants#KATANA's own order.
            new Entry(ToolConstants.KATANA, ForgeweaveItems.TOOL_KATANA),
            // #158: the Tool Forge tier's cleaver -- tough tool rod, large sword blade, large plate,
            // tough tool rod again. The same part in two different roles (HANDLE and EXTRA) is why
            // slot matching here is positional rather than by part identity.
            new Entry(ToolConstants.CLEAVER, ForgeweaveItems.TOOL_CLEAVER),
            // #157's five Tool Forge-tier harvest tools, each in ToolConstants' own part order. The
            // hammer takes large_plate in two separate HEAD slots and the scythe takes tough_tool_rod
            // in both a HANDLE and a second HANDLE slot -- positional matching, again, with no case.
            new Entry(ToolConstants.HAMMER, ForgeweaveItems.TOOL_HAMMER),
            new Entry(ToolConstants.EXCAVATOR, ForgeweaveItems.TOOL_EXCAVATOR),
            new Entry(ToolConstants.LUMBERAXE, ForgeweaveItems.TOOL_LUMBERAXE),
            new Entry(ToolConstants.SCYTHE, ForgeweaveItems.TOOL_SCYTHE),
            new Entry(ToolConstants.VEIN_HAMMER, ForgeweaveItems.TOOL_VEIN_HAMMER),
            // M3.5 #394: the shortbow -- limb, limb, string, upstream ShortBow's PartMaterialType
            // order. A Tool Station tool (TinkerRegistry.registerToolCrafting(shortBow)), so not in
            // LARGE_TOOLS. The two LIMB slots feed both the melee stats (their HEAD block) and the
            // LAUNCHER_STATS component (their BOW block); the string is a durability multiplier only.
            new Entry(ToolConstants.SHORTBOW, ForgeweaveItems.TOOL_SHORTBOW),
            // M3.5 #395: the Tool Forge tier's two bows, each in upstream's own PartMaterialType
            // order. LongBow -- limb, limb, large plate, string. CrossBow -- tough tool rod, limb,
            // tough binding, string, where the rod slot is ToolConstants.Role#CROSSBOW_BODY because
            // upstream's PartMaterialType.crossbow names both HANDLE and EXTRA and its buildTagData
            // spends the material's blocks in both places. Both are in LARGE_TOOLS
            // (ForgeweaveItemTagsProvider), which is the whole of the Tool Forge gate.
            new Entry(ToolConstants.LONGBOW, ForgeweaveItems.TOOL_LONGBOW),
            new Entry(ToolConstants.CROSSBOW, ForgeweaveItems.TOOL_CROSSBOW));

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
     *
     * <p>Issue #336 makes it the <em>only</em> roster split: {@link ToolStationTabs#visible} reads it
     * too, so the tabs a block draws and the assemblies it resolves can never disagree.
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
     * What one sharpening kit is worth as a repair item, in ordinary repair items (parity audit T32,
     * issue #463). Upstream {@code ToolCore#repairCustom} restores {@code headDurability *
     * sharpeningKit.getCost() / Material.VALUE_Ingot}, and {@code SharpeningKit}'s constructor prices
     * the part at {@code Material.VALUE_Shard * 4}, i.e. exactly two ingots -- so the ratio is 2.
     * Read off {@link PartBuilderRecipes}' own cost table rather than written out, so a change to the
     * kit's crafting cost keeps moving its repair value with it, the way upstream's division does.
     */
    private static final float SHARPENING_KIT_REPAIR_ITEMS =
            PartBuilderRecipes.HEAD_COST / (float) PartBuilderRecipes.INGOT_VALUE;

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

    /**
     * Whether {@code stack} repairs the tool currently in the head slot -- any of its repair parts'
     * materials, not only its head's (issue #462), and either as that material's ordinary repair item
     * or as a sharpening kit of it (issue #463).
     */
    static boolean isRepairItemFor(HolderLookup.Provider registries, ItemStack headSlotStack, ItemStack stack) {
        return repairMaterialsOf(registries, headSlotStack).stream()
                .anyMatch(repair -> repairUnitValue(registries, repair, stack) > 0
                        || isSharpeningKitOf(stack, repair.materialId()));
    }

    /**
     * How many value units one item of {@code stack} pays into a repair of {@code repair}'s material,
     * or {@code 0} if it is not a repair item for it at all (parity audit T30, issue #461).
     *
     * <p>Upstream 1.12 keeps one item-to-material table per material -- {@code Material extends
     * RecipeMatchRegistry} -- and {@code TinkersItem#calculateRepairAmount} matches a repair against
     * exactly that table, so every Part Builder crafting item repairs too, each worth its own
     * registered value ({@code match.amount / VALUE_Ingot} ingot-equivalents). That table is
     * Forgeweave's {@code crafting_items} plus the material's shard, which is what
     * {@link PartBuilderRecipes#unitValueAgainst} already reads.
     *
     * <p>The material's own {@code repair_item} stays a floor at one ingot: Forgeweave's is a
     * separate {@code Ingredient} and is in a few places wider than the crafting-item list (stone's
     * {@code #minecraft:stone_tool_materials} covers blackstone and cobbled deepslate, which its
     * {@code crafting_items} do not), and nothing an existing world could already repair with should
     * stop working over a parity fix.
     */
    private static int repairUnitValue(HolderLookup.Provider registries, RepairMaterial repair, ItemStack stack) {
        int value = PartBuilderRecipes.unitValueAgainst(registries, repair.materialId(), stack);
        return value > 0 ? value
                : (repair.material().repairItem().test(stack) ? PartBuilderRecipes.INGOT_VALUE : 0);
    }

    /**
     * Upstream {@code ToolCore#repairCustom}'s material check: the kit has to carry the very material
     * of the repair slot it is being spent on, or it is not a repair item at all.
     */
    private static boolean isSharpeningKitOf(ItemStack stack, ResourceLocation materialId) {
        return stack.is(ForgeweaveItems.PART_SHARPENING_KIT.get())
                && materialId.equals(stack.get(ForgeweaveDataComponents.MATERIAL.get()));
    }

    /**
     * Repairs {@code tool} with {@code items} outside the station, for the crafting-grid recipe
     * ({@link dev.gkissel.forgeweave.recipe.SharpeningKitRepairRecipe}, upstream
     * {@code tools/common/RepairRecipe}, parity audit T32/issue #463). Every item has to be spent --
     * upstream's "check if all items were used" bail, which here also keeps the grid from swallowing
     * kits the tool had no damage left to use.
     *
     * @return the repaired tool, or {@link ItemStack#EMPTY} if this is no repair
     */
    public static ItemStack repairOutsideStation(HolderLookup.Provider registries, ItemStack tool,
            List<ItemStack> items) {
        return resolveRepair(registries, tool, items, false)
                .filter(result -> result.slotsUsed().stream().skip(1).allMatch(used -> used == 1))
                .map(Result::output)
                .orElse(ItemStack.EMPTY);
    }

    /**
     * Resolves what the station should currently produce, or empty if the slots don't form any
     * of the recipes.
     *
     * @param freeSlots every input slot except the first, in slot order. Assembly reads as many as
     *     the tool has parts; repair, modifier application, embossing, fortification and part
     *     exchange read all five -- upstream {@code ContainerToolStation#getInputs} (parity audit
     *     T2, issue #434; before it repair and modifiers read only the first two).
     * @param forge whether the station is a Tool Forge (issue #152): gates large-tool assembly and
     *     applies the repair discount. Every other outcome is identical at both blocks.
     */
    static Optional<Result> resolve(HolderLookup.Provider registries, ItemStack headStack, List<ItemStack> freeSlots,
            boolean forge) {
        if (!(headStack.getItem() instanceof ToolItem)) {
            List<ItemStack> inputs = new ArrayList<>(freeSlots.size() + 1);
            inputs.add(headStack);
            inputs.addAll(freeSlots);
            return resolveAssembly(registries, inputs, forge);
        }
        Optional<Result> repair = resolveRepair(registries, headStack, freeSlots, forge);
        if (repair.isPresent()) {
            return repair;
        }
        // Part exchange (issue #264) sits exactly where upstream's ContainerToolStation puts it:
        // after repair, before modify/emboss. It engages only when every loaded free slot is a tool
        // part, so an embossing loadout (donor part plus reagents) falls through to the resolvers
        // below untouched -- upstream's tryReplaceToolParts refuses mixed inputs the same way.
        Optional<Exchange> exchange = resolveExchange(registries, headStack, freeSlots, forge);
        if (exchange.isPresent()) {
            Exchange swap = exchange.get();
            return swap.output().isEmpty()
                    ? Optional.empty()
                    : Optional.of(new Result(swap.output(), swap.slotsUsed()));
        }
        // Embossing before modifier application: a donor tool part is nothing any modifier recipe
        // accepts, so the two can never both match, and trying the more specific one first keeps the
        // modifier path from having to know embossing exists.
        Optional<Result> embossing = resolveEmbossing(registries, headStack, freeSlots);
        if (embossing.isPresent()) {
            return embossing;
        }
        // Fortification (issue #271) before generic modifier application, for the same reason
        // embossing goes before it: a sharpening kit is nothing any modifier recipe accepts, so the
        // two can never both match, and resolving the more specific one first keeps the modifier path
        // from having to know fortification exists. It also has to come first because the flint half
        // of the cost *is* a modifier recipe's reagent -- ModifierApplication#recipeFor skips that
        // one recipe so a lone flint is never applied generically.
        Optional<Result> fortification = resolveFortification(registries, headStack, freeSlots);
        return fortification.isPresent()
                ? fortification
                : resolveModifier(registries, headStack, freeSlots);
    }

    /**
     * Fortification (issue #271): a tool in the first slot, a sharpening kit and a flint across the
     * free ones. Every matched slot gives up exactly one item -- upstream {@code ModFortify}'s
     * {@code RecipeMatch.ItemCombination(1, kit, flint)} -- and a rejected fortification produces no
     * output here, only the message {@link ToolStationMenu#rejection} shows.
     */
    private static Optional<Result> resolveFortification(HolderLookup.Provider registries, ItemStack toolStack,
            List<ItemStack> freeSlots) {
        // One of everything, exactly as resolveEmbossing does: Fortification#resolve only returns an
        // outcome once the kit and every reagent are loaded, one per slot.
        int[] oneOfEach = new int[1 + freeSlots.size()];
        Arrays.fill(oneOfEach, 1);
        return Fortification.resolve(registries, toolStack, freeSlots)
                .filter(outcome -> !outcome.output().isEmpty())
                .map(outcome -> Result.of(outcome.output(), oneOfEach));
    }

    /**
     * Embossing (issue #154, ADR-0004): a tool in the first slot, a donor part and the reagent set
     * spread across the five free ones. Every matched slot gives up exactly one item -- upstream's
     * {@code RecipeMatch.ItemCombination(1, ...)} -- and a rejected embossment produces no output
     * here, only the message {@link ToolStationMenu#rejection} shows.
     */
    private static Optional<Result> resolveEmbossing(HolderLookup.Provider registries, ItemStack toolStack,
            List<ItemStack> freeSlots) {
        // One of everything: a resolved embossment always fills the tool slot plus every free slot
        // (Embossing#matchesAll requires exactly one slot per reagent beside the donor).
        int[] oneOfEach = new int[1 + freeSlots.size()];
        Arrays.fill(oneOfEach, 1);
        return Embossing.resolve(registries, toolStack, freeSlots)
                .filter(outcome -> !outcome.output().isEmpty())
                .map(outcome -> Result.of(outcome.output(), oneOfEach));
    }

    /**
     * A part exchange's outcome (issue #264). Same contract as {@code Embossing.Outcome}: exactly one
     * of {@code output} and {@code rejection} is meaningful, and {@code slotsUsed} is indexed like
     * {@link Result#slotsUsed} (slot 0 is the tool).
     */
    public record Exchange(ItemStack output, List<Integer> slotsUsed, @Nullable Component rejection) {

        static Exchange rejected(String key, Object... args) {
            return new Exchange(ItemStack.EMPTY, List.of(), Component.translatable(key, args));
        }
    }

    /**
     * Part exchange (issue #264): an assembled tool in the head slot plus replacement part(s) in the
     * free slots swap those parts in place. A port of upstream 1.12's
     * {@code ToolBuilder#tryReplaceToolParts} + {@code ToolBuilder#rebuildTool} (the pinned commit),
     * whose derived semantics are:
     *
     * <ul>
     *   <li><b>Engages only on an all-parts loadout.</b> Upstream returns empty the moment any
     *       non-empty input is not a tool part; a mixed loadout (an embossing donor plus reagents,
     *       say) is someone else's recipe.
     *   <li><b>Assignment is positional, by part shape, and refuses a same-material swap.</b>
     *       Upstream's slot scan requires {@code pmt.isValid(part)} (the part item the tool's slot
     *       takes) and a material different from the one already in that slot; a tool with the same
     *       part in several slots fills them input-order against slot-order, including upstream's
     *       later-slot preference quirk (the {@code i <= j} early break). A part that fits nowhere
     *       refuses the whole exchange -- upstream silently, here with a message per the station's
     *       standing explain-yourself rule.
     *   <li><b>The damage <em>value</em> carries over</b> against the new maximum: upstream's output
     *       is {@code toolStack.copy()} and {@code rebuildTool} never touches damage. If the damage
     *       exceeds the new maximum the exchange is refused ({@code gui.error.not_enough_durability}:
     *       "Not enough durability to replace parts! %d more durability required.").
     *   <li><b>Stats and traits are recomputed from the new material set</b> ({@code buildTag} +
     *       {@code addMaterialTraits}), and whatever the old tool carried beyond its own base --
     *       an embossment's donor traits -- is re-appended, mirroring how {@code rebuildTool}
     *       re-applies the modifier list (which is where upstream's embossment traits come from).
     *   <li><b>Modifiers survive untouched as {@code id + level}</b>; their baked effects are
     *       re-applied on top of the fresh base ({@link ModifierApplication#rebake}). Upstream also
     *       re-checks every modifier still {@code canApply} against the new materials; no Forgeweave
     *       modifier's applicability depends on materials, so that pass has nothing to do here.
     *   <li><b>Rename, repair count and the Broken flag ride the copy.</b> Upstream keeps the display
     *       name and repair count outside the rebuilt tool tag (the extra tag) and explicitly
     *       re-sets {@code Broken}.
     *   <li><b>Silk touch is re-derived</b>: it is the one enchantment grant that can depend on the
     *       material set (#228 squeaky's trait grant), so it is present on the output iff the new
     *       trait set or the silky modifier grants it -- upstream removes the {@code ench} tag and
     *       lets traits/modifiers re-add it. Every other granted enchantment (wind burst, luck's
     *       Fortune/Looting) depends only on the modifier list, which an exchange never changes, so
     *       those carry over on the copy.
     *   <li><b>Large tools exchange at the Tool Forge only</b> -- a Forgeweave decision matching the
     *       assembly gate (issue #152); upstream's stations have no such split for exchanges.
     * </ul>
     */
    public static Optional<Exchange> resolveExchange(HolderLookup.Provider registries, ItemStack toolStack,
            List<ItemStack> freeSlots, boolean forge) {
        if (!(toolStack.getItem() instanceof ToolItem)) {
            return Optional.empty();
        }
        Optional<Entry> found = entryFor(toolStack);
        ToolMaterials materials = toolStack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (found.isEmpty() || materials == null) {
            return Optional.empty();
        }
        Entry entry = found.get();
        List<ResourceLocation> ids = materials.parts();
        if (ids.size() != entry.slotCount()) {
            return Optional.empty(); // a materials list this entry's shape can't explain
        }

        List<Integer> loaded = new ArrayList<>();
        for (int i = 0; i < freeSlots.size(); i++) {
            ItemStack stack = freeSlots.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof PartItem part) || part.kind() == PartItem.Kind.NONE
                    || stack.get(ForgeweaveDataComponents.MATERIAL.get()) == null) {
                return Optional.empty(); // mixed loadout: not an exchange attempt
            }
            loaded.add(i);
        }
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        if (!forge && isLargeTool(entry)) {
            return Optional.of(Exchange.rejected("gui.forgeweave.exchange.needs_forge"));
        }

        // Upstream's assignment loop: each input part claims the first shape-matching slot whose
        // material differs and which no earlier input claimed -- keeping the later-slot preference
        // quirk (candidate keeps updating until a slot at or past the input's own index breaks out).
        Map<Integer, Integer> assigned = new LinkedHashMap<>();
        for (int input : loaded) {
            ItemStack part = freeSlots.get(input);
            ResourceLocation partMaterial = part.get(ForgeweaveDataComponents.MATERIAL.get());
            int candidate = -1;
            boolean fitsShape = false;
            for (int j = 0; j < entry.slotCount(); j++) {
                if (!part.is(entry.part(j))) {
                    continue;
                }
                fitsShape = true;
                if (partMaterial.equals(ids.get(j)) || assigned.containsValue(j)) {
                    continue;
                }
                candidate = j;
                if (input <= j) {
                    break;
                }
            }
            if (candidate < 0) {
                return Optional.of(Exchange.rejected(fitsShape
                        ? "gui.forgeweave.exchange.same_material"
                        : "gui.forgeweave.exchange.wrong_part"));
            }
            assigned.put(input, candidate);
        }

        List<ResourceLocation> newIds = new ArrayList<>(ids);
        assigned.forEach((input, slot) ->
                newIds.set(slot, freeSlots.get(input).get(ForgeweaveDataComponents.MATERIAL.get())));
        Optional<ItemStack> rebuilt = assemble(registries, entry, newIds);
        Optional<ItemStack> oldBase = assemble(registries, entry, ids);
        if (rebuilt.isEmpty() || oldBase.isEmpty()) {
            return Optional.empty(); // a material this pack no longer defines
        }
        ItemStack fresh = rebuilt.get();

        // Upstream's output = toolStack.copy(): everything not explicitly rebuilt below -- modifiers,
        // enchantments, rename, repair count, Broken, damage, alien's growth component -- rides along.
        ItemStack result = toolStack.copy();
        result.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                fresh.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()));
        result.set(ForgeweaveDataComponents.TOOL_STATS.get(), fresh.get(ForgeweaveDataComponents.TOOL_STATS.get()));
        // #593: the swapped-in material changes the mean, so this is rebuilt from the new part set
        // exactly like the stat block above rather than riding along on the copy.
        result.set(ForgeweaveDataComponents.ENCHANTABILITY.get(),
                fresh.get(ForgeweaveDataComponents.ENCHANTABILITY.get()));
        result.set(ForgeweaveDataComponents.TRAITS.get(), mergedTraits(fresh, oldBase.get(), toolStack));
        result.set(DataComponents.TOOL, fresh.get(DataComponents.TOOL));
        result.set(DataComponents.MAX_DAMAGE, fresh.get(DataComponents.MAX_DAMAGE));
        ModifierApplication.rebake(result);
        retuneSilkTouch(registries, result);

        // #293, upstream rebuildTool's own last gate (its `freeModifiers -= getBaseModifiersUsed`,
        // then `if (freeModifiers < 0) throw gui.error.not_enough_modifiers`): the slot budget is
        // re-derived from the new material set, and a swap can shrink it -- paper's writable traits
        // are the part-granted slots -- leaving the modifiers already on the tool over budget.
        // ForgeweaveModifiers#freeSlots is that same subtraction (budget minus #occupiedSlots),
        // asked of the would-be tool. Checked before the durability gate, upstream's own order.
        int missingSlots = -ForgeweaveModifiers.freeSlots(result);
        if (missingSlots > 0) {
            return Optional.of(Exchange.rejected("gui.forgeweave.exchange.not_enough_slots", missingSlots));
        }

        // Raw component read: ItemStack#getDamageValue clamps to the (new) maximum, which is exactly
        // the overflow this check exists to catch.
        int missing = result.getOrDefault(DataComponents.DAMAGE, 0) - result.getMaxDamage();
        if (missing > 0) {
            // Upstream's gui.error.not_enough_durability: the damage value survives the swap, so a
            // tool more worn than the new part set can hold is refused rather than handed out broken.
            return Optional.of(Exchange.rejected("gui.forgeweave.exchange.not_enough_durability", missing));
        }

        int[] used = new int[1 + freeSlots.size()];
        used[0] = 1;
        for (int input : assigned.keySet()) {
            used[1 + input] = 1;
        }
        return Optional.of(new Exchange(result, Arrays.stream(used).boxed().toList(), null));
    }

    /**
     * The exchanged tool's trait list: the fresh base derived from the new material set, plus
     * whatever the old tool carried beyond its own base -- which today is exactly an embossment's
     * donor traits ({@code Embossing#embossed} is the only writer that appends). Computed as a diff
     * against the old base rather than re-derived from the embossment id, because the id names only
     * the donor material, not the donor part kind the trait scope came from.
     */
    private static List<ResourceLocation> mergedTraits(ItemStack fresh, ItemStack oldBase, ItemStack oldTool) {
        List<ResourceLocation> traits =
                new ArrayList<>(fresh.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of()));
        List<ResourceLocation> base = oldBase.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
        List<ResourceLocation> carried = oldTool.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
        for (ResourceLocation trait : carried) {
            if (!base.contains(trait) && !traits.contains(trait)) {
                traits.add(trait);
            }
        }
        return List.copyOf(traits);
    }

    /**
     * Recomputes the one material-dependent enchantment grant after a part exchange: silk touch is
     * present iff the new trait set (#228 squeaky) or the silky modifier (#107) grants it. See
     * {@link #resolveExchange}'s javadoc for why the other grants need no recompute.
     */
    private static void retuneSilkTouch(HolderLookup.Provider registries, ItemStack stack) {
        Optional<Holder.Reference<Enchantment>> silk = registries.lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(Enchantments.SILK_TOUCH));
        if (silk.isEmpty()) {
            return;
        }
        if (ForgeweaveTraits.grantsSilkTouch(stack) || ForgeweaveModifiers.grantsSilkTouch(stack)) {
            stack.enchant(silk.get(), 1); // enchant upgrades, so an existing level-1 stays level-1
            return;
        }
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.getLevel(silk.get()) > 0) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
            mutable.set(silk.get(), 0);
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
    }

    /**
     * Whether any part loaded into the input slots belongs only to large tools -- i.e. whether the
     * player is trying to build one somewhere that cannot. Public so {@link ToolStationMenu#rejection}
     * can say so in the info panel and a GameTest can assert on the classification itself rather than
     * only on its effect.
     *
     * <p>Asks about every loaded slot rather than about the first one: since issue #155 the head is
     * not always slot 0 (the hammer's is slot 1, behind its tough rod), so "is the head slot's part a
     * large tool's head" would answer no for most of the roster. A part that also belongs to a
     * small tool -- a plain tool handle, say -- says nothing either way, which is why this looks for a
     * part <em>no</em> small tool uses.
     */
    public static boolean isLargeToolHead(List<ItemStack> inputs) {
        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) {
                continue;
            }
            List<Entry> using = ENTRIES.stream()
                    .filter(entry -> entry.parts().stream().anyMatch(stack::is))
                    .toList();
            if (!using.isEmpty() && using.stream().allMatch(ToolAssemblyRecipes::isLargeTool)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this tool can only be assembled at the Tool Forge (issue #152's {@link #LARGE_TOOLS}). */
    public static boolean isLargeTool(Entry entry) {
        return entry.tool().get().builtInRegistryHolder().is(LARGE_TOOLS);
    }

    /**
     * Modifier application (issue #105, ADR-0004) rides the same repair-tab slots: a tool in the
     * first one, reagents in any of the five free ones (#434). Repair is tried first, so an item that
     * is both a repair item and some modifier's reagent still repairs -- and a rejected application
     * (slots full, level cap) produces no output here, only the message the screen reads from
     * {@link ToolStationMenu#rejection}.
     */
    private static Optional<Result> resolveModifier(HolderLookup.Provider registries, ItemStack toolStack,
            List<ItemStack> freeSlots) {
        return ModifierApplication.resolve(registries, toolStack, freeSlots)
                .filter(outcome -> !outcome.output().isEmpty())
                .map(outcome -> {
                    List<Integer> slotsUsed = new ArrayList<>(1 + freeSlots.size());
                    slotsUsed.add(1);
                    slotsUsed.addAll(outcome.used());
                    return new Result(grantEnchantments(registries, outcome.output()), slotsUsed);
                });
    }

    /**
     * Silky (issue #107) grants vanilla Silk Touch the moment it's applied rather than through the
     * enchanting table (CONTEXT.md: Forgeweave tools aren't enchantable there by default), upstream
     * {@code ModSilktouch#applyEffect}'s {@code ToolBuilder#addEnchantment}. This is a modifier
     * effect that lands on a vanilla component and needs registry access to do it, which is exactly
     * why {@link ModifierApplication} -- deliberately registry-free, see its own javadoc -- doesn't do
     * it itself; this caller already threads {@code registries} through for everything else here.
     *
     * <p>Issue #223's wind burst is the general form of the same idea: any modifier reporting
     * {@link Modifier#grantedEnchantment} gets that enchantment upgraded onto the stack the same way,
     * keyed off whatever it names rather than silky's hardcoded Silk Touch. {@code enchant} upgrades
     * rather than overwrites (vanilla's own {@code ItemEnchantments.Mutable#upgrade}), so re-applying
     * a levelled modifier can never downgrade what an earlier application already granted.
     */
    private static ItemStack grantEnchantments(HolderLookup.Provider registries, ItemStack stack) {
        if (ForgeweaveModifiers.grantsSilkTouch(stack)) {
            registries.lookup(Registries.ENCHANTMENT)
                    .flatMap(lookup -> lookup.get(Enchantments.SILK_TOUCH))
                    .ifPresent(silkTouch -> stack.enchant(silkTouch, 1));
        }
        HolderGetter<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (ModifierEntry entry : ForgeweaveModifiers.of(stack)) {
            Modifier modifier = ForgeweaveModifiers.get(entry.id());
            if (modifier == null) {
                continue;
            }
            modifier.grantedEnchantment(entry.level())
                    .ifPresent(grant -> stack.enchant(enchantments.getOrThrow(grant.enchantment()), grant.level()));
        }
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
        if (!ContentFamilies.toolEnabled(entry)) {
            // Content-family toggles ticket: the family is off, so this is unassemblable at either
            // block. Same shape as the large-tool refusal above -- no output, and
            // ToolStationMenu#rejection is what says why.
            return Optional.empty();
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

        List<Material> heads = headMaterials(entry, materials);
        ToolStats.Stats stats = statsOf(entry, materials, heads);

        ToolItem tool = entry.tool().get();
        ItemStack result = new ItemStack(tool);
        result.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                ToolMaterials.of(entry.constants().parts(), materialIds));
        result.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        // #593: what the vanilla enchanting table reads while allowVanillaEnchanting is on, averaged
        // over every part's material here because ToolItem#getEnchantmentValue gets no registry.
        result.set(ForgeweaveDataComponents.ENCHANTABILITY.get(), ToolStats.averageEnchantability(materials));
        // M3.5 #394: a bow's ranged half (upstream ProjectileLauncherNBT#limb) -- present only for
        // a tool with LIMB slots, so every other tool's component set is exactly what it was.
        LauncherStats.of(entry.constants(), materials)
                .ifPresent(launcher -> result.set(ForgeweaveDataComponents.LAUNCHER_STATS.get(), launcher));
        // Trait ids come along as data so every later trait hook works off the stack alone
        // (ForgeweaveTraits: same id from two parts still counts once, as upstream 1.12 does).
        result.set(ForgeweaveDataComponents.TRAITS.get(), resolveTraits(entry, materials));
        // #228 squeaky: a trait-granted always-on Silk Touch lands at assembly exactly the way
        // silky's modifier grant lands at application (grantEnchantments below) -- this is the one
        // assembly call site with the registry access an enchantment holder needs.
        if (ForgeweaveTraits.grantsSilkTouch(result)) {
            registries.lookup(Registries.ENCHANTMENT)
                    .flatMap(lookup -> lookup.get(Enchantments.SILK_TOUCH))
                    .ifPresent(silkTouch -> result.enchant(silkTouch, 1));
        }
        // Mining tier, mining speed, and the durability bar all ride on vanilla components, so
        // vanilla's own block-breaking and rendering paths need no Forgeweave-specific handling.
        result.set(DataComponents.TOOL, tool.toolComponent(highestTierHead(heads), stats));
        result.set(DataComponents.MAX_DAMAGE, stats.durability());
        result.set(DataComponents.DAMAGE, 0);
        return Optional.of(result);
    }

    /**
     * Every HEAD-slot material, in slot order -- what upstream 1.12's {@code ToolNBT#head} receives as
     * its varargs (issue #294). One entry for the 15 single-head tools, two to three for the hammer,
     * cleaver, battleaxe, excavator, lumber axe, mattock and vein hammer -- and a bow's two limbs
     * (M3.5 #394: {@code ShortBow#buildTagData} hands the limbs' HEAD block to {@code data.head}).
     */
    private static List<Material> headMaterials(Entry entry, List<Material> materials) {
        List<ToolConstants.PartSlot> slots = entry.constants().parts();
        List<Material> heads = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ToolConstants.Role role = slots.get(i).role();
            if (role == ToolConstants.Role.HEAD || role == ToolConstants.Role.LIMB) {
                heads.add(materials.get(i));
            }
        }
        if (heads.isEmpty()) {
            throw new IllegalStateException(entry.constants().id() + " has no head part");
        }
        return heads;
    }

    /**
     * The head material whose tier gates the finished tool: the <b>highest</b> one, never the
     * first HEAD slot the station used to read this off (issue #294). Upstream {@code ToolNBT#head}
     * aggregates its
     * heads by averaging durability/attack/speed but taking {@code harvestLevel} as the max ("use
     * highest harvestlevel"), so a hammer with a cobalt large plate mines at cobalt's tier however
     * cheap its hammer head is. {@code Material#incorrectForTool} is Forgeweave's vanilla-tag stand-in
     * for that number (CONTEXT.md: no numeric harvest levels), ordered by
     * {@link ForgeweaveModifiers#tierIndexOf(TagKey)}; a tag off that ladder scores -1 and so only
     * wins if every head is off it, which keeps the first head as the fallback answer.
     */
    private static Material highestTierHead(List<Material> heads) {
        Material best = heads.get(0);
        int bestTier = ForgeweaveModifiers.tierIndexOf(best.incorrectForTool());
        for (Material head : heads) {
            int tier = ForgeweaveModifiers.tierIndexOf(head.incorrectForTool());
            if (tier > bestTier) {
                best = head;
                bestTier = tier;
            }
        }
        return best;
    }

    /**
     * {@link ToolConstants#compute}'s stored stat block plus the head materials' own durability
     * traits. Upstream fires that trait step ({@code TinkerEvent.OnItemBuilding}) after the tool
     * class's {@code buildTagData}, which is exactly this order; {@code ToolStats#compute} applies
     * the same step for M1's three-material shape, and reproduces this one exactly for those three.
     *
     * <p>Issue #294's second question, decided here: head-scoped trait effects fold across
     * <em>every</em> HEAD part, not just the designated slot. That is what upstream does --
     * {@code TinkersItem#addMaterialTraits} adds each part's applicable traits to the tool, then the
     * one {@code OnItemBuilding} event fires over the whole trait set -- and {@link #resolveTraits}
     * already gives the assembled stack traits from all heads, so reading only the first head's here
     * would have made a hammer show a trait on its tooltip that its durability had never paid for.
     * De-duplicated, since upstream counts a trait granted by two parts once ({@code
     * ToolBuilder#addTrait} skips one already present).
     */
    private static ToolStats.Stats statsOf(Entry entry, List<Material> materials, List<Material> heads) {
        ToolStats.Stats base = ToolConstants.compute(entry.constants(), materials);
        List<ResourceLocation> headTraits = heads.stream()
                .flatMap(head -> head.traits().forPart(PartItem.Kind.HEAD).stream())
                .distinct()
                .toList();
        int durability = ForgeweaveTraits.headDurability(headTraits, base.durability());
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
                    for (PartItem.Kind kind : kindsOf(role)) {
                        traits.addAll(materials.get(i).traits().forPart(kind));
                    }
                }
            }
        }
        return traits.stream().distinct().toList();
    }

    /**
     * The stat blocks -- and so the trait scopes -- a slot of {@code role} reads. One each, except a
     * limb (M3.5 #394) and the crossbow's body (#395): upstream's {@code PartMaterialType.bow} names
     * both {@code BOW} and {@code HEAD} and its {@code PartMaterialType.crossbow} both {@code HANDLE}
     * and {@code EXTRA}, so those slots grant their material's traits from either scope.
     */
    private static List<PartItem.Kind> kindsOf(ToolConstants.Role role) {
        return switch (role) {
            case HEAD -> List.of(PartItem.Kind.HEAD);
            case EXTRA -> List.of(PartItem.Kind.EXTRA);
            case HANDLE -> List.of(PartItem.Kind.HANDLE);
            case LIMB -> List.of(PartItem.Kind.BOW, PartItem.Kind.HEAD);
            case BOWSTRING -> List.of(PartItem.Kind.BOWSTRING);
            // #395: PartMaterialType.crossbow names HANDLE and EXTRA, so the body grants both scopes'.
            case CROSSBOW_BODY -> List.of(PartItem.Kind.HANDLE, PartItem.Kind.EXTRA);
        };
    }

    /**
     * Repairs the tool in the head slot with as many rounds of matching items as it takes (or as many
     * as are there), pooled across all five free slots and spent lowest slot first -- upstream
     * {@code ContainerToolStation#getInputs} feeds {@code TinkersItem#repair} every free slot and
     * {@code Material#matches} sums the repair item across them (parity audit T2, issue #434). A
     * free slot holding anything that is not a repair item makes this not a repair at all
     * (upstream's "check if all items were used" bail, {@code TinkersItem.java:325-331}), so the
     * loadout falls through to the modifier path and its explained refusal. Every other component
     * -- materials, stats, the vanilla tool component -- rides along untouched on the copy, so a
     * repaired tool is the same tool.
     *
     * <h2>Multi-part repair (issue #462, parity audit T31)</h2>
     *
     * <p>A tool is repairable through every slot its {@link ToolConstants.Entry#repairSlots()} names,
     * not just its head: a hammer takes its hammer-head material <em>or</em> either large plate's, a
     * scythe takes its head's or its tough binding's, a shortbow takes either limb's. One
     * <em>round</em> of repair pays one item of each distinct repair material that is actually
     * present, weights each by that slot's {@code repairModifier} (hammer head 2.5x, its plates 1.5x,
     * rapier blade 0.8x...) and, per upstream {@code TinkersItem#calculateRepairAmount}, adds
     * {@code 1/9} for every distinct material past the first. Upstream's own de-duplication rule is
     * kept: when two repair slots hold the same material only the first is counted, so an all-cobalt
     * hammer repairs at 2.5x once, not 2.5x + 1.5x + 1.5x with a triple-material bonus.
     */
    private static Optional<Result> resolveRepair(HolderLookup.Provider registries, ItemStack toolStack,
            List<ItemStack> freeSlots, boolean forge) {
        int damage = toolStack.getDamageValue();
        if (damage <= 0) {
            return Optional.empty(); // undamaged and unbroken: nothing to repair (upstream 1.12 does the same)
        }
        List<RepairMaterial> repairMaterials = repairMaterialsOf(registries, toolStack);
        if (repairMaterials.isEmpty()) {
            return Optional.empty();
        }

        // Which repair material each loaded free slot pays into, and whether it pays as a sharpening
        // kit (issue #463) or as the material's ordinary repair item. Upstream matches its repair
        // parts in order and runs both branches per part, so the first material that accepts the
        // stack in either branch claims it.
        int[] paysInto = new int[freeSlots.size()];
        boolean[] paysAsKit = new boolean[freeSlots.size()];
        int[] remaining = new int[freeSlots.size()];
        // Ingot-equivalents one item of this slot is worth to the material it pays into (issue #461).
        float[] unitWorth = new float[freeSlots.size()];
        boolean any = false;
        for (int i = 0; i < freeSlots.size(); i++) {
            ItemStack stack = freeSlots.get(i);
            paysInto[i] = -1;
            if (stack.isEmpty()) {
                continue;
            }
            for (int m = 0; m < repairMaterials.size(); m++) {
                if (isSharpeningKitOf(stack, repairMaterials.get(m).materialId())) {
                    paysInto[i] = m;
                    paysAsKit[i] = true;
                    break;
                }
                int value = repairUnitValue(registries, repairMaterials.get(m), stack);
                if (value > 0) {
                    paysInto[i] = m;
                    unitWorth[i] = value / (float) PartBuilderRecipes.INGOT_VALUE;
                    break;
                }
            }
            if (paysInto[i] < 0) {
                return Optional.empty(); // upstream: an untouched input means this is no repair
            }
            remaining[i] = stack.getCount();
            any = true;
        }
        if (!any) {
            return Optional.empty();
        }

        int maxDamage = toolStack.getMaxDamage();
        ToolStats.Stats baseStats = toolStack.get(ForgeweaveDataComponents.TOOL_STATS.get());
        int baseDurability = baseStats != null ? baseStats.durability() : maxDamage;
        int occupiedModifierSlots = ForgeweaveModifiers.occupiedSlots(toolStack);
        int repairCount = toolStack.getOrDefault(ForgeweaveDataComponents.REPAIR_COUNT.get(), 0);
        int rounds = 0;
        int[] spend = new int[freeSlots.size()];
        while (damage > 0) {
            // One round: the lowest slot still holding each distinct repair material, or none of it,
            // and independently the lowest slot still holding a sharpening kit of it -- upstream runs
            // repairCustom and Material#matches side by side within one pass over the repair parts,
            // so a kit and an ingot of the same material are spent together, not in two rounds.
            int[] payingSlot = new int[repairMaterials.size()];
            int[] payingKitSlot = new int[repairMaterials.size()];
            Arrays.fill(payingSlot, -1);
            Arrays.fill(payingKitSlot, -1);
            float weighted = 0f;
            int matched = 0;
            for (int i = 0; i < freeSlots.size(); i++) {
                int m = paysInto[i];
                if (m < 0 || remaining[i] == 0) {
                    continue;
                }
                if (paysAsKit[i]) {
                    if (payingKitSlot[m] >= 0) {
                        continue;
                    }
                    payingKitSlot[m] = i;
                    weighted += repairMaterials.get(m).weightedHeadDurability() * SHARPENING_KIT_REPAIR_ITEMS;
                    // Deliberately NOT counted in `matched`: upstream only ever adds to its
                    // `materialsMatched` set from the Material#matches branch, so a kit-only repair
                    // goes through the 1 + (matched - 1) / 9 term with a count of zero and lands at
                    // 8/9 of the kit's face value. Quirk, but it is upstream's arithmetic.
                    continue;
                }
                if (payingSlot[m] >= 0) {
                    continue;
                }
                payingSlot[m] = i;
                weighted += repairMaterials.get(m).weightedHeadDurability() * unitWorth[i];
                matched++;
            }
            int amount = ToolRepair.repairAmount(weighted, matched);
            if (amount <= 0) {
                break; // nothing left to repair with (upstream's own do-while bail)
            }
            int increment = repairIncrement(amount, baseDurability, maxDamage, repairCount + rounds,
                    occupiedModifierSlots, forge);
            // Traits get to top the repair up (upstream 1.12 fires ITrait#onToolHeal on every heal).
            damage -= increment + ForgeweaveTraits.repairBonus(toolStack, increment);
            for (int[] paid : new int[][] {payingSlot, payingKitSlot}) {
                for (int slot : paid) {
                    if (slot >= 0) {
                        remaining[slot]--;
                        spend[slot]++;
                    }
                }
            }
            rounds++;
        }
        if (rounds == 0) {
            return Optional.empty();
        }

        ItemStack result = toolStack.copy();
        result.set(DataComponents.DAMAGE, Math.max(0, damage));
        result.set(ForgeweaveDataComponents.REPAIR_COUNT.get(), repairCount + rounds);
        // Any repair moves the tool off the Broken threshold, since one repair round is always worth
        // at least 1/64 of the durability pool.
        result.remove(ForgeweaveDataComponents.BROKEN.get());
        List<Integer> slotsUsed = new ArrayList<>(1 + spend.length);
        slotsUsed.add(1);
        for (int count : spend) {
            slotsUsed.add(count);
        }
        return Optional.of(new Result(result, slotsUsed));
    }

    /**
     * One material a repair may be paid in: the {@link Material} itself, its registry id (what a
     * sharpening kit of it carries, issue #463) and its head durability already multiplied by the
     * {@code repairModifier} of the slot that contributed it.
     */
    private record RepairMaterial(ResourceLocation materialId, Material material, float weightedHeadDurability) {}

    /**
     * Every material {@code stack} can be repaired with, in repair-slot order -- upstream
     * {@code TinkersItem#calculateRepairAmount}'s loop over {@code getRepairParts()}, including its
     * two skips: a material already claimed by an earlier repair slot (so the first slot's factor
     * wins), and a material with no head stats at all (upstream's {@code if(stats != null)}).
     */
    private static List<RepairMaterial> repairMaterialsOf(HolderLookup.Provider registries, ItemStack stack) {
        Optional<Entry> entry = entryFor(stack);
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (entry.isEmpty() || materials == null) {
            return List.of();
        }
        List<ResourceLocation> parts = materials.parts();
        List<RepairMaterial> repairable = new ArrayList<>(2);
        Set<ResourceLocation> seen = new HashSet<>();
        for (ToolConstants.RepairPart part : entry.get().constants().repairSlots()) {
            if (part.slot() >= parts.size() || !seen.add(parts.get(part.slot()))) {
                continue;
            }
            Optional<Material> material = lookupMaterial(registries, parts.get(part.slot()));
            Optional<Integer> headDurability = material.flatMap(Material::head).map(Material.Head::durability);
            if (headDurability.isEmpty()) {
                continue;
            }
            repairable.add(new RepairMaterial(parts.get(part.slot()), material.get(),
                    headDurability.get() * part.modifier()));
        }
        return List.copyOf(repairable);
    }

    /**
     * What one round of repair items restores, with the Tool Forge's {@link #FORGE_REPAIR_DISCOUNT}
     * folded in. {@code amount} is {@link ToolRepair#repairAmount}'s result, which for the common
     * one-repair-part tool is simply its head material's head durability. Public so a GameTest can
     * assert the discount arithmetic directly rather than only through a tool's damage value.
     */
    public static int repairIncrement(int amount, int baseDurability, int actualDurability,
            int repairCount, int occupiedModifierSlots, boolean forge) {
        int increment = ToolRepair.repairIncrement(
                amount, baseDurability, actualDurability, repairCount, occupiedModifierSlots);
        return forge ? (int) Math.ceil(increment / FORGE_REPAIR_DISCOUNT) : increment;
    }

    private static Optional<Material> lookupMaterial(HolderLookup.Provider registries, ResourceLocation id) {
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, id)))
                .map(holder -> holder.value());
    }

    private ToolAssemblyRecipes() {}
}
