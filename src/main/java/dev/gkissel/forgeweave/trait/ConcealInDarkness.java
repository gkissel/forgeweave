package dev.gkissel.forgeweave.trait;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Mobs notice the wearer less in the dark -- the M6 armor library's
 * {@code conceal_in_darkness(lightThreshold)} (issue #831).
 *
 * <p>The second of this batch's two behaviours {@code Trait#onDefend} cannot serve: mob awareness is
 * decided long before anything is hit. It rides {@link Trait#visibilityMultiplier} (added under the
 * interface's "add a hook when a trait needs it" rule), consumed by
 * {@code ForgeweaveTraits#onLivingVisibility} on NeoForge's {@code LivingVisibilityEvent} -- the
 * same event vanilla's own sneaking and mob-head reductions ride, so the numbers compose rather than
 * fight.
 *
 * @param lightThreshold the highest block-light level that still counts as dark
 * @param visibility multiplied into how visible the wearer is; 0.5 halves the distance a mob
 *     notices them from
 */
public record ConcealInDarkness(int lightThreshold, float visibility) implements Trait {

    @Override
    public float visibilityMultiplier(ItemStack piece, LivingEntity wearer) {
        return wearer.level().getMaxLocalRawBrightness(wearer.blockPosition()) <= lightThreshold ? visibility : 1.0F;
    }
}
