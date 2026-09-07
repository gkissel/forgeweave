package dev.gkissel.forgeweave.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/** {@link RetexturedShapedRecipe#displayResults}: one textured result per block the texture ingredient accepts. */
class RetexturedShapedRecipeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RetexturedShapedRecipe tableOver(Ingredient base) {
        ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of('A', Ingredient.of(Items.PAPER), 'B', base), "A", "B");
        return new RetexturedShapedRecipe("", CraftingBookCategory.MISC, pattern, new ItemStack(Items.CRAFTING_TABLE));
    }

    @Test
    void oneTexturedResultPerBlockTheTextureIngredientAccepts() {
        List<ItemStack> results = tableOver(Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)).displayResults();

        assertEquals(List.of("minecraft:oak_planks", "minecraft:birch_planks"), results.stream()
                .map(stack -> stack.get(ForgeweaveDataComponents.TEXTURE.get()))
                .map(ResourceLocation::toString)
                .toList());
    }

    @Test
    void theBareResultWhenNoIngredientIsABlock() {
        List<ItemStack> results = tableOver(Ingredient.of(Items.STICK)).displayResults();

        assertEquals(1, results.size());
        assertNull(results.get(0).get(ForgeweaveDataComponents.TEXTURE.get()));
    }
}
