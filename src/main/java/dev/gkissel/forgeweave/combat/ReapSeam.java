package dev.gkissel.forgeweave.combat;

/**
 * The kama's combat rider (docs/SCOPE.md M3 issue #156, maintainer decision 2026-08-12 on the
 * issue): bonus damage against a target already below a health-fraction threshold -- "finishing"
 * damage, matching the rapier's own %-health innate in spirit but gated on the target's state
 * rather than scaling with it. No 1.12 counterpart; both the threshold and the bonus are the
 * maintainer's own numbers (25% health, +25% damage).
 *
 * <p>ADR-0005 decision 5: both numbers are constructor parameters rather than constants, so a
 * GameTest can assert the boundary precisely instead of only the shipped values.
 */
public final class ReapSeam implements CombatSeam {

    public static final float DEFAULT_HEALTH_THRESHOLD = 0.25F;
    public static final float DEFAULT_DAMAGE_BONUS = 0.25F;

    /** The instance every assembled kama resolves to; see {@code Forgeweave}'s seam registration. */
    public static final ReapSeam SEAM = new ReapSeam(DEFAULT_HEALTH_THRESHOLD, DEFAULT_DAMAGE_BONUS);

    private final float healthThreshold;
    private final float damageBonus;

    public ReapSeam(float healthThreshold, float damageBonus) {
        this.healthThreshold = healthThreshold;
        this.damageBonus = damageBonus;
    }

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        float maxHealth = hit.target().getMaxHealth();
        if (maxHealth <= 0.0F) {
            return damage;
        }
        float healthFraction = hit.target().getHealth() / maxHealth;
        return healthFraction < healthThreshold ? damage * (1.0F + damageBonus) : damage;
    }
}
