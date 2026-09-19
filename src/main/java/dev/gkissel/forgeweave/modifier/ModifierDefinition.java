package dev.gkissel.forgeweave.modifier;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.modifier.Modifier;

/**
 * A modifier a datapack defines, under {@code data/<namespace>/forgeweave/modifier_definition/
 * <name>.json} -- ADR-0004 item 3's second half, delivered by issue #973 (maintainer decision
 * 2026-09-18) and closing the ADR out. The trait half shipped at M6 as {@link
 * dev.gkissel.forgeweave.trait.TraitDefinition}, and this registry is deliberately the same shape
 * down to the field names, so a pack author learns one idiom rather than two.
 *
 * <p>The JSON is flat: a {@code behavior} field names one of {@link ModifierBehaviors}'
 * parameterized behaviors and the remaining fields are that behavior's parameters, so
 * <pre>{@code
 * { "behavior": "forgeweave:stat_bonus",
 *   "stat": "durability", "flat": 500.0 }
 * }</pre>
 * is a whole definition. The file name is the modifier id a {@link ModifierRecipe} then applies and
 * a tool then stores, and the pack supplies that id's {@code modifier.<namespace>.<path>.name} /
 * {@code .description} lang keys -- exactly the keys every built-in modifier already uses, so
 * tooltips, the Tool Station panel and the guide book need no code change to show one.
 *
 * <p><b>Serialization does not move</b> (ADR-0004 item 2): a tool stores {@code id + level} and
 * nothing else ({@link ModifierEntry}), so every save and every save-compat fixture stays valid, and
 * a tool carrying a pack-defined id whose datapack is gone keeps the entry inertly -- the same rule
 * {@link ForgeweaveModifiers#get} already applied to any unimplemented id.
 *
 * <p>A NeoForge datapack registry with the codec as its network codec ({@code
 * Forgeweave#registerDataPackRegistries}, the {@code Material} idiom), so definitions sync to the
 * client and a top-level {@code "neoforge:conditions"} array existence-gates a definition the same
 * way it gates a material. {@link ForgeweaveModifiers#onTagsUpdated} snapshots the loaded registry
 * into the static lookup every modifier hook reads from, since those hooks get a bare
 * {@code ItemStack} and no registry access.
 *
 * @param behavior which {@link ModifierBehaviors} entry built {@link #modifier}
 * @param modifier the runtime behavior, wired into every seam like a Java-registered modifier
 */
public record ModifierDefinition(ResourceLocation behavior, Modifier modifier) {

    public static final ResourceKey<Registry<ModifierDefinition>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "modifier_definition"));

    /** Dispatches on {@code behavior}; an unknown behavior id is a parse error, never a silent no-op modifier. */
    public static final Codec<ModifierDefinition> CODEC = ModifierBehaviors.CODEC;
}
