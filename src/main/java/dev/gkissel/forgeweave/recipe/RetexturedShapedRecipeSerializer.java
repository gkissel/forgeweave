package dev.gkissel.forgeweave.recipe;

import net.minecraft.world.item.crafting.RecipeSerializer;

/** Serializer for {@link RetexturedShapedRecipe}; see that class's javadoc. */
public final class RetexturedShapedRecipeSerializer {
    public static final RecipeSerializer<RetexturedShapedRecipe> INSTANCE =
            new RecipeSerializer<>(RetexturedShapedRecipe.CODEC, RetexturedShapedRecipe.STREAM_CODEC);

    private RetexturedShapedRecipeSerializer() {}
}
