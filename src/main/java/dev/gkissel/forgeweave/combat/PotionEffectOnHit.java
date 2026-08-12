package dev.gkissel.forgeweave.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A hit applies a {@link MobEffectInstance} to the target -- ADR-0004's M6 parameterized-behavior-
 * library candidate {@code potion_effect_on_hit}. Forgeweave's M1 shovel retrofit ("flatten",
 * docs/SCOPE.md M3 issue #164) is the first consumer; magnitude decided on the issue (2026-08-12):
 * Slowness I ({@code amplifier} 0) for 1.5 seconds (30 ticks).
 */
public final class PotionEffectOnHit implements CombatSeam {
    private final Holder<MobEffect> effect;
    private final int amplifier;
    private final int durationTicks;

    public PotionEffectOnHit(Holder<MobEffect> effect, int amplifier, int durationTicks) {
        this.effect = effect;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        hit.target().addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
    }
}
