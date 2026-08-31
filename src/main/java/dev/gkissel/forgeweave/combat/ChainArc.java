package dev.gkissel.forgeweave.combat;

import java.util.Comparator;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * A landed hit arcs on to the nearest other enemies around the target -- ADR-0004's M6 on-hit effect
 * library batch (issue #828) {@code chain_arc(chance, range, damageFraction, maxTargets)}, the
 * reference instance "Chain Lightning".
 *
 * <p>{@code chance} is not a field here, {@link EffectOnHit}'s reasoning: {@link ConditionalSeam}
 * already rolls a chance for an on-hit-only delegate, so the trait-definition call site is {@code
 * new ConditionalSeam(FULL_CHARGE, chance, new ChainArc(range, damageFraction, maxTargets))} for the
 * charged-hit-only reference instance.
 *
 * <p>Target selection reuses {@link SweepAttackSeam}'s shape (issue #828's own instruction: reuse
 * whatever AoE targeting the scythe/cleaver already have rather than writing a second one) --
 * everything alive and attackable in an inflated box around the <em>target</em>, not the attacker,
 * since an arc chains outward from the entity it just hit rather than sweeping in front of the
 * swing. Nearest first, capped at {@link #maxTargets}. The same static re-entrancy guard as {@link
 * SweepAttackSeam}/{@link SweepingBlow} keeps a struck link from arcing again off itself.
 *
 * @param range how far from the target a secondary victim may be, in blocks
 * @param damageFraction share of the landed blow's dealt damage each arced victim takes
 * @param maxTargets how many additional victims one arc may reach, at most
 */
public record ChainArc(double range, float damageFraction, int maxTargets) implements CombatSeam {

    private static boolean arcing;

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity attacker = hit.attacker();
        if (arcing || damageDealt <= 0.0F || maxTargets <= 0) {
            return;
        }
        float splash = damageDealt * damageFraction;
        if (splash <= 0.0F) {
            return;
        }
        AABB box = hit.target().getBoundingBox().inflate(range);
        List<LivingEntity> nearby = hit.level().getEntitiesOfClass(LivingEntity.class, box, other ->
                other != hit.target() && other != attacker && other.isAlive() && other.isAttackable());
        if (nearby.isEmpty()) {
            return;
        }
        nearby.sort(Comparator.comparingDouble(other -> other.distanceToSqr(hit.target())));
        arcing = true;
        try {
            int struck = 0;
            for (LivingEntity victim : nearby) {
                if (struck >= maxTargets) {
                    break;
                }
                victim.hurt(hit.source(), splash);
                struck++;
            }
        } finally {
            arcing = false;
        }
    }
}
