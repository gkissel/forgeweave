package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Upstream 1.12's {@code tools/tools/Hatchet.java} leaf carve-out (parity audit 2026-08-18 T65,
 * issue #496), verified end to end through a real Tool Station assembly (the only reliable way to
 * exercise real block-tag membership -- a bare unit test's {@code Bootstrap.bootStrap()} never loads
 * datapack tag contents, so {@code state.is(BlockTags.LEAVES)} would always read false there):
 *
 * <pre>
 * public float getStrVsBlock(ItemStack stack, IBlockState state) {
 *   if(state.getBlock().getMaterial(state) == Material.LEAVES) {
 *     return ToolHelper.calcDigSpeed(stack, state);
 *   }
 *   return super.getStrVsBlock(stack, state);
 * }
 *
 * public void afterBlockBreak(..., int damage, ...) {
 *   if(state.getBlock().isLeaves(state, world, pos)) {
 *     damage = 0;
 *   }
 *   super.afterBlockBreak(..., damage, ...);
 * }
 * </pre>
 *
 * <p>Leaves are never in upstream's {@code effective_materials} (WOOD, VINE, PLANTS, GOURD,
 * CACTUS), so a hatchet is fast on leaves without ever being "the right tool" for them -- unlike
 * {@code mineable/axe} blocks, which are both. {@code Hatchet#buildTagData}'s flat {@code +0.5}
 * attack (the other half of T65) is pinned separately, against the assembly formula itself, by
 * {@code ToolAssemblyRecipesTest} -- no block tags involved there, so a unit test suffices.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class HatchetGameTests {

    @GameTest(template = "empty")
    public static void leavesMineAtFullToolSpeedButAreNeverTheCorrectTool(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack hatchet = ToolAssembly.tool(helper, player, pos, ForgeweaveItems.PART_AXE_HEAD.get(),
                "stone", "wood", "wood");

        Tool tool = hatchet.get(DataComponents.TOOL);
        helper.assertTrue(tool != null, "an assembled hatchet must carry a tool component");
        ToolStats.Stats stats = hatchet.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null, "an assembled hatchet must carry tool stats");

        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();

        helper.assertFalse(tool.isCorrectForDrops(leaves),
                "upstream never adds LEAVES to Hatchet#effective_materials -- fast, but not effective");
        assertSpeed(helper, "leaves", tool.getMiningSpeed(leaves), stats.miningSpeed());

        helper.assertTrue(tool.isCorrectForDrops(log), "mineable/axe blocks must stay the correct tool");
        assertSpeed(helper, "oak log", tool.getMiningSpeed(log), stats.miningSpeed());

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingLeavesCostsNoDurabilityButOrdinaryBlocksStillDo(GameTestHelper helper) {
        BlockPos station = new BlockPos(1, 1, 1);
        BlockPos leafPos = new BlockPos(3, 1, 1);
        BlockPos stonePos = new BlockPos(4, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Survival, not the mock player's default: a creative break skips ItemStack#mineBlock
        // entirely, so the durability cost this test is about would never be charged either way.
        player.setGameMode(GameType.SURVIVAL);
        ItemStack hatchet = ToolAssembly.tool(helper, player, station, ForgeweaveItems.PART_AXE_HEAD.get(),
                "stone", "wood", "wood");
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        // Persistent so a stray decay tick can't turn "still there" into a false pass.
        helper.setBlock(leafPos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        helper.setBlock(stonePos, Blocks.STONE.defaultBlockState());

        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.getDamageValue() == 0, "the test needs an undamaged hatchet");

        player.gameMode.destroyBlock(helper.absolutePos(leafPos));
        helper.assertTrue(helper.getBlockState(leafPos).isAir(), "the leaves must actually break");
        helper.assertTrue(held.getDamageValue() == 0,
                "leaves must cost no durability, got " + held.getDamageValue());

        // Stone is neither mineable/axe nor leaves -- the ordinary "not effective" 2-durability
        // charge (ToolItem#miningDurabilityCost) must still apply, or this proves nothing.
        player.gameMode.destroyBlock(helper.absolutePos(stonePos));
        helper.assertTrue(helper.getBlockState(stonePos).isAir(), "the stone must actually break");
        helper.assertTrue(held.getDamageValue() == 2,
                "an ordinary not-effective block must still cost 2 durability, got " + held.getDamageValue());

        helper.succeed();
    }

    private static void assertSpeed(GameTestHelper helper, String what, float actual, float expected) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0e-4F,
                "expected " + what + " at " + expected + " mining speed, got " + actual);
    }
}
