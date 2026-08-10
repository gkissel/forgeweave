package dev.gkissel.forgeweave.modifier;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
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
 *   "reagent": {"item": "minecraft:redstone"},
 *   "cost": 1,
 *   "max_level": 250
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code modifier} -- the id stored on the tool, and the {@link Modifier} behavior looked up
 *       for it. An id no version of Forgeweave implements still applies and still serializes; it
 *       simply has no effect ({@link ForgeweaveModifiers#get}).
 *   <li>{@code reagent} -- what the player puts in the station's two free slots.
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
        ResourceLocation modifier, Ingredient reagent, int cost, int maxLevel, List<Integer> costPerLevel) {

    public static final ResourceKey<Registry<ModifierRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "modifier_recipe"));

    public static final Codec<ModifierRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("modifier").forGetter(ModifierRecipe::modifier),
            Ingredient.CODEC.fieldOf("reagent").forGetter(ModifierRecipe::reagent),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("cost", 1).forGetter(ModifierRecipe::cost),
            ExtraCodecs.POSITIVE_INT.fieldOf("max_level").forGetter(ModifierRecipe::maxLevel),
            ExtraCodecs.POSITIVE_INT.listOf().optionalFieldOf("cost_per_level", List.of())
                    .forGetter(ModifierRecipe::costPerLevel))
            .apply(instance, ModifierRecipe::new));

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
