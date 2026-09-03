package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;

/**
 * Builds one {@link EntityMeltingDisplay} per {@code entity_melting_recipe} registry entry, plus one
 * trailing row for {@link EntityMeltingRecipe#defaultResult} (issue #931) -- the same "no
 * override-collision, plain registry snapshot" shape {@link CoreTransformRecipes} uses, since an
 * entity-melting recipe is matched by its whole entity list ({@code EntityMeltingRecipe#find}) rather
 * than by resolving one item the way {@link MeltingRecipes} has to dedupe.
 */
final class EntityMeltingRecipes {

    /**
     * Stands in for "any other living entity" in the default row's live render -- any common,
     * always-registered mob works, since the row's own {@link EntityMeltingDisplay#defaultRow()} flag
     * is what {@link EntityMeltingCategory} actually reads for its label, not this type's name.
     */
    static final EntityType<Pig> DEFAULT_ROW_ENTITY = EntityType.PIG;

    static List<EntityMeltingDisplay> build(Map<ResourceLocation, EntityMeltingRecipe> recipes) {
        List<EntityMeltingDisplay> displays = new ArrayList<>();
        for (EntityMeltingRecipe recipe : recipes.values()) {
            displays.add(new EntityMeltingDisplay(recipe.entities(), recipe.fluid(), recipe.amount(), false));
        }

        FluidStack fallback = EntityMeltingRecipe.defaultResult();
        displays.add(new EntityMeltingDisplay(List.of(DEFAULT_ROW_ENTITY), fallback.getFluid(), fallback.getAmount(), true));
        return displays;
    }

    private EntityMeltingRecipes() {}
}
