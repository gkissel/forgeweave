package dev.gkissel.forgeweave.jei;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/** Casting Basin recipes ({@link CastingRecipe.Station#BASIN}): docs/SCOPE.md M2 issue #100, JEI category per issue #109. */
final class CastingBasinCategory extends CastingCategory {
    static final RecipeType<CastingRecipe> TYPE = RecipeType.create(Forgeweave.MODID, "casting_basin", CastingRecipe.class);

    CastingBasinCategory(IGuiHelper helper) {
        super(helper, TYPE, ForgeweaveItems.CASTING_BASIN.get(), "jei.category.forgeweave.casting_basin", BLOCK_V_BASIN);
    }
}
