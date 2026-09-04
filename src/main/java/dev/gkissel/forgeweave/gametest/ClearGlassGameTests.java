package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ConnectedGlassBlock;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Covers issue #275: clear glass and its 16 clear stained glass colors, upstream 1.12's {@code
 * BlockClearGlass}/{@code BlockClearStainedGlass} (NOTICE.md). {@link #clearGlassAndEveryStainedColorArePlaceableBlocks}
 * is the direct registration regression -- it fails on the pre-fix code, where {@code
 * ForgeweaveBlocks} has no {@code CLEAR_GLASS}/{@code CLEAR_STAINED_GLASS_*} fields to place at all.
 * The other two pin the two upstream recipe paths (furnace smelt, shaped stained-glass craft) down
 * through the real {@code RecipeManager}, the same shape {@code GroutGameTests} uses for grout.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ClearGlassGameTests {

    @GameTest(template = "empty")
    public static void clearGlassAndEveryStainedColorArePlaceableBlocks(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.CLEAR_GLASS.get());
        helper.assertBlockPresent(ForgeweaveBlocks.CLEAR_GLASS.get(), new BlockPos(1, 1, 1));

        List<ForgeweaveBlocks.StainedGlassColor> colors = ForgeweaveBlocks.clearStainedGlassColors();
        helper.assertTrue(colors.size() == 16, "expected 16 clear stained glass colors, found " + colors.size());

        for (int i = 0; i < colors.size(); i++) {
            BlockPos pos = new BlockPos(2 + i, 1, 1);
            helper.setBlock(pos, colors.get(i).block().get());
            helper.assertBlockPresent(colors.get(i).block().get(), pos);
        }

        helper.succeed();
    }

    /**
     * Issue #951: a pane's six {@code connected_*} flags follow its same-block neighbours, which is
     * what picks the connected-texture frame for each face. Upstream answers this in
     * {@code getActualState}; on 1.21 it is {@code updateShape}, reached here through the ordinary
     * block-placement update the helper's {@code setBlock} performs.
     */
    @GameTest(template = "empty")
    public static void connectedFlagsFollowSameBlockNeighbours(GameTestHelper helper) {
        BlockPos west = new BlockPos(1, 1, 1);
        BlockPos middle = new BlockPos(2, 1, 1);
        BlockPos east = new BlockPos(3, 1, 1);

        helper.setBlock(west, ForgeweaveBlocks.CLEAR_GLASS.get());
        helper.setBlock(middle, ForgeweaveBlocks.CLEAR_GLASS.get());
        helper.setBlock(east, ForgeweaveBlocks.CLEAR_STAINED_GLASS_RED.get());

        helper.assertTrue(helper.getBlockState(west).getValue(ConnectedGlassBlock.CONNECTED_EAST),
                "clear glass next to clear glass must connect east");
        helper.assertFalse(helper.getBlockState(middle).getValue(ConnectedGlassBlock.CONNECTED_EAST),
                "clear glass must not connect to a stained pane; upstream's canConnect is same-block only");
        helper.assertFalse(helper.getBlockState(west).getValue(ConnectedGlassBlock.CONNECTED_UP),
                "a side with nothing on it must stay unconnected");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clearGlassSmeltsFromAGlassBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack glass = new ItemStack(Blocks.GLASS);

        ItemStack smelted = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(glass), level)
                .map(match -> match.value().assemble(new SingleRecipeInput(glass), level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(smelted.is(ForgeweaveBlocks.CLEAR_GLASS.get().asItem()),
                "expected furnace-smelting glass to give clear glass, got " + smelted);

        helper.succeed();
    }

    /** Every one of the 16 colors crafts from 8 clear glass + its own dye, matching upstream's shape. */
    @GameTest(template = "empty")
    public static void everyStainedColorCraftsFromClearGlassAndItsDye(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack clearGlass = new ItemStack(ForgeweaveBlocks.CLEAR_GLASS.get());

        List<String> failures = new ArrayList<>();
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            ItemStack dye = new ItemStack(DyeItem.byColor(color.dye()));
            CraftingInput input = CraftingInput.of(3, 3, List.of(
                    clearGlass, clearGlass, clearGlass,
                    clearGlass, dye, clearGlass,
                    clearGlass, clearGlass, clearGlass));

            ItemStack crafted = level.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(match -> match.value().assemble(input, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);

            if (!crafted.is(color.block().get().asItem()) || crafted.getCount() != 8) {
                failures.add(color.dye().getName() + " -> " + crafted);
            }
        }

        helper.assertTrue(failures.isEmpty(),
                "expected 8 clear glass + dye to craft 8 stained glass of that color; failures: " + failures);
        helper.succeed();
    }
}
