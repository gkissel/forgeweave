package dev.gkissel.forgeweave.modifier;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * What an embossment costs, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/embossing_recipe/<name>.json} -- ADR-0004's decision 1 applied
 * to embossing exactly as {@link ModifierRecipe} applies it to the other modifiers: the behavior is
 * Java ({@link Embossing}), the cost is data.
 *
 * <pre>
 * {
 *   "reagents": [
 *     {"item": "minecraft:slime_block"},
 *     {"item": "minecraft:magma_block"},
 *     {"item": "minecraft:gold_block"}
 *   ]
 * }
 * </pre>
 *
 * <p>{@code reagents} is the set of items an embossment consumes <b>alongside</b> the donor tool
 * part, one of each, in any order across the station's free slots. The donor part is not listed:
 * which part it is, and which material it carries, is what picks the modifier id and the traits, so
 * it is a property of the mechanic rather than of the cost table (upstream 1.12 says the same thing
 * differently -- {@code ModExtraTrait#addCombination} builds one {@code RecipeMatch.ItemCombination}
 * per part/material pair out of a single shared {@code EMBOSSMENT_ITEMS} list, which is exactly this
 * field).
 *
 * <p><b>Reagent substitution</b> (maintainer decision on issue #154, 2026-08-12): upstream's cost is
 * green + blue + magma slime crystal + a gold block. Slime crystals do not exist until the
 * world-content milestone, so the shipped recipe substitutes slime block + magma block + gold block,
 * one block loosely standing in for each crystal colour. The revert to parity is tracked in
 * docs/SCOPE.md's deferred backlog, and because the cost lives here it is a JSON edit rather than a
 * code change when the crystals ship.
 *
 * <p>Registered as a NeoForge datapack registry with a network codec, same as {@link ModifierRecipe}
 * and {@code material.Material}, so {@code /reload} picks up an edit and the client can resolve the
 * same answer the server did (which is what lets the Tool Station screen explain a rejection with no
 * packet of its own).
 */
public record EmbossingRecipe(List<Ingredient> reagents) {

    public static final ResourceKey<Registry<EmbossingRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "embossing_recipe"));

    public static final Codec<EmbossingRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.listOf().fieldOf("reagents").forGetter(EmbossingRecipe::reagents))
            .apply(instance, EmbossingRecipe::new));
}
