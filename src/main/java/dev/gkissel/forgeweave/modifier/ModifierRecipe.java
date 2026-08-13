package dev.gkissel.forgeweave.modifier;

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
 * </ul>
 *
 * <p>Registered as a NeoForge datapack registry with a network codec, exactly as
 * {@code material.Material} is (ADR-0002's precedent), so {@code /reload} picks up an edit and
 * connecting clients get the same table the server resolved against -- which is what lets the Tool
 * Station screen explain a rejection without a packet of its own.
 */
public record ModifierRecipe(
        ResourceLocation modifier, List<Reagent> reagents, int cost, int maxLevel, List<Integer> costPerLevel) {

    public static final ResourceKey<Registry<ModifierRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "modifier_recipe"));

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
                    .forGetter(ModifierRecipe::costPerLevel))
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
