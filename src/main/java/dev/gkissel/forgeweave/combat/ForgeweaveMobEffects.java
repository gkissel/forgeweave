package dev.gkissel.forgeweave.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's own status effects. Only one so far -- the scimitar's bleed (issue #159) -- so this
 * is deliberately just the registry plumbing; see {@link LacerateEffect} for why that behavior is a
 * status effect at all, and {@link Lacerate} for the seam that applies it.
 */
public final class ForgeweaveMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Forgeweave.MODID);

    public static final DeferredHolder<MobEffect, LacerateEffect> LACERATE =
            MOB_EFFECTS.register("lacerate", LacerateEffect::new);

    private ForgeweaveMobEffects() {}
}
