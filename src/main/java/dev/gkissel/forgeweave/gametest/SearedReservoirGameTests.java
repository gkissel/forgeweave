package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedReservoirBlockEntity;
import dev.gkissel.forgeweave.block.SearedReservoirControllerBlock;
import dev.gkissel.forgeweave.block.SearedReservoirScan;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * The seared reservoir multiblock (parity audit T44, issue #475): upstream
 * {@code MultiblockTinkerTank}'s floor/wall/ceiling rules, {@code TileTinkerTank}'s shell-inclusive
 * capacity math, the drain pouring it, and the NBT round trip.
 *
 * <p>Layout is {@link SearedFurnaceGameTests}': floor at y=1 over the interior, walls from y=2 up,
 * ceiling on top; the controller in the middle of the -X wall on the lowest wall layer, facing out,
 * placed last so the real {@code onPlace} trigger is what scans. No tank is built -- a reservoir
 * requires none, which is the first thing these tests pin.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SearedReservoirGameTests {
    static final BlockPos CORE_POS = new BlockPos(0, 2, 1);
    /** The +X wall block on the controller's own layer. */
    private static final BlockPos DRAIN_POS = new BlockPos(2, 2, 1);

    /**
     * Upstream {@code TileTinkerTank#updateStructureInfo} counts the shell: a 1x1x1 interior is a
     * 3x3x3 cuboid, so 27 blocks at four buckets each -- and it forms with no seared tank anywhere,
     * unlike the smeltery and the furnace.
     */
    @GameTest(template = "smeltery")
    public static void minimumStructureFormsWithoutATankAndCountsTheShell(GameTestHelper helper) {
        buildReservoir(helper, 1, 1, 1);
        SearedReservoirBlockEntity reservoir = placeController(helper);

        SmelteryStructure structure = reservoir.structure();
        helper.assertTrue(structure != null, "expected the 1x1x1 reservoir to form: " + reservoir.lastResult().getString());
        helper.assertValueEqual(structure.interiorVolume(), 1, "interior volume");
        helper.assertValueEqual(reservoir.tank().getCapacity(), 27 * 4000, "capacity of a 1x1x1 reservoir");
        helper.succeed();
    }

    /** A 3x3 interior two tall is a 5x4x5 cuboid: 100 blocks, 400 buckets. */
    @GameTest(template = "smeltery")
    public static void largerStructureScalesTheCapacity(GameTestHelper helper) {
        buildReservoir(helper, 3, 3, 2);
        SearedReservoirBlockEntity reservoir = placeController(helper);

        helper.assertTrue(reservoir.isFormed(), "expected the 3x3x2 reservoir to form: " + reservoir.lastResult().getString());
        helper.assertValueEqual(reservoir.tank().getCapacity(), 100 * 4000, "capacity of a 3x3x2 reservoir");
        helper.succeed();
    }

    /**
     * Upstream {@code validTinkerTankBlocks} is {@code validSmelteryBlocks} itself, so a reservoir
     * wall takes seared glass and a tank <em>anywhere</em> -- including a corner column, where the
     * seared furnace is the only one of the three that also allows it, and off a corner, where the
     * furnace refuses.
     */
    @GameTest(template = "smeltery")
    public static void wallsTakeGlassAndTanksAnywhere(GameTestHelper helper) {
        buildReservoir(helper, 3, 3, 2);
        helper.setBlock(new BlockPos(2, 3, 0), ForgeweaveBlocks.SEARED_GLASS.get());
        helper.setBlock(new BlockPos(0, 3, 0), ForgeweaveBlocks.SEARED_TANK.get());
        helper.setBlock(new BlockPos(2, 2, 4), ForgeweaveBlocks.SEARED_WINDOW.get());
        SearedReservoirBlockEntity reservoir = placeController(helper);

        helper.assertTrue(reservoir.isFormed(),
                "expected glass and off-corner tanks in the walls to be accepted: " + reservoir.lastResult().getString());
        helper.succeed();
    }

    /**
     * Upstream {@code validTinkerTankFloorBlocks} takes a seared block, seared glass or a drain, but
     * not a tank -- the one place a tank is refused.
     */
    @GameTest(template = "smeltery")
    public static void theFloorTakesGlassAndADrainButNotATank(GameTestHelper helper) {
        buildReservoir(helper, 1, 1, 1);
        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.SEARED_GLASS.get());
        SearedReservoirBlockEntity reservoir = placeController(helper);
        helper.assertTrue(reservoir.isFormed(),
                "expected seared glass in the floor to be accepted: " + reservoir.lastResult().getString());

        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.SEARED_DRAIN.get());
        reservoir.updateStructure();
        helper.assertTrue(reservoir.isFormed(),
                "expected a drain in the floor to be accepted: " + reservoir.lastResult().getString());

        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.SEARED_TANK.get());
        reservoir.updateStructure();
        helper.assertTrue(!reservoir.isFormed(), "expected a tank in the floor to be refused");
        assertReason(helper, reservoir, SearedReservoirScan.KEY_INVALID_FLOOR);
        helper.succeed();
    }

    /** A bottom-half seared slab roofs a reservoir; a top-half one does not (upstream's {@code isCeilingBlock}). */
    @GameTest(template = "smeltery")
    public static void theCeilingTakesBottomHalfSlabsOnly(GameTestHelper helper) {
        buildReservoir(helper, 1, 1, 1);
        BlockState slab = ForgeweaveBlocks.SEARED_SLAB_BRICKS.get().defaultBlockState();
        helper.setBlock(new BlockPos(1, 3, 1), slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        SearedReservoirBlockEntity reservoir = placeController(helper);
        helper.assertTrue(reservoir.isFormed(),
                "expected a bottom-half slab ceiling to form: " + reservoir.lastResult().getString());

        helper.setBlock(new BlockPos(1, 3, 1), slab.setValue(SlabBlock.TYPE, SlabType.TOP));
        reservoir.updateStructure();
        helper.assertTrue(!reservoir.isFormed(), "expected a top-half slab in the ceiling to be refused");
        assertReason(helper, reservoir, SearedReservoirScan.KEY_INVALID_CEILING);
        helper.succeed();
    }

    /**
     * The ticket's drain integration: a drain in a reservoir wall pours the reservoir, the same
     * {@code SearedDrainBlockEntity} that pours a smeltery -- upstream's {@code TileDrain} looks up
     * {@code ISmelteryTankHandler}, which both multiblocks implement.
     */
    @GameTest(template = "smeltery")
    public static void aDrainInTheWallPoursTheReservoir(GameTestHelper helper) {
        buildReservoir(helper, 1, 1, 1);
        helper.setBlock(DRAIN_POS, ForgeweaveBlocks.SEARED_DRAIN.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        SearedReservoirBlockEntity reservoir = placeController(helper);
        helper.assertTrue(reservoir.isFormed(), "expected the reservoir to form: " + reservoir.lastResult().getString());

        IFluidHandler drain = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK, helper.absolutePos(DRAIN_POS), null);
        helper.assertTrue(drain != null, "expected a formed reservoir's drain to expose a fluid handler");

        int filled = drain.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(filled, 2000, "millibuckets accepted through the drain");
        helper.assertValueEqual(reservoir.tank().getFluidAmount(), 2000, "millibuckets in the reservoir");

        FluidStack drained = drain.drain(500, IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(drained.getAmount(), 500, "millibuckets drained back out");
        helper.assertValueEqual(reservoir.tank().getFluidAmount(), 1500, "millibuckets left in the reservoir");
        helper.succeed();
    }

    /**
     * #772: the same #757 visual-gap defect as {@code SmelteryControllerBlock} -- closing the last
     * gap in a wall three blocks from the controller sends it no neighbour update either, so nothing
     * would notice the structure forming until a player interacts. Deliberately never reads {@link
     * SearedReservoirBlockEntity#structure()} (which would trigger its own revalidation-on-read and
     * mask the bug being fixed) between closing the gap and asserting -- only the controller's own
     * bounded settle-window recheck tick may notice it. Meaningful here even though a reservoir never
     * needs an ongoing heartbeat once formed (nothing to melt or heat): the settle window is what
     * gets it formed at all without interaction.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void closingADistantWallFormsTheReservoirWithoutInteraction(GameTestHelper helper) {
        buildReservoir(helper, 3, 3, 2);
        BlockPos hole = new BlockPos(4, 3, 2);
        helper.setBlock(hole, Blocks.AIR);
        SearedReservoirBlockEntity reservoir = placeController(helper);
        helper.assertTrue(!helper.getBlockState(CORE_POS).getValue(SearedReservoirControllerBlock.ACTIVE),
                "expected the hole to leave the controller reading unformed: " + reservoir.lastResult().getString());

        helper.setBlock(hole, ForgeweaveBlocks.SEARED_BRICKS.get());
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getBlockState(CORE_POS).getValue(SearedReservoirControllerBlock.ACTIVE),
                    "expected the controller's front to relight once the distant wall closed the structure, without any interaction");
            helper.succeed();
        });
    }

    @GameTest(template = "smeltery")
    public static void stateRoundTripsThroughNbt(GameTestHelper helper) {
        buildReservoir(helper, 1, 1, 1);
        SearedReservoirBlockEntity reservoir = placeController(helper);
        reservoir.tank().fill(new FluidStack(Fluids.WATER, 12_345), IFluidHandler.FluidAction.EXECUTE);
        SmelteryStructure structure = reservoir.structure();

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = reservoir.saveWithFullMetadata(registries);
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(CORE_POS));
        BlockEntity reloaded = BlockEntity.loadStatic(helper.absolutePos(CORE_POS), state, tag, registries);

        helper.assertTrue(reloaded instanceof SearedReservoirBlockEntity, "expected the saved tag to rebuild a reservoir");
        SearedReservoirBlockEntity loaded = (SearedReservoirBlockEntity) reloaded;
        helper.assertTrue(structure.equals(loaded.structure()), "expected the structure bounds to survive an NBT round trip");
        helper.assertValueEqual(loaded.tank().getCapacity(), 27 * 4000, "capacity after an NBT round trip");
        helper.assertValueEqual(loaded.tank().getFluidAmount(), 12_345, "millibuckets after an NBT round trip");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Floor at y=1 over the interior, walls y=2.., ceiling of seared bricks over the interior.
     * Interior x/z from 1, so the walls run at 0 and {@code size + 1}. No tank: a reservoir needs
     * none.
     */
    static void buildReservoir(GameTestHelper helper, int width, int depth, int height) {
        // Both planes are laid whole, ring included: unlike the furnace's, a reservoir's floor and
        // ceiling frames take only seared blocks and I/O blocks, never just anything.
        for (int x = 0; x <= width + 1; x++) {
            for (int z = 0; z <= depth + 1; z++) {
                helper.setBlock(new BlockPos(x, 1, z), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(x, 2 + height, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
        for (int y = 2; y < 2 + height; y++) {
            for (int x = 0; x <= width + 1; x++) {
                helper.setBlock(new BlockPos(x, y, 0), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(x, y, depth + 1), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
            for (int z = 1; z <= depth; z++) {
                helper.setBlock(new BlockPos(0, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
                helper.setBlock(new BlockPos(width + 1, y, z), ForgeweaveBlocks.SEARED_BRICKS.get());
            }
        }
    }

    static SearedReservoirBlockEntity placeController(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ForgeweaveBlocks.SEARED_RESERVOIR_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CORE_POS);
    }

    private static void assertReason(GameTestHelper helper, SearedReservoirBlockEntity reservoir, String expectedKey) {
        Component message = reservoir.lastResult();
        String key = message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "<literal>";
        helper.assertTrue(expectedKey.equals(key),
                "expected the controller to report " + expectedKey + " but it reported " + key);
    }
}
