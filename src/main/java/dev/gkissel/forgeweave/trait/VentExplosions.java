package dev.gkissel.forgeweave.trait;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;

/**
 * An explosion does no damage and pushes the wearer instead -- the M6 armor library's
 * {@code vent_explosions(knockbackFactor)} (issue #831).
 *
 * <p>{@code #minecraft:is_explosion} is the tag, not a parameter: "vents explosions" is the whole
 * idea, and a tag field would only invite a second instance that vents something that has no blast
 * to redirect. The push is vanilla's own {@code LivingEntity#knockback} away from the blast
 * position, so it obeys knockback resistance and rides the same {@code LivingKnockBackEvent}
 * everything else does.
 *
 * @param knockbackFactor multiplied by the negated damage to get the push strength; vanilla's melee
 *     push is 0.4, so 0.05 turns a 10-damage blast into a slightly harder shove than a sword hit
 */
public record VentExplosions(float knockbackFactor) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (blow.damage() <= 0.0F || !defense.source().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        float strength = blow.damage() * knockbackFactor;
        blow.setDamage(0.0F);
        LivingEntity defender = defense.defender();
        Vec3 origin = defense.source().getSourcePosition();
        if (origin == null || strength <= 0.0F) {
            return;
        }
        // LivingEntity#knockback takes the vector *towards* the source and pushes the other way,
        // which is exactly how vanilla's own explosion knockback is signed.
        defender.knockback(strength, origin.x() - defender.getX(), origin.z() - defender.getZ());
    }
}
