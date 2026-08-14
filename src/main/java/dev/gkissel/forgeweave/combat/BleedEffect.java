package dev.gkissel.forgeweave.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sharp's armor-ignoring bleed (issue #229), ported from upstream 1.12's {@code TraitSharp.DoT}:
 * {@code (level + 1) / 3} damage every 15 ticks for a 121-tick application, non-stacking (sharp
 * always applies at level 0, so each tick is 1/3 damage -- eight ticks, ~2.67 total over 6 seconds).
 * The seam that applies it is a {@link Lacerate} with {@code maxStacks} 1; see
 * {@link LacerateEffect}'s javadoc for why a DoT is a vanilla status effect at all.
 *
 * <p>Two upstream mechanics mirrored exactly:
 *
 * <ul>
 *   <li><b>Armor-ignoring</b>: upstream's {@code attackEntitySecondary(source, damage, target, true,
 *       true)} bypasses armor; a magic-typed source is 1.21's equivalent, the same one
 *       {@link LacerateEffect} uses -- and, like there, it names no weapon, so it can never chain
 *       back into {@link CombatSeams}.
 *   <li><b>Attacker credit</b> (issue #297 parity fix): upstream's {@code dealDamage} builds the tick's
 *       {@code DamageSource} from {@code target.getLastAttackedEntity()}, set by {@code TraitSharp
 *       #afterHit} when the bleed was applied ({@link Lacerate#onHit}'s {@code setLastHurtByMob}).
 *       This does the same off {@link LivingEntity#getLastHurtByMob}: {@code indirectMagic}, vanilla's
 *       own armor-ignoring-with-a-credited-entity source (the same one a thrown Harming potion uses),
 *       when an attacker is remembered, plain {@code magic()} otherwise -- so a kill lands on the
 *       wielder instead of no one.
 *   <li><b>No invulnerability-window games</b>: upstream saves {@code hurtResistantTime}, deals the
 *       tick ignoring it, and restores it -- so a bleed tick neither gets swallowed by the window a
 *       real blow just opened nor grants a window that would shield the target from real blows. The
 *       zero-then-restore below is the same maneuver on 1.21's {@code invulnerableTime}.
 * </ul>
 */
public class BleedEffect extends MobEffect {

    /** Upstream {@code TraitSharp.dealDamage} at level 0: {@code (0 + 1) / 3}. */
    public static final float DAMAGE_PER_TICK = 1.0F / 3.0F;
    /** Upstream {@code TraitSharp#afterHit}: {@code DOT.apply(target, 121)}. */
    public static final int DURATION_TICKS = 121;

    /** Upstream {@code DoT#isReady}: every 15 ticks. */
    private static final int TICKS_PER_DAMAGE = 15;
    /** A brighter arterial red than lacerate's, which is what the HUD swirl particles are drawn in. */
    private static final int COLOR = 0xC41E1E;

    public BleedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /**
     * Vanilla hands this the duration still to run and then decrements it, so a 121-tick application
     * sees 121, 120, ..., 1 -- {@code % 15 == 0} lands the eight ticks at 15-tick spacing with the
     * first a moment after the blow, matching upstream's count-up {@code tick % 15 == 0}.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICKS_PER_DAMAGE == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        LivingEntity attacker = entity.getLastHurtByMob();
        DamageSource source = attacker != null
                ? entity.damageSources().indirectMagic(attacker, attacker)
                : entity.damageSources().magic();
        int invulnerableTime = entity.invulnerableTime;
        entity.invulnerableTime = 0;
        entity.hurt(source, DAMAGE_PER_TICK);
        entity.invulnerableTime = invulnerableTime;
        return true;
    }
}
