package dev.gkissel.forgeweave.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The thorns armor modifier (M4-6, issue #681), ported from the 1.20 clone's
 * {@code tools/modules/armor/ThornsModule} + {@code CounterModule#onAttacked}: on a <em>direct</em>
 * blow ({@code OnAttackedModifierHook#isDirectDamage} -- a living attacker, not indirect, not a
 * source in {@code #minecraft:avoids_guardian_thorns}) the piece rolls {@code chance}, and on a hit
 * deals {@code constant + random * nextFloat()} thorns-typed damage to the attacker
 * ({@code ThornsModule.type(DamageTypes.THORNS).constantFlat(1).randomFlat(3)}) and spends one
 * durability of the piece ({@code durability_usage: 1}). Rides #680's {@link CombatSeam#onDefend}
 * armor pass; the trait of the same name ({@code ForgeweaveTraits#THORNS}) is this at level 1.
 *
 * <p>Distinct from {@link ThornsReflectSeam} (cactus's 1.12 <em>spiky</em> trait: a deterministic
 * reflect of the held tool's own attack damage) -- the two share only the damage type.
 *
 * <p>ponytail: the clone doubles the level for a blocking shield ({@code CounterModule#getLevel});
 * Forgeweave ships no modifiable shield, so there is nothing to double.
 *
 * @param chance per-blow chance, the clone's {@code chance: {each_level: 0.15}} already multiplied by
 *     the piece's effective level
 * @param constant the flat part of the counter damage (clone 1)
 * @param random the random part, scaled by {@code nextFloat()} (clone 3)
 */
public record ThornsCounterSeam(float chance, float constant, float random) implements CombatSeam {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        LivingEntity attacker = defense.attacker();
        DamageSource source = defense.source();
        if (attacker == null || attacker == defense.defender() || !attacker.isAlive()
                || !source.isDirect() || source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)) {
            return;
        }
        if (chance <= 0.0F || (chance < 1.0F && defense.level().random.nextFloat() >= chance)) {
            return;
        }
        float value = constant + (random > 0.0F ? random * defense.level().random.nextFloat() : 0.0F);
        if (value <= 0.0F) {
            return;
        }
        attacker.hurt(defense.level().damageSources().thorns(defense.defender()), value);
        defense.tool().hurtAndBreak(1, defense.defender(), defense.slot());
    }
}
