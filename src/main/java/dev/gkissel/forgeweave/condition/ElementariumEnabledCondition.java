package dev.gkissel.forgeweave.condition;

import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * {@code forgeweave:elementarium_materials_enabled} -- the one existence condition {@code
 * elementariumMaterials} (D-M8-19, issue #998) needs. Every other Track A preset is existence-gated
 * only (D-M8-5: material presets are never toggled), but Elementarium's are <em>generated</em> from
 * its {@code c:ingots/*} tag family rather than hand-authored, so a pack that dislikes the
 * interpolation needs a way out that is not hand-editing generated JSON -- this condition is that
 * way out, added to every generated Elementarium material's {@code neoforge:conditions} array
 * alongside {@code neoforge:mod_loaded("elementarium")}.
 *
 * <p>Reading a live {@code SERVER} config value from a registry-element condition is safe despite
 * the {@code TAGS_INVALID} evaluation context {@link dev.gkissel.forgeweave.material.Material}'s own
 * javadoc warns tag-based conditions about: {@link ForgeweaveConfig#enabled} reads a {@code
 * ModConfigSpec} value, not a tag, and it already answers permissively (materials stay enabled)
 * whenever no server has spoken yet -- the same fallback every other {@code enabled} caller in this
 * codebase gets, so an early evaluation (before a world's {@code SERVER} config loads) is not a new
 * failure mode this condition introduces.
 */
public final class ElementariumEnabledCondition implements ICondition {
    public static final MapCodec<ElementariumEnabledCondition> CODEC =
            MapCodec.unit(ElementariumEnabledCondition::new);

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Forgeweave.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ElementariumEnabledCondition>> TYPE =
            CONDITION_CODECS.register("elementarium_materials_enabled", () -> CODEC);

    @Override
    public boolean test(IContext context) {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.ELEMENTARIUM_MATERIALS);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "forgeweave:elementarium_materials_enabled";
    }
}
