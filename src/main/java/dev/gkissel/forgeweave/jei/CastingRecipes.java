package dev.gkissel.forgeweave.jei;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.casting.CastingRecipe;

/**
 * Splits the one {@code casting_recipe} registry into the two lists {@link CastingTableCategory}
 * and {@link CastingBasinCategory} each display, by {@link CastingRecipe.Station} (docs/SCOPE.md M2
 * issue #100's discriminator, JEI split per issue #109). No dedup is needed here the way melting
 * needs one (#96's PR #124 warning): a casting recipe is matched by its whole (station, cast, fluid)
 * combination rather than by looking up one item in isolation, so two recipes sharing a cast item
 * are simply two different, both-correct display rows.
 */
final class CastingRecipes {
    static List<CastingRecipe> table(Map<ResourceLocation, CastingRecipe> recipes) {
        return byStation(recipes, CastingRecipe.Station.TABLE);
    }

    static List<CastingRecipe> basin(Map<ResourceLocation, CastingRecipe> recipes) {
        return byStation(recipes, CastingRecipe.Station.BASIN);
    }

    private static List<CastingRecipe> byStation(Map<ResourceLocation, CastingRecipe> recipes, CastingRecipe.Station station) {
        return recipes.values().stream().filter(recipe -> recipe.station() == station).toList();
    }

    private CastingRecipes() {}
}
