package dev.gkissel.forgeweave.recipe;

import java.util.function.Function;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/** Recipe serializers Forgeweave registers beyond the vanilla set. */
public final class ForgeweaveRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Forgeweave.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RetexturedShapedRecipe>> RETEXTURED_SHAPED =
            RECIPE_SERIALIZERS.register("retextured_shaped", () -> RetexturedShapedRecipeSerializer.INSTANCE);

    /** {@link GravelFlintRecipe} (parity audit T55, issue #486). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GravelFlintRecipe>> GRAVEL_FLINT =
            RECIPE_SERIALIZERS.register("gravel_flint", () -> GravelFlintRecipeSerializer.INSTANCE);

    /**
     * Issue #463's crafting-grid tool repair. A {@link #simpleCrafting} serializer because the
     * recipe carries no data of its own beyond the crafting-book category, the same shape upstream's
     * {@code RepairRecipe} had (an {@code IRecipe} with a registry name and nothing else).
     */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SharpeningKitRepairRecipe>>
            SHARPENING_KIT_REPAIR = RECIPE_SERIALIZERS.register("sharpening_kit_repair",
                    () -> simpleCrafting(SharpeningKitRepairRecipe::new));

    /** {@link MixedSlimeBlockRecipe} (issue #635, parity audit T57): carries no data of its own either. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MixedSlimeBlockRecipe>>
            MIXED_SLIME_BLOCK = RECIPE_SERIALIZERS.register("mixed_slime_block",
                    () -> simpleCrafting(MixedSlimeBlockRecipe::new));

    /** {@link EnergizedTankRecipe} (issue #972, M8 D-M8-11). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<EnergizedTankRecipe>> ENERGIZED_TANK =
            RECIPE_SERIALIZERS.register("energized_tank", () -> EnergizedTankRecipe.Serializer.INSTANCE);

    /** {@link MixedSlimeSlingRecipe} (issue #649, parity audit T57): carries no data of its own either. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MixedSlimeSlingRecipe>>
            MIXED_SLIME_SLING = RECIPE_SERIALIZERS.register("mixed_slime_sling",
                    () -> simpleCrafting(MixedSlimeSlingRecipe::new));

    /** {@link GatedShapedRecipe}: the coloured Slimeslings' shaped recipes, behind the {@code gadgets} toggle. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GatedShapedRecipe>> GATED_SHAPED =
            RECIPE_SERIALIZERS.register("gated_shaped", () -> GatedShapedRecipe.Serializer.INSTANCE);

    /**
     * 1.21.1's {@code SimpleCraftingRecipeSerializer}, which 26.1 removed: a recipe whose only data is
     * its crafting-book {@code category} (defaulting to {@code misc}), with the same JSON and wire shape.
     */
    static <T extends CraftingRecipe> RecipeSerializer<T> simpleCrafting(Function<CraftingBookCategory, T> factory) {
        MapCodec<T> codec = CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                .xmap(factory, CraftingRecipe::category);
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec =
                CraftingBookCategory.STREAM_CODEC.<T>map(factory, CraftingRecipe::category).cast();
        return new RecipeSerializer<>(codec, streamCodec);
    }

    private ForgeweaveRecipeSerializers() {}
}
