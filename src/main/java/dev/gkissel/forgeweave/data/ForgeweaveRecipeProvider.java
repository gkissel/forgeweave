package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Vanilla crafting-table recipes for the blank pattern and the Part Builder block (docs/SCOPE.md
 * M1 issue #9). Shapes are derived from upstream 1.12 (NOTICE.md):
 *
 * <ul>
 *   <li>Blank pattern: 2 planks + 2 sticks checkerboard ({@code recipes/tools/pattern.json}).
 *   <li>Part Builder: blank pattern on top of a log ({@code recipes/tools/table/part_builder.json}
 *       -- upstream's {@code #PART_BUILDER} constant is logs, not planks).
 * </ul>
 */
public class ForgeweaveRecipeProvider extends RecipeProvider {
    public ForgeweaveRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        // Upstream's pattern.json outputs 4 at once, but Forgeweave's PATTERN_BLANK stacks to 1
        // (ForgeweaveItems: patterns are reusable templates), so the recipe can only output 1.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PATTERN_BLANK.get())
                .pattern("AB")
                .pattern("BA")
                .define('A', ItemTags.PLANKS)
                .define('B', Items.STICK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PART_BUILDER.get())
                .pattern("A")
                .pattern("B")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', ItemTags.LOGS)
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);
    }
}
