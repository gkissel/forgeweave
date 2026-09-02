package dev.gkissel.forgeweave.combat;

/**
 * A hit deals {@code bonus} extra flat damage -- the unconditional core of upstream 1.12's
 * {@code TraitHellish}/{@code TraitHoly} damage hooks ({@code newDamage += bonusDamage}), with the
 * "vs what" lifted out into {@link ConditionalSeam}/{@link HitCondition} the same way
 * {@link BonusDamageFraction} lifted {@link BonusDamageVsBlocking}'s condition. ADR-0004 M6 library
 * shape: one number, nothing else -- a record since issue #832 so the datapack codec can read it
 * back ({@code TraitBehaviors}' {@code bonus_damage_vs}).
 */
public record FlatBonusDamage(float bonus) implements CombatSeam {

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return damage + bonus;
    }
}
