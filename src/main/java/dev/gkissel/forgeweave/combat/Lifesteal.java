package dev.gkissel.forgeweave.combat;

/**
 * Heals the attacker for a share of the damage just dealt, capped -- ADR-0004's M6 on-hit effect
 * library batch (issue #828) {@code lifesteal(fraction, cap)}, the reference instance "Vampiric".
 *
 * <p>Not a migration of {@link LifestealOnHitSeam}: that class backs necrotic, a combat
 * <em>modifier</em> whose fraction is the applied level times a fixed per-level constant and which
 * the issue never named for migration (only {@code poisonous} was). This is the M6 library's own
 * uncapped-to-capped generalization for a <em>trait</em> instance, matching how {@code
 * damage_scales_with} sits alongside rather than replacing every earlier bonus-damage seam.
 *
 * @param fraction share of the dealt damage to heal, e.g. {@code 0.15} for 15%
 * @param cap the largest single heal this may grant, in half-hearts of health
 */
public record Lifesteal(float fraction, float cap) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (hit.attacker() != null && damageDealt > 0.0F) {
            hit.attacker().heal(Math.min(damageDealt * fraction, cap));
        }
    }
}
