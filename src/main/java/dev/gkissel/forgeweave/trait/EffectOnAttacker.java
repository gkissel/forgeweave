package dev.gkissel.forgeweave.trait;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;
import dev.gkissel.forgeweave.combat.EffectOnHit;

/**
 * A blow taken while this piece is worn leaves a status effect on whoever struck it -- the M6 armor
 * library's {@code effect_on_attacker(effect, duration, amplifier, chance)} (issue #831), the
 * defensive mirror of #828's {@link EffectOnHit}. One class for slow-attackers, blind-attackers and
 * weaken-attackers alike.
 *
 * <p>Only a <em>direct</em> living attacker is marked, the same gate {@code
 * ForgeweaveTraits#PIERCING_GUARD} uses (the clone's {@code
 * OnAttackedModifierHook#isDirectDamage}): an arrow, a potion or a falling anvil has nobody to
 * debuff.
 *
 * <p>{@code chance} is a field here rather than a {@code ConditionalSeam} wrap, unlike
 * {@link EffectOnHit}'s: that gate reads a {@code CombatHit} and does not implement
 * {@code CombatSeam#onDefend} at all, so wrapping a defensive behaviour in it would silently
 * disable the behaviour instead of gating it.
 *
 * @param effect the effect to leave on the attacker
 * @param durationTicks how long it lasts
 * @param amplifier 0-indexed, so {@code 0} is level I
 * @param chance 0..1, rolled per blow taken
 */
public record EffectOnAttacker(Holder<MobEffect> effect, int durationTicks, int amplifier, float chance)
        implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        LivingEntity attacker = ForgeweaveTraits.directAttacker(defense);
        if (attacker == null || defense.level().getRandom().nextFloat() >= chance) {
            return;
        }
        attacker.addEffect(new MobEffectInstance(effect, durationTicks, amplifier), defense.defender());
    }
}
