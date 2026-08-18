package dev.gkissel.forgeweave.combat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * One blow struck with a Forgeweave tool, as every {@link CombatSeam} sees it. {@link CombatSeams}
 * builds exactly one of these per hit and hands the same instance to the pre-hit, on-hit and
 * post-kill calls of that hit, so a seam never has to re-derive who hit whom with what.
 *
 * @param level the server level the blow lands in -- seams are server side only ({@link CombatSeams})
 * @param weapon the Forgeweave tool the blow was struck with, never Broken (see {@link CombatSeams})
 * @param attacker whoever swung it, or {@code null} when the damage source names no living entity
 *     (a tool held by an arrow-shooting dispenser, a mob killed by its own thrown weapon, ...)
 * @param target what was hit
 * @param source the damage source of the blow, for seams that need its type tags (M3's smite, bane
 *     of arthropods and the armor-bypassing innates all key off it -- docs/SCOPE.md M3)
 * @param attackStrengthScale how charged vanilla's attack cooldown was when this blow was swung, 0
 *     to 1 -- the number vanilla itself scales attack damage by, and what "a full-charge hit" means
 *     for the innates keyed on it (the battleaxe's sweeping heavy blow, #159; the longsword leap and
 *     the frying pan's charged knockback when they land). {@code 1.0} for a blow that did not come
 *     from a player swing, since only players have an attack cooldown. See {@link
 *     CombatSeams#onPlayerAttack} for why this has to be captured rather than read at hook time.
 * @param critMultiplier the multiplier vanilla's critical hit put on this blow's damage ({@code 1.5}
 *     for a plain vanilla crit, whatever NeoForge's {@code CriticalHitEvent} settled on otherwise),
 *     {@code 1.0} when the blow was not a crit or did not come from a player swing. Captured the same
 *     way as {@link #attackStrengthScale}; together they are the factor {@link CombatSeams} unwinds
 *     before the seams run (issue #422).
 */
public record CombatHit(
        ServerLevel level,
        ItemStack weapon,
        @Nullable LivingEntity attacker,
        LivingEntity target,
        DamageSource source,
        float attackStrengthScale,
        float critMultiplier) {

    /** Vanilla's own threshold for a fully-charged swing ({@code Player#attack}'s sweep condition). */
    public static final float FULL_CHARGE = 0.9F;

    /**
     * A blow whose charge is not in question -- the default {@link CombatSeams} itself uses for
     * anything that did not come from a player swing, and what a test staging a hit directly wants
     * unless it is specifically exercising a partial charge.
     */
    public CombatHit(ServerLevel level, ItemStack weapon, @Nullable LivingEntity attacker, LivingEntity target,
            DamageSource source) {
        this(level, weapon, attacker, target, source, 1.0F, 1.0F);
    }

    /** A blow of the given charge that was not a crit. */
    public CombatHit(ServerLevel level, ItemStack weapon, @Nullable LivingEntity attacker, LivingEntity target,
            DamageSource source, float attackStrengthScale) {
        this(level, weapon, attacker, target, source, attackStrengthScale, 1.0F);
    }

    public boolean isFullCharge() {
        return attackStrengthScale > FULL_CHARGE;
    }

    public boolean isCritical() {
        return critMultiplier != 1.0F;
    }

    /**
     * {@code Player#attack}'s cooldown factor on this blow's base damage, {@code 0.2 + s^2 * 0.8} --
     * {@code 1.0} for a blow that was not a player swing (a mob's, a projectile's). With {@link
     * #critMultiplier} this is everything vanilla multiplied the base by before the seams see it.
     */
    public float cooldownFactor() {
        return 0.2F + attackStrengthScale * attackStrengthScale * 0.8F;
    }

    /**
     * Whether this blow arrived on a projectile rather than the attacker's own swing (M3.5 issue
     * #396): the source's direct entity is something other than its causing entity -- an arrow fired
     * from a Forgeweave bow, whose {@code getWeaponItem} is the bow. Upstream {@code
     * ToolHelper#attackEntity} keeps the same distinction as its {@code projectileEntity} argument.
     */
    public boolean isProjectile() {
        return source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity();
    }
}
