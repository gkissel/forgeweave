package dev.gkissel.forgeweave.trait;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Every heal the wearer receives is scaled -- the M6 armor library's
 * {@code amplify_incoming_healing(factor)} (issue #831).
 *
 * <p>The first of this batch's two behaviours {@code Trait#onDefend} genuinely cannot serve: a heal
 * is not a blow and never reaches the defensive pass. It rides {@link Trait#healingMultiplier},
 * added under the interface's standing "add a hook when a trait needs it" rule and consumed by
 * {@code ForgeweaveTraits#onLivingHeal} -- the {@code LivingHealEvent} listener that already exists
 * for {@code grievous}' reduced-healing mark, so no new listener is registered for it.
 *
 * @param factor multiplied into the heal; 1.25 is +25%
 */
public record AmplifyIncomingHealing(float factor) implements Trait {

    @Override
    public float healingMultiplier(ItemStack piece, LivingEntity wearer, float amount) {
        return factor;
    }
}
