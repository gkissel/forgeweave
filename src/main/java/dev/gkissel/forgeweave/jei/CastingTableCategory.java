package dev.gkissel.forgeweave.jei;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/** Casting Table recipes ({@link CastingRecipe.Station#TABLE}): docs/SCOPE.md M2 issue #100, JEI category per issue #109. */
final class CastingTableCategory extends CastingCategory {
    static final RecipeType<CastingRecipe> TYPE = RecipeType.create(Forgeweave.MODID, "casting_table", CastingRecipe.class);

    CastingTableCategory(IGuiHelper helper) {
        super(helper, TYPE, ForgeweaveItems.CASTING_TABLE.get(), "jei.category.forgeweave.casting_table", BLOCK_V_TABLE);
    }
}
