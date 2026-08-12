package dev.gkissel.forgeweave.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Applies a potion effect to the target on a landed hit -- shulking's levitation and webbed's
 * slowness (docs/SCOPE.md M3, issue #163), one class instantiated for each rather than a bespoke
 * seam per modifier. This is literally the {@code potion_effect_on_hit} entry ADR-0004's M6
 * parameterized behavior library names as an example: {@code effect}, {@code amplifier} and
 * {@code durationTicks} are its only fields, baked in by whichever {@link CombatSeams.Provider}
 * constructs it from a modifier's level.
 *
 * @param effect the potion effect to add
 * @param amplifier the effect's amplifier (0-indexed, so {@code 1} reads as level II)
 * @param durationTicks how long the effect lasts
 */
public record PotionEffectOnHitSeam(Holder<MobEffect> effect, int amplifier, int durationTicks) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        hit.target().addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
    }
}
