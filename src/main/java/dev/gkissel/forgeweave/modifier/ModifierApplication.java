package dev.gkissel.forgeweave.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Applying a {@link ModifierRecipe}'s reagents to an assembled tool at the Tool Station. The station
 * hands over its two free input slots (the same pair a repair uses) and gets back either a modified
 * tool plus what taking it costs, or the reason it can't be done.
 *
 * <p>Ported from upstream 1.12's {@code ToolBuilder#tryModifyTool} and
 * {@code ModifierAspect.MultiAspect}: reagents are consumed one application unit at a time, so a
 * single redstone is a valid application that partially fills the level -- but with upstream's
 * per-level slot charge replaced by one slot for the modifier's whole lifetime (see
 * {@link ForgeweaveModifiers}'s class javadoc and the PR for issue #105).
 *
 * <p>Everything the station needs is a pure function of the recipe, the tool and two counts
 * ({@link #apply}); the registry lookup around it exists only to find the recipe. That is what lets
 * a retune of the JSON change costs and caps with no code change, and what makes the unit test able
 * to drive two different cost tables through the same call.
 */
public final class ModifierApplication {

    /**
     * What the station should show and what taking it costs. Exactly one of {@code output} and
     * {@code rejection} is meaningful: a non-empty output means the reagents apply, a non-null
     * rejection is the lang-key message the screen shows instead.
     */
    public record Outcome(ItemStack output, int firstUsed, int secondUsed, @Nullable Component rejection) {

        static Outcome applied(ItemStack output, int firstUsed, int secondUsed) {
            return new Outcome(output, firstUsed, secondUsed, null);
        }

        static Outcome rejected(Component reason) {
            return new Outcome(ItemStack.EMPTY, 0, 0, reason);
        }
    }

    /** Whether any loaded modifier recipe accepts {@code stack} as its reagent. */
    public static boolean isReagent(HolderLookup.Provider registries, ItemStack stack) {
        return recipeFor(registries, stack).isPresent();
    }

    /** The recipe whose reagent {@code stack} matches, if a datapack defines one. */
    public static Optional<ModifierRecipe> recipeFor(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return registries.lookup(ModifierRecipe.REGISTRY)
                .flatMap(lookup -> lookup.listElements()
                        .map(holder -> holder.value())
                        .filter(recipe -> recipe.reagent().test(stack))
                        .findFirst());
    }

    /**
     * Resolves what the two reagent slots do to {@code tool}, or empty when they hold nothing this
     * station recognizes -- in which case the station stays silent rather than complaining about
     * items meant for another recipe (a repair item, say).
     */
    public static Optional<Outcome> resolve(HolderLookup.Provider registries, ItemStack tool,
            ItemStack first, ItemStack second) {
        if (tool.get(ForgeweaveDataComponents.TOOL_STATS.get()) == null) {
            return Optional.empty(); // not an assembled tool; nothing to modify.
        }
        Optional<ModifierRecipe> found = recipeFor(registries, first);
        if (found.isEmpty()) {
            found = recipeFor(registries, second);
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }

        ModifierRecipe recipe = found.get();
        boolean firstMatches = recipe.reagent().test(first);
        boolean secondMatches = recipe.reagent().test(second);
        if ((!first.isEmpty() && !firstMatches) || (!second.isEmpty() && !secondMatches)) {
            // One slot holds this modifier's reagent and the other holds something else -- another
            // modifier's reagent, or junk. Upstream applies every matching modifier in one pass;
            // Forgeweave does one at a time and says so.
            return Optional.of(Outcome.rejected(Component.translatable("gui.forgeweave.modifier.invalid_reagent")));
        }
        return Optional.of(apply(recipe, tool,
                firstMatches ? first.getCount() : 0,
                secondMatches ? second.getCount() : 0));
    }

    /**
     * The whole rule set, as a pure function: given a recipe, a tool and how many reagents sit in
     * each of the two slots, produce the modified tool or the reason there isn't one.
     *
     * <p>Reagents are spent from the first slot before the second, matching how a repair spends them.
     */
    public static Outcome apply(ModifierRecipe recipe, ItemStack tool, int firstAvailable, int secondAvailable) {
        ModifierEntry existing = ForgeweaveModifiers.entry(tool, recipe.modifier());
        int current = existing == null ? 0 : existing.level();

        if (existing == null && ForgeweaveModifiers.freeSlots(tool) <= 0) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.no_slots",
                    ForgeweaveModifiers.DEFAULT_SLOTS));
        }
        if (current >= recipe.maxLevel()) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.max_level",
                    name(recipe.modifier())));
        }

        int affordable = (firstAvailable + secondAvailable) / recipe.cost();
        int units = Math.min(affordable, recipe.maxLevel() - current);
        if (units <= 0) {
            // Enough for no whole application unit; nothing to show and nothing to complain about.
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.not_enough_reagents",
                    recipe.cost()));
        }

        int spent = units * recipe.cost();
        int firstUsed = Math.min(spent, firstAvailable);
        return Outcome.applied(modified(tool, recipe.modifier(), current + units), firstUsed, spent - firstUsed);
    }

    /**
     * The tool with {@code id} set to {@code level}, appended if it is new so the component keeps
     * application order. The vanilla {@code tool} component is rebuilt from the untouched base stats
     * plus the new modifier list, so vanilla's own block-breaking sees the modified mining speed and
     * so re-applying can never compound ({@link ForgeweaveModifiers#effectiveStats}).
     */
    private static ItemStack modified(ItemStack tool, ResourceLocation id, int level) {
        List<ModifierEntry> entries = new ArrayList<>(ForgeweaveModifiers.of(tool));
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            entries.add(new ModifierEntry(id, level));
        } else {
            entries.set(index, entries.get(index).withLevel(level));
        }

        ItemStack result = tool.copy();
        result.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(entries));
        retuneMiningSpeed(result);
        return result;
    }

    /**
     * Rewrites the mining speed inside the stack's existing vanilla {@code tool} component, leaving
     * every other rule (notably the head material's deny-drops tier rule) exactly as assembly wrote
     * it. Editing the component in place rather than rebuilding it from the material registry is
     * what keeps this whole class free of registry access -- and keeps it correct for whatever rules
     * a later tool type adds.
     */
    private static void retuneMiningSpeed(ItemStack stack) {
        Tool component = stack.get(DataComponents.TOOL);
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(stack);
        if (component == null || effective == null) {
            return;
        }
        List<Tool.Rule> rules = component.rules().stream()
                .map(rule -> rule.speed().isEmpty()
                        ? rule
                        : new Tool.Rule(rule.blocks(), Optional.of(effective.miningSpeed()), rule.correctForDrops()))
                .toList();
        stack.set(DataComponents.TOOL, new Tool(rules, component.defaultMiningSpeed(), component.damagePerBlock()));
    }

    /** A modifier's display name, keyed like a trait's: {@code modifier.<namespace>.<path>.name}. */
    public static Component name(ResourceLocation id) {
        return Component.translatable("modifier." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    private ModifierApplication() {}
}
