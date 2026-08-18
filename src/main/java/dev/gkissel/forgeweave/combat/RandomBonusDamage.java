package dev.gkissel.forgeweave.combat;

/**
 * A hit deals {@code base} extra flat damage plus a fresh {@code [0, range)} roll -- upstream 1.12's
 * hammer, the only consumer: {@code Hammer#dealDamage}'s {@code damage += 3 + TConstruct.random
 * .nextInt(4)} against the undead (parity audit T35, issue #466), gated the same way
 * {@link FlatBonusDamage} is gated for a conditional bonus -- through {@link ConditionalSeam}/
 * {@link HitCondition} rather than a predicate of its own. ADR-0004 M6 library shape: two numbers,
 * nothing else.
 */
public final class RandomBonusDamage implements CombatSeam {
    private final float base;
    private final int range;

    public RandomBonusDamage(float base, int range) {
        this.base = base;
        this.range = range;
    }

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return damage + base + hit.level().getRandom().nextInt(range);
    }
}
