package dev.gkissel.forgeweave.combat;

/**
 * Runs another seam only when the blow matches -- ADR-0004's M6 library gains one gate instead of a
 * conditional variant of every behavior in it. Issue #157's riders need two gates the shipped seams
 * did not have (a target that has lost no health, a target wearing armor, and a per-hit chance), and
 * both compose with any existing seam rather than duplicating it: the hammer's "concussion" is
 * {@code new ConditionalSeam(ANY, 0.2F, new PotionEffectOnHit(...))}, the vein hammer's "crushing
 * blow" is {@code new ConditionalSeam(ARMORED, 1.0F, new KnockbackOnHitSeam(...))}.
 *
 * <p>The roll is made per hook, so a {@code chance} below 1 belongs on a delegate that acts in one
 * hook only ({@link CombatSeam#onHit}, as both of the above do). A gate that is purely a
 * {@link HitCondition} has {@code chance} 1 and is therefore rolled-free in both hooks.
 *
 * @param when which targets the delegate applies to
 * @param chance 0..1 probability, rolled per hook call
 * @param delegate the behavior to gate
 */
public record ConditionalSeam(HitCondition when, float chance, CombatSeam delegate) implements CombatSeam {

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return passes(hit) ? delegate.preHit(hit, originalDamage, damage) : damage;
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (passes(hit)) {
            delegate.onHit(hit, damageDealt);
        }
    }

    @Override
    public void postKill(CombatHit hit) {
        if (passes(hit)) {
            delegate.postKill(hit);
        }
    }

    private boolean passes(CombatHit hit) {
        return when.matches(hit) && (chance >= 1.0F || hit.level().getRandom().nextFloat() < chance);
    }
}
