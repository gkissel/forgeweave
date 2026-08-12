package dev.gkissel.forgeweave.combat;

import java.util.List;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Passes the blow on to everything around the target -- the scythe's area attack (upstream 1.12
 * {@code tools/tools/Scythe.java} {@code #onLeftClickEntity}, which re-attacks every entity in the
 * same 3x3x3 box its harvest covers, NOTICE.md).
 *
 * <p>Two things differ from upstream, both because this rides the shared pipeline instead of
 * re-implementing the attack (ADR-0005 decision 3):
 *
 * <ul>
 *   <li><b>No cooldown gate.</b> Upstream refuses to sweep below {@code getCooledAttackStrength >
 *       0.9}, because its own left-click handler deals full damage regardless of the swing timer.
 *       Here the sweep passes on {@code damageDealt} -- the damage vanilla already scaled by that
 *       same swing charge -- so a spam-clicked swing sweeps for a spam-clicked swing's damage, which
 *       is what the gate was there to prevent. One less piece of state to get wrong.
 *   <li><b>Re-entrancy.</b> Each extra target's {@code hurt} runs the whole seam chain again for the
 *       same weapon, so without {@link #sweeping} the second target would sweep from itself, and so
 *       on. The flag is a plain field on the server thread, which is the only thread these run on.
 * </ul>
 *
 * @param radius how far from the target's own box to reach, in blocks -- {@code 1.0} is upstream's
 *     3x3x3, i.e. {@code (3 - 1) / 2}
 */
public record SweepAttackSeam(float radius) implements CombatSeam {

    private static boolean sweeping;

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity attacker = hit.attacker();
        if (sweeping || damageDealt <= 0.0F || attacker == null) {
            return;
        }
        AABB box = hit.target().getBoundingBox().inflate(radius);
        List<LivingEntity> others = hit.level().getEntitiesOfClass(LivingEntity.class, box,
                other -> other != hit.target() && other != attacker && other.isAlive() && other.isAttackable());
        if (others.isEmpty()) {
            return;
        }
        sweeping = true;
        try {
            for (LivingEntity other : others) {
                other.hurt(hit.source(), damageDealt);
            }
        } finally {
            sweeping = false;
        }
        hit.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, attacker.getSoundSource(), 1.0F, 1.0F);
    }
}
