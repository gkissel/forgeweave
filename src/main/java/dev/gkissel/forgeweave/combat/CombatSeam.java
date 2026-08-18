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
 * <p>ponytail: four hooks -- the three moments ADR-0005 names plus {@link #knockback}, added when
 * issue #465/T34 needed one. Upstream 1.12's {@code ITrait} exposes a dozen combat-adjacent hooks;
 * the ones no shipped behavior uses would be empty seams here. Add a hook when a behavior needs it.
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
     * <p>Reached for every Forgeweave tool the defender holds, in either hand ({@link CombatSeams}).
     * A behavior that only exists in one defensive state gates itself: on
     * {@link CombatDefense#using()} when it belongs to <em>this</em> tool's own use action (the
     * broadsword's parry window, the battlesign's stance), on {@link CombatDefense#blocking()} when
     * it is upstream's {@code ITrait#onBlock} state -- the defender is blocking with anything, a
     * raised vanilla shield included (stiff's damage shave, flammable's fire absorb, spiky's
     * full-strength thorns).
     */
    default float incomingHit(CombatDefense defense, float originalDamage, float damage) {
        return damage;
    }

    /**
     * This blow's knockback strength adjusted by this seam, called once per NeoForge {@code
     * LivingKnockBackEvent} that this hit's target's own {@code LivingEntity#hurt} produces -- the flat
     * {@code 0.4f} push vanilla applies to <em>every</em> successful hit from an attacking entity
     * ({@code LivingEntity#hurt}'s own {@code this.knockback(0.4F, ...)} call, unconditional on the
     * damage source carrying an entity and not tagged {@code minecraft:no_knockback}), strictly
     * separate from any bonus a sprint attack, a Knockback enchant, or a Forgeweave combat modifier
     * adds on top. Upstream 1.12's per-tool {@code ITool#knockback()} multiplier (issue #465/T34)
     * scales that exact same flat push ({@code ToolHelper#attackEntity}'s own vanilla-derived delta,
     * lines 737-740) and, like it, lives here riding NeoForge's own knockback event rather than a
     * custom pipeline (ADR-0005) -- the other three hooks ride NeoForge's damage/death events the
     * same way.
     *
     * <p>Never called for a push a seam applies itself from {@link #onHit} ({@link KnockbackOnHitSeam},
     * the frying pan's {@code HeavyKnockback}): those calls to {@code LivingEntity#knockback} happen
     * and finish while {@link CombatSeams} is still dispatching {@link #onHit}, before the flat push
     * above exists to fire this hook for -- {@link CombatSeams} excludes them the same way upstream's
     * separate trait-driven {@code addVelocity} call is untouched by {@code tool.knockback()}. Also
     * never called twice for one hit: {@code Player#attack}'s own additional sprint/enchant-driven push
     * (a second, later {@code LivingEntity#knockback} call, upstream's own separate un-multiplied
     * {@code knockback} local) finds nothing left to attribute itself to once the flat push above has
     * already consumed it -- see {@link CombatSeams#onKnockback}.
     *
     * @param knockback the push's strength as every earlier seam in the chain left it
     */
    default float knockback(CombatHit hit, float knockback) {
        return knockback;
    }
}
