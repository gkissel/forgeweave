package dev.gkissel.forgeweave.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * A secondary hit riding on a blow (a trait's true damage, a bleed tick, a thorns reflect), dealt
 * past the target's invulnerability window without disturbing it -- upstream 1.12
 * {@code Modifier#attackEntitySecondary(ignoreInvulv = true)} (issue #701).
 *
 * <p>Two things vanilla's {@code LivingEntity#hurt} sets on every landed hit are put back:
 * {@code invulnerableTime}, so the secondary neither opens nor extends a window, and
 * {@code lastHurt}, the amount the current window already absorbed, which every follow-up blow
 * inside the window is compared against ({@code amount <= lastHurt} is swallowed; otherwise only
 * the difference lands). Left at the secondary's own small number, a spam-clicked swing at 20%
 * strength came out above it, landed the difference, fired the tool's on-hit seams again, which
 * dealt the secondary again -- damage on every click, no invulnerability frames at all. Upstream's
 * {@code living.lastDamage += oldLastDamage} is the fix, mirrored here.
 */
public final class SecondaryDamage {

    private SecondaryDamage() {}

    /** @return whether the hit landed ({@code LivingEntity#hurt}'s own answer) */
    public static boolean deal(LivingEntity target, DamageSource source, float amount) {
        int invulnerableTime = target.invulnerableTime;
        float lastHurt = target.lastHurt;
        target.invulnerableTime = 0;
        boolean hit = target.hurt(source, amount);
        target.invulnerableTime = invulnerableTime;
        if (hit) {
            target.lastHurt += lastHurt;
        }
        return hit;
    }
}
