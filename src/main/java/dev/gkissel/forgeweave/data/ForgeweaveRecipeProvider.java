package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Vanilla crafting-table recipes for the blank pattern, the Part Builder block, and converting the
 * blank pattern into each part-specific pattern (docs/SCOPE.md M1 issue #9; SCOPE.md acceptance test
 * step 1: "craft a blank pattern... and convert it into part patterns"). The blank/table shapes are
 * derived from upstream 1.12 (NOTICE.md); the blank-to-part-pattern conversions are a fresh scheme
 * (maintainer decision, no upstream equivalent to derive from -- 1.12's stencil table used a GUI,
 * not vanilla-table recipes).
 *
 * <p>Conversion scheme: 1 blank pattern + a thematic, unambiguous marker item, one-way (the blank is
 * consumed, matching upstream's stencil-shaping step -- unlike the Part Builder itself, where the
 * resulting part pattern stays reusable; see {@code PartBuilderMenu}). The three head patterns use
 * their matching wooden tool (renewable via the vanilla tool recipe); binding and handle have no
 * matching tool, so they use distinct stick counts instead (1 vs 2) so the two recipes can't be
 * confused for each other.
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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.PART_BUILDER.get())
                .pattern("A")
                .pattern("B")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', ItemTags.LOGS)
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);

        patternConversion(recipeOutput, ForgeweaveItems.PATTERN_PICKAXE_HEAD, Items.WOODEN_PICKAXE, 1);
        patternConversion(recipeOutput, ForgeweaveItems.PATTERN_SHOVEL_HEAD, Items.WOODEN_SHOVEL, 1);
        patternConversion(recipeOutput, ForgeweaveItems.PATTERN_AXE_HEAD, Items.WOODEN_AXE, 1);
        patternConversion(recipeOutput, ForgeweaveItems.PATTERN_TOOL_HANDLE, Items.STICK, 1);
        patternConversion(recipeOutput, ForgeweaveItems.PATTERN_TOOL_BINDING, Items.STICK, 2);

        // Tool Station (docs/SCOPE.md M1 issue #10): same 1x2 "pattern over a material tag" shape as
        // the Part Builder above, but planks instead of logs -- upstream's own tool_station.json
        // instead crafts over the ore-dict "workbench" tag (a crafting table), which Forgeweave has
        // no equivalent of; using planks keeps this recipe visually and structurally consistent with
        // the Part Builder's, per the issue #10 brief.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.TOOL_STATION.get())
                .pattern("A")
                .pattern("B")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', ItemTags.PLANKS)
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);
    }

    private void patternConversion(RecipeOutput recipeOutput, DeferredItem<Item> result, Item marker, int markerCount) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result.get())
                .requires(ForgeweaveItems.PATTERN_BLANK.get())
                .requires(marker, markerCount)
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);
    }
}
