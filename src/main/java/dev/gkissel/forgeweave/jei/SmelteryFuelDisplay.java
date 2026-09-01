package dev.gkissel.forgeweave.jei;

import net.minecraft.world.level.material.Fluid;

/**
 * One {@code smeltery_fuel} registry entry, ready to draw (issue #890): {@code fluid} burns at
 * {@code amount} mB per drain, lasting {@code duration} smeltery melt-cycles (see {@link
 * dev.gkissel.forgeweave.recipe.SmelteryFuel}'s own javadoc on that unit), at {@code temperature}.
 * {@code hotterThanLavaBy} is the pre-computed, unit-agnostic delta over lava's own registered
 * temperature ({@link SmelteryFuelRecipes#build}) -- positive only for a fuel genuinely hotter than
 * lava, {@code 0} for lava itself and for anything no hotter, so {@link SmelteryFuelCategory} can
 * skip the "unlocks recipes lava can't reach" tooltip line rather than stating the obvious.
 */
record SmelteryFuelDisplay(Fluid fluid, int amount, int duration, int temperature, int hotterThanLavaBy) {}
