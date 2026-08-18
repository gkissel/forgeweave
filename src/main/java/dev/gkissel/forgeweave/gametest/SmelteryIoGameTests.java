package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * Issue #277's verification on a headless dedicated server: the seared duct (filtered fluid I/O) and
 * the seared chute (item I/O) are valid smeltery wall blocks, and each moves only what it is
 * supposed to. Also covers issue #470: the core itself exposes that same melting inventory as an item
 * handler, so a hopper needs no chute planted in the wall to feed it.
 *
 * <p>The duct and chute are a maintainer-approved deviation from the usual 1.12 parity rule -- the
 * 1.12 generation has only the plain drain -- so their semantics come from the 1.20 clone's {@code
 * DuctBlockEntity}/{@code DuctTankWrapper}/{@code DuctItemHandler} and {@code
 * SmelteryInputOutputBlockEntity.ChuteBlockEntity} (NOTICE.md). The core's own item handler, by
 * contrast, is 1.12 parity itself: upstream's {@code TileMultiblock} extends Mantle's {@code
 * TileInventory}, so a hopper on the core has always worked in the 1.12 generation with no separate
 * port block required (NOTICE.md).
 *
 * <p>Fixtures come from {@link SmelteryGameTests}: {@code buildWalls} then a substituted wall block,
 * the same shape {@link SearedGlassGameTests} uses. Lava and water stand in for two molten metals so
 * the filter assertions need nothing but vanilla buckets.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryIoGameTests {
    /** The +X wall block on the core's own layer: not the tank slot, not the core's slot. */
    private static final BlockPos DUCT = new BlockPos(2, 2, 1);
    /** The +Z wall block on the core's own layer. */
    private static final BlockPos CHUTE = new BlockPos(1, 2, 2);
    /**
     * #470: the open exterior corner north of the core -- {@link SmelteryGameTests#buildWalls} never
     * fills a corner, so it stays air, in bounds, and face-adjacent to {@link
     * SmelteryGameTests#CORE_POS} without needing a chute in the way.
     */
    private static final BlockPos HOPPER = new BlockPos(0, 2, 0);

    private static final int SOME = 500;

    // ------------------------------------------------------------------ structure

    /** Both blocks are valid smeltery wall blocks, the way upstream 1.20 tags them into SMELTERY_WALL. */
    @GameTest(template = "smeltery")
    public static void aDuctAndAChuteAreValidWallBlocks(GameTestHelper helper) {
        BlockPos core = buildSmelteryWithIo(helper);

        SmelteryStructure structure = helper.<SmelteryControllerBlockEntity>getBlockEntity(core).structure();
        helper.assertTrue(structure != null,
                "expected a duct and a chute in the walls to still let the smeltery form: "
                        + helper.<SmelteryControllerBlockEntity>getBlockEntity(core).lastResult().getString());
        helper.succeed();
    }

    /** Neither exposes the smeltery until a scan has claimed it, the same as the drain. */
    @GameTest(template = "smeltery")
    public static void ioBlocksOutsideAStructureExposeNothing(GameTestHelper helper) {
        helper.setBlock(DUCT, ForgeweaveBlocks.SEARED_DUCT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(CHUTE, ForgeweaveBlocks.SEARED_CHUTE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));

        helper.assertTrue(fluids(helper, DUCT) == null, "expected an unclaimed duct to expose no fluid handler");
        helper.assertTrue(items(helper, CHUTE) == null, "expected an unclaimed chute to expose no item handler");
        // The duct's own filter slot is its own inventory, so it exists either way (upstream's duct
        // hands out its item capability unconditionally).
        helper.assertTrue(items(helper, DUCT) != null, "expected the duct's filter slot to exist regardless");
        helper.succeed();
    }

    // ------------------------------------------------------------------ duct

    /** No filter item means no fluid passes at all, in either direction. */
    @GameTest(template = "smeltery")
    public static void aDuctWithNoFilterPassesNothing(GameTestHelper helper) {
        BlockPos core = buildSmelteryWithIo(helper);
        tankOf(helper, core).fill(new FluidStack(Fluids.LAVA, SOME), IFluidHandler.FluidAction.EXECUTE);

        IFluidHandler duct = requireFluids(helper, DUCT);
        helper.assertTrue(duct.drain(SOME, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "expected an unfiltered duct to drain nothing");
        helper.assertValueEqual(duct.fill(new FluidStack(Fluids.WATER, SOME), IFluidHandler.FluidAction.EXECUTE), 0,
                "an unfiltered duct's fill");
        helper.succeed();
    }

    /**
     * A duct moves its filtered fluid and nothing else, in either direction.
     *
     * <p>The smeltery holds one fluid and the filter is swapped rather than the other way round: two
     * fluids in a smeltery is an invitation to alloy them (lava plus water is molten obsidian), which
     * would take the fluid under test out of the tank mid-assertion.
     */
    @GameTest(template = "smeltery")
    public static void aDuctPassesOnlyItsFilteredFluid(GameTestHelper helper) {
        BlockPos core = buildSmelteryWithIo(helper);
        tankOf(helper, core).fill(new FluidStack(Fluids.LAVA, SOME), IFluidHandler.FluidAction.EXECUTE);
        IFluidHandler duct = requireFluids(helper, DUCT);

        // A filter naming something else closes the duct even to what the smeltery does hold.
        setFilter(helper, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(duct.drain(SOME, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "expected a water-filtered duct to leave the smeltery's lava alone");
        helper.assertValueEqual(duct.fill(new FluidStack(Fluids.LAVA, SOME), IFluidHandler.FluidAction.EXECUTE), 0,
                "a water-filtered duct's lava fill");

        setFilter(helper, new ItemStack(Items.LAVA_BUCKET));
        helper.assertValueEqual(duct.fill(new FluidStack(Fluids.WATER, SOME), IFluidHandler.FluidAction.EXECUTE), 0,
                "a lava-filtered duct's water fill");
        helper.assertTrue(duct.drain(new FluidStack(Fluids.WATER, SOME), IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "expected a lava-filtered duct to refuse to drain water");

        FluidStack drained = duct.drain(SOME, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drained.getFluid() == Fluids.LAVA, "expected the duct to drain lava, got " + drained);
        helper.assertValueEqual(drained.getAmount(), SOME, "drained lava");
        helper.assertValueEqual(duct.fill(new FluidStack(Fluids.LAVA, SOME), IFluidHandler.FluidAction.EXECUTE), SOME,
                "a lava-filtered duct's lava fill");
        helper.succeed();
    }

    /** The filter slot takes only a filled fluid container, upstream's {@code DuctItemHandler#isItemValid}. */
    @GameTest(template = "smeltery")
    public static void aDuctFilterTakesOnlyAFilledContainer(GameTestHelper helper) {
        buildSmelteryWithIo(helper);
        IItemHandler filter = requireItems(helper, DUCT);

        helper.assertTrue(!filter.insertItem(0, new ItemStack(Items.STICK), false).isEmpty(),
                "expected the duct filter to refuse a stick");
        helper.assertTrue(!filter.insertItem(0, new ItemStack(Items.BUCKET), false).isEmpty(),
                "expected the duct filter to refuse an empty bucket");
        helper.assertTrue(filter.insertItem(0, new ItemStack(Items.WATER_BUCKET), false).isEmpty(),
                "expected the duct filter to take a water bucket");
        helper.succeed();
    }

    // ------------------------------------------------------------------ chute

    /** A chute is the smeltery's melting inventory as seen from outside the walls. */
    @GameTest(template = "smeltery")
    public static void aChuteInsertsItemsForMelting(GameTestHelper helper) {
        BlockPos core = buildSmelteryWithIo(helper);
        IItemHandler chute = requireItems(helper, CHUTE);

        helper.assertTrue(chute.getSlots() > 0, "expected the chute to expose the melting slots");
        helper.assertTrue(chute.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                "expected the chute to take an iron ingot");
        helper.assertTrue(helper.<SmelteryControllerBlockEntity>getBlockEntity(core).meltingItems().stream()
                        .anyMatch(stack -> stack.is(Items.IRON_INGOT)),
                "expected the ingot to land in the melting inventory");
        helper.succeed();
    }

    /** Nothing the smeltery cannot melt gets in, matching the melting inventory's own rule. */
    @GameTest(template = "smeltery")
    public static void aChuteRefusesWhatCannotMelt(GameTestHelper helper) {
        buildSmelteryWithIo(helper);
        IItemHandler chute = requireItems(helper, CHUTE);

        helper.assertTrue(!chute.insertItem(0, new ItemStack(Items.STICK), false).isEmpty(),
                "expected the chute to refuse a stick");
        helper.succeed();
    }

    /** Items go back out the way they came in, upstream's chute being plain two-way item I/O. */
    @GameTest(template = "smeltery")
    public static void aChuteExtractsItemsBackOut(GameTestHelper helper) {
        BlockPos core = buildSmelteryWithIo(helper);
        IItemHandler chute = requireItems(helper, CHUTE);
        chute.insertItem(0, new ItemStack(Items.IRON_INGOT), false);

        ItemStack taken = chute.extractItem(0, 1, false);
        helper.assertTrue(taken.is(Items.IRON_INGOT), "expected the chute to hand the ingot back, got " + taken);
        helper.assertTrue(helper.<SmelteryControllerBlockEntity>getBlockEntity(core).meltingItems().stream()
                        .allMatch(ItemStack::isEmpty),
                "expected the melting inventory to be empty again");
        helper.succeed();
    }

    // ------------------------------------------------------------------ core (#470)

    /**
     * The core is its own melting inventory's item handler regardless of whether a structure has
     * formed yet -- unlike the chute (which has no core to re-expose until a scan claims it, see
     * {@link #ioBlocksOutsideAStructureExposeNothing}), the core block entity always exists. Zero
     * slots while unformed is the correct answer (nothing to feed into yet), not a missing handler.
     */
    @GameTest(template = "smeltery")
    public static void anUnformedCoreExposesAnEmptyItemHandler(GameTestHelper helper) {
        BlockPos core = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        IItemHandler handler = requireItems(helper, core);
        helper.assertValueEqual(handler.getSlots(), 0, "expected an unformed core's melting inventory to have no slots");
        helper.succeed();
    }

    /** Once formed, the core behaves exactly like a chute planted in its own wall (#277). */
    @GameTest(template = "smeltery")
    public static void theCoreInsertsAndRefusesForMeltingDirectly(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos core = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        IItemHandler handler = requireItems(helper, core);

        helper.assertTrue(handler.getSlots() > 0, "expected the formed core to expose the melting slots");
        helper.assertTrue(!handler.insertItem(0, new ItemStack(Items.STICK), false).isEmpty(),
                "expected the core to refuse a stick");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                "expected the core to take an iron ingot");
        helper.assertTrue(helper.<SmelteryControllerBlockEntity>getBlockEntity(core).meltingItems().stream()
                        .anyMatch(stack -> stack.is(Items.IRON_INGOT)),
                "expected the ingot to land in the melting inventory");
        helper.succeed();
    }

    /**
     * The literal claim in #470's title: a real hopper facing into the core pushes its own contents
     * into the melting inventory on its own, exactly as it would push into any other container --
     * this is what was missing before the core registered an item handler capability at all.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aHopperPushesItemsIntoTheCore(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos core = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        helper.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
        helper.<HopperBlockEntity>getBlockEntity(HOPPER).setItem(0, new ItemStack(Items.IRON_INGOT));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.<SmelteryControllerBlockEntity>getBlockEntity(core).meltingItems().stream()
                                .anyMatch(stack -> stack.is(Items.IRON_INGOT)),
                        "expected the hopper to have pushed the ingot into the melting inventory"))
                .thenExecute(() -> helper.assertTrue(helper.<HopperBlockEntity>getBlockEntity(HOPPER).isEmpty(),
                        "expected the hopper to have emptied itself into the core"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ fixtures

    /** {@link SmelteryGameTests#buildWalls} with a duct and a chute substituted into two wall slots. */
    private static BlockPos buildSmelteryWithIo(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.setBlock(DUCT, ForgeweaveBlocks.SEARED_DUCT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(CHUTE, ForgeweaveBlocks.SEARED_CHUTE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        return SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
    }

    /** Replaces whatever is in the duct's filter slot with {@code container}. */
    private static void setFilter(GameTestHelper helper, ItemStack container) {
        IItemHandler filter = requireItems(helper, DUCT);
        filter.extractItem(0, 1, false);
        helper.assertTrue(filter.insertItem(0, container, false).isEmpty(),
                "expected the duct filter to take " + container);
    }

    private static dev.gkissel.forgeweave.block.SmelteryTank tankOf(GameTestHelper helper, BlockPos core) {
        return helper.<SmelteryControllerBlockEntity>getBlockEntity(core).tank();
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), null);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null);
    }

    private static IFluidHandler requireFluids(GameTestHelper helper, BlockPos pos) {
        IFluidHandler handler = fluids(helper, pos);
        helper.assertTrue(handler != null, "expected a fluid handler at " + pos);
        return handler;
    }

    private static IItemHandler requireItems(GameTestHelper helper, BlockPos pos) {
        IItemHandler handler = items(helper, pos);
        helper.assertTrue(handler != null, "expected an item handler at " + pos);
        return handler;
    }
}
