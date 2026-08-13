package dev.gkissel.forgeweave.combat;

/**
 * A hit deals {@code bonus} extra flat damage -- the unconditional core of upstream 1.12's
 * {@code TraitHellish}/{@code TraitHoly} damage hooks ({@code newDamage += bonusDamage}), with the
 * "vs what" lifted out into {@link ConditionalSeam}/{@link HitCondition} the same way
 * {@link BonusDamageFraction} lifted {@link BonusDamageVsBlocking}'s condition. ADR-0004 M6 library
 * shape: one number, nothing else.
 */
public final class FlatBonusDamage implements CombatSeam {
    private final float bonus;

    public FlatBonusDamage(float bonus) {
        this.bonus = bonus;
    }

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return damage + bonus;
    }
}
