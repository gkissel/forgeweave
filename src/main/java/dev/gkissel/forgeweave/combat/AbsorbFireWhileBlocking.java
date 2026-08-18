package dev.gkissel.forgeweave.combat;

import net.minecraft.tags.DamageTypeTags;

/**
 * Blocking negates fire damage outright, at a flat durability cost -- flammable's defensive half
 * (issue #229), ported from upstream 1.12's {@code TraitFlammable#onBlock}: cancel the event
 * ({@code event.setCanceled(true)}) and {@code ToolHelper.damageTool(tool, 3, player)}. Returning 0
 * is this pipeline's cancel ({@link CombatSeams}), so the blow spends no invulnerability window and
 * plays no hurt animation, exactly like upstream's cancellation.
 */
public record AbsorbFireWhileBlocking(int durabilityCost) implements CombatSeam {

    @Override
    public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
        if (!defense.blocking() || !defense.source().is(DamageTypeTags.IS_FIRE)) {
            return damage;
        }
        defense.tool().hurtAndBreak(durabilityCost, defense.defender(), defense.slot());
        return 0.0F;
    }
}
