package dev.gkissel.forgeweave.combat;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectCategory;

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
    UNDEAD,
    /**
     * The target's current health is below the attacker's own -- one of the M6 {@code
     * bonus_damage_vs} predicates ADR-0004's damage-scaling library batch collapses onto this enum
     * (issue #827), "dominant"'s gate. {@code false} when the blow has no attacker to compare
     * against (a projectile, a mob's own attack) rather than treating a missing wielder as
     * infinitely healthy.
     */
    BELOW_WIELDER_HEALTH,
    /**
     * The target already carries a harmful status effect -- another M6 {@code bonus_damage_vs}
     * predicate (issue #827), "opportunist"'s gate. {@link MobEffectCategory#HARMFUL} is 1.21's
     * stand-in for "a negative effect"; vanilla's own Poison and Wither both carry it.
     */
    HARMFUL_EFFECT,
    /**
     * The swing that produced this blow was fully charged -- {@link CombatHit#isFullCharge}, read
     * straight off the captured {@link CombatHit#attackStrengthScale}. The M6 {@code
     * charged_bonus_damage} library behavior's gate (issue #827), and unlike {@link #FULL_HEALTH}
     * equally meaningful from {@link CombatSeam#preHit} or {@link CombatSeam#onHit}: the charge is
     * fixed for the whole hit, not a target-state read that can move between the two hooks.
     */
    FULL_CHARGE;

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
            case BELOW_WIELDER_HEALTH -> hit.attacker() != null && hit.target().getHealth() < hit.attacker().getHealth();
            case HARMFUL_EFFECT -> hit.target().getActiveEffects().stream()
                    .anyMatch(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL);
            case FULL_CHARGE -> hit.isFullCharge();
        };
    }
}
