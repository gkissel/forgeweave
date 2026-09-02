package dev.gkissel.forgeweave.trait;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;

/**
 * A blow taken while this piece is worn leaves a status effect on the <em>wearer</em> -- the M6
 * armor library's {@code effect_on_hurt(effect, duration, amplifier)} (issue #831):
 * regeneration-after-being-hit, a panic speed burst, a resistance flicker. Distinct from
 * {@link EffectOnAttacker} only in who is marked, which is why the two are separate classes rather
 * than one with a target field -- the attacker side additionally has to prove a direct living
 * attacker exists, and the wearer side never can fail.
 *
 * <p>A blow already cancelled by an earlier piece ({@code damage <= 0}) does not trigger it: the
 * point of reference is {@code ForgeweaveTraits#RESTORE}, which makes the same check.
 *
 * @param effect the effect to leave on the wearer
 * @param durationTicks how long it lasts
 * @param amplifier 0-indexed, so {@code 0} is level I
 */
public record EffectOnHurt(Holder<MobEffect> effect, int durationTicks, int amplifier) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (blow.damage() <= 0.0F) {
            return;
        }
        defense.defender().addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
    }
}
