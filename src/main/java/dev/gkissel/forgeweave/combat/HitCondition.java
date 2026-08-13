package dev.gkissel.forgeweave.combat;

import net.minecraft.tags.EntityTypeTags;

/**
 * When a parameterized {@link CombatSeam} applies. ADR-0004 commits M3's combat behaviors to a
 * datapack-constructible shape at M6, so the seams below take their "vs what" as this enum rather
 * than each hard-coding a predicate: {@code bonus_damage_vs: full_health} is a JSON field, a lambda
 * is not.
 *
 * <p>ponytail: one constant per shipped consumer -- three riders (issue #157's decision comment)
 * plus the three gates the M3.2 combat traits added (issue #229). Add a constant when a behavior
 * needs it, not before.
 */
public enum HitCondition {
    /** Always. */
    ANY,
    /** The target has lost no health yet -- the lumber axe's "timber" rider. */
    FULL_HEALTH,
    /** The target is wearing armor -- the vein hammer's "crushing blow" rider. */
    ARMORED,
    /** The target is not fire-immune -- hellish's "non-Nether mobs" gate (upstream {@code TraitHellish}). */
    NOT_FIRE_IMMUNE,
    /** The target is on fire -- superheat's gate (upstream {@code TraitSuperheat}). */
    BURNING,
    /**
     * The target is undead -- holy's gate. 1.21's {@code minecraft:undead} entity-type tag stands in
     * for upstream's {@code EnumCreatureAttribute.UNDEAD}, which 1.21 has no equivalent of (the same
     * attribute-to-tag adaptation {@link BonusDamageVsSeam} records for smite).
     */
    UNDEAD;

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
            case NOT_FIRE_IMMUNE -> !hit.target().fireImmune();
            case BURNING -> hit.target().isOnFire();
            case UNDEAD -> hit.target().getType().is(EntityTypeTags.UNDEAD);
        };
    }
}
