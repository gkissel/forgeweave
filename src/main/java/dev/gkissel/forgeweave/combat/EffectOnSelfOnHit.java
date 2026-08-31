package dev.gkissel.forgeweave.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The self-buff mirror of {@link EffectOnHit}: a landed hit applies a {@link MobEffectInstance} to
 * the <b>attacker</b> instead of the target -- ADR-0004's M6 on-hit effect library batch (issue
 * #828) {@code effect_on_self_on_hit(effect, duration, amplifier, chargedOnly)}, the reference
 * instance a speed burst on a charged hit.
 *
 * <p>{@code chargedOnly} is not a field here for the same reason {@code charged_bonus_damage}'s
 * levels ({@code ForgeweaveTraits#chargedStrike}) do not carry it as one: {@link HitCondition#FULL_CHARGE}
 * plus {@link ConditionalSeam} already express "only on a fully-charged swing", so the gate lives at
 * the trait-definition call site (a bare instance for {@code chargedOnly = false}, {@code new
 * ConditionalSeam(HitCondition.FULL_CHARGE, 1.0F, new EffectOnSelfOnHit(...))} otherwise) rather
 * than duplicating that machinery inside this class.
 *
 * <p>A blow with no attacker (a mob's own attack, a projectile with no living shooter) has no one to
 * buff and is a no-op.
 *
 * @param effect the potion effect to add to the attacker
 * @param durationTicks how long the effect lasts
 * @param amplifier the effect's amplifier (0-indexed, so {@code 0} is level I)
 */
public record EffectOnSelfOnHit(Holder<MobEffect> effect, int durationTicks, int amplifier) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (hit.attacker() != null) {
            hit.attacker().addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
        }
    }
}
