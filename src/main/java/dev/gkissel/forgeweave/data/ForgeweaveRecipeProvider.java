package dev.gkissel.forgeweave.data;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.RetexturedShapedRecipe;

/**
 * Vanilla crafting-table recipes for the blank pattern and the four station blocks (docs/SCOPE.md M1
 * issue #9; SCOPE.md acceptance test step 1: "craft a blank pattern... and convert it into part
 * patterns at a Stencil Table"). The blank/table shapes are derived from upstream 1.12 (NOTICE.md).
 *
 * <p>Blank-to-part-pattern conversion (issue #44) is no longer a vanilla-table recipe: issue #42
 * originally shipped it as five blank+wooden-tool/stick shapeless recipes here, but the maintainer
 * decision for #44 replaces them with the Stencil Table's GUI (select a pattern, one-way consuming
 * the blank -- {@code StencilTableMenu}), matching upstream 1.12's real stencil-shaping flow instead
 * of a vanilla-table stand-in. The Stencil Table is now the only conversion path.
 */
public class ForgeweaveRecipeProvider extends RecipeProvider {
    public ForgeweaveRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        // Upstream's pattern.json outputs 4 at once, but Forgeweave's PATTERN_BLANK stacks to 1
        // (ForgeweaveItems: patterns are reusable templates), so the recipe can only output 1 --
        // stacksTo(1) is shared by every pattern item (issue #8's design); bumping just this one
        // would break that. Not doubled: part patterns stay reusable at the Part Builder (unlike the
        // blank, which each conversion recipe below consumes one-way), so re-converting a blank is a
        // one-time cost per pattern owned, not a per-craft tax -- output 1 doesn't feel punishing.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PATTERN_BLANK.get())
                .pattern("AB")
                .pattern("BA")
                .define('A', ItemTags.PLANKS)
                .define('B', Items.STICK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(recipeOutput);

        // Retextured (issue #43): the placed block keeps the appearance of whichever log was used,
        // via RetexturedShapedRecipe -- see that class's javadoc.
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.PART_BUILDER.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(ItemTags.LOGS));

        // Tool Station (docs/SCOPE.md M1 issue #10): same 1x2 "pattern over a material tag" shape as
        // the Part Builder above, but planks instead of logs -- upstream's own tool_station.json
        // instead crafts over the ore-dict "workbench" tag (a crafting table), which Forgeweave has
        // no equivalent of; using planks keeps this recipe visually and structurally consistent with
        // the Part Builder's, per the issue #10 brief.
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.TOOL_STATION.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(ItemTags.PLANKS));

        // Crafting Station (docs/SCOPE.md M1 issue #40): same 1x2 family shape as the two stations
        // above, but over a vanilla crafting table -- upstream's real crafting_station.json recipe
        // *is* just a bare "any workbench" ore-dict ingredient (shapeless, no pattern), but folding it
        // into the same "pattern + retextured ingredient" shape keeps all three station recipes
        // structurally consistent (maintainer decision, matches the Tool Station precedent above of
        // preferring family consistency over an upstream-literal ingredient).
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.CRAFTING_STATION.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(Blocks.CRAFTING_TABLE));

        // Stencil Table (docs/SCOPE.md M1 issue #44): upstream 1.12's real stencil_table.json recipe
        // is "blank pattern + #STENCIL_TABLE" where that tag resolves to plankWood (NOTICE.md) --
        // the same ingredient the Tool Station uses above, so this keeps the family's "pattern +
        // retexturing ingredient" shape while matching upstream exactly (no maintainer deviation
        // needed here, unlike the Tool Station/Crafting Station rows above).
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.STENCIL_TABLE.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(ItemTags.PLANKS));
    }

    /**
     * A 1x2 "pattern over a retexturing ingredient" shaped recipe (same layout as the plain
     * {@code ShapedRecipeBuilder} shape it replaces) whose result also carries whichever ingredient
     * was a {@code BlockItem} as a {@code TEXTURE} component (see {@link RetexturedShapedRecipe}).
     */
    private void retexturedTableRecipe(RecipeOutput recipeOutput, ItemLike result, ItemLike patternItem, Ingredient ingredient) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.asItem());
        ShapedRecipePattern pattern = ShapedRecipePattern.of(
                Map.of('A', Ingredient.of(patternItem), 'B', ingredient), "A", "B");
        RetexturedShapedRecipe recipe =
                new RetexturedShapedRecipe("", CraftingBookCategory.MISC, pattern, new ItemStack(result));

        AdvancementHolder advancement = recipeOutput.advancement()
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
                .addCriterion("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .requirements(AdvancementRequirements.Strategy.OR)
                .build(id.withPrefix("recipes/misc/"));
        recipeOutput.accept(id, recipe, advancement);
    }
}
