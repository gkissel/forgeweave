package dev.gkissel.forgeweave.modifier;

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
 * </ul>
 *
 * <p>Registered as a NeoForge datapack registry with a network codec, exactly as
 * {@code material.Material} is (ADR-0002's precedent), so {@code /reload} picks up an edit and
 * connecting clients get the same table the server resolved against -- which is what lets the Tool
 * Station screen explain a rejection without a packet of its own.
 */
public record ModifierRecipe(ResourceLocation modifier, Ingredient reagent, int cost, int maxLevel) {

    public static final ResourceKey<Registry<ModifierRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "modifier_recipe"));

    public static final Codec<ModifierRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("modifier").forGetter(ModifierRecipe::modifier),
            Ingredient.CODEC.fieldOf("reagent").forGetter(ModifierRecipe::reagent),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("cost", 1).forGetter(ModifierRecipe::cost),
            ExtraCodecs.POSITIVE_INT.fieldOf("max_level").forGetter(ModifierRecipe::maxLevel))
            .apply(instance, ModifierRecipe::new));
}
