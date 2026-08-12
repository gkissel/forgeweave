package dev.gkissel.forgeweave.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The mattock's combat rider (docs/SCOPE.md M3 issue #156, maintainer decision 2026-08-12 on the
 * issue): a per-hit chance of a strong knockback, on top of whatever vanilla knockback the hit
 * already applies. No 1.12 counterpart -- utility tools getting a small combat rider is a fresh
 * maintainer directive, not a clone port -- so the magnitude is a Forgeweave pick: {@code 1.2}
 * (vanilla's own {@code knockback} attribute is a flat 0.4 per attack and each level of the
 * Knockback enchantment adds 0.6 more; 1.2 reads as "roughly Knockback II on top of a bare-handed
 * hit" without needing the enchantment's level bookkeeping).
 *
 * <p>ADR-0005 decision 5: parameter-shaped (the chance is a constructor argument, not a constant),
 * so a GameTest can force it to {@code 1.0} instead of asserting on a coin flip.
 */
public final class HeftSeam implements CombatSeam {

    /** Maintainer decision 2026-08-12: 15% chance per hit. */
    public static final float DEFAULT_CHANCE = 0.15F;

    /** See the class javadoc's magnitude note. */
    private static final double KNOCKBACK_STRENGTH = 1.2;

    /** The instance every assembled mattock resolves to; see {@code Forgeweave}'s seam registration. */
    public static final HeftSeam SEAM = new HeftSeam(DEFAULT_CHANCE);

    private final float chance;

    public HeftSeam(float chance) {
        this.chance = chance;
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity attacker = hit.attacker();
        if (attacker == null || attacker.getRandom().nextFloat() >= chance) {
            return;
        }
        LivingEntity target = hit.target();
        Vec3 delta = target.position().subtract(attacker.position());
        double horizontalDistance = delta.horizontalDistance();
        double dx = horizontalDistance < 1.0e-4 ? 0.0 : delta.x / horizontalDistance;
        double dz = horizontalDistance < 1.0e-4 ? 0.0 : delta.z / horizontalDistance;
        target.knockback(KNOCKBACK_STRENGTH, -dx, -dz);
    }
}
