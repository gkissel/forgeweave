package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.HitCondition;

/**
 * When a parameterized defensive behaviour applies -- the worn-armor mirror of {@link HitCondition},
 * which reads a {@code CombatHit} and therefore cannot answer anything about the <em>wearer</em>
 * (issue #831, M6 armor trait library).
 *
 * <p>ponytail: two constants, one per shipped consumer ({@link InvulnerabilityWindow}'s "struck at
 * full health"). Add a constant when a behaviour needs it, exactly as {@link HitCondition}'s own
 * javadoc says.
 */
public enum DefenseCondition {
    /** Always. */
    ANY,
    /** The wearer has lost no health yet when the blow arrives. */
    FULL_HEALTH;

    public boolean matches(CombatDefense defense) {
        return switch (this) {
            case ANY -> true;
            case FULL_HEALTH -> defense.defender().getHealth() >= defense.defender().getMaxHealth();
        };
    }
}
