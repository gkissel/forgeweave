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

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Applying a {@link ModifierRecipe}'s reagents to an assembled tool at the Tool Station. The station
 * hands over its two free input slots (the same pair a repair uses) and gets back either a modified
 * tool plus what taking it costs, or the reason it can't be done.
 *
 * <p>Ported from upstream 1.12's {@code ToolBuilder#tryModifyTool} and
 * {@code ModifierAspect.MultiAspect}: reagents are consumed one application unit at a time, so a
 * single redstone is a valid application that partially fills the level -- and, since issue #344,
 * with upstream's per-level slot charge intact: every new level spends a fresh modifier slot
 * ({@link Modifier#occupiedSlots}), and units past what the budget affords are left unconsumed the
 * way upstream rolls back the match that no longer fits (see {@link #apply}).
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
                        // #271: fortification's recipe holds the flint half of its cost so the cost
                        // stays data and JEI lists it, but its modifier id is a family marker that is
                        // never stored on a tool -- every real fortification id is generated per
                        // material. Applying it generically would put the marker itself on the tool
                        // at level 1, i.e. tier index 0. Fortification#resolve owns that loadout, and
                        // ToolAssemblyRecipes gives it its turn before this path.
                        .filter(recipe -> !recipe.modifier().equals(Fortification.RECIPE_ID))
                        .filter(recipe -> recipe.matches(stack))
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
        Optional<ModifierRecipe> firstFound = recipeFor(registries, first);
        Optional<ModifierRecipe> secondFound = recipeFor(registries, second);
        if (firstFound.isEmpty() && secondFound.isEmpty()) {
            return Optional.empty();
        }
        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.MODIFIERS)) {
            // Content-family toggles ticket: applying a modifier is off. Refused only once the slots
            // actually hold a reagent, so a station loaded with something else stays silent -- and
            // only the *application* is refused: every modifier already on a tool keeps working,
            // since nothing here touches an assembled stack.
            return Optional.of(Outcome.rejected(
                    Component.translatable("gui.forgeweave.modifier.modifiers_disabled")));
        }
        if ((!first.isEmpty() && firstFound.isEmpty()) || (!second.isEmpty() && secondFound.isEmpty())) {
            // One slot holds a reagent and the other something no modifier recipe accepts at all --
            // upstream's untouched-input check (ToolBuilder#tryModifyTool lines 226-234, the
            // gui.error.no_modifier_for_item refusal). Two different modifiers' reagents side by
            // side are NOT this case since issue #340: they apply together below.
            return Optional.of(Outcome.rejected(Component.translatable("gui.forgeweave.modifier.invalid_reagent")));
        }
        if (firstFound.isPresent() && secondFound.isPresent() && !firstFound.get().equals(secondFound.get())) {
            // Issue #340: two distinct recipes, one per slot -- upstream tryModifyTool's outer loop
            // over every registered modifier (lines 176-223), which applies each one whose reagents
            // are present in the same operation. Sequential, the second application re-checking the
            // slot budget against the first one's output; any rejection rejects the whole craft,
            // upstream's rethrow for a modifier not yet applied this craft (lines 207-208 --
            // "either all or none").
            return Optional.of(resolveBoth(registries, tool, firstFound.get(), first, secondFound.get(), second));
        }

        // One recipe across both slots -- possibly two reagent forms of it (issue #259: haste dust
        // in one slot, a redstone block in the other), which pool inside a single application.
        ModifierRecipe recipe = firstFound.isPresent() ? firstFound.get() : secondFound.get();
        ModifierRecipe.Reagent firstReagent = recipe.reagentFor(first);
        ModifierRecipe.Reagent secondReagent = recipe.reagentFor(second);
        return Optional.of(resolveOne(registries, recipe, tool,
                firstReagent != null ? first.getCount() : 0,
                firstReagent != null ? firstReagent.units() : 1,
                secondReagent != null ? second.getCount() : 0,
                secondReagent != null ? secondReagent.units() : 1));
    }

    /**
     * Issue #340's two-modifier craft: the first slot's recipe applied to the tool, then the second
     * slot's recipe applied to that result -- so the second application's slot-budget check
     * ({@code freeSlots}, i.e. {@link ForgeweaveModifiers#occupiedSlots}) sees the first one already
     * in place, exactly as upstream's sequential {@code canApply}/{@code apply} loop does. Slot
     * order rather than upstream's registry order: with one recipe per slot every outcome (including
     * the budget rejection -- whichever order, the modifier that doesn't fit refuses the craft) is
     * order-independent, only the modifier list's cosmetic ordering differs.
     */
    private static Outcome resolveBoth(HolderLookup.Provider registries, ItemStack tool,
            ModifierRecipe firstRecipe, ItemStack first, ModifierRecipe secondRecipe, ItemStack second) {
        Outcome one = resolveOne(registries, firstRecipe, tool,
                first.getCount(), firstRecipe.reagentFor(first).units(), 0, 1);
        if (one.output().isEmpty()) {
            return one;
        }
        Outcome two = resolveOne(registries, secondRecipe, one.output(),
                0, 1, second.getCount(), secondRecipe.reagentFor(second).units());
        if (two.output().isEmpty()) {
            return two; // discards the first application too: upstream's all-or-none rethrow.
        }
        return Outcome.applied(two.output(), one.firstUsed(), two.secondUsed());
    }

    /**
     * One recipe's whole application: the supported-tool check, the pure {@link #apply} arithmetic,
     * and the enchantment grants ({@code #106} batch: luck's Fortune/Looting -- the one call in the
     * class that needs registry access, which is why it lives here rather than in the registry-free
     * {@code apply()}/{@code modified()} below -- see {@code Modifier#fortuneLevel}).
     */
    private static Outcome resolveOne(HolderLookup.Provider registries, ModifierRecipe recipe, ItemStack tool,
            int firstAvailable, int firstUnitsPerItem, int secondAvailable, int secondUnitsPerItem) {
        Optional<Component> unsupported = unsupportedToolReason(registries, recipe, tool);
        if (unsupported.isPresent()) {
            return Outcome.rejected(unsupported.get());
        }
        Outcome outcome = apply(recipe, tool, firstAvailable, firstUnitsPerItem, secondAvailable, secondUnitsPerItem);
        if (!outcome.output().isEmpty()) {
            applyEnchantmentGrants(registries, recipe, outcome.output());
        }
        return outcome;
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
     *
     * <p>Package-private rather than {@code private}: issue #296's autonomous growth-on-use
     * ({@code ForgeweaveModifiers#growLuckOnUse}) calls this too, once its own roll bumps luck's raw
     * level outside the Tool Station, so a level crossed silently in the field takes effect
     * immediately rather than waiting on the next lapis application.
     */
    static void applyEnchantmentGrants(HolderLookup.Provider registries, ModifierRecipe recipe, ItemStack tool) {
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
     * each of the two slots, produce the modified tool or the reason there isn't one. Both slots are
     * taken to hold the recipe's primary (first-listed) reagent; {@link #resolve} calls the
     * per-slot-unit overload below with what each slot actually holds.
     *
     * <p>Reagents are spent from the first slot before the second, matching how a repair spends them.
     */
    public static Outcome apply(ModifierRecipe recipe, ItemStack tool, int firstAvailable, int secondAvailable) {
        int units = recipe.reagents().get(0).units();
        return apply(recipe, tool, firstAvailable, units, secondAvailable, units);
    }

    /**
     * {@link #apply(ModifierRecipe, ItemStack, int, int)} with each slot's per-item unit value made
     * explicit, for the two slots holding two different reagents of the same recipe (issue #259:
     * haste dust at 1 unit next to a redstone block at 9).
     *
     * <p>A multi-unit reagent near the cap is all-or-nothing, mirroring upstream 1.12's
     * {@code ToolBuilder#tryModifyTool}, which rolls the whole {@code RecipeMatch} back when a unit
     * mid-match stops applying: a block whose full 9 units no longer fit under {@code max_level} is
     * left unconsumed (and, unlike upstream's silent decline, refused with a message -- this class's
     * standing rule that the station explains itself).
     */
    public static Outcome apply(ModifierRecipe recipe, ItemStack tool, int firstAvailable, int firstUnitsPerItem,
            int secondAvailable, int secondUnitsPerItem) {
        ModifierEntry existing = ForgeweaveModifiers.entry(tool, recipe.modifier());
        int current = existing == null ? 0 : existing.level();

        if (current >= recipe.maxLevel()) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.max_level",
                    name(recipe.modifier())));
        }

        // Issue #344: upstream charges one free modifier per level (MultiAspect#canApply spends
        // FreeModifierAspect every time a new level starts), so the units this application may add
        // are capped at what the slot budget affords on top of the level cap. Free-slot count
        // floored at 0 the way upstream clamps Tags.FREE_MODIFIERS, so a fixture already past its
        // budget can still fill its current level (which charges nothing). Zero affordable units
        // is upstream's FreeModifierAspect throw: gui.error.not_enough_modifiers, the whole
        // application refused.
        int remaining = recipe.maxLevel() - current;
        int free = Math.max(0, ForgeweaveModifiers.freeSlots(tool));
        int occupiedNow = ForgeweaveModifiers.occupiedSlots(recipe.modifier(), current);
        int affordable = 0;
        while (affordable < remaining
                && ForgeweaveModifiers.occupiedSlots(recipe.modifier(), current + affordable + 1) - occupiedNow <= free) {
            affordable++;
        }
        if (affordable == 0) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.no_slots",
                    ForgeweaveModifiers.DEFAULT_SLOTS));
        }
        remaining = affordable;
        int units;
        int firstUsed;
        int secondUsed;
        if (firstUnitsPerItem == secondUnitsPerItem) {
            // Both slots hold equally-valued items, so they pool: cost items buy unitsPerItem units,
            // spent from the first slot before the second -- the pre-#259 arithmetic, generalized by
            // the per-item multiplier (1 for every legacy recipe, so this branch is exactly it).
            int steps = Math.min((firstAvailable + secondAvailable) / recipe.cost(), remaining / firstUnitsPerItem);
            units = steps * firstUnitsPerItem;
            int spent = steps * recipe.cost();
            firstUsed = Math.min(spent, firstAvailable);
            secondUsed = spent - firstUsed;
        } else {
            // Differently-valued reagents side by side: whole cost-steps of one slot's reagent at a
            // time, first slot first, each step only if its full grant still fits under the cap.
            units = 0;
            firstUsed = 0;
            secondUsed = 0;
            while (firstUsed + recipe.cost() <= firstAvailable && units + firstUnitsPerItem <= remaining) {
                firstUsed += recipe.cost();
                units += firstUnitsPerItem;
            }
            while (secondUsed + recipe.cost() <= secondAvailable && units + secondUnitsPerItem <= remaining) {
                secondUsed += recipe.cost();
                units += secondUnitsPerItem;
            }
        }
        if (units <= 0) {
            if (firstAvailable + secondAvailable >= recipe.cost()) {
                // A whole reagent step is loaded but its full grant overshoots the cap (a 9-unit
                // block against 5 units of room) -- see the method javadoc for the upstream mirror.
                return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.reagent_overshoot",
                        name(recipe.modifier())));
            }
            // Enough for no whole application unit; nothing to show and nothing to complain about.
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.not_enough_reagents",
                    recipe.cost()));
        }

        return Outcome.applied(modified(tool, recipe.modifier(), current + units), firstUsed, secondUsed);
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
        // #230: alien's distributed durability growth lives outside both tool_stats and the modifier
        // list, so it is re-added here the way upstream's TraitProgressiveStats#applyEffect re-adds
        // its bonus on every rebuild -- without this, applying any modifier would shrink max_damage
        // back to the materials-plus-modifiers number and wipe the growth.
        stack.set(DataComponents.MAX_DAMAGE, effective.durability() + ForgeweaveTraits.maxDurabilityBonus(stack));
    }

    /**
     * Re-applies every modifier's baked effects onto a tool whose base components were just rebuilt
     * from a new material set -- a part exchange (issue #264), upstream
     * {@code ToolBuilder#rebuildTool}'s "reapply modifiers" pass. The {@code id + level} entries
     * themselves are never touched; this recomputes only what they bake into vanilla components: the
     * mining-speed/durability retune, then each modifier's tool-tier bump in application order.
     * Re-running the bumps against the fresh material's tier reproduces exactly what first
     * application wrote, because {@link Modifier#toolTierIndex} only ever raises the index.
     */
    public static void rebake(ItemStack stack) {
        retuneStats(stack);
        for (ModifierEntry entry : ForgeweaveModifiers.of(stack)) {
            Modifier modifier = ForgeweaveModifiers.get(entry.id());
            if (modifier != null) {
                retuneToolTier(stack, modifier, entry.level());
            }
        }
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
            return Component.translatable("modifier.forgeweave.embossment.name", materialName(material));
        }
        // #271: fortification ids are generated per material too, and upstream names them the same
        // way ({@code ModFortify#getLocalizedName}: "Fortified (Cobalt)"). The bare
        // `forgeweave:fortification` recipe marker is not one of these and falls through to the plain
        // key below, which is the family name JEI shows.
        ResourceLocation fortifiedWith = Fortification.materialOf(id);
        if (fortifiedWith != null) {
            return Component.translatable("modifier.forgeweave.fortification.material",
                    materialName(fortifiedWith));
        }
        return Component.translatable("modifier." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    /** A material's own display key, the placeholder both generated-id families interpolate. */
    private static Component materialName(ResourceLocation material) {
        return Component.translatable("material." + material.getNamespace() + "." + material.getPath());
    }

    /**
     * A modifier's one-line effect description, keyed {@code modifier.<namespace>.<path>.description}
     * next to {@link #name}'s {@code .name} -- the hover text the Tool Station's modifier rows show
     * (issue #258). Embossing gets the same per-material treatment as {@link #name}, for the same
     * reason: its generated ids have no lang key of their own.
     */
    public static Component description(ResourceLocation id) {
        ResourceLocation material = Embossing.materialOf(id);
        if (material != null) {
            return Component.translatable("modifier.forgeweave.embossment.description", materialName(material));
        }
        ResourceLocation fortifiedWith = Fortification.materialOf(id);
        if (fortifiedWith != null) {
            return Component.translatable("modifier.forgeweave.fortification.material_description",
                    materialName(fortifiedWith));
        }
        return Component.translatable("modifier." + id.getNamespace() + "." + id.getPath() + ".description");
    }

    private ModifierApplication() {}
}
