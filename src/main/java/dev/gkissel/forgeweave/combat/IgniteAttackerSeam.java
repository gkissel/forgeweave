package dev.gkissel.forgeweave.combat;

import net.minecraft.world.entity.LivingEntity;

import dev.gkissel.forgeweave.api.combat.CombatDefense;
import dev.gkissel.forgeweave.api.combat.CombatSeam;
import dev.gkissel.forgeweave.trait.TraitFeedback;

/**
 * Whoever hits the tool's holder catches fire -- flammable's offensive-by-retaliation half (issue
 * #229), ported from upstream 1.12's {@code TraitFlammable}: {@code attacker.setFire(3)} both while
 * the tool is merely held ({@code onPlayerHurt}) and while blocking ({@code onBlock}), so this seam
 * deliberately does not read {@link CombatDefense#blocking()}. The blocking-only fire-absorb half is
 * {@link AbsorbFireWhileBlocking}, a separate seam on the same trait.
 */
public record IgniteAttackerSeam(int fireSeconds) implements CombatSeam {

    @Override
    public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
        LivingEntity attacker = defense.attacker();
        if (attacker != null && attacker != defense.defender()) {
            attacker.igniteForSeconds(fireSeconds);
            // #1112: the same fire heart IgniteOnHitSeam gives its target, for the attacker this
            // seam burns instead.
            TraitFeedback.fire(this, TraitFeedback.Kind.BURN, defense.level(), attacker);
        }
        return damage;
    }
}
