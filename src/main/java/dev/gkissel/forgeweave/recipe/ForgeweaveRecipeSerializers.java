package dev.gkissel.forgeweave.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/** Recipe serializers Forgeweave registers beyond the vanilla set. */
public final class ForgeweaveRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Forgeweave.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RetexturedShapedRecipeSerializer> RETEXTURED_SHAPED =
            RECIPE_SERIALIZERS.register("retextured_shaped", () -> RetexturedShapedRecipeSerializer.INSTANCE);

    private ForgeweaveRecipeSerializers() {}
}
