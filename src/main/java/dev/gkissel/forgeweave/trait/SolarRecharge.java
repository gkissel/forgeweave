package dev.gkissel.forgeweave.trait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * {@code solar_recharge(ratePerTick)}: refills the tool's energy buffer while its holder stands in
 * daylight (issue #830 deliverable 3) -- the reference pool's "Photovoltaic" idea (design pool
 * docs/research/m6-material-expansion-references.md §6.5), own numbers and own daylight check.
 * Rides {@link Trait#inventoryTick}, the same seam mending moss's self-repair uses, and is a no-op
 * on a tool whose traits carry no {@link Trait#energyCapacity} -- there is nowhere to put the
 * energy without an {@code energized} trait alongside it.
 *
 * <p>ponytail: {@link #isDaylight} is a plain {@code Level#isDay() && canSeeSky} check rather than
 * sharing #829's sibling {@code self_repair_when} daylight condition -- issue #830 asked to reuse
 * it, but #829 had not landed when this one did, so there is nothing to share yet. If #829 lands
 * with its own daylight helper, fold this one into it rather than keeping two.
 */
public record SolarRecharge(int ratePerTick) implements Trait {

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        if (ratePerTick <= 0 || !isDaylight(level, holder)) {
            return;
        }
        int capacity = ForgeweaveTraits.energyCapacity(stack);
        EnergyBuffer.receive(stack, capacity, ratePerTick, false);
    }

    private static boolean isDaylight(ServerLevel level, LivingEntity holder) {
        return level.isDay() && level.canSeeSky(holder.blockPosition());
    }
}
