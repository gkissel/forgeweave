package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryScan;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * Scan robustness (docs/SCOPE.md M3.3 issue #288): what a scan does when the blocks it wants already
 * belong to another smeltery, and when part of a formed smeltery is not loaded.
 *
 * <p>Both behaviours are upstream 1.12's (NOTICE.md): {@code MultiblockTinker#isValidSlave} refuses
 * any structure block whose servant already points at a different master, and
 * {@code TileMultiblock#checkMultiblockStructure} only rechecks a formed structure when
 * {@code MultiblockDetection#checkIfMultiblockCanBeRechecked} says its whole area is loaded.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryScanGameTests {
    /** Core of the -X smeltery, in its own -X wall, facing out. */
    private static final BlockPos CORE_A = new BlockPos(0, 2, 1);
    /** Core of the +X smeltery, in its own +X wall, facing out. */
    private static final BlockPos CORE_B = new BlockPos(4, 2, 1);
    /** The single seared tank, in the wall column both smelteries share. */
    private static final BlockPos SHARED_TANK = new BlockPos(2, 2, 1);
    /** A wall of the -X smeltery far enough from its core to send it no neighbour update. */
    private static final BlockPos A_FAR_WALL = new BlockPos(1, 2, 2);

    /**
     * Two smelteries cannot both own the tank in the wall between them. Before #288 the second core's
     * scan simply took it, leaving two formed smelteries whose fuel came out of the same tank and
     * whose {@code setCore} calls fought over it on every rescan.
     */
    @GameTest(template = "smeltery")
    public static void aTankOwnedByAFormedSmelteryIsNotStolen(GameTestHelper helper) {
        buildTwins(helper);
        SmelteryControllerBlockEntity first = placeCore(helper, CORE_A, Direction.WEST);
        helper.assertTrue(first.isFormed(), "expected the first smeltery to form: " + reason(first));

        SearedTankBlockEntity tank = helper.getBlockEntity(SHARED_TANK);
        BlockPos ownerBefore = tank.core();
        helper.assertTrue(helper.absolutePos(CORE_A).equals(ownerBefore),
                "expected the shared tank to belong to the first smeltery, but it belonged to " + ownerBefore);

        SmelteryControllerBlockEntity second = placeCore(helper, CORE_B, Direction.EAST);
        helper.assertTrue(!second.isFormed(), "expected the second smeltery to refuse a tank the first one owns");
        assertReason(helper, second, SmelteryScan.KEY_CLAIMED);
        helper.assertTrue(helper.absolutePos(CORE_A).equals(tank.core()),
                "expected the shared tank to still belong to the first smeltery, but it belonged to " + tank.core());
        helper.assertTrue(first.isFormed(), "expected the first smeltery to stay formed: " + reason(first));
        helper.succeed();
    }

    /**
     * The other half of the claim rule: a claim only holds while the smeltery that made it still
     * stands. Otherwise breaking one smeltery would lock its tank away from every other one forever,
     * since nothing ever clears the position a tank remembers.
     */
    @GameTest(template = "smeltery")
    public static void aClaimFromABrokenSmelteryDoesNotBlockANewOne(GameTestHelper helper) {
        buildTwins(helper);
        SmelteryControllerBlockEntity first = placeCore(helper, CORE_A, Direction.WEST);
        SmelteryControllerBlockEntity second = placeCore(helper, CORE_B, Direction.EAST);
        helper.assertTrue(!second.isFormed(), "expected the second smeltery to be blocked while the first one stands");

        // Break the first smeltery. Its wall is too far from its own core to notify it, which is the
        // real-world case as well: the claim goes stale before anything reads the owning core again.
        helper.setBlock(A_FAR_WALL, Blocks.AIR);
        first.updateStructure();
        helper.assertTrue(!first.isFormed(), "expected the first smeltery to break");

        second.updateStructure();
        helper.assertTrue(second.isFormed(),
                "expected the second smeltery to take over the freed tank: " + reason(second));
        helper.assertTrue(helper.absolutePos(CORE_B).equals(helper.<SearedTankBlockEntity>getBlockEntity(SHARED_TANK).core()),
                "expected the freed tank to belong to the second smeltery");
        helper.succeed();
    }

    /**
     * A formed smeltery whose far half has fallen out of the loaded region must be left alone rather
     * than rescanned: the scan can only fail there for want of loaded chunks, and before #288 that
     * failure cleared the structure, shrank the tank back to one block of capacity and threw away
     * everything above it -- a player walking away from a full smeltery came back to an empty one.
     *
     * <p>Built far outside the test area, straddling a chunk border, with a chunk ticket holding only
     * the core's own chunk (radius 0, so the ticket does not spill over into the neighbour the way
     * {@code setChunkForced} would). The other half then falls out of the loaded set on its own, which
     * is exactly the shape of the bug.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void anUnloadedRegionKeepsTheStructureAndItsContents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getChunkSource().addRegionTicket(TicketType.FORCED, CORE_CHUNK, 0, CORE_CHUNK);
        buildStraddlingSmeltery(level);

        SmelteryControllerBlockEntity core = (SmelteryControllerBlockEntity) level.getBlockEntity(FAR_CORE);
        helper.assertTrue(core != null, "expected a smeltery core at " + FAR_CORE);
        SmelteryStructure formed = core.structure();
        helper.assertTrue(formed != null, "expected the straddling smeltery to form: " + reason(core));
        core.tank().fill(new FluidStack(Fluids.WATER, FILLED_AMOUNT), IFluidHandler.FluidAction.EXECUTE);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!level.hasChunkAt(FAR_WALL),
                        "waiting for the far half of the smeltery to leave the loaded region"))
                .thenExecute(() -> {
                    core.updateStructure();
                    helper.assertTrue(formed.equals(core.structure()),
                            "expected a scan over an unloaded region to leave the structure alone, but it reported " + reason(core));
                    helper.assertValueEqual(core.tank().getFluidAmount(), FILLED_AMOUNT,
                            "tank contents after a scan over an unloaded region");
                    level.getChunkSource().removeRegionTicket(TicketType.FORCED, CORE_CHUNK, 0, CORE_CHUNK);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- the straddling smeltery

    /** More than one block's worth of capacity, so a structure wrongly cleared visibly loses fluid. */
    private static final int FILLED_AMOUNT = 5000;

    /** Core of the straddling smeltery, far outside anything the test framework keeps loaded. */
    private static final BlockPos FAR_CORE = new BlockPos(8190, 64, 8182);
    /** The chunk holding {@link #FAR_CORE}; blocks past x = 8192 belong to the next one along. */
    private static final ChunkPos CORE_CHUNK = new ChunkPos(FAR_CORE);
    /** The opposite wall, in that next chunk along -- the block the test waits to see unloaded. */
    private static final BlockPos FAR_WALL = new BlockPos(8194, 64, 8182);

    /** A 3x3x2 smeltery whose -X wall (with the core and the tank) and +X wall sit in different chunks. */
    private static void buildStraddlingSmeltery(ServerLevel level) {
        BlockState brick = ForgeweaveBlocks.SEARED_BRICKS.get().defaultBlockState();
        int minX = FAR_CORE.getX() + 1;
        int maxX = minX + 2;
        int minZ = FAR_CORE.getZ() - 1;
        int maxZ = minZ + 2;
        int floor = FAR_CORE.getY() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(new BlockPos(x, floor, z), brick);
            }
        }
        for (int y = FAR_CORE.getY(); y <= FAR_CORE.getY() + 1; y++) {
            for (int x = minX; x <= maxX; x++) {
                level.setBlockAndUpdate(new BlockPos(x, y, minZ - 1), brick);
                level.setBlockAndUpdate(new BlockPos(x, y, maxZ + 1), brick);
            }
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(new BlockPos(minX - 1, y, z), brick);
                level.setBlockAndUpdate(new BlockPos(maxX + 1, y, z), brick);
            }
        }
        // The tank goes in the core's own chunk, so the fuel side of the smeltery is not what the
        // unloaded half is being tested through.
        level.setBlockAndUpdate(new BlockPos(minX, FAR_CORE.getY(), minZ - 1),
                ForgeweaveBlocks.SEARED_TANK.get().defaultBlockState());
        // Placed last, so the scan runs off the real placement event.
        level.setBlockAndUpdate(FAR_CORE, ForgeweaveBlocks.STANDARD_CORE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
    }

    // ---------------------------------------------------------------- the side-by-side pair

    /**
     * Two 1x1x2 smelteries side by side sharing the wall column between them, with the only seared
     * tank in that shared column. Both core slots are left for {@link #placeCore}.
     */
    private static void buildTwins(GameTestHelper helper) {
        for (int x : new int[] {1, 3}) {
            helper.setBlock(new BlockPos(x, 1, 1), ForgeweaveBlocks.SEARED_BRICKS.get());
            for (int y = 2; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(x, y, 2), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        for (int y = 2; y <= 3; y++) {
            for (int x : new int[] {0, 2, 4}) {
                helper.setBlock(new BlockPos(x, y, 1), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        helper.setBlock(SHARED_TANK, ForgeweaveBlocks.SEARED_TANK.get());
    }

    private static SmelteryControllerBlockEntity placeCore(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ForgeweaveBlocks.STANDARD_CORE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing));
        return helper.getBlockEntity(pos);
    }

    private static String reason(SmelteryControllerBlockEntity core) {
        return core.lastResult().getString();
    }

    private static void assertReason(GameTestHelper helper, SmelteryControllerBlockEntity core, String expectedKey) {
        Component message = core.lastResult();
        String key = message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<literal>";
        helper.assertTrue(expectedKey.equals(key), "expected the core to report " + expectedKey + " but it reported " + key);
    }
}
