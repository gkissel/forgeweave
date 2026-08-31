package dev.gkissel.forgeweave.combat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A landed hit removes up to {@code count} of the target's positive status effects -- ADR-0004's M6
 * on-hit effect library batch (issue #828) {@code strip_effects(chance, count, chargedOnly)}, the
 * reference instance a leveled (I-III), charged-hit-only buff strip.
 *
 * <p>{@code chance} and {@code chargedOnly} are not fields here, the same reasoning {@link
 * EffectOnHit} and {@link EffectOnSelfOnHit} give: {@link HitCondition}/{@link ConditionalSeam}
 * already generalize both, so a leveled instance is {@code new ConditionalSeam(FULL_CHARGE, chance,
 * new StripEffects(count))} at the trait-definition call site rather than three near-duplicate
 * fields on this class.
 *
 * <p>ponytail: target's active effects in their natural iteration order, first {@code count}
 * removed -- no shuffle. {@link MobEffectInstance}'s own iteration order is not player-visible
 * (there is no "which buff got stripped" UI beyond the buff vanishing), so a random pick would add a
 * {@code RandomSource} dependency for a difference nothing can observe.
 *
 * @param count how many positive effects to remove, at most
 */
public record StripEffects(int count) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (count <= 0) {
            return;
        }
        List<Holder<MobEffect>> positive = new ArrayList<>();
        for (MobEffectInstance instance : hit.target().getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                positive.add(instance.getEffect());
                if (positive.size() >= count) {
                    break;
                }
            }
        }
        for (Holder<MobEffect> effect : positive) {
            hit.target().removeEffect(effect);
        }
    }
}
