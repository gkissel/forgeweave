package dev.gkissel.forgeweave.modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.AmmoToolItem;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Applying a {@link ModifierRecipe}'s reagents to an assembled tool at the Tool Station. The station
 * hands over its five free input slots (the same ones a repair uses; parity audit T2, issue #434)
 * and gets back either a modified tool plus what taking it costs, or the reason it can't be done.
 *
 * <p>Ported from upstream 1.12's {@code ToolBuilder#tryModifyTool} and
 * {@code ModifierAspect.MultiAspect}: reagents are consumed one application unit at a time, so a
 * single redstone is a valid application that partially fills the level -- and, since issue #344,
 * with upstream's per-level slot charge intact: every new level spends a fresh modifier slot
 * ({@link Modifier#occupiedSlots}), and units past what the budget affords are left unconsumed the
 * way upstream rolls back the match that no longer fits (see {@link #apply}).
 *
 * <p>Everything the station needs is a pure function of the recipe, the tool and per-slot counts
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
    public record Outcome(ItemStack output, List<Integer> used, @Nullable Component rejection) {

        static Outcome applied(ItemStack output, List<Integer> used) {
            return new Outcome(output, used, null);
        }

        static Outcome rejected(Component reason) {
            return new Outcome(ItemStack.EMPTY, List.of(), reason);
        }

        /** Items spent from free slot {@code slot} (0-based), 0 for a slot this outcome never read. */
        public int used(int slot) {
            return slot < used.size() ? used.get(slot) : 0;
        }

        /** {@code used(0)} -- the pre-#434 two-slot accessor, kept for the unit tests' fixtures. */
        public int firstUsed() {
            return used(0);
        }

        /** {@code used(1)}. */
        public int secondUsed() {
            return used(1);
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
     * Resolves what the free slots do to {@code tool}, or empty when they hold nothing this station
     * recognizes -- in which case the station stays silent rather than complaining about items meant
     * for another recipe (a repair item, say).
     *
     * <p>Two-slot convenience over {@link #resolve(HolderLookup.Provider, ItemStack, List)}.
     */
    public static Optional<Outcome> resolve(HolderLookup.Provider registries, ItemStack tool,
            ItemStack first, ItemStack second) {
        return resolve(registries, tool, List.of(first, second));
    }

    /**
     * Resolves what every free slot does to {@code tool} (parity audit T2, issue #434: upstream
     * {@code ContainerToolStation#getInputs} hands {@code ToolBuilder#tryModifyTool} all five), or
     * empty when they hold nothing this station recognizes.
     *
     * <p>Each distinct recipe found is applied in turn to the previous one's output, in order of the
     * first slot it appears in -- upstream's outer loop over every registered modifier (lines
     * 176-223), which applies each one whose reagents are present in one operation, its second
     * application re-checking the slot budget against the first one's result. Any rejection rejects
     * the whole craft: upstream's rethrow for a modifier not yet applied this craft (lines 207-208,
     * "either all or none"). Slot order rather than registry order: with each recipe pooling every
     * slot that holds it, every outcome (including the budget rejection -- whichever order, the
     * modifier that doesn't fit refuses the craft) is order-independent; only the modifier list's
     * cosmetic ordering differs.
     */
    public static Optional<Outcome> resolve(HolderLookup.Provider registries, ItemStack tool,
            List<ItemStack> freeSlots) {
        // M4-6 (#681): an assembled armor piece carries ARMOR_STATS in place of TOOL_STATS.
        if (tool.get(ForgeweaveDataComponents.TOOL_STATS.get()) == null
                && tool.get(ForgeweaveDataComponents.ARMOR_STATS.get()) == null) {
            return Optional.empty(); // not an assembled tool; nothing to modify.
        }
        List<ModifierRecipe> recipes = new ArrayList<>(); // distinct, first-slot order
        boolean foreign = false;
        for (ItemStack stack : freeSlots) {
            if (stack.isEmpty()) {
                continue;
            }
            Optional<ModifierRecipe> found = recipeFor(registries, stack);
            if (found.isEmpty()) {
                foreign = true;
            } else if (!recipes.contains(found.get())) {
                recipes.add(found.get());
            }
        }
        if (recipes.isEmpty()) {
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
        if (foreign) {
            // A reagent beside something no modifier recipe accepts at all -- upstream's
            // untouched-input check (ToolBuilder#tryModifyTool lines 226-234, the
            // gui.error.no_modifier_for_item refusal).
            return Optional.of(Outcome.rejected(Component.translatable("gui.forgeweave.modifier.invalid_reagent")));
        }

        ItemStack current = tool;
        int[] used = new int[freeSlots.size()];
        for (ModifierRecipe recipe : recipes) {
            // Every slot holding one of this recipe's reagent forms pools into its application
            // (issue #259: haste dust in one slot, a redstone block in another).
            int[] available = new int[freeSlots.size()];
            int[] unitsPerItem = new int[freeSlots.size()];
            for (int i = 0; i < freeSlots.size(); i++) {
                ModifierRecipe.Reagent reagent = recipe.reagentFor(freeSlots.get(i));
                available[i] = reagent != null ? freeSlots.get(i).getCount() : 0;
                unitsPerItem[i] = reagent != null ? reagent.units() : 1;
            }
            Outcome outcome = resolveOne(registries, recipe, current, available, unitsPerItem);
            if (outcome.output().isEmpty()) {
                return Optional.of(outcome); // discards every earlier application: all or none.
            }
            current = outcome.output();
            for (int i = 0; i < used.length; i++) {
                used[i] += outcome.used(i);
            }
        }
        return Optional.of(Outcome.applied(current, Arrays.stream(used).boxed().toList()));
    }

    /**
     * One recipe's whole application: the supported-tool check, the pure {@link #apply} arithmetic,
     * and the enchantment grants ({@code #106} batch: luck's Fortune/Looting -- the one call in the
     * class that needs registry access, which is why it lives here rather than in the registry-free
     * {@code apply()}/{@code modified()} below -- see {@code Modifier#fortuneLevel}).
     */
    private static Outcome resolveOne(HolderLookup.Provider registries, ModifierRecipe recipe, ItemStack tool,
            int[] available, int[] unitsPerItem) {
        if (recipe.modifier().equals(OverslimeRefill.ID)) {
            return OverslimeRefill.apply(tool, available, unitsPerItem); // #728: no modifier entry, a refill.
        }
        Optional<Component> unsupported = unsupportedToolReason(registries, recipe, tool);
        if (unsupported.isPresent()) {
            return Outcome.rejected(unsupported.get());
        }
        Outcome outcome = apply(recipe, tool, available, unitsPerItem);
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
        // M3.5 #396: upstream's category aspects (ModLuck's CategoryAnyAspect(HARVEST, WEAPON,
        // PROJECTILE) -- a bow is TOOL + LAUNCHER only). Upstream's aspect returns false and
        // ToolBuilder#tryModifyTool silently yields EMPTY; this class's standing deviation is to say
        // why, with the same message the wind burst restriction below uses.
        if (tool.getItem() instanceof BowItem && !modifier.appliesToLaunchers()) {
            return Optional.of(Component.translatable("gui.forgeweave.modifier.unsupported_tool", name(recipe.modifier())));
        }
        // #653: upstream's ModifierAspect.projectileOnly (fins) -- Category.PROJECTILE is exactly
        // the ammo tools, which is what AmmoToolItem is.
        if (modifier.projectileOnly() && !(tool.getItem() instanceof AmmoToolItem)) {
            return Optional.of(Component.translatable("gui.forgeweave.modifier.unsupported_tool", name(recipe.modifier())));
        }
        // Issue #438: upstream's ModifierAspect.aoeOnly, the Category.AOE gate the two expanders carry
        // -- every AoeToolCore subclass passes it and nothing else does. Stated here as "the tool has
        // an area this modifier could widen" so a shape with no width/height axis (the Forgeweave-only
        // vein hammer) is refused rather than silently doing nothing.
        if (modifier.aoeExpansion(1).isPresent()
                && !(tool.getItem() instanceof ToolItem toolItem && toolItem.aoeShape().expandable())) {
            return Optional.of(Component.translatable("gui.forgeweave.modifier.unsupported_tool", name(recipe.modifier())));
        }
        // T24: upstream's ModifierAspect.harvestOnly, CategoryAspect(Category.HARVEST) -- blasting's
        // gate. Read off the tool's own assembly entry, which is where Forgeweave records the same
        // category upstream's ToolCore#addCategory does; an item no tab builds has no category at all
        // and is refused rather than defaulted in.
        if (modifier.harvestOnly() && ToolAssemblyRecipes.entryFor(tool)
                .map(entry -> entry.constants().category() != ToolConstants.Category.HARVEST)
                .orElse(true)) {
            return Optional.of(Component.translatable("gui.forgeweave.modifier.unsupported_tool", name(recipe.modifier())));
        }
        // M4-6 (#681): the clone's `tconstruct:modifiable/armor` recipe tool tag, D15's armorOnly()
        // -- the same assembly-entry category read as harvestOnly above. #729: the protections'
        // second tag, `modifiable/held`, is MELEE here (Modifier#alsoHeld).
        if (modifier.armorOnly() && ToolAssemblyRecipes.entryFor(tool)
                .map(entry -> entry.constants().category() != ToolConstants.Category.ARMOR
                        && !(modifier.alsoHeld() && entry.constants().category() == ToolConstants.Category.MELEE))
                .orElse(true)) {
            return Optional.of(Component.translatable("gui.forgeweave.modifier.unsupported_tool", name(recipe.modifier())));
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
     * haste dust at 1 unit next to a redstone block at 9). Two-slot convenience over
     * {@link #apply(ModifierRecipe, ItemStack, int[], int[])}.
     */
    public static Outcome apply(ModifierRecipe recipe, ItemStack tool, int firstAvailable, int firstUnitsPerItem,
            int secondAvailable, int secondUnitsPerItem) {
        return apply(recipe, tool, new int[] {firstAvailable, secondAvailable},
                new int[] {firstUnitsPerItem, secondUnitsPerItem});
    }

    /**
     * The whole rule set over any number of free slots (parity audit T2, issue #434): slot {@code i}
     * holds {@code available[i]} items each worth {@code unitsPerItem[i]} units of {@code recipe}'s
     * modifier (0 available for a slot holding something else).
     *
     * <p>Equally-valued slots pool: {@code cost} items buy {@code unitsPerItem} units, spent from the
     * lowest slot up -- upstream {@code RecipeMatch.Item#matches} sums one reagent's count across
     * every input stack. Differently-valued forms (dust vs. block) are pooled per form, in order of
     * the first slot each appears in, whole cost-steps at a time, each step only if its full grant
     * still fits under the cap. A multi-unit reagent near the cap is therefore all-or-nothing,
     * mirroring upstream 1.12's {@code ToolBuilder#tryModifyTool}, which rolls the whole
     * {@code RecipeMatch} back when a unit mid-match stops applying: a block whose full 9 units no
     * longer fit under {@code max_level} is left unconsumed (and, unlike upstream's silent decline,
     * refused with a message -- this class's standing rule that the station explains itself).
     */
    public static Outcome apply(ModifierRecipe recipe, ItemStack tool, int[] available, int[] unitsPerItem) {
        // T23 (#454): upstream Modifier#canApply's trait/modifier/enchantment refusals run before any
        // aspect (slot budget, level cap) gets a look.
        Optional<Component> incompatible = ModifierCompatibility.refusal(tool, recipe.modifier(), name(recipe.modifier()));
        if (incompatible.isPresent()) {
            return Outcome.rejected(incompatible.get());
        }
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

        int units = 0;
        int totalAvailable = 0;
        int[] used = new int[available.length];
        List<Integer> forms = new ArrayList<>(); // distinct unitsPerItem values, first-slot order
        for (int i = 0; i < available.length; i++) {
            totalAvailable += available[i];
            if (available[i] > 0 && !forms.contains(unitsPerItem[i])) {
                forms.add(unitsPerItem[i]);
            }
        }
        for (int form : forms) {
            int pooled = 0;
            for (int i = 0; i < available.length; i++) {
                if (unitsPerItem[i] == form) {
                    pooled += available[i];
                }
            }
            int steps = Math.min(pooled / recipe.cost(), (remaining - units) / form);
            units += steps * form;
            int spend = steps * recipe.cost();
            for (int i = 0; i < available.length && spend > 0; i++) {
                if (unitsPerItem[i] == form) {
                    used[i] = Math.min(spend, available[i]);
                    spend -= used[i];
                }
            }
        }
        if (units <= 0) {
            if (totalAvailable >= recipe.cost()) {
                // A whole reagent step is loaded but its full grant overshoots the cap (a 9-unit
                // block against 5 units of room) -- see the method javadoc for the upstream mirror.
                return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.reagent_overshoot",
                        name(recipe.modifier())));
            }
            // Enough for no whole application unit; nothing to show and nothing to complain about.
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.not_enough_reagents",
                    recipe.cost()));
        }

        return Outcome.applied(modified(tool, recipe.modifier(), current + units),
                Arrays.stream(used).boxed().toList());
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
     * Rewrites the mining rules inside the stack's existing vanilla {@code tool} component and the
     * stack's {@code max_damage}, leaving every rule that carries no speed (notably the head
     * material's deny-drops tier rule -- {@link #retuneToolTier} is the only thing that touches that
     * one) exactly as assembly wrote it. Keeping the deny-drops rule rather than rebuilding the whole
     * component is what keeps this class free of registry access: only the material knows the tier
     * tag, and only {@link ToolItem} knows the speed rules. Attack damage needs no retuning here:
     * {@code ToolItem} reads it from {@link ForgeweaveModifiers#effectiveStats} directly, since
     * (unlike mining speed and durability) it was never baked into a stored vanilla component in the
     * first place.
     *
     * <p>Issue #598: the speed rules are rebuilt through {@link ToolItem#miningRules}, the same method
     * assembly builds them with, rather than by overwriting each existing rule's speed with the raw
     * {@code effectiveStats} number. That overwrite discarded everything the rules carry beyond the
     * head material's stat -- the tool type's own {@code miningSpeedModifier} (the sword family's 0.5,
     * the hammer's 0.4, the excavator's 0.28), the sword family's 7.5x cobweb rule, the hatchet's
     * leaves rule -- so one redstone put a broadsword back to mining at full harvest-tool speed,
     * undoing issue #437. Rebuilding is also idempotent, which scaling the stored rules would not be:
     * every rebake recomputes from the untouched {@code tool_stats} base rather than compounding.
     *
     * <p>#106 batch: durability grew from a mining-speed-only method (issue #105) to also cover
     * diamond/emerald's durability bonus, on the same {@code effectiveStats} mechanism -- CONTEXT.md's
     * hard rule that the stored {@code tool_stats} component stays the untouched base means the
     * <em>pool</em> a durability bonus grows is {@code max_damage}, not {@code tool_stats}, so growing
     * it costs the player nothing of their current wear.
     */
    private static void retuneStats(ItemStack stack) {
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(stack);
        int durability;
        if (effective != null) {
            durability = effective.durability();
            Tool component = stack.get(DataComponents.TOOL);
            if (component != null && stack.getItem() instanceof ToolItem tool) {
                List<Tool.Rule> rules = new ArrayList<>(
                        component.rules().stream().filter(rule -> rule.speed().isEmpty()).toList());
                rules.addAll(tool.miningRules(effective));
                stack.set(DataComponents.TOOL, new Tool(rules, component.defaultMiningSpeed(), component.damagePerBlock()));
            }
        } else {
            // #721: an armor piece's base is ARMOR_STATS (D14); the modifier pass over its durability
            // is the same fold a tool's gets, and its max_damage is the same pool.
            ArmorStats armor = stack.get(ForgeweaveDataComponents.ARMOR_STATS.get());
            if (armor == null) {
                return;
            }
            durability = ForgeweaveModifiers.modifiedDurability(stack, armor.durability());
        }
        // #230: alien's distributed durability growth lives outside both tool_stats and the modifier
        // list, so it is re-added here the way upstream's TraitProgressiveStats#applyEffect re-adds
        // its bonus on every rebuild -- without this, applying any modifier would shrink max_damage
        // back to the materials-plus-modifiers number and wipe the growth.
        stack.set(DataComponents.MAX_DAMAGE, durability + ForgeweaveTraits.maxDurabilityBonus(stack));
        // #736: netherite's dropped-item fire immunity is vanilla's own component, baked like max_damage.
        if (ForgeweaveModifiers.fireResistant(stack)) {
            stack.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        }
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

    /**
     * A modifier's full display name at {@code displayLevel} -- upstream
     * {@code Modifier#getLeveledTooltip} (Modifier.java:214-235), parity audit T26 (issue #457).
     * Upstream walks down from the current level looking for the highest {@code modifier.<id>.nameN}
     * key that exists and falls back to {@code name + roman numeral}; Forgeweave keeps the ladder in
     * {@link ForgeweaveModifiers#leveledNameCount} instead of probing the language table, because
     * {@code I18n.canTranslate} has no server-safe equivalent and a table lookup would make the
     * tooltip depend on which language is loaded.
     *
     * <p>Reinforced is upstream's one modifier whose top level renames outside that ladder
     * ({@code ModReinforced#getTooltip}: "Unbreakable" once the negation chance reaches 100%), and it
     * is reproduced off the chance itself rather than off a hardcoded max level, so a datapack that
     * retunes the cap keeps the name honest.
     */
    public static MutableComponent displayName(ResourceLocation id, int displayLevel) {
        if (ForgeweaveModifiers.REINFORCED == ForgeweaveModifiers.get(id)
                && ForgeweaveModifiers.REINFORCED.durabilityNegationChance(displayLevel) >= 1.0F) {
            return Component.translatable(ForgeweaveModifiers.UNBREAKABLE_KEY);
        }
        int leveled = ForgeweaveModifiers.leveledNameCount(id);
        if (displayLevel > 1 && displayLevel <= leveled) {
            return Component.translatable(
                    "modifier." + id.getNamespace() + "." + id.getPath() + ".name" + displayLevel);
        }
        MutableComponent line = name(id).copy();
        if (displayLevel > 1) {
            line.append(CommonComponents.SPACE)
                    .append(Component.translatable("enchantment.level." + displayLevel));
        }
        return line;
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
