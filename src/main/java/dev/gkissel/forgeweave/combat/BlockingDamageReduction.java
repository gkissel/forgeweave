package dev.gkissel.forgeweave.combat;

/**
 * Blocking shaves a flat amount off an incoming blow, never below a floor -- stiff, steel's trait
 * (issue #229), ported from upstream 1.12's {@code TraitStiff#onBlock}:
 * {@code event.setAmount(Math.max(1f, event.getAmount() - 1f))}. Both numbers arrive through the
 * constructor per ADR-0004's M6 library shape; the floor is upstream's own quirk -- a stiff block
 * always lets at least 1 damage through, it never fully negates.
 */
public record BlockingDamageReduction(float reduction, float floor) implements CombatSeam {

    @Override
    public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
        return defense.blocking() ? Math.max(floor, damage - reduction) : damage;
    }
}
