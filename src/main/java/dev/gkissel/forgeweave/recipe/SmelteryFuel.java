package dev.gkissel.forgeweave.recipe;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * A fluid the smeltery can burn as fuel, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/smeltery_fuel/<name>.json} (docs/SCOPE.md M2 issue #97). Ported
 * from upstream 1.12's {@code TinkerRegistry.registerSmelteryFuel} (NOTICE.md), which is a hardcoded
 * Java call there ({@code registerSmelteryFuel(new FluidStack(FluidRegistry.LAVA, 50), 100)} in
 * {@code TinkerSmeltery.registerSmelteryFuel}) and datapack JSON here.
 *
 * <pre>
 * {
 *   "fluid": "minecraft:lava",
 *   "amount": 50,
 *   "duration": 100
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code amount} -- mB drained from a wall tank each burn cycle, upstream's registered
 *       {@code FluidStack} amount.
 *   <li>{@code duration} -- how many melt ticks (every {@code
 *       SmelteryControllerBlockEntity.MELT_INTERVAL_TICKS}, i.e. upstream's own {@code heatItems}
 *       cadence of once every four real ticks) one drain of {@code amount} lasts before the smeltery
 *       needs another. This is upstream's own unit: its {@code fuel} counter only ever decrements
 *       inside {@code heatItems()}, which already runs once every four real ticks there too -- so
 *       lava's shipped {@code duration: 100} is exactly upstream's
 *       {@code registerSmelteryFuel(lava, 100)}, not 100 real ticks (NOTICE.md).
 *   <li>{@code temperature} -- optional; the working heat this fuel burns at, defaulting to the
 *       fluid's own {@code FluidType} temperature (lava needs no override). A datapack fuel whose
 *       burning temperature should differ from its fluid's own -- a modded superfuel -- sets it.
 * </ul>
 */
public record SmelteryFuel(Fluid fluid, int amount, int duration, int temperature) {

    public static final ResourceKey<Registry<SmelteryFuel>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "smeltery_fuel"));

    public static final Codec<SmelteryFuel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(SmelteryFuel::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(SmelteryFuel::amount),
            ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(SmelteryFuel::duration),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("temperature").forGetter(fuel -> Optional.of(fuel.temperature())))
            .apply(instance, SmelteryFuel::withDerivedTemperature));

    private static SmelteryFuel withDerivedTemperature(Fluid fluid, int amount, int duration, Optional<Integer> temperature) {
        return new SmelteryFuel(fluid, amount, duration, temperature.orElseGet(() -> fluid.getFluidType().getTemperature()));
    }

    /**
     * The registered fuel for {@code fluid}, or empty if it cannot be burned. Every fluid registers
     * at most one fuel in practice; ties (a datapack authoring mistake) break on registry id, the same
     * way {@link MeltingRecipe#find} does.
     */
    public static Optional<SmelteryFuel> find(RegistryAccess registries, Fluid fluid) {
        Registry<SmelteryFuel> fuels = registries.registryOrThrow(REGISTRY);
        return fuels.entrySet().stream()
                .filter(entry -> entry.getValue().fluid() == fluid)
                .min(Comparator.comparing(entry -> entry.getKey().location()))
                .map(Map.Entry::getValue);
    }
}
