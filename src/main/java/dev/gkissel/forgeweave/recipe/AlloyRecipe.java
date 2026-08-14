package dev.gkissel.forgeweave.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Which molten metals combine into which alloy inside a smeltery's tank, defined entirely in datapack
 * JSON under {@code data/<namespace>/forgeweave/alloy_recipe/<name>.json} (docs/SCOPE.md M2 issue
 * #98). Ported from upstream 1.12's {@code library/smeltery/AlloyRecipe} (NOTICE.md), which is a
 * hardcoded Java registry there ({@code TinkerRegistry.registerAlloy}) and datapack JSON here.
 *
 * <pre>
 * {
 *   "inputs": [
 *     {"fluid": "forgeweave:molten_cobalt", "amount": 2},
 *     {"fluid": "forgeweave:molten_ardite", "amount": 2}
 *   ],
 *   "result": {"fluid": "forgeweave:molten_manyullyn", "amount": 2}
 * }
 * </pre>
 *
 * <p><b>The amounts are a ratio, not a batch size</b> -- upstream's own convention, and the reason it
 * writes manyullyn as {@code 2 + 2 = 2} rather than {@code 144 + 144 = 144} even though its comment
 * reads "1 ingot cobalt + 1 ingot ardite = 1 ingot manyullyn". {@link #matches} finds the largest whole
 * number of times the ratio fits what is in the tank, so a tank holding one ingot of cobalt and half an
 * ingot of ardite alloys the half it can and leaves the rest of the cobalt sitting there. Writing the
 * ratio in ingots instead would make that same tank do nothing at all.
 *
 * <p>Two rules are enforced at parse time rather than trusted, both from upstream's constructor:
 * at least two inputs, and the result may not be one of the inputs. The second is not cosmetic --
 * a recipe consuming its own output alloys forever, and the smeltery's alloying pass runs to
 * exhaustion (see {@code SmelteryControllerBlockEntity#alloyTankContents}).
 *
 * <p>{@code priority} (#291) breaks contention when a tank holds enough of a shared fluid to match
 * more than one recipe at once -- lower resolves first, default {@code 0}. This is upstream's own
 * answer, just moved from Java to JSON: 1.12's {@code TinkerRegistry.getAlloys()} is a {@code
 * LinkedList} that returns recipes in registration order rather than sorted by name, and 1.20
 * datagens rose gold ahead of netherite for the same reason. Forgeweave's datapack registry has no
 * such registration order of its own to inherit (JSON files load in whatever order the resource
 * manager lists them), so the ordering has to be explicit in the recipe rather than implicit in
 * load order -- see {@code SmelteryControllerBlockEntity#alloyOnce}, the only reader that cares.
 */
public record AlloyRecipe(List<FluidStack> inputs, FluidStack result, int priority) {

    public static final ResourceKey<Registry<AlloyRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "alloy_recipe"));

    /**
     * One {@code {"fluid": ..., "amount": ...}} entry, the same two keys {@link MeltingRecipe} and
     * {@code CastingRecipe} spell their fluid with rather than NeoForge's own {@code id}/{@code
     * amount} {@link FluidStack} codec.
     */
    private static final Codec<FluidStack> FLUID_AMOUNT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidStack::getFluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidStack::getAmount))
            .apply(instance, FluidStack::new));

    public static final Codec<AlloyRecipe> CODEC = RecordCodecBuilder.<AlloyRecipe>create(instance -> instance.group(
            FLUID_AMOUNT_CODEC.listOf().fieldOf("inputs").forGetter(AlloyRecipe::inputs),
            FLUID_AMOUNT_CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(AlloyRecipe::priority))
            .apply(instance, AlloyRecipe::new))
            .validate(AlloyRecipe::validate);

    private static DataResult<AlloyRecipe> validate(AlloyRecipe recipe) {
        if (recipe.inputs.size() < 2) {
            return DataResult.error(() -> "an alloy recipe needs at least two inputs");
        }
        if (recipe.inputs.stream().anyMatch(input -> input.getFluid() == recipe.result.getFluid())) {
            return DataResult.error(() -> "an alloy recipe's result may not also be one of its inputs");
        }
        return DataResult.success(recipe);
    }

    /**
     * How many times this recipe's ratio fits {@code tankFluids} -- upstream's {@code
     * AlloyRecipe#matches}, which takes the smallest whole multiple any one input allows and returns
     * {@code 0} the moment an input is missing or is present in less than a single application's
     * worth.
     *
     * @param tankFluids the smeltery's contents, in tank order (order does not affect the answer)
     */
    public int matches(List<FluidStack> tankFluids) {
        int times = Integer.MAX_VALUE;
        for (FluidStack needed : inputs) {
            int available = 0;
            for (FluidStack held : tankFluids) {
                // Components, not just the fluid: the alloying pass drains by FluidStack, and
                // SmelteryTank#drain matches components too. Matching more loosely here than the
                // drain does would let a componented molten metal satisfy the recipe and then
                // survive the drain -- the alloy would be created out of nothing.
                if (FluidStack.isSameFluidSameComponents(held, needed)) {
                    available = held.getAmount();
                    break;
                }
            }
            times = Math.min(times, available / needed.getAmount());
            if (times == 0) {
                return 0;
            }
        }
        return times;
    }

    /** Total input volume of one application, i.e. how much fluid {@code times = 1} consumes. */
    public int inputVolume() {
        int total = 0;
        for (FluidStack input : inputs) {
            total += input.getAmount();
        }
        return total;
    }
}
