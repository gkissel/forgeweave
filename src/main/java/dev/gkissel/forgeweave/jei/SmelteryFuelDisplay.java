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
 *
 * <p>One row in the category is not a registry entry at all: the energized tank's (issue #972),
 * flagged by {@code energizedTank} the same way {@link EntityMeltingDisplay#defaultRow()} flags the
 * default entity rule. It shows the tank as the thing you pour a sample into rather than a bucket,
 * and {@code energizedCost} carries what one melt tick costs at that row's temperature, so a player
 * can read the cost off the tooltip instead of a wiki. Both fields are their no-op values for every
 * ordinary fluid row.
 */
record SmelteryFuelDisplay(Fluid fluid, int amount, int duration, int temperature, int hotterThanLavaBy,
        boolean energizedTank, int energizedCost) {

    /** An ordinary fluid row: not the energized tank's, so it costs no energy (issue #972). */
    SmelteryFuelDisplay(Fluid fluid, int amount, int duration, int temperature, int hotterThanLavaBy) {
        this(fluid, amount, duration, temperature, hotterThanLavaBy, false, 0);
    }
}
