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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * What one item melts into inside a smeltery, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/melting_recipe/<name>.json} (docs/SCOPE.md M2 issue #96).
 * Ported from upstream 1.12's {@code library/smeltery/MeltingRecipe} (NOTICE.md), which is a
 * hardcoded Java registry there and datapack JSON here.
 *
 * <pre>
 * {
 *   "input": {"tag": "c:ores/iron"},
 *   "fluid": "forgeweave:molten_iron",
 *   "amount": 144,
 *   "temperature": 534
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code input} -- a vanilla {@link Ingredient}, so an item or a tag. Tag inputs are what make
 *       docs/SCOPE.md's M2 ladder promise work: the shipped recipes key off the {@code c:} convention
 *       tags ({@code c:ores/iron}, {@code c:ingots/copper}, ...), so a mod that tags its own ore or
 *       ingot melts with no Forgeweave code and no Forgeweave datapack. It is upstream's
 *       {@code registerOredictMeltingCasting} with 1.21's tags in place of 1.12's ore dictionary.
 *   <li>{@code amount} -- the <b>base</b> output in mB, before any core-tier multiplier. Upstream's
 *       ore recipes bake their 2x ore bonus into this number; docs/SCOPE.md splits the two ("melting
 *       recipes hold base amounts, the core multiplies"), so an ore melts here as its raw-drop
 *       equivalent and {@code SmelteryCore#yieldMultiplier} scales it in issue #99.
 *   <li>{@code temperature} -- optional. Left out, it is derived from the output fluid's own
 *       temperature by {@link #calcTemperature}, exactly as upstream's two-argument constructor does.
 * </ul>
 */
public record MeltingRecipe(Ingredient input, Fluid fluid, int amount, int temperature) {

    /** Upstream {@code Material.VALUE_Nugget}: a ninth of an ingot. */
    public static final int VALUE_NUGGET = 16;
    /** Upstream {@code Material.VALUE_Ingot}. */
    public static final int VALUE_INGOT = 144;
    /** Upstream {@code Material.VALUE_Block}: nine ingots, and {@link #calcTemperature}'s baseline. */
    public static final int VALUE_BLOCK = VALUE_INGOT * 9;

    /**
     * Where both upstream and Forgeweave put "cold": a recipe or a smeltery at 300 is at zero working
     * heat. Upstream's {@code getUsableTemperature} and its {@code getTemperature() - 300} fuel
     * conversion are the two halves of this one constant.
     */
    public static final int AMBIENT_TEMPERATURE = 300;

    /**
     * Upstream {@code TileHeatingStructure.TIME_FACTOR}: melt progress is tracked at eight times the
     * required temperature so a slow smeltery still makes visible progress each step.
     */
    public static final int TIME_FACTOR = 8;

    /** Upstream {@code MeltingRecipe.LOG9_2}, i.e. log base 9 of 2: a ninth of a block melts at half a block's heat. */
    private static final double LOG9_2 = 0.31546487678;

    public static final ResourceKey<Registry<MeltingRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "melting_recipe"));

    public static final Codec<MeltingRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.fieldOf("input").forGetter(MeltingRecipe::input),
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(MeltingRecipe::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(MeltingRecipe::amount),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("temperature").forGetter(recipe -> Optional.of(recipe.temperature())))
            .apply(instance, MeltingRecipe::withDerivedTemperature));

    private static MeltingRecipe withDerivedTemperature(Ingredient input, Fluid fluid, int amount, Optional<Integer> temperature) {
        return new MeltingRecipe(input, fluid, amount,
                temperature.orElseGet(() -> calcTemperature(fluid.getFluidType().getTemperature(), amount)));
    }

    /**
     * Upstream {@code MeltingRecipe.calcTemperature}: a full block of the metal melts at the fluid's
     * own temperature, and every ninth of that costs half the heat above {@link #AMBIENT_TEMPERATURE}
     * -- so an ingot sits halfway, a nugget a quarter of the way.
     */
    public static int calcTemperature(int fluidTemperature, int amount) {
        int headroom = Math.max(0, fluidTemperature - AMBIENT_TEMPERATURE);
        double fraction = Math.pow((double) amount / (double) VALUE_BLOCK, LOG9_2);
        return AMBIENT_TEMPERATURE + (int) (fraction * (double) headroom);
    }

    /** Upstream {@code MeltingRecipe#getUsableTemperature}: {@link #temperature} on the smeltery's own zero-is-300 scale. */
    public int usableTemperature() {
        return Math.max(1, temperature - AMBIENT_TEMPERATURE);
    }

    /**
     * Total melt progress this recipe needs, in the units the smeltery accumulates -- upstream's
     * {@code updateHeatRequired} floor of 5 followed by {@code setHeatRequiredForSlot}'s
     * {@link #TIME_FACTOR} scaling.
     */
    public int heatRequired() {
        return Math.max(5, usableTemperature()) * TIME_FACTOR;
    }

    /** The base output; the yield multiplier of the core melting it is issue #99's, not this recipe's. */
    public FluidStack result() {
        return new FluidStack(fluid, amount);
    }

    /**
     * The recipe for {@code stack}, or empty if nothing melts it.
     *
     * <p>ponytail: a linear scan of a registry holding a few dozen entries, run once per item
     * inserted rather than per tick. Index it by item if a pack ever makes the scan show up in a
     * profile. Ties are broken by registry id so two overlapping recipes always pick the same winner
     * -- overlapping inputs are a datapack authoring mistake, not something to resolve cleverly.
     */
    public static Optional<MeltingRecipe> find(RegistryAccess registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Registry<MeltingRecipe> recipes = registries.registryOrThrow(REGISTRY);
        return recipes.entrySet().stream()
                .filter(entry -> entry.getValue().input.test(stack))
                .min(Comparator.comparing(entry -> entry.getKey().location()))
                .map(Map.Entry::getValue);
    }
}
