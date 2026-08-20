package dev.gkissel.forgeweave.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/** Recipe serializers Forgeweave registers beyond the vanilla set. */
public final class ForgeweaveRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Forgeweave.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RetexturedShapedRecipeSerializer> RETEXTURED_SHAPED =
            RECIPE_SERIALIZERS.register("retextured_shaped", () -> RetexturedShapedRecipeSerializer.INSTANCE);

    /** {@link GravelFlintRecipe} (parity audit T55, issue #486). */
    public static final DeferredHolder<RecipeSerializer<?>, GravelFlintRecipeSerializer> GRAVEL_FLINT =
            RECIPE_SERIALIZERS.register("gravel_flint", () -> GravelFlintRecipeSerializer.INSTANCE);

    /**
     * Issue #463's crafting-grid tool repair. A {@link SimpleCraftingRecipeSerializer} because the
     * recipe carries no data of its own beyond the crafting-book category, the same shape upstream's
     * {@code RepairRecipe} had (an {@code IRecipe} with a registry name and nothing else).
     */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SharpeningKitRepairRecipe>>
            SHARPENING_KIT_REPAIR = RECIPE_SERIALIZERS.register("sharpening_kit_repair",
                    () -> new SimpleCraftingRecipeSerializer<>(SharpeningKitRepairRecipe::new));

    /** {@link MixedSlimeBlockRecipe} (issue #635, parity audit T57): carries no data of its own either. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<MixedSlimeBlockRecipe>>
            MIXED_SLIME_BLOCK = RECIPE_SERIALIZERS.register("mixed_slime_block",
                    () -> new SimpleCraftingRecipeSerializer<>(MixedSlimeBlockRecipe::new));

    /** {@link MixedSlimeSlingRecipe} (issue #649, parity audit T57): carries no data of its own either. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<MixedSlimeSlingRecipe>>
            MIXED_SLIME_SLING = RECIPE_SERIALIZERS.register("mixed_slime_sling",
                    () -> new SimpleCraftingRecipeSerializer<>(MixedSlimeSlingRecipe::new));

    private ForgeweaveRecipeSerializers() {}
}
