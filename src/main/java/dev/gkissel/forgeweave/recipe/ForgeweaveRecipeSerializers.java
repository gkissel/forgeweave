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

    /**
     * Issue #463's crafting-grid tool repair. A {@link SimpleCraftingRecipeSerializer} because the
     * recipe carries no data of its own beyond the crafting-book category, the same shape upstream's
     * {@code RepairRecipe} had (an {@code IRecipe} with a registry name and nothing else).
     */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SharpeningKitRepairRecipe>>
            SHARPENING_KIT_REPAIR = RECIPE_SERIALIZERS.register("sharpening_kit_repair",
                    () -> new SimpleCraftingRecipeSerializer<>(SharpeningKitRepairRecipe::new));

    private ForgeweaveRecipeSerializers() {}
}
