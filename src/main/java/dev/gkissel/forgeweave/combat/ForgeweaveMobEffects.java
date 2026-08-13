package dev.gkissel.forgeweave.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's own status effects -- deliberately just the registry plumbing. The scimitar's bleed
 * (issue #159): see {@link LacerateEffect} for why a DoT is a status effect at all, and
 * {@link Lacerate} for the seam that applies it. Issue #229's combat traits added sharp's own bleed
 * ({@link BleedEffect}) and two inert marks ({@link MarkerEffect}): splintering's stack counter and
 * enderference's teleport block.
 */
public final class ForgeweaveMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Forgeweave.MODID);

    public static final DeferredHolder<MobEffect, LacerateEffect> LACERATE =
            MOB_EFFECTS.register("lacerate", LacerateEffect::new);

    /** Sharp's DoT (issue #229, upstream {@code TraitSharp.DoT}). */
    public static final DeferredHolder<MobEffect, BleedEffect> BLEED =
            MOB_EFFECTS.register("bleed", BleedEffect::new);

    /** Splintering's stack counter (issue #229, upstream {@code TraitSplintering.Splinter}); bone-white. */
    public static final DeferredHolder<MobEffect, MarkerEffect> SPLINTER =
            MOB_EFFECTS.register("splinter", () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0xE8E5D2));

    /**
     * Enderference's teleport block (issue #229, upstream {@code TraitEnderference.Enderference},
     * including its 0x21985f color). {@code ForgeweaveTraits#onEnderTeleport} is what reads it.
     */
    public static final DeferredHolder<MobEffect, MarkerEffect> ENDERFERENCE =
            MOB_EFFECTS.register("enderference", () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0x21985F));

    private ForgeweaveMobEffects() {}
}
