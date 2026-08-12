package dev.gkissel.forgeweave.combat;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * "Sweeping heavy blow" -- the battleaxe's innate (maintainer decision on issue #159, 2026-08-12):
 * <b>a full-charge hit strikes every enemy in a short arc for 50% damage, and applies slowness I for
 * 1.5s to the primary target</b>.
 *
 * <p>Like {@link Lacerate}, every magnitude is a constructor parameter and nothing here names the
 * battleaxe (ADR-0004's M6 commitment, see {@link CombatSeam}) -- this is one parameterized
 * {@code aoe_on_hit} behavior, and {@code ForgeweaveInnates} is what attaches it to one tool.
 *
 * <h2>Arc targeting</h2>
 *
 * <p>The arc is measured from the attacker's horizontal look direction, so it is the wedge in front
 * of the swing rather than a ring around the attacker -- which is what makes it read as a sweep and
 * not as an explosion. Vertical position is deliberately ignored in the angle (only the horizontal
 * heading matters), the same simplification vanilla's own sweep attack makes with its flat inflated
 * bounding box.
 *
 * @param damageFraction share of the primary blow's dealt damage each secondary target takes
 * @param radius how far from the attacker a secondary target may be, in blocks
 * @param arcDegrees the full width of the wedge in front of the attacker
 * @param primaryEffect a status effect applied to the primary target only, or {@code null} for none
 * @param primaryEffectTicks how long that effect lasts
 * @param primaryEffectAmplifier its amplifier ({@code 0} is level I)
 */
public record SweepingBlow(float damageFraction, double radius, double arcDegrees,
                           @Nullable Holder<MobEffect> primaryEffect, int primaryEffectTicks,
                           int primaryEffectAmplifier) implements CombatSeam {

    /**
     * Guards the secondary blows from feeding back into the pipeline. The secondary hits are dealt
     * with the primary blow's own damage source, deliberately -- so a secondary target takes the
     * tool's traits and combat modifiers too -- but that source still names the weapon, so
     * {@link CombatSeams} would resolve this very seam again for each of them and sweep from every
     * victim in turn. Server thread only, and the whole loop below runs inside one hook call, so a
     * plain field is the entire mechanism needed.
     */
    private static boolean sweeping;

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity attacker = hit.attacker();
        if (attacker == null || damageDealt <= 0.0F || !hit.isFullCharge() || sweeping) {
            return;
        }
        if (primaryEffect != null) {
            hit.target().addEffect(
                    new MobEffectInstance(primaryEffect, primaryEffectTicks, primaryEffectAmplifier), attacker);
        }

        float splash = damageDealt * damageFraction;
        if (splash <= 0.0F) {
            return;
        }
        sweeping = true;
        try {
            int struck = 0;
            for (LivingEntity victim : inArc(hit, attacker)) {
                victim.hurt(hit.source(), splash);
                struck++;
            }
            if (struck > 0) {
                hit.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, attacker.getSoundSource(), 1.0F, 1.0F);
            }
        } finally {
            sweeping = false;
        }
    }

    /**
     * Everything the sweep should catch: alive, attackable, not the attacker, not the target that
     * already took the full blow, within {@link #radius} and inside the wedge. Public so a GameTest
     * can assert the targeting without staging a blow (the same reason {@code CombatSeams#seams} is).
     */
    public List<LivingEntity> inArc(CombatHit hit, LivingEntity attacker) {
        Vec3 origin = attacker.position();
        Vec3 facing = horizontal(attacker.getLookAngle());
        double cosHalfArc = Math.cos(Math.toRadians(arcDegrees / 2.0));
        AABB box = attacker.getBoundingBox().inflate(radius);
        return hit.level().getEntitiesOfClass(LivingEntity.class, box, candidate ->
                candidate != attacker
                        && candidate != hit.target()
                        && candidate.isAlive()
                        && candidate.isAttackable()
                        && !candidate.isAlliedTo(attacker)
                        && candidate.distanceTo(attacker) <= radius
                        && facing.dot(horizontal(candidate.position().subtract(origin))) >= cosHalfArc);
    }

    /** {@code v} flattened onto the XZ plane and normalized; the zero vector stays zero. */
    private static Vec3 horizontal(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0.0, v.z);
        return flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
    }
}
