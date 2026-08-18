package dev.gkissel.forgeweave.combat;

import net.minecraft.world.entity.LivingEntity;

import dev.gkissel.forgeweave.particle.ForgeweaveParticles;

/**
 * A landed hit is followed by a small randomly-sized secondary hit that skips armor entirely --
 * prickly, cactus's head trait (issue #229), ported from upstream 1.12's
 * {@code TraitPrickly#causeDamage}: {@code base + max(minOffset, gaussian * spread)}, dealt only when
 * the roll comes out positive, as an armor-bypassing secondary damage instance
 * ({@code setDamageBypassesArmor}). Upstream's constants are {@code 0.5 + max(-0.5, gaussian * 0.75)};
 * all three arrive through the constructor per ADR-0004's M6 library shape.
 *
 * <p>The secondary hit uses the same primitive as {@code ForgeweaveInnates.CurrentHealthStrike}:
 * clear the invulnerability window the primary blow just claimed (otherwise a follow-up smaller than
 * the blow is silently swallowed -- upstream's {@code attackEntitySecondary} passes
 * {@code ignoreInvulnerableTime}), then a plain armor-bypassing hurt that credits the attacker.
 * Upstream does not restore the window afterwards, so neither does this.
 */
public final class GaussianArmorPiercingHit implements CombatSeam {
    private final float base;
    private final float spread;
    private final float minOffset;

    public GaussianArmorPiercingHit(float base, float spread, float minOffset) {
        this.base = base;
        this.spread = spread;
        this.minOffset = minOffset;
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity target = hit.target();
        if (!target.isAlive()) {
            return; // Upstream's own isEntityAlive gate: no thorn in a corpse.
        }
        float damage = base + Math.max(minOffset, (float) hit.level().getRandom().nextGaussian() * spread);
        if (damage <= 0.0F) {
            return;
        }
        target.invulnerableTime = 0;
        if (target.hurt(ForgeweaveInnates.armorBypassing(hit.level(), hit.attacker()), damage)) {
            // #482 -- upstream gates its single cactus heart on the same secondary hit landing.
            ForgeweaveParticles.spawnHearts(ForgeweaveParticles.HEART_CACTUS.get(), hit.level(), target, 1);
        }
    }
}
