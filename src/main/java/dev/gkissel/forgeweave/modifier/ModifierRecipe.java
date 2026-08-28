package dev.gkissel.forgeweave.modifier;

import java.util.BitSet;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * How a modifier is applied at the Tool Station, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/modifier_recipe/<name>.json} -- ADR-0004's decision 1: the
 * behavior is Java, the reagents and costs are data, and retuning any of them takes no code change.
 *
 * <pre>
 * {
 *   "modifier": "forgeweave:haste",
 *   "reagents": [
 *     {"ingredient": {"item": "minecraft:redstone"}},
 *     {"ingredient": {"item": "minecraft:redstone_block"}, "units": 9}
 *   ],
 *   "cost": 1,
 *   "max_level": 250
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code modifier} -- the id stored on the tool, and the {@link Modifier} behavior looked up
 *       for it. An id no version of Forgeweave implements still applies and still serializes; it
 *       simply has no effect ({@link ForgeweaveModifiers#get}).
 *   <li>{@code reagent} -- what the player puts in the station's two free slots. The legacy
 *       single-ingredient shape, still accepted: it decodes as one {@link Reagent} worth 1 unit.
 *   <li>{@code reagents} -- issue #259's general form, {@code [{"ingredient": ..., "units": n}]}:
 *       each entry names an accepted ingredient and how many application units one item of it is
 *       worth ({@code units} optional, default 1). Haste's shipped recipe is the upstream-parity
 *       case, redstone dust at 1 plus a redstone block at 9 ({@code TinkerModifiers}'
 *       {@code modHaste.addItem("blockRedstone", 1, 9)}). Exactly one of {@code reagent}/
 *       {@code reagents} must be present; encode always writes {@code reagents}, the same
 *       accept-old-write-new posture as {@code Material}'s {@code TRAITS_CODEC}.
 *   <li>{@code cost} -- reagents per application unit, upstream 1.12's partial-fill model where one
 *       redstone is one unit. Optional, default 1.
 *   <li>{@code max_level} -- the cap, in the same application units as {@link ModifierEntry#level}:
 *       haste's 250 is upstream's 5 levels of 50 redstone.
 *   <li>{@code cost_per_level} -- optional, default empty. Application-unit increments for each
 *       display level, for a modifier whose per-level cost isn't uniform -- upstream's
 *       {@code LuckAspect#getMaxForLevel}, a triangular {@code countPerLevel * level * (level + 1) / 2}
 *       schedule, is the one shipped case: {@code [60, 120, 180]} reproduces its 60/180/360 cumulative
 *       thresholds exactly (issue #106 review). Empty means "uniform", i.e. whatever
 *       {@link Modifier#unitsPerLevel} says (haste's shipped precedent) -- see {@link #levelsReached}.
 *   <li>{@code require_all_reagents} -- optional, default {@code false}. Every other recipe's
 *       {@code reagents} list is OR-variant forms of one conceptual reagent (haste's dust vs. block,
 *       either alone is enough). Issue #776's creative flight needs the opposite: end crystal
 *       <em>and</em> nether star, both distinct items required at once, neither substituting for the
 *       other. {@code true} switches {@code reagents} to that AND reading -- see {@link #isSatisfiedBy}
 *       and {@link ModifierApplication#resolve}, which prefers whichever satisfied recipe consumes the
 *       largest matching set of the station's input items when two recipes would otherwise both claim
 *       the same item (a lone nether star is soulbound's reagent too).
 * </ul>
 *
 * <p>Registered as a NeoForge datapack registry with a network codec, exactly as
 * {@code material.Material} is (ADR-0002's precedent), so {@code /reload} picks up an edit and
 * connecting clients get the same table the server resolved against -- which is what lets the Tool
 * Station screen explain a rejection without a packet of its own.
 */
public record ModifierRecipe(
        ResourceLocation modifier, List<Reagent> reagents, int cost, int maxLevel, List<Integer> costPerLevel,
        boolean requireAllReagents) {

    public static final ResourceKey<Registry<ModifierRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "modifier_recipe"));

    /** Pre-#776 recipes: no {@code require_all_reagents} field, so the legacy OR reading applies. */
    public ModifierRecipe(ResourceLocation modifier, List<Reagent> reagents, int cost, int maxLevel,
            List<Integer> costPerLevel) {
        this(modifier, reagents, cost, maxLevel, costPerLevel, false);
    }

    /** One accepted reagent and how many application units a single item of it is worth. */
    public record Reagent(Ingredient ingredient, int units) {
        public static final Codec<Reagent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Reagent::ingredient),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("units", 1).forGetter(Reagent::units))
                .apply(instance, Reagent::new));
    }

    /**
     * Accepts both the pre-#259 single {@code "reagent": {...}} (one ingredient, 1 unit per item)
     * and the current {@code "reagents": [...]} list, and always writes the latter -- the same
     * pattern, precedent and rationale as {@code Material#TRAITS_CODEC} (modifier recipes are a
     * public datapack surface). A recipe naming both fields reads as the old shape; naming neither
     * still fails, as it always has.
     */
    private static final MapCodec<List<Reagent>> REAGENTS_CODEC = Codec.mapEither(
            Ingredient.CODEC.fieldOf("reagent"),
            ExtraCodecs.nonEmptyList(Reagent.CODEC.listOf()).fieldOf("reagents"))
            .xmap(either -> either.map(single -> List.of(new Reagent(single, 1)), Function.identity()),
                    Either::right);

    public static final Codec<ModifierRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("modifier").forGetter(ModifierRecipe::modifier),
            REAGENTS_CODEC.forGetter(ModifierRecipe::reagents),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("cost", 1).forGetter(ModifierRecipe::cost),
            ExtraCodecs.POSITIVE_INT.fieldOf("max_level").forGetter(ModifierRecipe::maxLevel),
            ExtraCodecs.POSITIVE_INT.listOf().optionalFieldOf("cost_per_level", List.of())
                    .forGetter(ModifierRecipe::costPerLevel),
            Codec.BOOL.optionalFieldOf("require_all_reagents", false).forGetter(ModifierRecipe::requireAllReagents))
            .apply(instance, ModifierRecipe::new));

    /**
     * The first reagent's ingredient -- the whole story for the many single-reagent recipes, and the
     * primary (1-unit, upstream "dust") form for a multi-reagent one like haste. Matching decisions
     * go through {@link #matches}/{@link #reagentFor} instead, which consider every entry.
     */
    public Ingredient reagent() {
        return reagents.get(0).ingredient();
    }

    /** Whether any of this recipe's reagents accepts {@code stack}. */
    public boolean matches(ItemStack stack) {
        return reagentFor(stack) != null;
    }

    /** The reagent entry accepting {@code stack} (first match wins), or {@code null} for none. */
    @Nullable
    public Reagent reagentFor(ItemStack stack) {
        for (Reagent reagent : reagents) {
            if (reagent.ingredient().test(stack)) {
                return reagent;
            }
        }
        return null;
    }

    /**
     * How many side-by-side slots a display (JEI, the guide book) needs to draw this recipe without
     * reading as the wrong logical operator (issue #781): the legacy OR reading has always been one
     * slot whose accepted alternatives cycle in place (haste's dust vs. block), which is also the
     * upstream {@code ContentModifier#build} behavior for {@code inCount == 1}. The AND reading
     * ({@link #requireAllReagents}) needs one slot per declared {@link Reagent} instead, since none
     * can substitute for another -- creative flight's end crystal and nether star both stay on
     * screen at once, exactly {@code ContentModifier}'s {@code inCount} for a modifier whose
     * {@code IModifierDisplay#getItems} returns one list per required slot.
     */
    public int reagentSlotCount() {
        return requireAllReagents ? reagents.size() : 1;
    }

    /** Every free-slot index (issue #776) whose item matches one of this recipe's reagents. */
    public BitSet matchingSlots(List<ItemStack> freeSlots) {
        BitSet slots = new BitSet(freeSlots.size());
        for (int i = 0; i < freeSlots.size(); i++) {
            ItemStack stack = freeSlots.get(i);
            if (!stack.isEmpty() && matches(stack)) {
                slots.set(i);
            }
        }
        return slots;
    }

    /**
     * Whether the free slots, as a whole, complete this recipe (issue #776) -- the station-wide
     * counterpart to {@link #matches}, which only ever asked about one item at a time. The legacy OR
     * reading ({@link #requireAllReagents} false) is unchanged from before: any one slot holding any
     * one reagent form is enough, exactly what {@link ModifierApplication#resolve} used to compute
     * per slot. The AND reading requires every declared {@link Reagent} to have its own matching slot
     * among the ones already found -- a lone end crystal (creative flight's first reagent) is not
     * enough without a nether star (its second) also present.
     */
    public boolean isSatisfiedBy(List<ItemStack> freeSlots) {
        BitSet slots = matchingSlots(freeSlots);
        if (slots.isEmpty()) {
            return false;
        }
        if (!requireAllReagents) {
            return true;
        }
        for (Reagent reagent : reagents) {
            boolean found = false;
            for (int i = slots.nextSetBit(0); i >= 0; i = slots.nextSetBit(i + 1)) {
                if (reagent.ingredient().test(freeSlots.get(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many display levels {@code units} application units reach, 0-indexed (issue #106 review:
     * datapack-retunable per-level cost, not a hardcoded Java constant -- ADR-0004 decision 1). With
     * {@code cost_per_level} present, walks its cumulative thresholds exactly as upstream's
     * {@code LuckAspect#getLevel} does; 0 below the first threshold is correct and deliberate (unlike
     * {@link ForgeweaveModifiers#displayLevel}'s tooltip-oriented floor of 1, this is the raw count of
     * levels actually reached, e.g. the Fortune/Looting level luck grants). Falls back to
     * {@link ForgeweaveModifiers#displayLevel} (uniform, {@link Modifier#unitsPerLevel}) when empty.
     */
    public int levelsReached(int units) {
        if (costPerLevel.isEmpty()) {
            return ForgeweaveModifiers.displayLevel(modifier, units);
        }
        int level = 0;
        int cumulative = 0;
        for (int increment : costPerLevel) {
            cumulative += increment;
            if (units < cumulative) {
                break;
            }
            level++;
        }
        return level;
    }
}
