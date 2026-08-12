package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Covers docs/SCOPE.md M2 issue #100's verification on a headless dedicated server: molten gold
 * poured over a crafted part makes that part's cast, the cast survives casting a metal part, the
 * basin casts a block, and the faucet moves upstream's exact amounts.
 *
 * <p>Every test builds the same real rig -- a seared tank, a faucet on its side, a casting block
 * underneath -- and starts the pour by activating the faucet, so what is exercised is the whole
 * chain (faucet transaction, casting fluid handler, recipe lookup, scheduled cooling tick) rather
 * than any one piece in isolation. The one exception is the basin, which is filled through its own
 * capability so the test does not also spend 216 ticks watching nine faucet transactions.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CastingGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    // #183: the rig a player actually builds -- a formed smeltery with a drain in its wall, a faucet
    // on the outside of that drain, and a casting block under the faucet. Positions are relative to
    // SmelteryGameTests' 1x1x2 minimum structure (core (0,2,1), wall tank (1,2,0)).
    private static final BlockPos DRAIN = new BlockPos(1, 2, 2);
    private static final BlockPos DRAIN_FAUCET = new BlockPos(1, 2, 3);
    private static final BlockPos DRAIN_CASTING = new BlockPos(1, 1, 3);

    /**
     * #183 regression: right-click the faucet hanging off a smeltery drain and the molten metal has
     * to reach the basin underneath. Every other casting test feeds the faucet from a seared tank,
     * so nothing covered the drain -- the only way fluid leaves a smeltery, and so the only way a
     * player ever fills a basin.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aFaucetOnASmelteryDrainPoursIntoTheBasin(GameTestHelper helper) {
        CastingBlockEntity basin = drainRig(helper, ForgeweaveBlocks.CASTING_BASIN.get());

        helper.useBlock(DRAIN_FAUCET, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.succeedWhen(() -> helper.assertTrue(!basin.tank().isEmpty(),
                "expected molten gold to have reached the basin"));
    }

    /**
     * #183, the order a player actually builds in: the smeltery is put up and formed first, and only
     * then is a wall block swapped for a drain. The drain is not next to the core, so nothing tells
     * the core to look at its walls again.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aDrainAddedToAFormedSmelteryStillPours(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the smeltery to form first: " + core.lastResult().getString());
        core.tank().fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), 4000), IFluidHandler.FluidAction.EXECUTE);

        // Only now the drain, the faucet and the basin -- the smeltery is already built and idle.
        helper.setBlock(DRAIN, ForgeweaveBlocks.SEARED_DRAIN.get());
        helper.setBlock(DRAIN_CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        helper.setBlock(DRAIN_FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.NORTH));
        CastingBlockEntity basin = helper.getBlockEntity(DRAIN_CASTING);

        // A few ticks between building and right-clicking, as a player takes.
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.useBlock(DRAIN_FAUCET, helper.makeMockPlayer(GameType.SURVIVAL)))
                .thenWaitUntil(() -> helper.assertTrue(!basin.tank().isEmpty(),
                        "expected molten gold to have reached the basin"))
                .thenSucceed();
    }

    /** A smeltery full of molten gold, a drain in its wall, a faucet on the drain, {@code casting} below it. */
    private static CastingBlockEntity drainRig(GameTestHelper helper, net.minecraft.world.level.block.Block casting) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.setBlock(DRAIN, ForgeweaveBlocks.SEARED_DRAIN.get());
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the drain smeltery to form: " + core.lastResult().getString());
        core.tank().fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), 4000), IFluidHandler.FluidAction.EXECUTE);

        helper.setBlock(DRAIN_CASTING, casting);
        helper.setBlock(DRAIN_FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(DRAIN_CASTING);
    }

    /**
     * SCOPE.md M2's acceptance step 2: "pour molten gold over a crafted part to create a reusable
     * gold cast". Upstream consumes the part and leaves the fresh cast in the input slot.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pouringGoldOverAPartCreatesItsCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.GOLD.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PICKAXE_HEAD.get()),
                    "expected the finished cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the part is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    /**
     * The other half of "casts are gold-only and reusable (pure parity)": casting a metal part
     * leaves the cast exactly where it was, ready for the next pour.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aCastSurvivesCastingAndProducesTheMetalPart(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                    "expected an iron pickaxe head, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to carry the iron material");
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_PICKAXE_HEAD.get()),
                    "expected the cast to survive its own casting cycle");
        });
    }

    /**
     * Basin casting: no cast at all, one block's worth of fluid, one metal block out. Filled through
     * the capability rather than the faucet -- see the class javadoc.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theBasinCastsAMetalBlock(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        CastingBlockEntity basin = helper.getBlockEntity(CASTING);

        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.UP);
        helper.assertTrue(handler != null, "expected the basin to expose a fluid handler");
        int filled = handler.fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), 4000),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(filled, 1296, "a gold block's worth of fluid, and no more");

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.GOLD_BLOCK),
                "expected a gold block, found " + basin.output()));
    }

    /**
     * #183 regression: the basin fed the way a player feeds it -- a faucet above it, one
     * {@value FaucetBlockEntity#TRANSACTION_AMOUNT} mB transaction at a time -- rather than through
     * one capability call that hands it the whole recipe amount at once.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void theBasinCastsABlockFromAPouredFaucet(GameTestHelper helper) {
        CastingBlockEntity basin = rig(helper, ForgeweaveBlocks.CASTING_BASIN.get(), ForgeweaveFluids.GOLD.still().get());
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(basin.output().is(Items.GOLD_BLOCK),
                "expected a gold block, found " + basin.output()));
    }

    /**
     * #183 regression: a basin that was poured into, then reloaded from disk, keeps taking fluid and
     * still finishes. A block entity's {@code loadAdditional} runs before it has a level, so nothing
     * resolved from a recipe can be restored there.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void aPartlyPouredBasinStillFinishesAfterAReload(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_BASIN.get());
        fill(helper, new FluidStack(ForgeweaveFluids.GOLD.still().get(), FaucetBlockEntity.TRANSACTION_AMOUNT));

        helper.startSequence()
                .thenExecute(() -> reload(helper, CASTING))
                .thenIdle(2)
                .thenExecute(() -> helper.assertValueEqual(
                        fill(helper, new FluidStack(ForgeweaveFluids.GOLD.still().get(), 4000)),
                        1296 - FaucetBlockEntity.TRANSACTION_AMOUNT, "the rest of the gold block still fits"))
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.<CastingBlockEntity>getBlockEntity(CASTING).output().is(Items.GOLD_BLOCK),
                        "expected a gold block after the reload"))
                .thenSucceed();
    }

    /**
     * #186 regression: the other half of the same defect -- a table left holding a partial pour by a
     * source that ran dry must not be sealed shut by a reload, with its cast trapped inside.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void aPartlyPouredTableGivesItsCastBackAfterAReload(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        insert(helper, helper.getBlockEntity(CASTING), new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        fill(helper, new FluidStack(ForgeweaveFluids.IRON.still().get(), 72));

        helper.startSequence()
                .thenExecute(() -> reload(helper, CASTING))
                .thenIdle(2)
                .thenExecute(() -> helper.assertValueEqual(
                        fill(helper, new FluidStack(ForgeweaveFluids.IRON.still().get(), 4000)), 72,
                        "the rest of the ingot still fits"))
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.<CastingBlockEntity>getBlockEntity(CASTING).output().is(Items.IRON_INGOT),
                        "expected an iron ingot after the reload"))
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    helper.useBlock(CASTING, player);
                    helper.useBlock(CASTING, player);
                    helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)),
                            "expected the ingot back");
                    helper.assertTrue(player.getInventory().contains(new ItemStack(ForgeweaveItems.CAST_INGOT.get())),
                            "expected the cast back");
                })
                .thenSucceed();
    }

    /** Pours into the casting block through its own capability, the way a faucet does. */
    private static int fill(GameTestHelper helper, FluidStack fluid) {
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(CASTING), Direction.UP);
        helper.assertTrue(handler != null, "expected the casting block to expose a fluid handler");
        return handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * Puts the block entity through the exact round trip a chunk load does: saved to NBT, rebuilt by
     * {@link BlockEntity#loadStatic} with no level, and only then handed one.
     */
    private static void reload(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        CompoundTag saved = level.getBlockEntity(absolute).saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(absolute);
        BlockEntity reloaded = BlockEntity.loadStatic(absolute, level.getBlockState(absolute), saved,
                level.registryAccess());
        helper.assertTrue(reloaded != null, "expected the block entity to load back");
        level.setBlockEntity(reloaded);
    }

    /**
     * #186 regression: the whole right-click path through the block, not {@link
     * CastingBlockEntity#interact} directly -- a cast goes in, comes back out, and the finished
     * result comes out after it.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rightClickingTheTablePutsItemsInAndTakesThemBackOut(GameTestHelper helper) {
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        CastingBlockEntity table = helper.getBlockEntity(CASTING);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));

        helper.useBlock(CASTING, player);
        helper.assertTrue(table.input().is(ForgeweaveItems.CAST_INGOT.get()),
                "expected the right-click to put the cast in, found " + table.input());

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(CASTING, player);
        helper.assertTrue(table.input().isEmpty(), "expected the cast to come back out, found " + table.input());
        helper.assertTrue(player.getInventory().contains(new ItemStack(ForgeweaveItems.CAST_INGOT.get())),
                "expected the cast in the player's inventory");
        helper.succeed();
    }

    /** #186 regression, second half: a finished result is retrievable by right-clicking too. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rightClickingTheTableTakesOutAFinishedResult(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        faucet(helper).activate();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(table.output().is(Items.IRON_INGOT),
                        "expected an iron ingot, found " + table.output()))
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    helper.useBlock(CASTING, player);
                    helper.assertTrue(table.output().isEmpty(),
                            "expected the ingot to come back out, found " + table.output());
                    helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)),
                            "expected the ingot in the player's inventory");
                })
                .thenSucceed();
    }

    /**
     * Upstream's two faucet constants: a transaction leaves the source in one go
     * ({@code TRANSACTION_AMOUNT}, one ingot) and reaches the target {@code LIQUID_TRANSFER} mB at a
     * time. Both are checked on the very tick the pour starts, before anything else can move.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void theFaucetMovesUpstreamsExactAmounts(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        SearedTankBlockEntity tank = helper.getBlockEntity(TANK);
        int before = tank.tank().getFluidAmount();

        FaucetBlockEntity faucet = faucet(helper);
        faucet.activate();

        helper.assertValueEqual(before - tank.tank().getFluidAmount(), FaucetBlockEntity.TRANSACTION_AMOUNT,
                "one ingot leaves the tank the moment the pour starts");
        helper.assertValueEqual(table.tank().getFluidAmount(), FaucetBlockEntity.LIQUID_TRANSFER,
                "and only one step of it has reached the table");
        helper.assertValueEqual(faucet.buffered().getAmount(),
                FaucetBlockEntity.TRANSACTION_AMOUNT - FaucetBlockEntity.LIQUID_TRANSFER, "the rest is in the faucet");

        // An ingot cast wants exactly one ingot, so the faucet stops itself after this transaction
        // rather than draining the tank dry.
        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(Items.IRON_INGOT), "expected an iron ingot, found " + table.output());
            helper.assertValueEqual(tank.tank().getFluidAmount(), before - FaucetBlockEntity.TRANSACTION_AMOUNT,
                    "exactly one ingot left the tank in total");
            helper.assertFalse(faucet.isPouring(), "and the faucet stopped once the table stopped accepting");
        });
    }

    /**
     * #200 regression: the packet that ends a pour has to actually say so. A faucet's whole state is
     * its buffered fluid, so once that emptied it saved nothing, {@link
     * net.minecraft.world.level.block.entity.BlockEntity#getUpdateTag} returned an empty tag, and
     * NeoForge's {@code IBlockEntityExtension#onDataPacket} throws empty tags away -- leaving every
     * client stuck on the last mid-pour state it heard about, drawing the stream forever.
     *
     * <p>Checked at the level it broke: the tag really leaving the block entity, and what happens
     * when a faucet that is already mid-pour is handed it -- which is exactly a client's situation.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aFinishedPourTellsClientsToStopDrawingTheStream(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveBlocks.CASTING_TABLE.get(), ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        FaucetBlockEntity faucet = faucet(helper);
        faucet.activate();

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BlockPos absolute = helper.absolutePos(FAUCET);
        CompoundTag midPour = faucet.getUpdateTag(registries);
        helper.assertFalse(midPour.isEmpty(), "a pouring faucet has something to tell clients");

        helper.succeedWhen(() -> {
            helper.assertFalse(faucet.isPouring(), "the faucet stops once the table stops accepting");
            CompoundTag idle = faucet.getUpdateTag(registries);
            helper.assertFalse(idle.isEmpty(),
                    "an idle faucet's update tag must not be empty -- an empty one is dropped unread");

            // A client already holds the mid-pour state when the idle packet lands on top of it.
            FaucetBlockEntity clientSide = new FaucetBlockEntity(absolute, helper.getLevel().getBlockState(absolute));
            clientSide.loadWithComponents(midPour, registries);
            helper.assertTrue(clientSide.isPouring(), "expected the client to have been drawing a stream");
            clientSide.loadWithComponents(idle, registries);
            helper.assertFalse(clientSide.isPouring(), "and to stop once the pour-ended update arrives");
            helper.assertTrue(clientSide.buffered().isEmpty(), "with nothing left for the renderer to draw");
        });
    }

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting block below. */
    private static CastingBlockEntity rig(GameTestHelper helper, net.minecraft.world.level.block.Block casting, Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, casting);
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    private static FaucetBlockEntity faucet(GameTestHelper helper) {
        return helper.getBlockEntity(FAUCET);
    }

    /** Puts {@code stack} in the casting block the way a player does -- through the real right-click path. */
    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Item expected = stack.getItem();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(casting.input().is(expected), "expected the right-click to put the " + expected + " in");
    }
}
