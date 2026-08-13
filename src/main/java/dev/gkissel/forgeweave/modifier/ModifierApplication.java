package dev.gkissel.forgeweave.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;
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
        Optional<Component> unsupported = unsupportedToolReason(registries, recipe, tool);
        if (unsupported.isPresent()) {
            return Optional.of(Outcome.rejected(unsupported.get()));
        }
        Outcome outcome = apply(recipe, tool,
                firstMatches ? first.getCount() : 0,
                secondMatches ? second.getCount() : 0);
        if (!outcome.output().isEmpty()) {
            // #106 batch: luck's Fortune/Looting grant. This is the one call in the whole class that
            // needs registry access (resolving the Enchantment holders), which is why it lives here
            // rather than in the registry-free apply()/modified() below -- see Modifier#fortuneLevel.
            applyEnchantmentGrants(registries, recipe, outcome.output());
        }
        return Optional.of(outcome);
    }

    /**
     * Issue #223's wind burst restriction, read off vanilla itself rather than a Forgeweave-side item
     * check: if the modifier grants a vanilla enchantment ({@link Modifier#grantedEnchantment}), that
     * enchantment's own {@code supported_items} ({@code Enchantment#getSupportedItems}) is the one
     * true answer for which tools it works on -- wind burst's is {@code #minecraft:enchantable/mace},
     * so the warmace has to be a member of that tag ({@code ForgeweaveItemTagsProvider}) for this to
     * pass. Generic on purpose: a future enchantment-granting modifier is restricted the same way with
     * no code here to touch. Silky (issue #107) grants Silk Touch through its own
     * {@link Modifier#grantsSilkTouch} boolean hook, which predates this method and reports no
     * {@link Modifier#grantedEnchantment}, so it stays unrestricted.
     */
    private static Optional<Component> unsupportedToolReason(HolderLookup.Provider registries,
            ModifierRecipe recipe, ItemStack tool) {
        Modifier modifier = ForgeweaveModifiers.get(recipe.modifier());
        if (modifier == null) {
            return Optional.empty();
        }
        // The level passed here only decides whether a grant exists at all (every shipped grant is
        // present from level 1 on), not what level it would be -- that's resolved again, for real,
        // once the application actually lands (grantEnchantments).
        Optional<Modifier.EnchantmentGrant> grant = modifier.grantedEnchantment(1);
        if (grant.isEmpty()) {
            return Optional.empty();
        }
        Optional<Holder.Reference<Enchantment>> enchantment = registries.lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(grant.get().enchantment()));
        if (enchantment.isEmpty() || tool.is(enchantment.get().value().getSupportedItems())) {
            return Optional.empty();
        }
        return Optional.of(Component.translatable("gui.forgeweave.modifier.unsupported_tool", name(recipe.modifier())));
    }

    /**
     * Upgrades {@code tool}'s stored {@code minecraft:enchantments} to whatever Fortune/Looting the
     * modifier just applied now grants (issue #106's luck; #106 review: the level this reads is
     * {@code recipe.levelsReached}, not raw application units, so a non-uniform per-level cost like
     * luck's triangular one is honored). {@code upgrade} rather than {@code set} because fortune/
     * looting only ever increase as more reagent is applied, so taking the max with whatever is
     * already there is exactly as correct as a full recompute and needs neither a loop over every
     * other entry on the tool nor a recipe-by-id lookup for entries this call didn't touch.
     */
    private static void applyEnchantmentGrants(HolderLookup.Provider registries, ModifierRecipe recipe, ItemStack tool) {
        Modifier modifier = ForgeweaveModifiers.get(recipe.modifier());
        ModifierEntry entry = ForgeweaveModifiers.entry(tool, recipe.modifier());
        if (modifier == null || entry == null) {
            return;
        }
        int level = recipe.levelsReached(entry.level());
        boolean weapon = tool.getItem() instanceof ToolItem toolItem && toolItem.isWeapon();
        int fortune = modifier.fortuneLevel(level);
        int looting = weapon ? modifier.lootingLevel(level) : 0;
        if (fortune == 0 && looting == 0) {
            return;
        }
        HolderGetter<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable mutable =
                new ItemEnchantments.Mutable(tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        mutable.upgrade(enchantments.getOrThrow(Enchantments.FORTUNE), fortune);
        mutable.upgrade(enchantments.getOrThrow(Enchantments.LOOTING), looting);
        tool.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
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
        boolean firstApplication = index < 0;
        if (firstApplication) {
            entries.add(new ModifierEntry(id, level));
        } else {
            entries.set(index, entries.get(index).withLevel(level));
        }

        ItemStack result = tool.copy();
        result.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(entries));
        retuneStats(result);
        if (firstApplication) {
            // #106 batch: diamond/emerald's tier bump. Only on first application -- see
            // Modifier#toolTierIndex's javadoc for why that is what keeps this from compounding.
            Modifier modifier = ForgeweaveModifiers.get(id);
            if (modifier != null) {
                retuneToolTier(result, modifier, level);
            }
        }
        return result;
    }

    /**
     * Rewrites the mining speed inside the stack's existing vanilla {@code tool} component and the
     * stack's {@code max_damage}, leaving every other rule (notably the head material's deny-drops
     * tier rule -- {@link #retuneToolTier} is the only thing that touches that one) exactly as
     * assembly wrote it. Editing the component in place rather than rebuilding it from the material
     * registry is what keeps this whole class free of registry access -- and keeps it correct for
     * whatever rules a later tool type adds. Attack damage needs no retuning here: {@code ToolItem}
     * reads it from {@link ForgeweaveModifiers#effectiveStats} directly, since (unlike mining speed
     * and durability) it was never baked into a stored vanilla component in the first place.
     *
     * <p>#106 batch: durability grew from a mining-speed-only method (issue #105) to also cover
     * diamond/emerald's durability bonus, on the same {@code effectiveStats} mechanism -- CONTEXT.md's
     * hard rule that the stored {@code tool_stats} component stays the untouched base means the
     * <em>pool</em> a durability bonus grows is {@code max_damage}, not {@code tool_stats}, so growing
     * it costs the player nothing of their current wear.
     */
    private static void retuneStats(ItemStack stack) {
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(stack);
        if (effective == null) {
            return;
        }
        Tool component = stack.get(DataComponents.TOOL);
        if (component != null) {
            List<Tool.Rule> rules = component.rules().stream()
                    .map(rule -> rule.speed().isEmpty()
                            ? rule
                            : new Tool.Rule(rule.blocks(), Optional.of(effective.miningSpeed()), rule.correctForDrops()))
                    .toList();
            stack.set(DataComponents.TOOL, new Tool(rules, component.defaultMiningSpeed(), component.damagePerBlock()));
        }
        stack.set(DataComponents.MAX_DAMAGE, effective.durability());
    }

    /**
     * The one-shot tool-tier bump ({@link Modifier#toolTierIndex}, diamond/emerald): finds the
     * deny-drops rule (the one rule with no speed -- {@link #retuneStats} never touches it, so it is
     * always exactly what assembly or an earlier bump left), and if the modifier being newly applied
     * raises its ladder index, replaces it with the higher tag. Left alone if the current tag isn't on
     * {@code ForgeweaveModifiers}'s ladder at all (a material below wood tier, say) -- nothing to
     * bump from.
     */
    private static void retuneToolTier(ItemStack stack, Modifier modifier, int level) {
        Tool component = stack.get(DataComponents.TOOL);
        if (component == null) {
            return;
        }
        List<Tool.Rule> rules = component.rules();
        for (int i = 0; i < rules.size(); i++) {
            Tool.Rule rule = rules.get(i);
            if (rule.speed().isPresent()) {
                continue; // the mining-speed rule, not the deny-drops one.
            }
            int currentIndex = ForgeweaveModifiers.tierIndexOf(rule.blocks());
            if (currentIndex < 0) {
                return;
            }
            int newIndex = modifier.toolTierIndex(level, currentIndex);
            if (newIndex != currentIndex) {
                List<Tool.Rule> updated = new ArrayList<>(rules);
                updated.set(i, Tool.Rule.deniesDrops(ForgeweaveModifiers.tierTag(newIndex)));
                stack.set(DataComponents.TOOL, new Tool(updated, component.defaultMiningSpeed(), component.damagePerBlock()));
            }
            return;
        }
    }

    /**
     * A modifier's display name, keyed like a trait's: {@code modifier.<namespace>.<path>.name}.
     *
     * <p>Embossing (issue #154) is the one exception, because its ids are generated per material and
     * so have no lang key of their own to point at: it reads {@code Embossment (Iron)} from one
     * shared key plus the material's own name key, which is exactly what upstream's
     * {@code ModExtraTrait#getLocalizedName} builds ({@code translate(LOC_Name, "extratrait") + " ("
     * + material.getLocalizedName() + ")"}). Both keys derive from ids alone, which is what keeps
     * this method registry-free for the tooltips that call it without one.
     */
    public static Component name(ResourceLocation id) {
        ResourceLocation material = Embossing.materialOf(id);
        if (material != null) {
            return Component.translatable("modifier.forgeweave.embossment.name",
                    Component.translatable("material." + material.getNamespace() + "." + material.getPath()));
        }
        return Component.translatable("modifier." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    private ModifierApplication() {}
}
