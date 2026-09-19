package dev.gkissel.forgeweave.modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisSockets;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * The Modifier Worktable's two functions (issue #1057), as pure statics over a tool stack and the
 * table's two input slots, so both sides of the menu compute the same answer from the same synced
 * slot contents and neither needs a packet of its own.
 *
 * <h2>Remove a modifier</h2>
 *
 * <p>Takes one whole level's worth of application units off the chosen modifier and hands back the
 * slots that level occupied. Nothing is refunded: what was spent applying it is gone, upstream 1.20's
 * {@code ModifierRemovalRecipe} rule and what its book page says outright. A modifier down to nothing
 * leaves the list entirely.
 *
 * <p>"One level's worth" is {@link Modifier#unitsPerLevel} units, because {@link ModifierEntry#level}
 * counts application units rather than displayed levels (see that record's javadoc). That keeps the
 * slot arithmetic symmetric with applying: {@link Modifier#occupiedSlots}' default is the displayed
 * level, so dropping one level's units gives back exactly one slot, and a modifier that charges
 * differently -- luck's flat one slot, a {@link Modifier#utility} that charges none -- gives back
 * exactly what it took, because this asks {@link ForgeweaveModifiers#occupiedSlots} rather than
 * assuming.
 *
 * <h2>Sort modifiers</h2>
 *
 * <p>Swaps the chosen modifier with its neighbour, wrapping at both ends, direction decided by which
 * input slot holds the reagent -- upstream's {@code ModifierSortingRecipe}, which reads the first
 * slot being empty as "go backwards". The reagent is never spent and the tool needs two modifiers
 * before there is anything to swap.
 *
 * <p>Order is not cosmetic here. {@link ForgeweaveModifiers#foldTierIndex} folds every tier-bumping
 * modifier except diamond and emerald in the list's own order, and those hooks do not commute: a
 * fortification's {@link Modifier#toolTierIndex} <em>sets</em> the ladder index while netherite's
 * raises it to a floor, so which of the two runs last decides the tool's mining tier. The durability,
 * attack and mining-speed folds thread a running total through the list in the same way. So both
 * functions end by calling {@link ModifierApplication#rebake}, which recomputes every baked component
 * from the untouched base against the new list.
 */
public final class Worktable {

    /** The tool being worked on, upstream's {@code TINKER_SLOT}. */
    public static final int TOOL_SLOT = 0;
    /** The first reagent slot, upstream's {@code INPUT_START}. */
    public static final int INPUT_START = 1;
    /** Upstream's {@code INPUT_COUNT}: two slots, whose order is what picks the sorting direction. */
    public static final int INPUT_COUNT = 2;
    /** Tool plus reagents, the block entity's container size. */
    public static final int CONTAINER_SLOTS = INPUT_START + INPUT_COUNT;

    /** Sorting needs something to swap with, upstream's own floor. */
    private static final int MIN_MODIFIERS_TO_SORT = 2;

    /**
     * What the table will hand back, or why it will not. {@code used} is one count per input slot, so
     * the menu spends exactly the slot the reagent came out of; {@code leftovers} is what that
     * reagent turns back into (the dry sponge).
     */
    public record Result(ItemStack output, List<Integer> used, List<ItemStack> leftovers,
            @Nullable Component rejection) {

        public static Result of(ItemStack output, List<Integer> used, List<ItemStack> leftovers) {
            return new Result(output, List.copyOf(used), List.copyOf(leftovers), null);
        }

        public static Result rejected(Component reason) {
            return new Result(ItemStack.EMPTY, List.of(), List.of(), reason);
        }

        public boolean isRejected() {
            return rejection != null;
        }
    }

    /**
     * The recipe the loaded inputs drive, or empty when they drive none. First match wins, and a slot
     * matching nothing simply does not contribute -- a player who leaves an unrelated item in the
     * second slot still gets the first slot's recipe, which is also how the sorting direction is
     * expressed (a compass in the second slot alone means "backwards").
     */
    public static Optional<WorktableRecipe> recipeFor(HolderLookup.Provider registries, List<ItemStack> inputs) {
        return registries.lookup(WorktableRecipe.REGISTRY)
                .map(lookup -> lookup.listElements()
                        .map(holder -> holder.value())
                        .filter(recipe -> inputs.stream().anyMatch(
                                stack -> !stack.isEmpty() && recipe.input().test(stack)))
                        .findFirst())
                .orElse(Optional.empty());
    }

    /**
     * Whether {@code stack} is a reagent for any loaded worktable recipe -- what the input slots
     * accept, so an unrelated item cannot be parked in them.
     */
    public static boolean isReagent(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return registries.lookup(WorktableRecipe.REGISTRY)
                .map(lookup -> lookup.listElements().anyMatch(holder -> holder.value().input().test(stack)))
                .orElse(false);
    }

    /**
     * What the tool slot accepts: anything Forgeweave assembled, which is upstream's
     * {@code tconstruct:modifiable} tag read off the components a build writes rather than off a tag.
     * A broken tool qualifies -- breaking costs a tool its durability, not its stats component -- and
     * so does an armor piece, whose base is {@code ARMOR_STATS} instead (issue #721).
     */
    public static boolean isModifiable(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.has(ForgeweaveDataComponents.TOOL_STATS.get())
                        || stack.has(ForgeweaveDataComponents.LAUNCHER_STATS.get())
                        || stack.has(ForgeweaveDataComponents.ARMOR_STATS.get()));
    }

    /**
     * The modifiers the table offers as buttons, in the tool's own order.
     *
     * <p>Material traits and innates are never in this list because they are never
     * {@link ModifierEntry}s in the first place -- Forgeweave keeps them in their own components
     * ({@code ForgeweaveTraits}, {@code ForgeweaveInnates}), so upstream's
     * {@code getUpgrades()}-not-{@code getModifiers()} distinction needs no filter here. The one
     * entry that is filtered is a {@code socketed} modifier with a gem seated in it, which
     * {@link #isRemovable} explains.
     */
    public static List<ModifierEntry> options(ItemStack tool, WorktableRecipe.Kind kind) {
        List<ModifierEntry> entries = ForgeweaveModifiers.of(tool);
        if (kind == WorktableRecipe.Kind.SORT) {
            return entries.size() < MIN_MODIFIERS_TO_SORT ? List.of() : entries;
        }
        return entries.stream().filter(entry -> isRemovable(tool, entry)).toList();
    }

    /**
     * Whether a level of {@code entry} can come off without losing something the table cannot hand
     * back.
     *
     * <p>The one case that cannot is Apotheosis' {@code socketed} with a gem in it (M8, D-M8-1): its
     * level <em>is</em> the socket count, and {@code ApotheosisSockets#gems} reads the stored gems
     * back only up to that count, so dropping a level would leave the last socket's gem in the
     * component with nothing to read it. Refusing costs the player nothing -- an empty socket still
     * comes off here, and a seated gem stays where the player put it -- whereas handing the gem back
     * would mean a per-modifier refund channel in this recipe that exactly one compat modifier would
     * ever use.
     */
    public static boolean isRemovable(ItemStack tool, ModifierEntry entry) {
        if (!entry.id().equals(ApotheosisSockets.SOCKETED_ID)) {
            return true;
        }
        return ApotheosisSockets.gems(tool).stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * What the table hands back for the modifier at {@code selected} in {@link #options}' list, given
     * what is in the input slots.
     *
     * @param inputs the two input slots' contents, in slot order
     */
    public static Result resolve(HolderLookup.Provider registries, ItemStack tool, List<ItemStack> inputs,
            int selected) {
        Optional<WorktableRecipe> found = recipeFor(registries, inputs);
        if (tool.isEmpty() || found.isEmpty()) {
            return Result.rejected(null);
        }
        WorktableRecipe recipe = found.get();
        List<ModifierEntry> options = options(tool, recipe.kind());
        if (options.isEmpty()) {
            return Result.rejected(Component.translatable(recipe.kind() == WorktableRecipe.Kind.SORT
                    ? "gui.forgeweave.worktable.not_enough_modifiers"
                    : "gui.forgeweave.worktable.no_modifiers"));
        }
        if (selected < 0 || selected >= options.size()) {
            return Result.rejected(null);
        }
        ModifierEntry entry = options.get(selected);
        return recipe.kind() == WorktableRecipe.Kind.SORT
                ? sort(tool, entry, inputs, recipe)
                : remove(tool, entry, inputs, recipe);
    }

    /** One level of {@code entry} off, and the slots it held back. */
    private static Result remove(ItemStack tool, ModifierEntry entry, List<ItemStack> inputs,
            WorktableRecipe recipe) {
        // #952: a compat integration that applied an upgrade without spending a slot was handed the
        // slots back through GRANTED_SLOTS, and nothing on the stack records which entry carried that
        // grant. Refunding this entry's slots on such a tool would let a Draconic fusion upgrade be
        // removed and re-applied for a free slot every round, so the whole tool is refused instead.
        // ponytail: whole-tool refusal, because the grant is a bare count. Tag the granting entry and
        // this becomes a per-entry check.
        if (tool.getOrDefault(ForgeweaveDataComponents.GRANTED_SLOTS.get(), 0) > 0) {
            return Result.rejected(Component.translatable("gui.forgeweave.worktable.granted_slots"));
        }
        Modifier modifier = ForgeweaveModifiers.get(entry.id());
        int perLevel = modifier == null ? 1 : Math.max(1, modifier.unitsPerLevel());
        int newLevel = entry.level() - perLevel;

        List<ModifierEntry> entries = new ArrayList<>(ForgeweaveModifiers.of(tool));
        int index = indexOf(entries, entry);
        if (index < 0) {
            return Result.rejected(null);
        }
        if (newLevel > 0) {
            entries.set(index, entry.withLevel(newLevel));
        } else {
            entries.remove(index);
        }

        ItemStack output = tool.copy();
        output.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(entries));
        ModifierApplication.rebake(output);
        return Result.of(output, spend(inputs, recipe), recipe.leftovers());
    }

    /**
     * {@code entry} swapped with its neighbour. Upstream's direction rule verbatim: the first input
     * slot standing empty means the reagent is in the second one, which reads as backwards.
     */
    private static Result sort(ItemStack tool, ModifierEntry entry, List<ItemStack> inputs,
            WorktableRecipe recipe) {
        List<ModifierEntry> entries = new ArrayList<>(ForgeweaveModifiers.of(tool));
        int index = indexOf(entries, entry);
        if (index < 0) {
            return Result.rejected(null);
        }
        boolean forward = !inputs.isEmpty() && !inputs.get(0).isEmpty();
        int last = entries.size() - 1;
        int target = forward
                ? (index == last ? 0 : index + 1)
                : (index == 0 ? last : index - 1);
        Collections.swap(entries, index, target);

        ItemStack output = tool.copy();
        output.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(entries));
        ModifierApplication.rebake(output);
        return Result.of(output, spend(inputs, recipe), recipe.leftovers());
    }

    /** One from the first slot holding this recipe's reagent, or nothing at all when it is reusable. */
    private static List<Integer> spend(List<ItemStack> inputs, WorktableRecipe recipe) {
        List<Integer> used = new ArrayList<>(inputs.size());
        boolean spent = false;
        for (ItemStack stack : inputs) {
            boolean take = recipe.consumesInput() && !spent && !stack.isEmpty() && recipe.input().test(stack);
            used.add(take ? 1 : 0);
            spent |= take;
        }
        return used;
    }

    private static int indexOf(List<ModifierEntry> entries, ModifierEntry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(entry.id())) {
                return i;
            }
        }
        return -1;
    }

    private Worktable() {}
}
