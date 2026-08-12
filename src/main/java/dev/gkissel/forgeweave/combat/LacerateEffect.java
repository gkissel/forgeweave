package dev.gkissel.forgeweave.combat;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The scimitar's bleed (issue #159, maintainer decision 2026-08-12): <b>1 damage per second for 4
 * seconds per application, stacking up to 3 concurrent bleeds</b>.
 *
 * <h2>Why a vanilla status effect</h2>
 *
 * <p>A damage-over-time needs somewhere to tick from and somewhere to keep its remaining duration.
 * Vanilla already has exactly that machinery -- {@code MobEffectInstance} is ticked by the game,
 * saved with the entity, synced to the client, shown in the HUD/inventory, and cleared by milk --
 * so the alternative (a Forgeweave-owned attached-data map plus a tick handler plus its own
 * serialization plus its own client sync) would be a second, worse copy of it. ADR-0005's rule is
 * that a combat behavior attaches at the seams; it says nothing about a behavior being forbidden to
 * use a vanilla mechanism to carry itself once applied, and {@link Lacerate} -- the actual seam --
 * is still the only thing that decides when a bleed happens.
 *
 * <h2>Stacking</h2>
 *
 * <p>ponytail: the amplifier <em>is</em> the stack count ({@code amplifier + 1} bleeds), rather than
 * three independently-expiring {@code MobEffectInstance}s. Vanilla allows exactly one instance of an
 * effect per entity, so genuinely independent per-bleed timers would need Forgeweave-owned state
 * after all, and the observable difference is only in when a stack applied late drops off. The
 * decision's two numbers that a player can actually see -- how fast it ticks and how much a full
 * stack hurts -- are exact. Upgrade path if playtest wants true independent timers: a list of expiry
 * ticks in a data component on the target, ticked from here.
 *
 * <p>Damage is dealt with a magic-typed source rather than the weapon's: a bleed is not a second
 * blow, so it must not chain back into {@link CombatSeams} (that source names no weapon, so the
 * pipeline's own gate refuses it), must not consume the tool's durability, and must not be reduced
 * by armor.
 */
public class LacerateEffect extends MobEffect {

    /** Maintainer decision: 1 damage per second, per concurrent bleed. */
    public static final float DAMAGE_PER_STACK = 1.0F;
    /** Maintainer decision: 4 seconds per application. */
    public static final int DURATION_TICKS = 4 * 20;
    /** Maintainer decision: at most 3 concurrent bleeds, i.e. amplifier 2. */
    public static final int MAX_STACKS = 3;

    private static final int TICKS_PER_DAMAGE = 20;
    /** A deep red, which is what the effect's HUD swirl particles are drawn in. */
    private static final int COLOR = 0x981E20;

    public LacerateEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /**
     * One damage tick per second, the first landing a full second <em>after</em> the blow rather than
     * on the same tick as it.
     *
     * <p>Vanilla calls this with the duration still to run and then decrements it, so the very first
     * call sees {@link #DURATION_TICKS} itself: firing on {@code duration % 20 == 0} would bleed
     * instantly, inside the target's post-hit invulnerability window, where a 1-damage magic hit no
     * bigger than the blow that just landed is simply dropped ({@code Entity#hurt}). Offsetting by
     * one puts the four ticks at exactly 1, 2, 3 and 4 seconds -- and the last of them on the final
     * tick of the effect, so "1 damage per second for 4 seconds" is 4 damage, not 3.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICKS_PER_DAMAGE == 1;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().magic(), DAMAGE_PER_STACK * stacks(amplifier));
        return true;
    }

    /** How many concurrent bleeds an amplifier represents; see the class javadoc. */
    public static int stacks(int amplifier) {
        return amplifier + 1;
    }
}
