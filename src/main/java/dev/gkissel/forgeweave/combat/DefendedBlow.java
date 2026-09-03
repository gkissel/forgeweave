package dev.gkissel.forgeweave.combat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

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
 * <p>M7-6 (issue #923) added the "which piece contributed what" this class's javadoc used to defer:
 * {@link #attribute} records one {@link Share} per worn piece the walk visited, so armor leveling can
 * pay each piece for what it actually took off the blow. The walk measures a piece's leg by reading
 * the three numbers before and after it rather than by instrumenting the setters, so the accumulator
 * stays three floats plus a list and no mutator has to know a piece is in progress.
 */
public final class DefendedBlow {
    private final float originalDamage;
    private float damage;
    private float protection;
    private float flatReduction;
    private int invulnerabilityTicks;

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

    /**
     * The post-hit invulnerability window this blow should leave behind, {@code 0} for vanilla's own
     * 20 ticks -- {@code trait.InvulnerabilityWindow} (issue #831, M6 armor library). Applied by
     * {@link CombatSeams} through {@code LivingIncomingDamageEvent#setInvulnerabilityTicks}, which
     * is the only place the number can be set: vanilla writes
     * {@code DamageContainer#getPostAttackInvulnerabilityTicks} into {@code invulnerableTime} after
     * the event and before {@code actuallyHurt}, so a piece assigning the field directly would be
     * overwritten a line later.
     */
    public int invulnerabilityTicks() {
        return invulnerabilityTicks;
    }

    /** The longest window any piece asked for wins; two pieces do not add up to double immunity. */
    public void requestInvulnerabilityTicks(int ticks) {
        this.invulnerabilityTicks = Math.max(this.invulnerabilityTicks, ticks);
    }

    /**
     * What one worn piece took off this blow during its own leg of the walk (M7-6, issue #923): the
     * pre-mitigation damage it removed through {@link #setDamage}, and the {@link #addProtection} and
     * {@link #addFlatReduction} it added, both of which only become a damage number once vanilla's
     * armor has run -- {@code CombatSeams#onDamagePre} converts them there and pays the piece.
     *
     * <p>Overslime needs no field of its own: the only thing that spends it inside {@code onDefend}
     * is knightslime's overshield, which spends it <em>as</em> protection, so {@link #protection}
     * already carries it.
     */
    public record Share(ItemStack piece, float damageRemoved, float protection, float flatReduction) {}

    private final List<Share> shares = new ArrayList<>();

    /** Records {@code piece}'s leg; called once per worn unbroken piece, contribution or not. */
    public void attribute(ItemStack piece, float damageRemoved, float protection, float flatReduction) {
        shares.add(new Share(piece, damageRemoved, protection, flatReduction));
    }

    /** The worn pieces this blow walked, in slot order. Empty when nothing worn was reached. */
    public List<Share> shares() {
        return shares;
    }
}
