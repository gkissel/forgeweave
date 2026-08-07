package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Serializer for {@link RetexturedShapedRecipe}; see that class's javadoc. */
public class RetexturedShapedRecipeSerializer implements RecipeSerializer<RetexturedShapedRecipe> {
    public static final RetexturedShapedRecipeSerializer INSTANCE = new RetexturedShapedRecipeSerializer();

    @Override
    public MapCodec<RetexturedShapedRecipe> codec() {
        return RetexturedShapedRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, RetexturedShapedRecipe> streamCodec() {
        return RetexturedShapedRecipe.STREAM_CODEC;
    }
}
