package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Regression coverage for issue #274 (seared stairs + slabs): fails red on the pre-fix code, where
 * {@code ForgeweaveBlocks} has no {@code SEARED_STAIRS_*}/{@code SEARED_SLAB_*} fields and none of
 * the crafting/stonecutting recipes below exist to resolve.
 *
 * <p>{@link #stairsAndSlabsAreRegisteredWithTheParentBlocksProperties} pins the parity claim in
 * {@code ForgeweaveBlocks}' javadoc -- same {@code searedProperties()} (hardness/resistance/sound) as
 * the plain block each is built from, no tool-tier gate. The remaining tests exercise the real {@code
 * RecipeManager}: the vanilla 6-block-to-4-stairs / 3-block-to-6-slab crafting shapes, the bonus
 * brick-item slab shape unique to the bricks variant, and the stonecutting recipes that are issue
 * #274's own called-out modern-API deviation (1.12 predates the stonecutter).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SearedStairsSlabsGameTests {

    @GameTest(template = "empty")
    public static void stairsAndSlabsAreRegisteredWithTheParentBlocksProperties(GameTestHelper helper) {
        assertSameProperties(helper, ForgeweaveBlocks.SEARED_STONE.get(), ForgeweaveBlocks.SEARED_STAIRS_STONE.get());
        assertSameProperties(helper, ForgeweaveBlocks.SEARED_STONE.get(), ForgeweaveBlocks.SEARED_SLAB_STONE.get());
        assertSameProperties(helper, ForgeweaveBlocks.SEARED_BRICKS.get(), ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get());
        assertSameProperties(helper, ForgeweaveBlocks.SEARED_BRICKS.get(), ForgeweaveBlocks.SEARED_SLAB_BRICKS.get());
        assertSameProperties(helper, ForgeweaveBlocks.SEARED_CREEPER.get(), ForgeweaveBlocks.SEARED_STAIRS_CREEPER.get());
        assertSameProperties(helper, ForgeweaveBlocks.SEARED_CREEPER.get(), ForgeweaveBlocks.SEARED_SLAB_CREEPER.get());

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stairsCraftSixSearedStoneIntoFourStairs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack block = new ItemStack(ForgeweaveBlocks.SEARED_STONE.get());
        ItemStack empty = ItemStack.EMPTY;
        // Vanilla's own stairs shape: "#  " / "## " / "###", 6 blocks total.
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                block, empty, empty,
                block, block, empty,
                block, block, block));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveBlocks.SEARED_STAIRS_STONE.get().asItem()) && crafted.getCount() == 4,
                "expected 6 seared stone to craft 4 seared stone stairs, got " + crafted);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void slabsCraftThreeSearedCreeperIntoSixSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack block = new ItemStack(ForgeweaveBlocks.SEARED_CREEPER.get());
        CraftingInput input = CraftingInput.of(3, 1, List.of(block, block, block));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveBlocks.SEARED_SLAB_CREEPER.get().asItem()) && crafted.getCount() == 6,
                "expected 3 seared creeper to craft 6 seared creeper slabs, got " + crafted);
        helper.succeed();
    }

    /** Upstream's bonus shape, {@code bricks_slab_simple.json}: 2 loose brick items, not a block. */
    @GameTest(template = "empty")
    public static void twoSearedBrickItemsCraftOneBricksSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack brickItem = new ItemStack(ForgeweaveItems.SEARED_BRICK.get());
        CraftingInput input = CraftingInput.of(2, 1, List.of(brickItem, brickItem));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveBlocks.SEARED_SLAB_BRICKS.get().asItem()) && crafted.getCount() == 1,
                "expected 2 seared brick items to craft 1 seared bricks slab, got " + crafted);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void searedPaverStonecutsIntoStairsAndSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(ForgeweaveBlocks.SEARED_PAVER.get()));

        List<ItemStack> results = level.getRecipeManager()
                .getRecipesFor(RecipeType.STONECUTTING, input, level).stream()
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .toList();

        helper.assertTrue(results.stream().anyMatch(stack -> stack.is(ForgeweaveBlocks.SEARED_STAIRS_PAVER.get().asItem())),
                "expected seared paver to stonecut into seared paver stairs, got " + results);
        helper.assertTrue(results.stream().anyMatch(stack -> stack.is(ForgeweaveBlocks.SEARED_SLAB_PAVER.get().asItem())),
                "expected seared paver to stonecut into seared paver slabs, got " + results);
        helper.succeed();
    }

    private static void assertSameProperties(GameTestHelper helper, Block parent, Block child) {
        helper.assertTrue(child.defaultDestroyTime() == parent.defaultDestroyTime(),
                "expected " + child + " to share " + parent + "'s destroy time");
        helper.assertTrue(child.defaultBlockState().getSoundType() == parent.defaultBlockState().getSoundType(),
                "expected " + child + " to share " + parent + "'s sound type");
    }

}
