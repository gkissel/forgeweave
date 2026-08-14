package dev.gkissel.forgeweave.combat;

import java.util.List;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The broadsword's sweep-on-hit, ported whole from upstream 1.12 {@code
 * tools/melee/item/BroadSword.java#dealDamage} (NOTICE.md, lines 44-74) -- the behavior issue #303's
 * audit found in the clone where an earlier version of this codebase (and docs/SCOPE.md) claimed
 * upstream's broadsword innate was "sword-blocking". It never was: {@code SwordCore} extends {@code
 * TinkerToolCore}, not vanilla's {@code ItemSword}, so 1.12's own automatic sweep (an {@code ItemSword}
 * -only feature there, same as modern vanilla's {@code ItemAbilities.SWORD_SWEEP} gate) never reached
 * it, and upstream re-implements the same gate and AOE by hand instead of blocking anything.
 *
 * <p>Every number and condition below is upstream's own, translated to this codebase's modern fields
 * verbatim rather than approximated:
 *
 * <ul>
 *   <li>{@code getCooledAttackStrength(0.5F) > 0.9f} is exactly {@link CombatHit#isFullCharge()} --
 *       both default to "charged" for a non-player swing, and both read the same captured cooldown
 *       scale for a player one (see {@link CombatHit#attackStrengthScale}'s javadoc);
 *   <li>{@code distanceWalkedModified - prevDistanceWalkedModified < getAIMoveSpeed()} is {@code
 *       walkDist - walkDistO < getSpeed()}, the same fields modern vanilla's own sweep gate reads;
 *   <li>the "recently airborne" exclusion ({@code fallDistance > 0 && !onGround && !isOnLadder &&
 *       !isInWater && !isPotionActive(BLINDNESS) && !isRiding}) is {@code fallDistance > 0 &&
 *       !onGround() && !onClimbable() && !isInWater() && !hasEffect(BLINDNESS) && !isPassenger()};
 *   <li>the hit box is {@code target.getBoundingBox().expand(1.0, 0.25, 1.0)} -- 1.12's {@code
 *       AxisAlignedBB#expand} grows symmetrically on every axis, which is modern {@link AABB#inflate};
 *   <li>{@code getDistanceSqToEntity < 9.0D} and {@code isOnSameTeam} become {@code distanceToSqr <
 *       9.0} and {@link LivingEntity#isAlliedTo}, vanilla's own modern substitute for the same check
 *       (its current sweep code makes the identical swap);
 *   <li>each extra target takes a flat {@code 1f} through {@code super.dealDamage} (not vanilla's own
 *       enchantment-scaled sweep damage) and {@code 0.4F} knockback along the attacker's facing.
 * </ul>
 *
 * <p>Re-entrant like {@link SweepAttackSeam} for the same reason: hurting an extra target replays the
 * whole seam chain for this same weapon, so {@link #sweeping} stops a swept target from sweeping again.
 */
public final class BroadswordSweep implements CombatSeam {

    /** Upstream's flat per-extra-target damage, not vanilla's own enchantment-scaled sweep amount. */
    private static final float SWEEP_DAMAGE = 1.0F;

    /** Upstream {@code getDistanceSqToEntity(entitylivingbase) < 9.0D} -- 3 blocks from the attacker. */
    private static final double SWEEP_RANGE_SQR = 9.0D;

    /** Upstream {@code entitylivingbase.knockBack(player, 0.4F, ...)}. */
    private static final float SWEEP_KNOCKBACK = 0.4F;

    /** Upstream {@code entity.getEntityBoundingBox().expand(1.0D, 0.25D, 1.0D)} around the primary target. */
    private static final double BOX_HORIZONTAL = 1.0D;
    private static final double BOX_VERTICAL = 0.25D;

    private static boolean sweeping;

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (sweeping) {
            return;
        }
        LivingEntity attacker = hit.attacker();
        if (attacker == null || !isSweepCondition(hit, attacker)) {
            return;
        }
        AABB box = hit.target().getBoundingBox().inflate(BOX_HORIZONTAL, BOX_VERTICAL, BOX_HORIZONTAL);
        List<LivingEntity> others = hit.level().getEntitiesOfClass(LivingEntity.class, box,
                other -> other != attacker && other != hit.target() && !attacker.isAlliedTo(other)
                        && attacker.distanceToSqr(other) < SWEEP_RANGE_SQR);
        if (others.isEmpty()) {
            return;
        }
        sweeping = true;
        try {
            float yawRadians = attacker.getYRot() * ((float) Math.PI / 180.0F);
            for (LivingEntity other : others) {
                other.knockback(SWEEP_KNOCKBACK, Mth.sin(yawRadians), -Mth.cos(yawRadians));
                other.hurt(hit.source(), SWEEP_DAMAGE);
            }
        } finally {
            sweeping = false;
        }
        hit.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, attacker.getSoundSource(), 1.0F, 1.0F);
        if (attacker instanceof Player player) {
            player.sweepAttack(); // upstream's own EntityPlayer-only spawnSweepParticles() carve-out
        }
    }

    /** Upstream's whole gate, field for field -- see the class javadoc. */
    private static boolean isSweepCondition(CombatHit hit, LivingEntity attacker) {
        boolean recentlyAirborne = attacker.fallDistance > 0.0F && !attacker.onGround() && !attacker.onClimbable()
                && !attacker.isInWater() && !attacker.hasEffect(MobEffects.BLINDNESS) && !attacker.isPassenger();
        double walked = attacker.walkDist - attacker.walkDistO;
        return hit.isFullCharge() && !attacker.isSprinting() && !recentlyAirborne && attacker.onGround()
                && walked < attacker.getSpeed();
    }
}
