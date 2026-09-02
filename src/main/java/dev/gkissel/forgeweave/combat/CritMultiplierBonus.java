package dev.gkissel.forgeweave.combat;

/**
 * Widens a critical hit's own multiplier by {@code extra}, on top of whatever vanilla's crit roll
 * (or NeoForge's {@code CriticalHitEvent}) already settled on -- ADR-0004's M6 damage-scaling
 * library batch (issue #827) collapsing the reference addons' "bonus crit damage" trait class into
 * one parameter (a record since issue #832, so the datapack codec can read it back).
 *
 * <p>{@link CombatSeams#weaponPass} hands every seam damage already divided by {@link
 * CombatHit#critMultiplier} and re-multiplies the chain's result by it afterward (issue #422's
 * unwind of {@code attackStrengthScale x critMultiplier}), so a seam cannot simply add
 * {@code originalDamage * extra}: that term would be re-multiplied by {@code critMultiplier} along
 * with everything else and land as {@code extra * critMultiplier} instead of {@code extra}.
 * Dividing the addition by {@link CombatHit#critMultiplier} here cancels that re-multiplication, so
 * the net effect on a critical hit's final damage is exactly {@code + originalDamage * extra} -- as
 * if {@code critMultiplier} itself had been {@code extra} higher for this blow only. A non-critical
 * blow ({@link CombatHit#isCritical}) is untouched, matching what "extra crit damage" means.
 */
public record CritMultiplierBonus(float extra) implements CombatSeam {

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return hit.isCritical() ? damage + originalDamage * extra / hit.critMultiplier() : damage;
    }
}
