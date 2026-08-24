package dev.gkissel.forgeweave.combat;

/**
 * One blow as the defender's <em>worn armor</em> sees it -- the mutable accumulator every
 * {@link CombatSeam#onDefend} call on every worn piece writes into, and the one thing
 * {@link CombatSeams} reads back to settle the hit (issue #680, M4-5; SCOPE.md D8). Two moments of
 * the same blow live here, because the 1.20 clone settles them at two different events:
 *
 * <ul>
 *   <li>{@link #damage} is the pre-mitigation amount, upstream's {@code MODIFY_HURT} hook on
 *       {@code LivingHurtEvent} ({@code ToolEvents#livingHurt}): a piece may lower it, or zero it to
 *       cancel the blow outright, before armor ever sees it.
 *   <li>{@link #protection} and {@link #flatReduction} are settled <em>after</em> armor and vanilla
 *       enchantments, on {@code LivingDamageEvent.Pre} -- the protection value is upstream's
 *       {@code ProtectionModifierHook} {@code modifierValue} (1 = one level of vanilla Protection,
 *       {@code /25} of the post-armor damage, capped at 20 = 80%), and the flat reduction is
 *       upstream's {@code MODIFY_DAMAGE} hook (warded).
 * </ul>
 *
 * <p>ponytail: a class with three floats, not a builder or a context object. When M7's leveling
 * needs to know which piece contributed what, add the bookkeeping then.
 */
public final class DefendedBlow {
    private final float originalDamage;
    private float damage;
    private float protection;
    private float flatReduction;

    public DefendedBlow(float damage) {
        this.originalDamage = damage;
        this.damage = damage;
    }

    /** The damage before any piece touched it, fixed for the whole walk. */
    public float originalDamage() {
        return originalDamage;
    }

    /** The pre-mitigation damage as every earlier piece left it. */
    public float damage() {
        return damage;
    }

    /** Sets the pre-mitigation damage; {@code 0} or less cancels the blow ({@link CombatSeams}). */
    public void setDamage(float damage) {
        this.damage = damage;
    }

    /** Upstream's {@code modifierValue}: the summed protection so far, vanilla Protection levels excluded. */
    public float protection() {
        return protection;
    }

    /** Adds protection; negative values are allowed (depth protection above its range). */
    public void addProtection(float amount) {
        this.protection += amount;
    }

    /** Flat damage removed after armor and protection, floored at 1 (warded). */
    public float flatReduction() {
        return flatReduction;
    }

    public void addFlatReduction(float amount) {
        this.flatReduction += amount;
    }
}
