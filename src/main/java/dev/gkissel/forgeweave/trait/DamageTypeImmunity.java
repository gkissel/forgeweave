package dev.gkissel.forgeweave.trait;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;

/**
 * Blows of a named damage type simply do not land -- the M6 armor library's
 * {@code damage_type_immunity(damageTypeTag)} (issue #831), the lightning-immunity idea
 * parameterized on the tag so one class covers every "immune to X" a preset batch wants.
 *
 * <p>Distinct from {@link ConvertDamageToHealing} by intent, not by mechanism: this one grants
 * nothing back, which is what makes it cheap enough to hand a material that should merely shrug a
 * hazard off.
 *
 * @param damageType which blows are ignored
 */
public record DamageTypeImmunity(TagKey<DamageType> damageType) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (defense.source().is(damageType)) {
            blow.setDamage(0.0F);
        }
    }
}
