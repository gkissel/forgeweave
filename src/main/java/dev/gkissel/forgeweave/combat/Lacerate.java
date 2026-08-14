package dev.gkissel.forgeweave.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Applies a stacking damage-over-time on hit" -- the scimitar's innate (docs/SCOPE.md M3 combat
 * innates table; magnitudes decided by the maintainer on issue #159: 1 damage/second for 4 seconds
 * per application, stacking to at most 3 concurrent bleeds).
 *
 * <p>Every number is a constructor parameter and nothing here names the scimitar, per ADR-0004's M6
 * commitment ({@link CombatSeam}): this is the {@code potion_effect_on_hit} shape from that list,
 * with a stack cap. {@code ForgeweaveInnates} is what decides it applies to the scimitar and no other tool.
 *
 * @param effect the effect to apply -- {@link ForgeweaveMobEffects#LACERATE} for the scimitar
 * @param durationTicks how long one application lasts, refreshed by each new one
 * @param maxStacks the cap on concurrent applications; an amplifier of {@code maxStacks - 1}
 */
public record Lacerate(Holder<MobEffect> effect, int durationTicks, int maxStacks) implements CombatSeam {

    /**
     * Adds a bleed, or deepens and refreshes the one already running.
     *
     * <p>Vanilla's own {@code MobEffectInstance#update} rule is what enforces the cap without any
     * bookkeeping here: a re-application at the cap has the same amplifier and the full duration, so
     * it refreshes the timer and cannot go past {@link #maxStacks}. A hit that dealt no health (fully
     * absorbed, target already dead) applies nothing -- on-hit means the blow actually landed.
     *
     * <p>Remembers the attacker on the target ({@code LivingEntity#setLastHurtByMob}, upstream
     * {@code TraitSharp#afterHit}'s {@code target.setLastAttackedEntity(player)}) so a DoT tick that
     * builds its damage source from {@link LivingEntity#getLastHurtByMob} -- {@code BleedEffect}'s
     * armor-ignoring source -- credits the attacker for a kill instead of a source that names no one
     * (issue #297 parity fix).
     */
    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (damageDealt <= 0.0F) {
            return;
        }
        LivingEntity target = hit.target();
        MobEffectInstance current = target.getEffect(effect);
        int amplifier = current == null ? 0 : Math.min(current.getAmplifier() + 1, maxStacks - 1);
        target.addEffect(new MobEffectInstance(effect, durationTicks, amplifier), hit.attacker());
        if (hit.attacker() != null) {
            target.setLastHurtByMob(hit.attacker());
        }
    }
}
