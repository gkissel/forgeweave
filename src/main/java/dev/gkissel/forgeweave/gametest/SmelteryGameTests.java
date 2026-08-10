package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryCore;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryScan;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * Covers docs/SCOPE.md M2 issue #95's verification on a headless dedicated server: the smeltery
 * multiblock forms at its minimum and maximum size, rejects an oversized interior and a hole in a
 * wall (with a reason a player can read), notices a wall broken far from the core without the block
 * entity ever ticking, and round-trips its formed state through NBT.
 *
 * <p>Structures are built with the core placed last, so what is exercised is the real trigger --
 * {@code SmelteryControllerBlock#onPlace} -- rather than a direct call into the scan.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryGameTests {
    /** Where the core always goes: the middle of the -X wall, on the lowest wall layer. */
    static final BlockPos CORE_POS = new BlockPos(0, 2, 1);

    /** The one seared tank {@link #buildWalls} puts in the walls; issue #96's tests fill it with lava. */
    static final BlockPos TANK_POS = new BlockPos(1, 2, 0);

    @GameTest(template = "smeltery")
    public static void minimumStructureForms(GameTestHelper helper) {
        // SCOPE.md's "minimum 3x3x3 smeltery": a 1x1 interior two blocks tall over a seared floor.
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryStructure structure = structureOf(helper, core);
        helper.assertTrue(structure != null, "expected the minimum smeltery to form: " + reason(helper, core));
        helper.assertValueEqual(structure.width(), 1, "interior width");
        helper.assertValueEqual(structure.depth(), 1, "interior depth");
        helper.assertValueEqual(structure.height(), 2, "interior height");
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void maximumInteriorForms(GameTestHelper helper) {
        buildWalls(helper, SmelteryScan.MAX_SIZE, SmelteryScan.MAX_SIZE, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryStructure structure = structureOf(helper, core);
        helper.assertTrue(structure != null, "expected a 9x9 interior to form: " + reason(helper, core));
        helper.assertValueEqual(structure.interiorVolume(), SmelteryScan.MAX_SIZE * SmelteryScan.MAX_SIZE * 2, "interior volume");
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void oversizedInteriorIsRejected(GameTestHelper helper) {
        buildWalls(helper, SmelteryScan.MAX_SIZE + 1, SmelteryScan.MAX_SIZE + 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.assertTrue(structureOf(helper, core) == null, "expected a 10x10 interior to be rejected");
        assertReason(helper, core, SmelteryScan.KEY_TOO_LARGE);
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void wallHoleIsRejectedWithAReason(GameTestHelper helper) {
        buildWalls(helper, 3, 3, 2);
        // Punch a hole in the far wall on the core's own layer, so no wall layer can complete.
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.assertTrue(structureOf(helper, core) == null, "expected a hole in a wall to be rejected");
        assertReason(helper, core, SmelteryScan.KEY_INVALID_WALL);
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void wallsWithoutATankAreRejected(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        helper.setBlock(new BlockPos(1, 2, 0), ForgeweaveBlocks.SEARED_BRICKS.get()); // replace the tank
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.assertTrue(structureOf(helper, core) == null, "expected a tankless smeltery to be rejected");
        assertReason(helper, core, SmelteryScan.KEY_NO_TANK);
        helper.succeed();
    }

    /**
     * Breaking a wall four blocks from the core sends it no neighbour update, so this is what proves
     * the revalidation-on-read path in {@link SmelteryControllerBlockEntity#structure()} works --
     * the block entity has no ticker to notice it any other way. The delay is one tick past that
     * method's rescan interval.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void breakingADistantWallInvalidatesTheStructure(GameTestHelper helper) {
        buildWalls(helper, 3, 3, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        helper.assertTrue(structureOf(helper, core) != null, "expected the smeltery to form first: " + reason(helper, core));

        helper.setBlock(new BlockPos(4, 2, 2), Blocks.AIR);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(structureOf(helper, core) == null, "expected the broken wall to invalidate the structure");
            assertReason(helper, core, SmelteryScan.KEY_INVALID_WALL);
            helper.succeed();
        });
    }

    @GameTest(template = "smeltery")
    public static void netherCoreCarriesItsOwnTier(GameTestHelper helper) {
        buildWalls(helper, 1, 1, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.NETHER_CORE.get());

        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        helper.assertTrue(blockEntity.isFormed(), "expected a Nether Core to form a smeltery too: " + reason(helper, core));
        helper.assertTrue(blockEntity.core() == SmelteryCore.NETHER, "expected the Nether Core tier to be recorded");
        helper.assertTrue(blockEntity.core().yieldMultiplier() > SmelteryCore.STANDARD.yieldMultiplier(),
                "expected the Nether Core to out-yield the Standard Core");
        helper.succeed();
    }

    /** SCOPE.md M2's save-compat fixture list calls out "smeltery block-entity NBT (tank fluids, structure bounds)". */
    @GameTest(template = "smeltery")
    public static void formedStateRoundTripsThroughNbt(GameTestHelper helper) {
        buildWalls(helper, 3, 3, 2);
        BlockPos core = placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SmelteryControllerBlockEntity blockEntity = helper.getBlockEntity(core);
        SmelteryStructure structure = blockEntity.structure();
        helper.assertTrue(structure != null, "expected the smeltery to form first: " + reason(helper, core));
        blockEntity.tank().fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = blockEntity.saveWithFullMetadata(registries);
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(core));
        BlockEntity reloaded = BlockEntity.loadStatic(helper.absolutePos(core), state, tag, registries);

        helper.assertTrue(reloaded instanceof SmelteryControllerBlockEntity, "expected the saved tag to rebuild a smeltery core");
        SmelteryControllerBlockEntity loaded = (SmelteryControllerBlockEntity) reloaded;
        helper.assertTrue(structure.equals(loaded.structure()), "expected the structure bounds to survive an NBT round trip");
        helper.assertValueEqual(loaded.tank().getFluidAmount(), 500, "tank contents after an NBT round trip");
        helper.assertValueEqual(loaded.tank().getCapacity(),
                structure.interiorVolume() * SmelteryControllerBlockEntity.CAPACITY_PER_BLOCK, "tank capacity after an NBT round trip");
        helper.succeed();
    }

    /**
     * Floor, four walls and one tank around an interior of {@code width} x {@code depth} x
     * {@code height}, with the interior starting at relative (1, 2, 1). The core's slot in the -X
     * wall is left open for {@link #placeCore}.
     */
    static void buildWalls(GameTestHelper helper, int width, int depth, int height) {
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        for (int y = 2; y < 2 + height; y++) {
            for (int x = 1; x <= width; x++) {
                helper.setBlock(new BlockPos(x, y, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(x, y, depth + 1), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(new BlockPos(0, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(width + 1, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        // A smeltery needs at least one tank in its walls (upstream's hasTank check).
        helper.setBlock(TANK_POS, ForgeweaveBlocks.SEARED_TANK.get());
    }

    /** Placed last so the scan runs off the real placement event with the structure already complete. */
    static BlockPos placeCore(GameTestHelper helper, Block core) {
        helper.setBlock(CORE_POS, core.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        return CORE_POS;
    }

    private static SmelteryStructure structureOf(GameTestHelper helper, BlockPos core) {
        return helper.<SmelteryControllerBlockEntity>getBlockEntity(core).structure();
    }

    private static String reason(GameTestHelper helper, BlockPos core) {
        return helper.<SmelteryControllerBlockEntity>getBlockEntity(core).lastResult().getString();
    }

    private static void assertReason(GameTestHelper helper, BlockPos core, String expectedKey) {
        Component message = helper.<SmelteryControllerBlockEntity>getBlockEntity(core).lastResult();
        String key = message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<literal>";
        helper.assertTrue(expectedKey.equals(key), "expected the core to report " + expectedKey + " but it reported " + key);
    }
}
