package dev.gkissel.forgeweave.combat;

/**
 * A hit deals {@code fraction} extra damage, as a fraction of the blow's original (pre-mitigation)
 * damage, when the target is actively blocking with a shield -- ADR-0004's M6 parameterized-behavior-
 * library candidate {@code bonus_damage_vs_blocking}. Forgeweave's M1 hatchet retrofit ("sunder",
 * docs/SCOPE.md M3 issue #164) is the first consumer; magnitude decided on the issue (2026-08-12):
 * 20%.
 *
 * <p>{@link net.minecraft.world.entity.LivingEntity#isBlocking()} is vanilla's own "actively raising a
 * shield" check, the same one {@code LivingEntity#isDamageSourceBlocked} consults before a shield
 * actually absorbs a hit -- this seam adds its bonus in {@link #preHit}, before that resolution, so
 * whatever the shield block ultimately does to the hit (fully blocks it, or lets an unblockable source
 * through) applies to the boosted amount, same as any other pre-mitigation seam.
 *
 * <p>Sunder's other half -- disabling the shield outright, the vanilla-axe rule -- is not a seam at
 * all; see {@code ToolItem#canDisableShield}'s javadoc for why.
 */
public final class BonusDamageVsBlocking implements CombatSeam {
    private final float fraction;

    public BonusDamageVsBlocking(float fraction) {
        this.fraction = fraction;
    }

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return hit.target().isBlocking() ? damage + originalDamage * fraction : damage;
    }
}
