package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryCore;
import dev.gkissel.forgeweave.block.TieredSearedBricksBlock;

/**
 * The walls follow the core's tier ({@link TieredSearedBricksBlock}): forming a smeltery around a
 * higher core re-skins every seared brick in its shell, the change spreads out from the core over
 * about three seconds rather than landing all at once, and swapping a Standard Core back in undoes
 * it the same way.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryTierWaveGameTests {
    private static final int WIDTH = 3;
    private static final int DEPTH = 3;
    private static final int HEIGHT = 2;

    /** The wall brick farthest from the core: the far corner of the +X wall, top course. */
    private static final BlockPos FARTHEST_BRICK = new BlockPos(WIDTH + 1, 1 + HEIGHT, DEPTH);

    /** The brick right next to the core in the same wall. */
    private static final BlockPos NEAREST_BRICK = SmelteryGameTests.CORE_POS.south();

    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void wallsTakeTheCoresTierSpreadingOutFromIt(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, WIDTH, DEPTH, HEIGHT);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.NETHER_CORE.get());

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    assertTier(helper, NEAREST_BRICK, SmelteryCore.NETHER);
                    assertTier(helper, FARTHEST_BRICK, SmelteryCore.STANDARD);
                })
                .thenWaitUntil(() -> assertEveryBrickIs(helper, SmelteryCore.NETHER))
                .thenSucceed();
    }

    @GameTest(template = "smeltery", timeoutTicks = 400)
    public static void swappingAStandardCoreBackInReturnsTheWallsToStandard(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, WIDTH, DEPTH, HEIGHT);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.DEEP_CORE.get());

        helper.startSequence()
                .thenWaitUntil(() -> assertEveryBrickIs(helper, SmelteryCore.DEEP))
                .thenExecute(() -> SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get()))
                .thenWaitUntil(() -> assertEveryBrickIs(helper, SmelteryCore.STANDARD))
                .thenSucceed();
    }

    private static void assertEveryBrickIs(GameTestHelper helper, SmelteryCore tier) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(WIDTH + 1, 1 + HEIGHT, DEPTH + 1))) {
            if (helper.getBlockState(pos).getBlock() instanceof TieredSearedBricksBlock) {
                assertTier(helper, pos, tier);
            }
        }
    }

    private static void assertTier(GameTestHelper helper, BlockPos pos, SmelteryCore tier) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.getBlock() instanceof TieredSearedBricksBlock, "expected seared bricks at " + pos);
        helper.assertValueEqual(state.getValue(TieredSearedBricksBlock.TIER), tier, "brick tier at " + pos);
    }
}
