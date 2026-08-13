package dev.gkissel.forgeweave.combat;

/**
 * One combat behavior attached to a blow struck with a Forgeweave tool. ADR-0005 decision 3: this is
 * the <b>only</b> attachment point for combat innates (the rapier's %-health strike, the katana's
 * ramp, the scimitar's damage-over-time, ...) and combat modifiers (smite, fiery, necrotic, ...) --
 * no tool class and no modifier registers combat event handlers of its own. {@link CombatSeams}
 * drives all three hooks from NeoForge's damage and death events.
 *
 * <p>ADR-0004's M6 commitment shapes these signatures: an implementation is meant to be a
 * <em>parameterized</em> behavior ({@code bonus_damage_vs}, {@code potion_effect_on_hit}, ...) whose
 * numbers arrive through its constructor, so it can later be constructed from datapack JSON instead
 * of Java. That means an implementation takes its magnitudes as fields and reads everything else
 * from {@link CombatHit} -- never from a specific {@code ToolItem} subclass, and never from static
 * state that ties it to one tool. Whether a seam applies to a given tool at all is decided by the
 * {@link CombatSeams.Provider} that produced it, not by the seam itself.
 *
 * <p>ponytail: three hooks, matching the three moments ADR-0005 names. Upstream 1.12's
 * {@code ITrait} exposes a dozen combat-adjacent hooks; the ones no shipped behavior uses would be
 * empty seams here. Add a hook when a behavior needs it.
 */
public interface CombatSeam {

    /**
     * This blow's damage after this seam has adjusted it, called before any mitigation (armor,
     * resistance, absorption) is applied -- upstream 1.12 runs its {@code ITrait#damage} hook at the
     * same point, inside {@code ToolHelper#attackEntity}, before armor.
     *
     * <p>Chained the same way {@code Trait#miningSpeed} is: every seam sees the untouched
     * {@code originalDamage} so its magnitude is order-independent, plus the running
     * {@code damage} it must return (adjusted or not) for the next seam.
     *
     * @param originalDamage the damage before any seam touched it, fixed for the whole chain
     * @param damage the damage as adjusted by every earlier seam in the chain
     */
    default float preHit(CombatHit hit, float originalDamage, float damage) {
        return damage;
    }

    /**
     * Called once the blow has actually landed and health has been lost -- the moment to apply an
     * on-hit effect (ignite, lifesteal, a potion effect) or to advance per-tool combat state.
     *
     * @param damageDealt the health the target actually lost, after all mitigation
     */
    default void onHit(CombatHit hit, float damageDealt) {}

    /**
     * Called once the blow has killed the target, after {@link #onHit} for the same blow. Beheading's
     * head drop and the M7 leveling XP grant both belong here rather than on a death listener of
     * their own (ADR-0005).
     */
    default void postKill(CombatHit hit) {}

    /**
     * This blow's damage after this seam has adjusted it, for a blow the seam's tool is <em>taking</em>
     * rather than dealing -- the defensive mirror of {@link #preHit}, called at the same moment on the
     * same event and chained the same way. Returning {@code 0} (or less) negates the blow outright:
     * {@link CombatSeams} cancels the damage event when the chain leaves nothing, so a parry or a
     * reflect stops the hurt animation and the invulnerability window too, not just the number.
     *
     * <p>Reached while the defender is actively using the tool <em>or</em> merely holding it in the
     * main hand ({@link CombatSeams}); {@link CombatDefense#blocking()} distinguishes the two, and a
     * behavior that only exists while blocking (the broadsword's parry window, the battlesign's
     * stance, stiff's damage shave) gates itself on it.
     */
    default float incomingHit(CombatDefense defense, float originalDamage, float damage) {
        return damage;
    }
}
