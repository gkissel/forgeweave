package dev.gkissel.forgeweave.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Extra knockback on a landed hit, on top of whatever vanilla's own attack math already applied
 * (ADR-0005 decision 1 leaves cooldown, crits and knockback to the game) -- the knockback combat
 * modifier's shape (docs/SCOPE.md M3, issue #163). A candidate for ADR-0004's M6 parameterized
 * behavior library: {@code magnitude} is this seam's only field, baked in by whichever
 * {@link CombatSeams.Provider} constructs it from a modifier's level, so a JSON-configured generic
 * "knockback_on_hit" behavior could produce the same seam without this class changing.
 *
 * <p>Direction mirrors vanilla's own bonus-knockback push (the one the {@code
 * minecraft:generic.attack_knockback} attribute drives in {@code Player#attack}): away from the
 * attacker's facing, computed from {@code getYRot()}, not from the two entities' relative position --
 * so a knockback tool shoves a target exactly the way a vanilla Knockback-enchanted one would.
 *
 * @param magnitude added knockback strength, upstream {@code ModKnockback#calcKnockback}: {@code 0.1}
 *     per application unit, uncapped ("the sky is the limit" -- upstream's own comment)
 */
public record KnockbackOnHitSeam(float magnitude) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        LivingEntity attacker = hit.attacker();
        if (attacker == null) {
            return; // Nothing to compute a push direction from -- see CombatHit's javadoc.
        }
        float yawRadians = attacker.getYRot() * ((float) Math.PI / 180.0F);
        hit.target().knockback(magnitude, Mth.sin(yawRadians), -Mth.cos(yawRadians));
    }
}
