package dev.gkissel.forgeweave.combat;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Stacks Slowness on the target one amplifier deeper per landed hit, capped -- upstream 1.12's
 * {@code TraitFreezing#onHit} (issue #653, parity audit T17): read the target's current Slowness
 * amplifier (or -1 with none), add one, cap at {@code maxAmplifier}, re-apply for
 * {@code durationTicks}. A {@link PotionEffectOnHitSeam} cannot express the read-then-escalate, so
 * this is its own parameterized seam (ADR-0004: magnitudes in the constructor, everything else off
 * {@link CombatHit}).
 *
 * @param durationTicks upstream's 30 -- each hit refreshes a second and a half
 * @param maxAmplifier upstream's 4, i.e. Slowness V at the deepest
 */
public record StackingSlownessOnHitSeam(int durationTicks, int maxAmplifier) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        MobEffectInstance current = hit.target().getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int amplifier = Math.min(maxAmplifier, (current == null ? -1 : current.getAmplifier()) + 1);
        hit.target().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, amplifier));
    }
}
