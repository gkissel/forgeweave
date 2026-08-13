package dev.gkissel.forgeweave.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Repeated hits mark the target and each mark deepens the next hit -- splintering, bone's head
 * trait (issue #229), ported from upstream 1.12's {@code TraitSplintering}: the damage hook adds
 * {@code 0.3 * (amplifier + 1)} while the target carries the splinter mark, and {@code afterHit}
 * re-applies the mark at {@code min(5, amplifier + 1)} for 40 ticks. With upstream's constants that
 * is +0.3 per landed hit, capping at +1.8 ({@code maxStacks} 6).
 *
 * <p>Same marker-as-vanilla-effect choice as {@link Lacerate}/{@code LacerateEffect}: the mark needs
 * a countdown the game already saves, syncs and ticks, and vanilla's {@code MobEffectInstance#update}
 * rule (same amplifier + full duration refreshes) enforces the cap with no bookkeeping here. The
 * first hit of a fight is unmarked and gets no bonus, exactly as upstream's read-then-apply order has it.
 *
 * @param mark the inert marker effect carrying the stack count as its amplifier
 * @param bonusPerStack flat damage added per stack the target already carries
 * @param maxStacks the stack cap ({@code amplifier} caps at {@code maxStacks - 1})
 * @param durationTicks how long a mark lasts, refreshed by every landed hit
 */
public record StackingHitBonus(Holder<MobEffect> mark, float bonusPerStack, int maxStacks, int durationTicks)
        implements CombatSeam {

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        MobEffectInstance current = hit.target().getEffect(mark);
        return current == null ? damage : damage + bonusPerStack * (current.getAmplifier() + 1);
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        MobEffectInstance current = hit.target().getEffect(mark);
        int amplifier = current == null ? 0 : Math.min(maxStacks - 1, current.getAmplifier() + 1);
        hit.target().addEffect(new MobEffectInstance(mark, durationTicks, amplifier), hit.attacker());
    }
}
