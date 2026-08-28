package dev.gkissel.forgeweave.data;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.common.Tags;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.GravelFlintRecipe;
import dev.gkissel.forgeweave.recipe.MixedSlimeBlockRecipe;
import dev.gkissel.forgeweave.recipe.MixedSlimeSlingRecipe;
import dev.gkissel.forgeweave.recipe.RetexturedShapedRecipe;
import dev.gkissel.forgeweave.recipe.SharpeningKitRepairRecipe;

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
        // Issue #463 (parity audit T32): upstream registers its sharpening-kit tool repair as a
        // code-only IRecipe (tools/common/RepairRecipe); 1.21.1 wants a datapack entry naming the
        // serializer, which is all this is -- see SharpeningKitRepairRecipe.
        SpecialRecipeBuilder.special(SharpeningKitRepairRecipe::new)
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "sharpening_kit_repair"));

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
        //
        // Plain shaped recipe, not retexturedTableRecipe (issue #755): upstream's tool_station.json
        // is a bare forge:ore_shaped recipe -- unlike Part Builder/Stencil Table, it never carries a
        // tconstruct:table_recipe wood variant, so a Tool Station always wears its default oak look.
        // retexturedTableRecipe's RetexturedShapedRecipe#assemble copies the TEXTURE component off
        // the *first* BlockItem ingredient it finds, with no regard for which ingredient that is; the
        // crafting table used here is that first (and only) BlockItem, so every crafted Tool Station
        // was retextured to look like a crafting table -- its bottom/leg faces rendered with the
        // crafting table's own sprite instead of oak planks (playtest defect, issue #755).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.TOOL_STATION.get())
                .pattern("A")
                .pattern("B")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', Blocks.CRAFTING_TABLE)
                .unlockedBy("has_pattern_blank", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);

        toolForgeRecipe(recipeOutput);

        // Armor Station (docs/SCOPE.md M4 issue #782, reversing D13): a blank pattern over a Tool
        // Station, the same 1x2 "pattern over the block it upgrades" shape as the Tool Station's own
        // recipe above, so the two read as siblings at a glance. Deliberately not
        // retexturedTableRecipe (issue #762 found that helper copies the TEXTURE component off the
        // first BlockItem ingredient it finds -- exactly the issue #755 defect the Tool Station's own
        // recipe comment above already documents -- and the Armor Station never retexturing at all
        // makes that defect pure downside here). A plain ShapedRecipeBuilder call, same as the Tool
        // Station's own recipe, needs no ingredient distinct from every other 1x2 pattern recipe here:
        // "pattern over a Tool Station" is not "pattern over a crafting table" or "pattern over
        // planks/logs" (Stencil Table/Part Builder), so this cannot collide with any of them.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.ARMOR_STATION.get())
                .pattern("A")
                .pattern("B")
                .define('A', ForgeweaveItems.PATTERN_BLANK.get())
                .define('B', ForgeweaveItems.TOOL_STATION.get())
                .unlockedBy("has_tool_station", has(ForgeweaveItems.TOOL_STATION.get()))
                .save(recipeOutput);

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

        // The guide book (issue #273): upstream 1.12's recipes/tools/book.json is shapeless
        // "vanilla book + blank pattern", and its recipes/common/book.json also lets 3 paper +
        // string + 2 blank patterns make the vanilla book itself, skipping leather (NOTICE.md).
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ForgeweaveItems.GUIDE_BOOK.get())
                .requires(Items.BOOK)
                .requires(ForgeweaveItems.PATTERN_BLANK.get())
                .unlockedBy("has_pattern", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.BOOK)
                .requires(Items.PAPER, 3)
                .requires(Items.STRING)
                .requires(ForgeweaveItems.PATTERN_BLANK.get(), 2)
                .unlockedBy("has_pattern", has(ForgeweaveItems.PATTERN_BLANK.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "book_from_patterns"));

        // The five coloured Slimeslings (T22 issue #453, split into colours by #649): upstream's
        // recipes/gadgets/slimesling/{green,blue,purple,blood,magma}.json all share one shape -- two
        // string over that colour's congealed slime block, three of that colour's slime balls around
        // it. #649 reverts #453's one-sling widening of the balls to the whole `c:slimeballs` tag:
        // each colour takes exactly its own ball, as upstream's per-colour ore dicts do, and the
        // mixed-colour grids the tag used to absorb are MixedSlimeSlingRecipe's (upstream's
        // fallback.json). Pink has no shaped recipe of its own upstream and gets none here.
        for (ForgeweaveItems.SlimeSling sling : ForgeweaveItems.slimeSlings()) {
            if (sling.colour() == SlimeColour.PINK) {
                continue;
            }
            ItemLike ball = ForgeweaveItems.slimeBall(sling.colour());
            ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, sling.item().get())
                    .pattern("SCS")
                    .pattern("B B")
                    .pattern(" B ")
                    .define('S', Items.STRING)
                    .define('C', ForgeweaveBlocks.slimeFamily(sling.colour()).congealed().get())
                    .define('B', ball)
                    .unlockedBy("has_slime_ball", has(ball))
                    .save(recipeOutput);
        }
        // Upstream's slimesling/fallback.json (NOTICE.md): the same shape over mixed slime colours
        // makes the pink sling. A special recipe for the same reason as MixedSlimeBlockRecipe.
        SpecialRecipeBuilder.special(MixedSlimeSlingRecipe::new)
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "mixed_slime_sling").toString());

        // Stencil Table (docs/SCOPE.md M1 issue #44): upstream 1.12's real stencil_table.json recipe
        // is "blank pattern + #STENCIL_TABLE" where that tag resolves to plankWood (NOTICE.md).
        retexturedTableRecipe(recipeOutput, ForgeweaveItems.STENCIL_TABLE.get(), ForgeweaveItems.PATTERN_BLANK.get(), Ingredient.of(ItemTags.PLANKS));

        // 3 gravel -> 1 flint (parity audit T55, issue #486): upstream's recipes/common/flint.json,
        // gated by addFlintRecipe -- see GravelFlintRecipe's javadoc for why the gate is a match-time
        // config check here instead of upstream's load-time recipe condition.
        gravelFlintRecipe(recipeOutput);

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
        buildModifierRecipes(recipeOutput);
        buildStorageBlockRecipes(recipeOutput);
        buildSlimeCrystalRecipes(recipeOutput);
        buildClearGlassRecipes(recipeOutput);
    }

    /**
     * #275 -- clear glass and its 16 clear stained glass colors. Upstream 1.12's {@code
     * TinkerCommons#addRecipes} furnace-smelts a vanilla glass block into clear glass at 0.1 xp
     * ({@code GameRegistry.addSmelting(Blocks.GLASS, ...)}, NOTICE.md); each color is then a shaped
     * craft, upstream's own {@code recipes/common/glass/&lt;color&gt;_stained_clear_glass.json}
     * (NOTICE.md; every one of the 16 files shares this exact shape, verified against the clone) -- 8
     * clear glass in a ring around one dye of that color, yielding 8 stained glass.
     */
    private void buildClearGlassRecipes(RecipeOutput recipeOutput) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(Blocks.GLASS), RecipeCategory.BUILDING_BLOCKS,
                        ForgeweaveBlocks.CLEAR_GLASS.get(), 0.1F, 200)
                .unlockedBy("has_glass", has(Blocks.GLASS))
                .save(recipeOutput);

        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, color.block().get(), 8)
                    .pattern("AAA")
                    .pattern("ABA")
                    .pattern("AAA")
                    .define('A', ForgeweaveBlocks.CLEAR_GLASS.get())
                    .define('B', DyeItem.byColor(color.dye()))
                    .unlockedBy("has_clear_glass", has(ForgeweaveBlocks.CLEAR_GLASS.get()))
                    .save(recipeOutput);
        }
    }

    /**
     * #206 -- vanilla's own 9-ingot &lt;-&gt; storage-block conversion (block &lt;-&gt; ingots), for
     * the four M2 metals that had no block form at all. Iron/copper/gold/netherite already have this
     * both ways courtesy of vanilla's own recipes. Hand-rolled rather than the vanilla {@code
     * RecipeProvider#nineBlockStorageRecipes} datagen helper -- which does the same shapes but saves
     * both recipe ids under the plain {@code minecraft:} namespace -- so these land under {@code
     * forgeweave:}, like every other recipe in this file; not an upstream 1.12 port (upstream keyed
     * its version off the ore dictionary instead), so this carries no NOTICE.md row.
     */
    private void buildStorageBlockRecipes(RecipeOutput recipeOutput) {
        storageBlockRecipes(recipeOutput, ForgeweaveItems.INGOT_COBALT.get(), ForgeweaveItems.COBALT_BLOCK.get());
        storageBlockRecipes(recipeOutput, ForgeweaveItems.INGOT_ARDITE.get(), ForgeweaveItems.ARDITE_BLOCK.get());
        storageBlockRecipes(recipeOutput, ForgeweaveItems.INGOT_MANYULLYN.get(), ForgeweaveItems.MANYULLYN_BLOCK.get());
        storageBlockRecipes(recipeOutput, ForgeweaveItems.INGOT_ROSE_GOLD.get(), ForgeweaveItems.ROSE_GOLD_BLOCK.get());
        storageBlockRecipes(recipeOutput, ForgeweaveItems.INGOT_KNIGHTSLIME.get(), ForgeweaveItems.KNIGHTSLIME_BLOCK.get()); // #232
        // #233 -- pig iron, same both-ways 9:1 shape.
        storageBlockRecipes(recipeOutput, ForgeweaveItems.INGOT_PIG_IRON.get(), ForgeweaveItems.PIG_IRON_BLOCK.get());

        // #233 -- firewood. Upstream 1.12's recipes/common/firewood/firewood.json is shapeless
        // "blaze powder + lavawood + blaze powder", and lavawood itself is basin-cast: any plank
        // block under 250 mB of lava (TinkerSmeltery's registerBasinCasting). Forgeweave has no
        // lavawood block (not on docs/SCOPE.md's M3.2 roster), so the chain collapses to its closest
        // single-step equivalent: the same two blaze powders and the plank, with the lava step
        // carried by a lava bucket (the bucket comes back as its vanilla crafting remainder).
        // Substitution flagged for maintainer review in the #233 PR body.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ForgeweaveItems.FIREWOOD.get())
                .requires(Items.BLAZE_POWDER)
                .requires(ItemTags.PLANKS)
                .requires(Items.BLAZE_POWDER)
                .requires(Items.LAVA_BUCKET)
                .unlockedBy("has_blaze_powder", has(Items.BLAZE_POWDER))
                .save(recipeOutput);
    }

    /**
     * #339 (revising #232) -- the three slime crystals (docs/SCOPE.md M3.2) via upstream 1.12's real
     * path: craft slimy mud, then furnace-smelt the mud into its crystal
     * ({@code TinkerTools#registerSmeltingRecipes}, 0.75 xp, NOTICE.md). #232's javadoc miscited the
     * smelt input as congealed slime and shipped vanilla slime/magma blocks as the shortcut; both are
     * removed here.
     *
     * <p>Green mud is upstream's {@code slimy_mud_green.json} 1:1, with its {@code forge:ore_dict}
     * "sand"/"dirt" entries read as the modern vanilla tags. #635 (parity audit T57) reverts the two
     * substitutions #339 and #232 had to make for want of coloured slime balls, now that there are
     * some: magma mud is upstream's {@code slimy_mud_magma.json} shape again (2 magma slime balls + 2
     * magma cream, not four cream), and blue mud is upstream's {@code slimy_mud_blue.json} (4 blue
     * slime balls + sand + dirt) with the furnace smelt that gives the blue slime crystal, replacing
     * #232's interim "green crystal + lapis" craft.
     */
    private void buildSlimeCrystalRecipes(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ForgeweaveItems.SLIMY_MUD_GREEN.get())
                .requires(Items.SLIME_BALL, 4)
                .requires(ItemTags.SAND)
                .requires(ItemTags.DIRT)
                .unlockedBy("has_slime_ball", has(Items.SLIME_BALL))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ForgeweaveItems.SLIMY_MUD_MAGMA.get())
                .requires(ForgeweaveItems.slimeBall(SlimeColour.MAGMA), 2)
                .requires(Items.MAGMA_CREAM, 2)
                .requires(Items.SOUL_SAND)
                .requires(Items.NETHERRACK)
                .unlockedBy("has_magma_cream", has(Items.MAGMA_CREAM))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ForgeweaveItems.SLIMY_MUD_BLUE.get())
                .requires(ForgeweaveItems.slimeBall(SlimeColour.BLUE), 4)
                .requires(ItemTags.SAND)
                .requires(ItemTags.DIRT)
                .unlockedBy("has_blue_slime_ball", has(ForgeweaveItems.slimeBall(SlimeColour.BLUE)))
                .save(recipeOutput);

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ForgeweaveItems.SLIMY_MUD_GREEN.get()), RecipeCategory.MISC,
                        ForgeweaveItems.GREEN_SLIME_CRYSTAL.get(), 0.75F, 200)
                .unlockedBy("has_slimy_mud_green", has(ForgeweaveItems.SLIMY_MUD_GREEN.get()))
                .save(recipeOutput);

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ForgeweaveItems.SLIMY_MUD_MAGMA.get()), RecipeCategory.MISC,
                        ForgeweaveItems.MAGMA_SLIME_CRYSTAL.get(), 0.75F, 200)
                .unlockedBy("has_slimy_mud_magma", has(ForgeweaveItems.SLIMY_MUD_MAGMA.get()))
                .save(recipeOutput);

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ForgeweaveItems.SLIMY_MUD_BLUE.get()), RecipeCategory.MISC,
                        ForgeweaveItems.BLUE_SLIME_CRYSTAL.get(), 0.75F, 200)
                .unlockedBy("has_slimy_mud_blue", has(ForgeweaveItems.SLIMY_MUD_BLUE.get()))
                .save(recipeOutput);

        // #452 -- the slime boots (parity audit T21), upstream's recipes/gadgets/slimeboots/green.json
        // shape: two slime balls over two congealed slime blocks. #635 replaces #452's vanilla slime
        // block stand-in with the real green congealed slime that now exists.
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ForgeweaveItems.SLIME_BOOTS.get())
                .pattern("A A")
                .pattern("B B")
                .define('A', Items.SLIME_BALL)
                .define('B', ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get())
                .unlockedBy("has_green_congealed_slime", has(ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get()))
                .save(recipeOutput);

        // #235 -- amethyst bronze (M3.2), same #206 shape.
        storageBlockRecipes(recipeOutput, ForgeweaveItems.INGOT_AMETHYST_BRONZE.get(), ForgeweaveItems.AMETHYST_BRONZE_BLOCK.get());
    }

    /** 9 {@code ingot} &lt;-&gt; 1 {@code block}, vanilla's own storage-block shape, both directions. */
    private void storageBlockRecipes(RecipeOutput recipeOutput, ItemLike ingot, ItemLike block) {
        ResourceLocation blockId = BuiltInRegistries.ITEM.getKey(block.asItem());
        ResourceLocation ingotId = BuiltInRegistries.ITEM.getKey(ingot.asItem());

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, block)
                .pattern("###")
                .pattern("###")
                .pattern("###")
                .define('#', ingot)
                .unlockedBy("has_" + ingotId.getPath(), has(ingot))
                .save(recipeOutput, blockId);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ingot, 9)
                .requires(block)
                .unlockedBy("has_" + blockId.getPath(), has(block))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID,
                        ingotId.getPath() + "_from_" + blockId.getPath()));
    }

    /**
     * Reagent items for the M2-16 modifiers batch (docs/SCOPE.md issue #107), ported from upstream
     * 1.12's {@code recipes/tools/materials/*.json} (NOTICE.md) with the ore-dict alternation folded
     * to the one vanilla item each resolved to for M2's scope (no smeltery/tag-melting equivalent
     * exists yet). Mending moss itself has no table recipe -- it's obtained by right-clicking a
     * bookshelf while holding moss with 10+ XP levels banked ({@code ForgeweaveModifiers
     * #onRightClickBookshelf}), same as upstream's {@code ToolEvents#onInteract}. Soulbound reuses the
     * vanilla nether star directly and needs no recipe here either.
     */
    private void buildModifierRecipes(RecipeOutput recipeOutput) {
        // Moss (upstream ball_of_moss.json: 9x the ore-dict blockMossy, here narrowed to mossy
        // cobblestone, the item that dict resolved to for a vanilla-only crafting table).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.MOSS.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', Blocks.MOSSY_COBBLESTONE)
                .unlockedBy("has_mossy_cobblestone", has(Blocks.MOSSY_COBBLESTONE))
                .save(recipeOutput);

        // Reinforced plate (T69/#500; upstream reinforcement.json: obsidian ring around
        // #REINFORCEMENT_CENTER, recipes/_constants.json). That constant resolves to a plain gold
        // ingot only while TinkerSmeltery is unloaded; once it's loaded it resolves to ore:cast (any
        // gold cast). Forgeweave's smeltery has no such optional-pulse toggle -- it always ships -- so
        // the smeltery-loaded branch is the only one that applies here, matching parity audit T69.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.REINFORCED_PLATE.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', Items.OBSIDIAN)
                .define('B', ForgeweaveItemTagsProvider.CASTS_GOLD)
                .unlockedBy("has_obsidian", has(Items.OBSIDIAN))
                .save(recipeOutput);

        // Silky cloth (upstream silky_cloth.json: string ring around a gold ingot).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.SILKY_CLOTH.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', Items.STRING)
                .define('B', Items.GOLD_INGOT)
                .unlockedBy("has_string", has(Items.STRING))
                .save(recipeOutput);

        // Silky jewel (upstream silky_jewel.json: four silky cloth in a plus around an emerald).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.SILKY_JEWEL.get())
                .pattern(" A ")
                .pattern("ABA")
                .pattern(" A ")
                .define('A', ForgeweaveItems.SILKY_CLOTH.get())
                .define('B', Items.EMERALD)
                .unlockedBy("has_silky_cloth", has(ForgeweaveItems.SILKY_CLOTH.get()))
                .save(recipeOutput);

        // Extra modifier (deviation, recorded in the issue #107 PR): upstream's creative_modifier
        // reagent is admin/creative-only (ModCreative#isHidden) and has no survival recipe at all.
        // Forgeweave gives it one so docs/SCOPE.md acceptance test 5 is reachable in survival.
        // Issue #338 (maintainer playtest of 0.3.2-alpha, decision 2026-08-14): gold block + diamond
        // was too cheap for what this buys, so the recipe is repriced to endgame -- nether star + gold
        // block. Precedent: TiC 1.7.10's extra-modifier ladder topped out at a nether star.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ForgeweaveItems.EXTRA_MODIFIER.get())
                .requires(Items.NETHER_STAR)
                .requires(Items.GOLD_BLOCK)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(recipeOutput);

        // #429 -- smite's own upstream reagent, which had been standing in as glowstone dust.
        // Graveyard soil (upstream common/soil/graveyard_soil.json: dirt + rotten flesh + bone meal
        // -- upstream writes the last as `minecraft:dye` data 15 -- shapeless, yields one), then the
        // furnace smelt into consecrated soil (TinkerCommons#registerSmeltingRecipes, 0.1 xp).
        // Necrotic bone has no recipe in either: wither skeletons drop it.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ForgeweaveItems.GRAVEYARD_SOIL.get())
                .requires(Items.DIRT)
                .requires(Items.ROTTEN_FLESH)
                .requires(Items.BONE_MEAL)
                .unlockedBy("has_rotten_flesh", has(Items.ROTTEN_FLESH))
                .save(recipeOutput);

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ForgeweaveItems.GRAVEYARD_SOIL.get()), RecipeCategory.MISC,
                        ForgeweaveItems.CONSECRATED_SOIL.get(), 0.1F, 200)
                .unlockedBy("has_graveyard_soil", has(ForgeweaveItems.GRAVEYARD_SOIL.get()))
                .save(recipeOutput);

        // The two expanders (issue #438), upstream expander_w.json / expander_h.json: two pistons and
        // two lapis in a plus around one purple slime ball, with the pistons on the axis the expander
        // widens -- horizontally for Width++, vertically for Height++. Deviation, same narrowing the
        // moss/reinforced-plate recipes above already make: upstream's centre is `tconstruct:edible`
        // meta 2, its purple slime ball, and Forgeweave ships no slime tiers, so the centre is the
        // vanilla slime ball.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.EXPANDER_W.get())
                .pattern(" A ")
                .pattern("BCB")
                .pattern(" A ")
                .define('A', Items.LAPIS_LAZULI)
                .define('B', Blocks.PISTON)
                .define('C', Items.SLIME_BALL)
                .unlockedBy("has_piston", has(Blocks.PISTON))
                .save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.EXPANDER_H.get())
                .pattern(" B ")
                .pattern("ACA")
                .pattern(" B ")
                .define('A', Items.LAPIS_LAZULI)
                .define('B', Blocks.PISTON)
                .define('C', Items.SLIME_BALL)
                .unlockedBy("has_piston", has(Blocks.PISTON))
                .save(recipeOutput);
    }

    /**
     * Grout, the furnace smelt into seared brick, and the seared brick block family
     * (docs/SCOPE.md M2 issue #93), ported from upstream 1.12 (NOTICE.md):
     *
     * <ul>
     *   <li>{@code recipes/smeltery/grout_simple.json} -- clay ball + sand (or red sand, upstream's
     *       ore-dict {@code sand} alternation) + gravel, shapeless, yields 2 grout.
     *   <li>{@code recipes/smeltery/grout.json} (issue #503, T72) -- the bulk counterpart: a clay
     *       block + four sand + four gravel, shapeless, yields 8 grout.
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
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ForgeweaveItems.GROUT.get(), 2)
                .requires(Items.CLAY_BALL)
                .requires(Ingredient.of(Items.SAND, Items.RED_SAND))
                .requires(Items.GRAVEL)
                .unlockedBy("has_clay_ball", has(Items.CLAY_BALL))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ForgeweaveItems.GROUT.get(), 8)
                .requires(Items.CLAY)
                .requires(Ingredient.of(Items.SAND, Items.RED_SAND), 4)
                .requires(Items.GRAVEL, 4)
                .unlockedBy("has_clay", has(Items.CLAY))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "grout_from_clay_block"));

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

        buildSearedStairsSlabRecipes(recipeOutput);

        // #502 (T71 parity audit): mud brick block, upstream's recipes/common/soil/mud_bricks_block.json
        // (NOTICE.md) -- four mud brick items, 2x2, craft one mud brick block. Slab/stairs variants
        // are out of scope here (no Forgeweave mud brick slab/stairs blocks exist).
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ForgeweaveItems.MUD_BRICK_BLOCK.get())
                .pattern("AA")
                .pattern("AA")
                .define('A', ForgeweaveItems.MUD_BRICK.get())
                .unlockedBy("has_mud_brick", has(ForgeweaveItems.MUD_BRICK.get()))
                .save(recipeOutput);

        buildSlimeFamilyRecipes(recipeOutput);

        smelteryRecipes(recipeOutput);
    }

    /**
     * The slime family's crafting loop -- upstream's
     * {@code recipes/common/slime/<colour>/{congealed,slimeball_from_congealed,slimeblock,slimeball_from_block}.json}
     * (NOTICE.md), all four for every colour: four balls make a congealed block and it gives them
     * back, nine make a slime block and it gives them back. Green shipped its congealed pair with
     * #449; the rest arrive with #635 (parity audit T57).
     *
     * <p>Green has no {@code slimeblock} pair here: its slime block is vanilla's, and vanilla's own
     * {@code minecraft:slime_block} recipe and {@code minecraft:slime_ball} shapeless already are
     * that pair. Upstream needs its own copies only because it replaces vanilla's recipe outright;
     * see {@link dev.gkissel.forgeweave.recipe.MixedSlimeBlockRecipe} for why Forgeweave does not.
     */
    private void buildSlimeFamilyRecipes(RecipeOutput recipeOutput) {
        for (ForgeweaveBlocks.SlimeFamily family : ForgeweaveBlocks.slimeFamilies()) {
            SlimeColour colour = family.colour();
            ItemLike ball = ForgeweaveItems.slimeBall(colour);
            Block congealed = family.congealed().get();
            String ballName = BuiltInRegistries.ITEM.getKey(ball.asItem()).getPath();
            String congealedName = BuiltInRegistries.BLOCK.getKey(congealed).getPath();

            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, congealed)
                    .pattern("AA")
                    .pattern("AA")
                    .define('A', ball)
                    .unlockedBy("has_" + ballName, has(ball))
                    .save(recipeOutput);
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ball, 4)
                    .requires(congealed)
                    .unlockedBy("has_" + congealedName, has(congealed))
                    .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID,
                            ballName + "_from_" + congealedName));

            if (family.slimeBlock() == null) {
                continue;
            }
            Block slimeBlock = family.slimeBlock().get();
            String slimeBlockName = BuiltInRegistries.BLOCK.getKey(slimeBlock).getPath();
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, slimeBlock)
                    .pattern("AAA")
                    .pattern("AAA")
                    .pattern("AAA")
                    .define('A', ball)
                    .unlockedBy("has_" + ballName, has(ball))
                    .save(recipeOutput);
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ball, 9)
                    .requires(slimeBlock)
                    .unlockedBy("has_" + slimeBlockName, has(slimeBlock))
                    .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID,
                            ballName + "_from_" + slimeBlockName));
        }

        // Upstream's ShapedFallbackRecipe for mixed slime balls (TinkerCommons#registerRecipes,
        // NOTICE.md). It carries no data of its own, so it is a special recipe with a fixed shape;
        // see MixedSlimeBlockRecipe.
        SpecialRecipeBuilder.special(MixedSlimeBlockRecipe::new)
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "mixed_slime_block").toString());
    }

    /**
     * Stairs + slab crafting recipes for the seared brick family (docs/SCOPE.md M3.4-5 issue #274).
     * Every one of upstream 1.12's twelve {@code recipes/smeltery/seared/{stairs,slab}/*.json} pairs
     * (NOTICE.md) uses the same shape: 6 blocks into 4 stairs, 3 blocks into 6 slabs -- exactly what
     * vanilla's own {@link #stairBuilder}/{@link #slabBuilder} helpers already produce, so this just
     * keys them on each seared block instead of a vanilla one. Stonecutting recipes have no upstream
     * counterpart (1.12 predates the stonecutter); adding them is issue #274's own called-out default
     * deviation, the modern-API adaptation for a block family that otherwise has no way to convert a
     * stair/slab back into another shape.
     */
    private void buildSearedStairsSlabRecipes(RecipeOutput recipeOutput) {
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_STONE.get(),
                ForgeweaveBlocks.SEARED_STAIRS_STONE.get(), ForgeweaveBlocks.SEARED_SLAB_STONE.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_COBBLESTONE.get(),
                ForgeweaveBlocks.SEARED_STAIRS_COBBLESTONE.get(), ForgeweaveBlocks.SEARED_SLAB_COBBLESTONE.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_PAVER.get(),
                ForgeweaveBlocks.SEARED_STAIRS_PAVER.get(), ForgeweaveBlocks.SEARED_SLAB_PAVER.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_BRICKS.get(),
                ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get(), ForgeweaveBlocks.SEARED_SLAB_BRICKS.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_CRACKED_BRICKS.get(),
                ForgeweaveBlocks.SEARED_STAIRS_CRACKED_BRICKS.get(), ForgeweaveBlocks.SEARED_SLAB_CRACKED_BRICKS.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_FANCY_BRICKS.get(),
                ForgeweaveBlocks.SEARED_STAIRS_FANCY_BRICKS.get(), ForgeweaveBlocks.SEARED_SLAB_FANCY_BRICKS.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_SQUARE_BRICKS.get(),
                ForgeweaveBlocks.SEARED_STAIRS_SQUARE_BRICKS.get(), ForgeweaveBlocks.SEARED_SLAB_SQUARE_BRICKS.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS.get(),
                ForgeweaveBlocks.SEARED_STAIRS_TRIANGLE_BRICKS.get(), ForgeweaveBlocks.SEARED_SLAB_TRIANGLE_BRICKS.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_SMALL_BRICKS.get(),
                ForgeweaveBlocks.SEARED_STAIRS_SMALL_BRICKS.get(), ForgeweaveBlocks.SEARED_SLAB_SMALL_BRICKS.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_ROAD.get(),
                ForgeweaveBlocks.SEARED_STAIRS_ROAD.get(), ForgeweaveBlocks.SEARED_SLAB_ROAD.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_TILE.get(),
                ForgeweaveBlocks.SEARED_STAIRS_TILE.get(), ForgeweaveBlocks.SEARED_SLAB_TILE.get());
        searedStairsAndSlab(recipeOutput, ForgeweaveBlocks.SEARED_CREEPER.get(),
                ForgeweaveBlocks.SEARED_STAIRS_CREEPER.get(), ForgeweaveBlocks.SEARED_SLAB_CREEPER.get());

        // Upstream's one extra shape, bricks_slab_simple.json: two loose seared brick items (rather
        // than a full seared bricks block) also make one seared bricks slab, an early-game shortcut
        // unique to the brick item -- ported verbatim (NOTICE.md).
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ForgeweaveBlocks.SEARED_SLAB_BRICKS.get())
                .pattern("AA")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "seared_slab_bricks_from_seared_brick"));
    }

    /** One seared variant's stairs + slab crafting and stonecutting recipes -- see {@link #buildSearedStairsSlabRecipes}. */
    private void searedStairsAndSlab(RecipeOutput recipeOutput, ItemLike base, ItemLike stairs, ItemLike slab) {
        String hasBase = "has_" + BuiltInRegistries.ITEM.getKey(base.asItem()).getPath();

        stairBuilder(stairs, Ingredient.of(base)).unlockedBy(hasBase, has(base)).save(recipeOutput);
        slabBuilder(RecipeCategory.BUILDING_BLOCKS, slab, Ingredient.of(base)).unlockedBy(hasBase, has(base)).save(recipeOutput);

        stonecutting(recipeOutput, base, stairs, 1);
        stonecutting(recipeOutput, base, slab, 2);
    }

    /**
     * One stonecutter conversion, saved under the {@code forgeweave:} namespace like every other
     * recipe in this file -- vanilla's own {@code stonecutterResultFromBase} saves its id as a bare
     * string, which resolves to {@code minecraft:}.
     */
    private void stonecutting(RecipeOutput recipeOutput, ItemLike base, ItemLike result, int count) {
        String resultName = BuiltInRegistries.ITEM.getKey(result.asItem()).getPath();
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(base), RecipeCategory.BUILDING_BLOCKS, result, count)
                .unlockedBy("has_" + BuiltInRegistries.ITEM.getKey(base.asItem()).getPath(), has(base))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, resultName + "_from_" + BuiltInRegistries.ITEM.getKey(base.asItem()).getPath() + "_stonecutting"));
    }

    /**
     * The smeltery multiblock's blocks (docs/SCOPE.md M2 issue #95). Shapes ported 1:1 from upstream
     * 1.12's {@code recipes/smeltery/{smeltery_controller,smeltery_drain}.json} and {@code
     * recipes/smeltery/seared/{tank,gauge,window,glass}.json} (NOTICE.md), with upstream's {@code
     * blockGlass} ore-dict entry becoming the modern {@code c:glass_blocks} tag. Plain seared glass
     * (issue #289) reuses the same tank-family shape helper: its plus pattern is upstream's own.
     *
     * <p>The Nether Core has no upstream shape -- tiered cores are SCOPE.md's own addition -- so it
     * is the Standard Core's ring with a netherite ingot at its heart, matching SCOPE.md's
     * "netherite-built" and the tank's own brick-ring-around-a-core layout.
     */
    private void smelteryRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.STANDARD_CORE.get())
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);

        // #442 -- upstream's recipes/smeltery/seared/furnace_controller.json: eight seared bricks
        // around a vanilla furnace (NOTICE.md).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.SEARED_FURNACE_CONTROLLER.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .define('B', Items.FURNACE)
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);

        // T44/#475 -- upstream's recipes/smeltery/tinker_tank_controller.json: eight seared bricks
        // around a vanilla bucket (NOTICE.md).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.SEARED_RESERVOIR_CONTROLLER.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .define('B', Items.BUCKET)
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.NETHER_CORE.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("AAA")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .define('B', Items.NETHERITE_INGOT)
                .unlockedBy("has_netherite_ingot", has(Items.NETHERITE_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.SEARED_DRAIN.get())
                .pattern("A A")
                .pattern("A A")
                .pattern("A A")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);

        // #277 -- the duct and chute (docs/SCOPE.md M3.4). Shapes ported 1:1 from the 1.20 clone's
        // recipes/smeltery/seared/{duct,chute}.json (NOTICE.md): the duct is a drain's ring of bricks
        // with a pair of gold ingots through the middle, the chute a hollow frame of bricks around a
        // pair of copper ingots.
        ioRecipe(recipeOutput, ForgeweaveItems.SEARED_DUCT.get(), Tags.Items.INGOTS_GOLD, "has_gold_ingot",
                "A A", "B B", "A A");
        ioRecipe(recipeOutput, ForgeweaveItems.SEARED_CHUTE.get(), Tags.Items.INGOTS_COPPER, "has_copper_ingot",
                "ABA", "   ", "ABA");

        tankRecipe(recipeOutput, ForgeweaveItems.SEARED_TANK.get(), "AAA", "ABA", "AAA");
        tankRecipe(recipeOutput, ForgeweaveItems.SEARED_GAUGE.get(), "ABA", "BBB", "ABA");
        tankRecipe(recipeOutput, ForgeweaveItems.SEARED_WINDOW.get(), "ABA", "ABA", "ABA");

        // Plain seared glass (docs/SCOPE.md M3.3 issue #289): upstream's recipes/smeltery/seared/
        // glass.json shape, a plus of 4 seared bricks around a glass block (NOTICE.md).
        tankRecipe(recipeOutput, ForgeweaveItems.SEARED_GLASS.get(), " A ", "ABA", " A ");

        // #100 -- casting (docs/SCOPE.md M2 issue #100). Shapes ported 1:1 from upstream 1.12's
        // recipes/smeltery/{casting_table,casting_basin,faucet}.json (NOTICE.md). The casts
        // themselves have no crafting recipe in either mod -- pouring molten gold over the thing you
        // want a cast of is how you get one.
        searedBrickShape(recipeOutput, ForgeweaveItems.CASTING_TABLE.get(), "AAA", "A A", "A A");
        searedBrickShape(recipeOutput, ForgeweaveItems.CASTING_BASIN.get(), "A A", "A A", "AAA");
        searedBrickShape(recipeOutput, ForgeweaveItems.FAUCET.get(), "A A", " A ");

        // #441 (parity audit T9) -- the channel, upstream 1.12's recipes/smeltery/channel.json
        // (NOTICE.md): five seared bricks in a trough shape make three channels.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ForgeweaveItems.SEARED_CHANNEL.get(), 3)
                .pattern("A A")
                .pattern("AAA")
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);
    }

    /** A shape made purely of seared bricks. */
    private void searedBrickShape(RecipeOutput recipeOutput, ItemLike result, String... rows) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result);
        for (String row : rows) {
            builder.pattern(row);
        }
        builder.define('A', ForgeweaveItems.SEARED_BRICK.get())
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);
    }

    /** A smeltery I/O shape (#277): {@code A} seared bricks, {@code B} the metal that makes it a duct or a chute. */
    private void ioRecipe(RecipeOutput recipeOutput, ItemLike result, TagKey<Item> metal, String criterion,
            String top, String middle, String bottom) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern(top)
                .pattern(middle)
                .pattern(bottom)
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .define('B', metal)
                .unlockedBy(criterion, has(metal))
                .save(recipeOutput);
    }

    /** A tank-family shape: {@code A} seared bricks, {@code B} any glass block. */
    private void tankRecipe(RecipeOutput recipeOutput, ItemLike result, String top, String middle, String bottom) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern(top)
                .pattern(middle)
                .pattern(bottom)
                .define('A', ForgeweaveItems.SEARED_BRICK.get())
                .define('B', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_seared_brick", has(ForgeweaveItems.SEARED_BRICK.get()))
                .save(recipeOutput);
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
    /**
     * The Tool Forge (docs/SCOPE.md M3 issue #152). Upstream 1.12 registers one recipe per metal
     * ore-dict entry ({@code TinkerTools#registerToolForgeRecipe}), all of them the same shape:
     *
     * <pre>
     *   BBB     B = seared brick block
     *   MTM     M = the metal storage block, which also becomes the forge's texture
     *   M M     T = a Tool Station
     * </pre>
     *
     * <p>One recipe covers every metal here instead of nineteen, because {@code M} is a tag -- see
     * {@link ForgeweaveItemTagsProvider#TOOL_FORGE_BLOCKS}, which lists the same metals upstream's
     * ore-dict calls do. {@code texture_source} points the {@link RetexturedShapedRecipe} at that
     * tag: the first block in the grid is a seared brick, and the appearance a player expects is the
     * metal they built it from.
     */
    private void toolForgeRecipe(RecipeOutput recipeOutput) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(ForgeweaveItems.TOOL_FORGE.get());
        Ingredient metal = Ingredient.of(ForgeweaveItemTagsProvider.TOOL_FORGE_BLOCKS);
        ShapedRecipePattern pattern = ShapedRecipePattern.of(
                Map.of('B', Ingredient.of(ForgeweaveBlocks.SEARED_BRICKS.get()),
                        'M', metal,
                        'T', Ingredient.of(ForgeweaveItems.TOOL_STATION.get())),
                "BBB", "MTM", "M M");
        RetexturedShapedRecipe recipe = new RetexturedShapedRecipe("", CraftingBookCategory.MISC, pattern,
                new ItemStack(ForgeweaveItems.TOOL_FORGE.get()), Optional.of(metal));

        AdvancementHolder advancement = recipeOutput.advancement()
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
                .addCriterion("has_tool_station", has(ForgeweaveItems.TOOL_STATION.get()))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .requirements(AdvancementRequirements.Strategy.OR)
                .build(id.withPrefix("recipes/misc/"));
        recipeOutput.accept(id, recipe, advancement);
    }

    private void gravelFlintRecipe(RecipeOutput recipeOutput) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "flint_from_gravel");
        GravelFlintRecipe recipe = new GravelFlintRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.FLINT),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GRAVEL), Ingredient.of(Items.GRAVEL), Ingredient.of(Items.GRAVEL)));

        AdvancementHolder advancement = recipeOutput.advancement()
                .addCriterion("has_gravel", has(Items.GRAVEL))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .requirements(AdvancementRequirements.Strategy.OR)
                .build(id.withPrefix("recipes/misc/"));
        recipeOutput.accept(id, recipe, advancement);
    }

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
