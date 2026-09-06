package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTier;
import dev.gkissel.forgeweave.block.SmelteryCore;

/**
 * The walls follow the core's tier ({@link SearedTier}): forming a smeltery around a higher core
 * re-skins every wall and floor block in its shell -- bricks, another seared style, glass, the tank,
 * a drain -- the change spreads out from the core over about three seconds rather than landing all
 * at once, and swapping a Standard Core back in undoes it the same way.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryTierWaveGameTests {
    private static final int WIDTH = 3;
    private static final int DEPTH = 3;
    private static final int HEIGHT = 2;

    /** The wall block farthest from the core: the far corner of the +X wall, top course. */
    private static final BlockPos FARTHEST_BLOCK = new BlockPos(WIDTH + 1, 1 + HEIGHT, DEPTH);

    /** The brick right next to the core in the same wall. */
    private static final BlockPos NEAREST_BLOCK = SmelteryGameTests.CORE_POS.south();

    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void wallsTakeTheCoresTierSpreadingOutFromIt(GameTestHelper helper) {
        buildMixedWalls(helper);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.NETHER_CORE.get());

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    assertTier(helper, NEAREST_BLOCK, SmelteryCore.NETHER);
                    assertTier(helper, FARTHEST_BLOCK, SmelteryCore.STANDARD);
                })
                .thenWaitUntil(() -> assertEveryWallBlockIs(helper, SmelteryCore.NETHER))
                .thenSucceed();
    }

    @GameTest(template = "smeltery", timeoutTicks = 400)
    public static void swappingAStandardCoreBackInReturnsTheWallsToStandard(GameTestHelper helper) {
        buildMixedWalls(helper);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.DEEP_CORE.get());

        helper.startSequence()
                .thenWaitUntil(() -> assertEveryWallBlockIs(helper, SmelteryCore.DEEP))
                .thenExecute(() -> SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get()))
                .thenWaitUntil(() -> assertEveryWallBlockIs(helper, SmelteryCore.STANDARD))
                .thenSucceed();
    }

    /** {@link SmelteryGameTests#buildWalls}'s bricks and tank, plus one of each other wall block kind. */
    private static void buildMixedWalls(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, WIDTH, DEPTH, HEIGHT);
        helper.setBlock(new BlockPos(2, 2, 0), ForgeweaveBlocks.SEARED_GLASS.get());
        helper.setBlock(new BlockPos(3, 2, 0), ForgeweaveBlocks.SEARED_DRAIN.get());
        helper.setBlock(new BlockPos(2, 3, 0), ForgeweaveBlocks.SEARED_COBBLESTONE.get());
        helper.setBlock(new BlockPos(2, 1, 2), ForgeweaveBlocks.SEARED_PAVER.get());
    }

    private static void assertEveryWallBlockIs(GameTestHelper helper, SmelteryCore tier) {
        int tiered = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(WIDTH + 1, 1 + HEIGHT, DEPTH + 1))) {
            if (helper.getBlockState(pos).hasProperty(SearedTier.TIER)) {
                assertTier(helper, pos, tier);
                tiered++;
            }
        }
        helper.assertTrue(tiered > 20, "expected the whole shell to carry a tier, found " + tiered + " blocks");
    }

    private static void assertTier(GameTestHelper helper, BlockPos pos, SmelteryCore tier) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.hasProperty(SearedTier.TIER), "expected a tiered wall block at " + pos);
        helper.assertValueEqual(state.getValue(SearedTier.TIER), tier, "tier at " + pos);
    }
}
