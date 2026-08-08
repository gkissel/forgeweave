package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.recipe.RetexturedShapedRecipe;

/**
 * Covers docs/SCOPE.md issue #68 fix 7: a recipe viewer's rendering of a station recipe and the
 * arrangement that actually crafts it must be the same arrangement.
 *
 * <p>The defect this guards against was silent by construction. {@link RetexturedShapedRecipe} used
 * to be a {@link CraftingRecipe} that was <em>not</em> vanilla's {@code ShapedRecipe}, so JEI had no
 * width or height to lay it out with and fell back to a shapeless
 * left-to-right row: it drew the Stencil Table as blank pattern <em>beside</em> planks, while the
 * recipe only matched upstream's pattern-<em>above</em>-planks column. Every ingredient, JSON and
 * output was correct; only the two views of the shape disagreed, and nothing in the build compared
 * them.
 *
 * <p>So the check is exactly that comparison, over every recipe of the type at once (the maintainer
 * reported "some recipes are wrong on JEI", not just the Stencil Table): take the width/height each
 * recipe advertises to viewers, fill a grid of precisely that size with its own ingredients in its
 * own order -- i.e. reproduce what a viewer draws -- and require the real {@code RecipeManager} to
 * resolve that grid back to that recipe's result.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class RecipeShapeGameTests {

    @GameTest(template = "empty")
    public static void everyStationRecipeCraftsAtTheShapeViewersDisplay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<RecipeHolder<CraftingRecipe>> retextured = level.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(holder -> holder.value() instanceof RetexturedShapedRecipe)
                .toList();

        helper.assertTrue(!retextured.isEmpty(), "expected the station recipes to be loaded");

        for (RecipeHolder<CraftingRecipe> holder : retextured) {
            RetexturedShapedRecipe recipe = (RetexturedShapedRecipe) holder.value();
            CraftingInput displayed = asDisplayed(recipe);

            ItemStack crafted = level.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, displayed, level)
                    .map(match -> match.value().assemble(displayed, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            ItemStack expected = recipe.getResultItem(level.registryAccess());

            helper.assertTrue(crafted.is(expected.getItem()),
                    "recipe " + holder.id() + " displays as " + recipe.getWidth() + "x" + recipe.getHeight()
                            + " but crafting that arrangement gives " + crafted + " instead of " + expected);
        }

        helper.succeed();
    }

    /** The maintainer's own repro, spelled out so a regression names the block instead of an id. */
    @GameTest(template = "empty")
    public static void theStencilTableCraftsAtTheShapeViewersDisplay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RetexturedShapedRecipe recipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .map(RecipeHolder::value)
                .filter(RetexturedShapedRecipe.class::isInstance)
                .map(RetexturedShapedRecipe.class::cast)
                .filter(candidate -> candidate.getResultItem(level.registryAccess())
                        .is(ForgeweaveBlocks.STENCIL_TABLE.get().asItem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no Stencil Table crafting recipe is loaded"));

        CraftingInput displayed = asDisplayed(recipe);
        ItemStack crafted = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, displayed, level)
                .map(match -> match.value().assemble(displayed, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveBlocks.STENCIL_TABLE.get().asItem()),
                "the Stencil Table's displayed arrangement did not craft it; got " + crafted);
        helper.succeed();
    }

    /**
     * The grid a recipe viewer draws: the recipe's own ingredients, in its own order, in a grid of
     * the width and height it advertises. One representative stack per ingredient, which is what a
     * player copying the display would place.
     */
    private static CraftingInput asDisplayed(RetexturedShapedRecipe recipe) {
        List<ItemStack> items = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] candidates = ingredient.getItems();
            items.add(ingredient.isEmpty() || candidates.length == 0 ? ItemStack.EMPTY : candidates[0].copy());
        }
        return CraftingInput.of(recipe.getWidth(), recipe.getHeight(), items);
    }
}
