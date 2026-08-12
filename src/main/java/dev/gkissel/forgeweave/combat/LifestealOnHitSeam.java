package dev.gkissel.forgeweave.combat;

/**
 * Necrotic (issue #162): heals the attacker for a fraction of the damage just dealt, upstream
 * {@code ModNecrotic#afterHit} ported whole. {@code fraction} is the modifier's raw level (here also
 * its display level -- one bone per level, upstream's {@code LevelAspect}) times 10%, already resolved
 * by the time this instance is constructed, the same shape {@link BonusDamageVsSeam}'s javadoc
 * explains.
 */
public final class LifestealOnHitSeam implements CombatSeam {
    private final float fraction;

    public LifestealOnHitSeam(float fraction) {
        this.fraction = fraction;
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (hit.attacker() != null && damageDealt > 0.0F) {
            hit.attacker().heal(damageDealt * fraction);
        }
    }
}
