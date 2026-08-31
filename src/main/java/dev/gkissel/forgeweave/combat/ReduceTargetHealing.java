package dev.gkissel.forgeweave.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A landed hit temporarily attenuates healing the target receives -- ADR-0004's M6 on-hit effect
 * library batch (issue #828) {@code reduce_target_healing(fraction, duration)}, the reference
 * instance "Mortal Wounds".
 *
 * <p>The issue asked for whichever is cheapest between a new seam and a per-target marker rather
 * than a general status framework. This is a per-target marker ({@link ForgeweaveMobEffects#REDUCED_HEALING},
 * inert like {@link MarkerEffect}) plus one listener ({@code ForgeweaveTraits#onLivingHeal}) that
 * shaves {@code net.neoforged.neoforge.event.entity.living.LivingHealEvent} while the mark is live
 * -- no new data component, no framework. The one wrinkle a plain marker cannot express on its own
 * is <em>how much</em> to shave, since the mob effect registry has exactly one shared instance for
 * every applier; this seam reuses the mark's own amplifier as a 0-100 percent encoding of {@code
 * fraction} rather than adding a data component to carry it, which keeps a second {@code
 * reduce_target_healing} instance (a different fraction) expressible without any new plumbing.
 *
 * @param fraction how much of an incoming heal to shave, {@code 0..1}
 * @param durationTicks how long the mark lasts
 */
public record ReduceTargetHealing(float fraction, int durationTicks) implements CombatSeam {

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        int percent = Math.round(Mth.clamp(fraction, 0.0F, 1.0F) * 100.0F);
        hit.target().addEffect(
                new MobEffectInstance(ForgeweaveMobEffects.REDUCED_HEALING, durationTicks, percent));
    }
}
