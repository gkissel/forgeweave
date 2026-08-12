package dev.gkissel.forgeweave.combat;

/**
 * When a parameterized {@link CombatSeam} applies. ADR-0004 commits M3's combat behaviors to a
 * datapack-constructible shape at M6, so the seams below take their "vs what" as this enum rather
 * than each hard-coding a predicate: {@code bonus_damage_vs: full_health} is a JSON field, a lambda
 * is not.
 *
 * <p>ponytail: three constants, one per shipped rider (issue #157's decision comment). Add a
 * constant when a behavior needs it, not before.
 */
public enum HitCondition {
    /** Always. */
    ANY,
    /** The target has lost no health yet -- the lumber axe's "timber" rider. */
    FULL_HEALTH,
    /** The target is wearing armor -- the vein hammer's "crushing blow" rider. */
    ARMORED;

    /**
     * Evaluated before the blow's damage is applied for {@link CombatSeam#preHit}, and immediately
     * after for {@link CombatSeam#onHit}. {@link #FULL_HEALTH} is therefore only meaningful pre-hit,
     * which is where the one seam that uses it reads it.
     */
    public boolean matches(CombatHit hit) {
        return switch (this) {
            case ANY -> true;
            case FULL_HEALTH -> hit.target().getHealth() >= hit.target().getMaxHealth();
            case ARMORED -> hit.target().getArmorValue() > 0;
        };
    }
}
