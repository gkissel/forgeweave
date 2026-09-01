package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;

import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * Builds one {@link SmelteryFuelDisplay} per {@code smeltery_fuel} registry entry (issue #890): a
 * plain snapshot of the registry, the same shape {@link AlloyingRecipes} uses for its own
 * no-collision registry, plus the one derived field {@link SmelteryFuelCategory} needs and the
 * registry itself does not carry -- how much hotter this fuel burns than lava.
 *
 * <p>Lava's own registered temperature (not {@code Fluids.LAVA.getFluidType().getTemperature()}
 * directly) is the baseline, since a pack can retune lava's {@code smeltery_fuel} row same as any
 * other; the fluid type's own default is only a fallback for the case lava has no row at all
 * (config-disabled smeltery, or a test registry that never shipped lava).
 */
final class SmelteryFuelRecipes {
    static List<SmelteryFuelDisplay> build(Map<ResourceLocation, SmelteryFuel> fuels) {
        int lavaTemperature = fuels.values().stream()
                .filter(fuel -> fuel.fluid() == Fluids.LAVA)
                .mapToInt(SmelteryFuel::temperature)
                .findFirst()
                .orElseGet(() -> Fluids.LAVA.getFluidType().getTemperature());

        List<SmelteryFuelDisplay> displays = new ArrayList<>();
        for (SmelteryFuel fuel : fuels.values()) {
            // Lava vs. lava never gets its own "hotter than lava" note, and nothing colder than lava
            // gets one either -- the note exists to explain why a fuel is worth using over lava.
            int hotterThanLavaBy = fuel.fluid() == Fluids.LAVA ? 0 : Math.max(0, fuel.temperature() - lavaTemperature);
            displays.add(new SmelteryFuelDisplay(fuel.fluid(), fuel.amount(), fuel.duration(), fuel.temperature(), hotterThanLavaBy));
        }
        return displays;
    }

    private SmelteryFuelRecipes() {}
}
