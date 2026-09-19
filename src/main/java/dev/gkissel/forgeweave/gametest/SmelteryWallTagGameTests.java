package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;

/**
 * Issue #1067 (part 3 of #1008's addon audit): {@link dev.gkissel.forgeweave.block.SmelteryScan}
 * decides its wall/floor/tank/I-O roles by block tag membership rather than a fixed {@code Set.of},
 * so an addon or a datapack can add its own smeltery wall block. This proves it with a plain vanilla
 * block a datapack tag admits, nothing Forgeweave itself ever grants the role.
 *
 * <p>The fixture lives at {@code data/forgeweave/tags/block/smeltery/wall_addon.json}
 * ({@code src/gametest/resources}), not at {@code smeltery/wall.json}'s own path: {@code
 * src/gametest/resources/README.md} documents that a gametest file at a shipped tag's own path
 * shadows it instead of merging with it, since the gametest and main resource trees fold into one
 * pack for {@code runGameTestServer}. {@link dev.gkissel.forgeweave.block.SmelteryScan#WALL_ADDON}
 * is the reserved, always-empty-in-production tag {@code WALL} references with {@code
 * addOptionalTag} so this fixture can add to the wall role without touching the real tag's file.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryWallTagGameTests {
    @GameTest(template = "smeltery")
    public static void aGametestOnlyTagAdmitsAVanillaBlockAsAWall(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 3, 3, 2);
        // Swap one plain wall block, away from the core and the tank, for a block nothing in
        // Forgeweave's own wall/floor/tank/io/energized tags names. It only passes the scan
        // because data/forgeweave/tags/block/smeltery/wall_addon.json (this test's own fixture)
        // feeds SmelteryScan.WALL_ADDON, which the shipped forgeweave:smeltery/wall tag references.
        BlockPos vanillaWallBlock = new BlockPos(4, 2, 2);
        helper.setBlock(vanillaWallBlock, Blocks.DIAMOND_BLOCK);
        BlockPos core = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.isFormed(),
                "expected a datapack-tagged vanilla block to be accepted as a wall: " + reason(helper, core));
        helper.succeed();
    }

    private static String reason(GameTestHelper helper, BlockPos core) {
        return helper.<SmelteryControllerBlockEntity>getBlockEntity(core).lastResult().getString();
    }
}
