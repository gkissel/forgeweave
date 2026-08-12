package dev.gkissel.forgeweave.combat;

/**
 * A hit deals {@code fraction} extra damage, as a fraction of the blow's original (pre-mitigation)
 * damage -- {@link BonusDamageVsBlocking} with its condition lifted out into {@link ConditionalSeam},
 * which is what lets the lumber axe's "timber" (issue #157: +15% against a target that has lost no
 * health) reuse it instead of adding a fourth near-identical damage class.
 *
 * <p>Scales the original rather than the running damage, so the magnitude is order-independent no
 * matter where in the chain this seam sits -- the rule {@link CombatSeam#preHit} sets for all of them.
 */
public final class BonusDamageFraction implements CombatSeam {
    private final float fraction;

    public BonusDamageFraction(float fraction) {
        this.fraction = fraction;
    }

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return damage + originalDamage * fraction;
    }
}
