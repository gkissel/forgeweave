package dev.gkissel.forgeweave.trait;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;

/**
 * A blow of a named damage type does nothing and heals the wearer a fraction of what it would have
 * dealt -- the M6 armor library's {@code convert_damage_to_healing(damageType, fraction)} (issue
 * #831), the "fire damage heals you" idea keyed on a damage-type tag rather than hard-coded to
 * fire.
 *
 * <p>Cancelling by setting the pre-mitigation damage to zero is {@code CombatSeams}' own contract
 * for a blow that must not land at all (a zeroed blow would still burn the wearer's invulnerability
 * window and play the hurt animation, so {@code armorPass} cancels the event instead).
 *
 * @param damageType which blows are converted
 * @param fraction how much of the blow is healed back; 0.5 heals half of what it would have dealt
 */
public record ConvertDamageToHealing(TagKey<DamageType> damageType, float fraction) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (blow.damage() <= 0.0F || !defense.source().is(damageType)) {
            return;
        }
        defense.defender().heal(blow.damage() * fraction);
        blow.setDamage(0.0F);
    }
}
