package dev.gkissel.forgeweave.recipe;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * What pouring a fluid onto a smeltery core transforms it into (issue #845), defined entirely in
 * datapack JSON under {@code data/<namespace>/forgeweave/core_transform_recipe/<name>.json} -- the
 * same shape as Forgeweave's other eight datapack registries (see {@link Forgeweave}'s
 * {@code registerDataPackRegistries}), so a pack can retune the cost or add its own tier without
 * touching Java.
 *
 * <pre>
 * {
 *   "fluid": "forgeweave:molten_dragon_breath",
 *   "from_block": "forgeweave:nether_core",
 *   "to_block": "forgeweave:end_core",
 *   "amount": 1000
 * }
 * </pre>
 *
 * <p>Pouring {@code fluid} onto a core whose block currently equals {@code from_block} accumulates
 * towards {@code amount} mB ({@link dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity}'s
 * transform fluid handler, which reuses the faucet -> block-entity plumbing the drain/tank already
 * use rather than a bespoke "pour onto any block" mechanic). Once the threshold is reached the block
 * becomes {@code to_block} and the core's saved state -- structure, tank contents, melting progress,
 * fuel -- carries across via an NBT round-trip, whether the smeltery was formed or not.
 *
 * <p>Pouring the wrong fluid, or the right fluid onto a tier with no matching row, is simply not
 * found here, and the handler's {@code fill} then refuses the fluid outright (returns 0) rather than
 * silently absorbing it -- which is also what keeps a faucet from draining its source into a core
 * that has nothing to do with what it holds.
 */
public record CoreTransformRecipe(Fluid fluid, Block fromBlock, Block toBlock, int amount) {

    public static final ResourceKey<Registry<CoreTransformRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "core_transform_recipe"));

    public static final Codec<CoreTransformRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(CoreTransformRecipe::fluid),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("from_block").forGetter(CoreTransformRecipe::fromBlock),
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("to_block").forGetter(CoreTransformRecipe::toBlock),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(CoreTransformRecipe::amount))
            .apply(instance, CoreTransformRecipe::new));

    /**
     * The row that turns {@code fromBlock} into something else when {@code fluid} is poured over it,
     * or empty if no such row exists -- the wrong fluid, and the right fluid on the wrong tier, both
     * fall through to this and become a no-op at the call site.
     *
     * <p>ponytail: a linear scan of a registry that will only ever hold a handful of rows, same
     * reasoning as {@link EntityMeltingRecipe#find}.
     */
    public static Optional<CoreTransformRecipe> find(RegistryAccess registries, Fluid fluid, Block fromBlock) {
        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.SMELTERY)) {
            return Optional.empty();
        }
        Registry<CoreTransformRecipe> recipes = registries.registryOrThrow(REGISTRY);
        return recipes.stream()
                .filter(recipe -> recipe.fluid() == fluid && recipe.fromBlock() == fromBlock)
                .findFirst();
    }
}
