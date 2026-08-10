package dev.gkissel.forgeweave.data;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

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
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
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
        // Upstream's tools/pattern.json outputs 4 from two planks and two sticks, and upstream's
        // pattern item stacks (issue #64 restored both here -- see ForgeweaveItems).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PATTERN_BLANK.get(), 4)
                .pattern("AB")
                .pattern("BA")
                .define('A', ItemTags.PLANKS)
                .define('B', Items.STICK)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(recipeOutput);

        // Retextured (issue #43): the placed block keeps the appearance of whichever log was used,
        // via RetexturedShapedRecipe -- see that class's javadoc.
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.PART_BUILDER.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(ItemTags.LOGS));

        // Tool Station (docs/SCOPE.md M1 issue #10): upstream's tool_station.json is "blank pattern
        // over the ore-dict `workbench`", i.e. over a vanilla crafting table. It used to be over
        // #planks here, on the reasoning that 1.21 has no `workbench` equivalent -- it does,
        // minecraft:crafting_table is exactly what that ore dict resolved to -- and the deviation
        // made this recipe *character for character* the Stencil Table's, so the recipe manager
        // resolved that shared shape to whichever it happened to index first and the Stencil Table
        // became uncraftable (issue #68 fix 7). Upstream's own ingredients keep all four distinct.
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.TOOL_STATION.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(Blocks.CRAFTING_TABLE));

        // Crafting Station (docs/SCOPE.md M1 issue #40): upstream's crafting_station.json is a bare
        // shapeless "any workbench", with no pattern and -- unlike part_builder.json and
        // stencil_table.json, the only two upstream table recipes that use its retexturing
        // `table_recipe` type -- no wood variant either. It was folded into the 1x2 "pattern over a
        // retextured ingredient" family shape for consistency; issue #68 fix 7 unfolds it, both
        // because upstream is the default and because the family shape is what collided above. A
        // Crafting Station therefore no longer carries a TEXTURE component and renders in the
        // model's default wood, which is also what upstream's does.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ForgeweaveItems.CRAFTING_STATION.get())
                .requires(Blocks.CRAFTING_TABLE)
                .unlockedBy("has_crafting_table", has(Blocks.CRAFTING_TABLE))
                .save(recipeOutput);

        // Stencil Table (docs/SCOPE.md M1 issue #44): upstream 1.12's real stencil_table.json recipe
        // is "blank pattern + #STENCIL_TABLE" where that tag resolves to plankWood (NOTICE.md).
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.STENCIL_TABLE.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(ItemTags.PLANKS));

        // Pattern Chest (docs/SCOPE.md M1 issue #66): upstream ships two recipes for it, both in the
        // `tconstruct:pattern_chest` group -- chest/pattern.json (blank pattern stacked directly on a
        // chest) and chest/pattern_simple.json (a ring of 8 planks around a blank pattern, for
        // players who have not built a chest). Both ported verbatim (NOTICE.md); no
        // RetexturedShapedRecipe for either, since ChestBlock carries no TEXTURE component.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PATTERN_CHEST.get())
                .pattern("A")
                .pattern("B")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', Items.CHEST)
                .group("pattern_chest")
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PATTERN_CHEST.get())
                .pattern("BBB")
                .pattern("BAB")
                .pattern("BBB")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', ItemTags.PLANKS)
                .group("pattern_chest")
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput,
                        ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "pattern_chest_from_planks"));

        // Part Chest (docs/SCOPE.md M1 issue #66): upstream's real part.json shape (a blank pattern
        // over a chest flanked by sticks, with a plank below) -- ported verbatim (NOTICE.md).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PART_CHEST.get())
                .pattern(" A ")
                .pattern("BCB")
                .pattern(" D ")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', Items.STICK)
                .define('C', Items.CHEST)
                .define('D', ItemTags.PLANKS)
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);

        buildSearedRecipes(recipeOutput);
    }

    /**
     * Grout, the furnace smelt into seared brick, and the seared brick block family
     * (docs/SCOPE.md M2 issue #93), ported from upstream 1.12 (NOTICE.md):
     *
     * <ul>
     *   <li>{@code recipes/smeltery/grout_simple.json} -- clay ball + sand (or red sand, upstream's
     *       ore-dict {@code sand} alternation) + gravel, shapeless, yields 2 grout.
     *   <li>{@code TinkerSmeltery#registerSmelting} -- grout smelts into one seared brick (item,
     *       0.4 xp); a seared bricks block smelts into a cracked seared bricks block (0.1 xp).
     *   <li>{@code recipes/smeltery/seared/bricks/bricks.json} -- four seared brick items, 2x2,
     *       craft one seared bricks block.
     *   <li>The ten remaining {@code seared/bricks/*.json} files are all shapeless 1:1 conversions
     *       between two block variants; chained together they form one loop (stone -> paver ->
     *       bricks -> fancy -> square -> triangle -> creeper -> small -> tile -> road -> paver).
     *       Cobblestone has no vanilla-table recipe in either direction upstream -- that gap is
     *       parity, not an omission; upstream only ever produces it via the smeltery/casting system
     *       (issue #95), same as the loop's own entry point (nothing here can produce the first
     *       seared stone block either, until #95 ships).
     * </ul>
     */
    private void buildSearedRecipes(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ForgeweaveItems.GROUT.get(), 2)
                .requires(Items.CLAY_BALL)
                .requires(Ingredient.of(Items.SAND, Items.RED_SAND))
                .requires(Items.GRAVEL)
                .unlockedBy("has_clay_ball", has(Items.CLAY_BALL))
                .save(recipeOutput);

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ForgeweaveItems.GROUT.get()), RecipeCategory.MISC,
                        ForgeweaveItems.SEARED_BRICK.get(), 0.4F, 200)
                .unlockedBy("has_grout", has(ForgeweaveItems.GROUT.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "seared_brick_from_smelting"));

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ForgeweaveItems.SEARED_BRICKS.get()), RecipeCategory.BUILDING_BLOCKS,
                        ForgeweaveItems.SEARED_CRACKED_BRICKS.get(), 0.1F, 200)
                .unlockedBy("has_seared_bricks", has(ForgeweaveItems.SEARED_BRICKS.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "seared_cracked_bricks_from_smelting"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ForgeweaveItems.SEARED_BRICKS.get())
                .pattern("AA")
                .pattern("AA")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);

        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_STONE.get(), ForgeweaveBlocks.SEARED_PAVER.get(), "seared_paver_from_stone");
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_PAVER.get(), ForgeweaveBlocks.SEARED_BRICKS.get(), "seared_bricks_from_paver");
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_BRICKS.get(), ForgeweaveBlocks.SEARED_FANCY_BRICKS.get(), null);
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_FANCY_BRICKS.get(), ForgeweaveBlocks.SEARED_SQUARE_BRICKS.get(), null);
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_SQUARE_BRICKS.get(), ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS.get(), null);
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS.get(), ForgeweaveBlocks.SEARED_CREEPER.get(), null);
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_CREEPER.get(), ForgeweaveBlocks.SEARED_SMALL_BRICKS.get(), null);
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_SMALL_BRICKS.get(), ForgeweaveBlocks.SEARED_TILE.get(), null);
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_TILE.get(), ForgeweaveBlocks.SEARED_ROAD.get(), null);
        searedConversion(recipeOutput, ForgeweaveBlocks.SEARED_ROAD.get(), ForgeweaveBlocks.SEARED_PAVER.get(), "seared_paver_from_road");
    }

    /** A shapeless 1:1 block-variant conversion; {@code id} disambiguates when two conversions share a result (paver). */
    private void searedConversion(RecipeOutput recipeOutput, ItemLike from, ItemLike to, @Nullable String id) {
        ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, to)
                .requires(from)
                .unlockedBy("has_" + BuiltInRegistries.ITEM.getKey(from.asItem()).getPath(), has(from));
        if (id != null) {
            builder.save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, id));
        } else {
            builder.save(recipeOutput);
        }
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
