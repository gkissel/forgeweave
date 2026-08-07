package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;

/**
 * Wired per ADR-0002 ("recipes ... produced by standard NeoForge datagen") but intentionally
 * empty: master has no recipes yet (issue #8 only added patterns/parts). The stations that craft
 * with them own those recipes in later issues; this provider isn't the place to invent gameplay.
 */
public class ForgeweaveRecipeProvider extends RecipeProvider {
    public ForgeweaveRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        // ponytail: no recipes to generate yet; upgrade when a station issue adds the first one.
    }
}
