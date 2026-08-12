package dev.gkissel.forgeweave.combat;

import net.minecraft.world.entity.LivingEntity;

/**
 * Fiery (issue #162), ported whole from upstream {@code ModFiery#dealFireDamage}: ignites the target
 * for a fixed duration and follows up with a small true-fire damage instance of its own, both derived
 * from the modifier's raw application units at construction time -- see
 * {@code ForgeweaveModifiers#fieryDurationSeconds}/{@code #fieryDamage}, the same
 * constructor-parameterized shape {@link BonusDamageVsSeam}'s javadoc explains.
 *
 * <p>Resets the target's invulnerability window before the secondary hit, the same trick vanilla's own
 * sweeping-edge attack uses to land a second damage instance in the same swing -- otherwise the
 * primary blow's own hurt-resistant time would silently swallow it.
 */
public final class IgniteOnHitSeam implements CombatSeam {
    private final int fireSeconds;
    private final float trueDamage;

    public IgniteOnHitSeam(int fireSeconds, float trueDamage) {
        this.fireSeconds = fireSeconds;
        this.trueDamage = trueDamage;
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity target = hit.target();
        target.igniteForSeconds(fireSeconds);
        if (trueDamage > 0.0F) {
            target.invulnerableTime = 0;
            target.hurt(hit.level().damageSources().onFire(), trueDamage);
        }
    }
}
