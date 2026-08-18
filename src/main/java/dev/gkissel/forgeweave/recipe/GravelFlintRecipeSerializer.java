package dev.gkissel.forgeweave.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Serializer for {@link GravelFlintRecipe}; see that class's javadoc. */
public class GravelFlintRecipeSerializer implements RecipeSerializer<GravelFlintRecipe> {
    public static final GravelFlintRecipeSerializer INSTANCE = new GravelFlintRecipeSerializer();

    @Override
    public MapCodec<GravelFlintRecipe> codec() {
        return GravelFlintRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, GravelFlintRecipe> streamCodec() {
        return GravelFlintRecipe.STREAM_CODEC;
    }
}
