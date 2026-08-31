package dev.gkissel.forgeweave.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A hit applies a {@link MobEffectInstance} to the target, optionally escalating the amplifier one
 * step deeper on every repeat landed hit instead of merely refreshing -- ADR-0004's M6 on-hit
 * effect library batch (issue #828) {@code effect_on_hit(effect, duration, amplifier, chance,
 * stackingCap)}. Covers wither-stacking, weakness, a brief root/slow, a glowing mark, and a
 * deliberately unhelpful regeneration-on-target novelty (issue #828's own list) with one class.
 *
 * <p>{@code chance} is not a field here: {@link ConditionalSeam} already generalizes "roll a chance
 * before an on-hit-only delegate runs" ({@link ConditionalSeam#chance}, the same reasoning
 * {@code bonus_damage_vs}'s javadoc gives for not duplicating {@link HitCondition} machinery), so an
 * unconditional instance (like the migrated {@code poisonous}, chance 1) is constructed bare and a
 * chance-gated one is wrapped: {@code new ConditionalSeam(ANY, chance, new EffectOnHit(...))}.
 *
 * <p>{@link #stackingCap} generalizes {@link StackingSlownessOnHitSeam}'s read-current-then-escalate
 * shape: {@code 0} means "only refresh at {@code amplifier}" (every non-stacking instance below,
 * {@code poisonous}'s migrated shape included); a positive value reads the target's current
 * amplifier of this same effect (or {@code amplifier - 1} with none yet) and adds one, capped at
 * {@code stackingCap}.
 *
 * @param effect the potion effect to add
 * @param durationTicks how long each application lasts
 * @param amplifier the amplifier a fresh application starts at (0-indexed, so {@code 0} is level I)
 * @param stackingCap 0 for a non-stacking refresh, otherwise the deepest amplifier repeat hits may
 *     escalate to
 */
public record EffectOnHit(Holder<MobEffect> effect, int durationTicks, int amplifier, int stackingCap)
        implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        int applied = amplifier;
        if (stackingCap > 0) {
            MobEffectInstance current = hit.target().getEffect(effect);
            int base = current == null ? amplifier - 1 : current.getAmplifier();
            applied = Math.min(stackingCap, base + 1);
        }
        hit.target().addEffect(new MobEffectInstance(effect, durationTicks, applied));
    }
}
