package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;

import dev.gkissel.forgeweave.block.EnergizedHeat;
import dev.gkissel.forgeweave.block.EnergizedTankBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
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
    static List<SmelteryFuelDisplay> build(Map<ResourceLocation, SmelteryFuel> fuels, boolean energizedTank) {
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
        if (energizedTank) {
            displays.add(energizedTankRow(lavaTemperature));
        }
        return displays;
    }

    /**
     * The energized tank's own row (issue #972), which is not a registry entry -- the same synthetic
     * extra row {@link EntityMeltingRecipes} appends for its default rule. Lava is the worked
     * example: the tank imitates whichever registered fuel it holds a sample of, and every one of
     * those already has a row above showing the temperature this formula is applied to, so the
     * arithmetic only needs spelling out once against the fuel every world has.
     *
     * <p>{@code amount} is the sample, one bucket, and the tooltip is where the row says it is never
     * drained. {@code duration} is 1: the tank pays per melt tick rather than buying a run of them
     * with one drain.
     */
    private static SmelteryFuelDisplay energizedTankRow(int lavaTemperature) {
        int cost = EnergizedHeat.costPerMeltTick(lavaTemperature,
                ForgeweaveConfig.energizedTankRfPerMeltTickBase(),
                ForgeweaveConfig.energizedTankTemperatureDivisor(),
                false, ForgeweaveConfig.energizedTankOverdriveCost());
        return new SmelteryFuelDisplay(Fluids.LAVA, EnergizedTankBlockEntity.SAMPLE_CAPACITY, 1,
                lavaTemperature, 0, true, cost);
    }

    private SmelteryFuelRecipes() {}
}
