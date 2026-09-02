package dev.gkissel.forgeweave.trait;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * A trait a datapack defines, under {@code data/<namespace>/forgeweave/trait_definition/<name>.json}
 * -- ADR-0004 item 3, delivered for traits by issue #832 (maintainer decision 2026-09-02: option
 * (a), traits only; modifier definitions stay deferred to M8).
 *
 * <p>The JSON is flat: a {@code behavior} field names one of {@link TraitBehaviors}' parameterized
 * behaviours and the remaining fields are that behaviour's parameters, so
 * <pre>{@code
 * { "behavior": "forgeweave:effect_on_hit",
 *   "effect": "minecraft:poison", "duration": 100, "amplifier": 0 }
 * }</pre>
 * is a whole definition. The file name is the trait id a material JSON then names ({@code
 * forgeweave:traits} on the tool stays a plain id list, so nothing about save data or the
 * save-compat corpus moves), and the pack supplies the id's {@code trait.<namespace>.<path>.name}
 * / {@code .description} lang keys -- exactly the keys every built-in trait already uses, so
 * tooltips, the Tool Station panel and the guide book need no code change to show one.
 *
 * <p>A NeoForge datapack registry with the codec as its network codec ({@code
 * Forgeweave#registerDataPackRegistries}, the {@code Material} idiom), so definitions sync to the
 * client and a top-level {@code "neoforge:conditions"} array existence-gates a definition the same
 * way it gates a material. {@link ForgeweaveTraits#onTagsUpdated} snapshots the loaded registry
 * into the static lookup every trait hook reads from, since those hooks get a bare {@code
 * ItemStack} and no registry access (see {@code ForgeweaveTraits}' class javadoc).
 *
 * @param behavior which {@link TraitBehaviors} entry built {@link #trait}
 * @param trait the runtime behaviour, wired into every seam like a Java-registered trait
 */
public record TraitDefinition(ResourceLocation behavior, Trait trait) {

    public static final ResourceKey<Registry<TraitDefinition>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "trait_definition"));

    /** Dispatches on {@code behavior}; an unknown behaviour id is a parse error, never a silent no-op trait. */
    public static final Codec<TraitDefinition> CODEC = TraitBehaviors.CODEC;
}
