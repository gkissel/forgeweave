package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.data.ForgeweaveRecipeProvider;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Covers issue #989's verification: the war mace head pattern is gated behind a vanilla Heavy Core.
 * {@link #warMaceHeadPatternIsNotAStencilTableOption} and {@link #blankAloneDoesNotCraftTheWarMaceHeadPattern}
 * are the negative side -- the Stencil Table's selection list omits it, and a lone blank pattern
 * matches no crafting-table recipe for it either. {@link #blankPlusHeavyCoreCraftsTheWarMaceHeadPattern}
 * is the positive side, {@link ForgeweaveRecipeProvider}'s new shapeless recipe. {@link
 * #partBuilderMakesAWarMaceHeadFromThatPattern} closes the loop: the pattern this recipe produces
 * still works at the Part Builder exactly like every other pattern (see also {@code
 * M3PartGameTests#everyM3PartCraftsFromItsPattern}, which already covers this same pattern/part pair).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class WarMaceHeadPatternGameTests {

    @GameTest(template = "empty")
    public static void warMaceHeadPatternIsNotAStencilTableOption(GameTestHelper helper) {
        helper.assertFalse(StencilTableMenu.PATTERNS.contains(ForgeweaveItems.PATTERN_WAR_MACE_HEAD),
                "expected the war mace head pattern to be absent from the Stencil Table's selection list");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blankAloneDoesNotCraftTheWarMaceHeadPattern(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())));

        boolean matched = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .map(result -> result.is(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get()))
                .orElse(false);
        helper.assertFalse(matched, "expected a lone blank pattern to craft no war mace head pattern");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blankPlusHeavyCoreCraftsTheWarMaceHeadPattern(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 1,
                List.of(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()), new ItemStack(Items.HEAVY_CORE)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get()) && crafted.getCount() == 1,
                "expected a blank pattern plus a heavy core to craft 1 war mace head pattern, got " + crafted);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partBuilderMakesAWarMaceHeadFromThatPattern(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get()));
        ResourceLocation wood = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood");
        int shardCount = PartBuilderRecipes.LARGE_HEAD_COST / PartBuilderRecipes.SHARD_VALUE;
        ItemStack shards = new ItemStack(ForgeweaveItems.SHARD.get(), shardCount);
        shards.set(ForgeweaveDataComponents.MATERIAL.get(), wood);
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(shards);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_WAR_MACE_HEAD.get()),
                "expected the war mace head part from its pattern at the Part Builder, got " + output);

        helper.succeed();
    }
}
