package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.Fluid;

/**
 * One entity-melting JEI row (issue #931): {@code entities} names every entity type the underlying
 * {@code entity_melting_recipe} JSON lists (see
 * {@link dev.gkissel.forgeweave.recipe.EntityMeltingRecipe}), melting into {@code amount} mB of
 * {@code fluid} per smeltery interaction -- {@link dev.gkissel.forgeweave.recipe.EntityMeltingRecipe#DAMAGE}
 * is the same flat 2 damage for every row, so {@link EntityMeltingCategory} draws that once rather
 * than repeating it per recipe.
 *
 * <p>{@code defaultRow} marks the one synthetic row {@link EntityMeltingRecipes#build} appends for
 * {@link dev.gkissel.forgeweave.recipe.EntityMeltingRecipe#defaultResult} rather than an actual
 * registry entry -- {@code entities} for it is a single stand-in type purely so the category has
 * something to render live, not a claim that this type in particular falls to the fallback.
 */
record EntityMeltingDisplay(List<EntityType<?>> entities, Fluid fluid, int amount, boolean defaultRow) {

    /**
     * The entity this row renders -- always the first listed type. ponytail: cycling through every
     * type in a multi-entity recipe (e.g. {@code emerald_mobs.json}'s four) is a nice-to-have the
     * issue never asked for ("a live entity render in the slot", singular); add it if a playtest
     * flags a row as misleading.
     */
    EntityType<?> primaryEntity() {
        return entities.get(0);
    }
}
