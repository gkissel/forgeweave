package dev.gkissel.forgeweave.recipe;

import net.minecraft.world.item.crafting.RecipeSerializer;

/** Serializer for {@link GravelFlintRecipe}; see that class's javadoc. */
public final class GravelFlintRecipeSerializer {
    public static final RecipeSerializer<GravelFlintRecipe> INSTANCE =
            new RecipeSerializer<>(GravelFlintRecipe.CODEC, GravelFlintRecipe.STREAM_CODEC);

    private GravelFlintRecipeSerializer() {}
}
