package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Covers issue #129: grout is a placeable block matching upstream 1.12's {@code BlockSoil}
 * (NOTICE.md), not the flat item PR #115 shipped. {@link #groutIsAPlaceableBlock} is the direct
 * regression -- it fails on the pre-fix code, where {@code ForgeweaveBlocks} has no {@code GROUT}
 * field to place at all. The other two tests pin down that becoming a block didn't disturb the
 * crafting-table recipe (clay + sand + gravel -> 2 grout) or the furnace smelt (grout -> seared
 * brick), both of which keep resolving through the real {@code RecipeManager}.
 *
 * <p>{@link #groutCraftsInBulkFromClayBlockSandAndGravel} covers issue #503 (T72, parity audit
 * 2026-08-18): upstream 1.12 ships a second, bulk grout recipe alongside the simple one --
 * {@code grout.json}, a clay block plus four sand and four gravel (ore-dict, so red sand works
 * too), shapeless, yielding 8 grout. Forgeweave only ever ported {@code grout_simple.json}; this
 * fails until the bulk recipe is added to {@code ForgeweaveRecipeProvider}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class GroutGameTests {

    @GameTest(template = "empty")
    public static void groutIsAPlaceableBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.GROUT.get());

        helper.assertBlockPresent(ForgeweaveBlocks.GROUT.get(), pos);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groutCraftsFromClaySandAndGravel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 1,
                List.of(new ItemStack(Items.CLAY_BALL), new ItemStack(Items.SAND), new ItemStack(Items.GRAVEL)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveBlocks.GROUT.get().asItem()) && crafted.getCount() == 2,
                "expected clay ball + sand + gravel to craft 2 grout, got " + crafted);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groutCraftsInBulkFromClayBlockSandAndGravel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 3,
                List.of(new ItemStack(Items.SAND), new ItemStack(Items.GRAVEL), new ItemStack(Items.SAND),
                        new ItemStack(Items.GRAVEL), new ItemStack(Items.CLAY), new ItemStack(Items.GRAVEL),
                        new ItemStack(Items.SAND), new ItemStack(Items.GRAVEL), new ItemStack(Items.SAND)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveBlocks.GROUT.get().asItem()) && crafted.getCount() == 8,
                "expected clay block + 4 sand + 4 gravel to craft 8 grout, got " + crafted);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groutSmeltsIntoSearedBrick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack grout = new ItemStack(ForgeweaveBlocks.GROUT.get());

        ItemStack smelted = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(grout), level)
                .map(match -> match.value().assemble(new SingleRecipeInput(grout), level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(smelted.is(ForgeweaveItems.SEARED_BRICK.get()),
                "expected furnace-smelting grout to give seared brick, got " + smelted);

        helper.succeed();
    }
}
