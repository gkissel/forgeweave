package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

/**
 * Issue #286's regression: every molten fluid registers a bucket, matching upstream 1.12 where
 * every smeltery fluid is bucketable ({@code TinkerFluids#registerItems} and
 * {@code MaterialIntegration#preInit} both call {@code FluidRegistry.addBucketForFluid}).
 *
 * <p>Issue #604 adds the other half a player actually reaches for: getting a fluid <em>into</em> a
 * bucket. Playtest alpha.3 item 34.a ("balde vazio na mesa enche com o fluido") failed for water,
 * because #474 shipped upstream's one fluid-agnostic {@code BucketCastingRecipe} as one datapack row
 * per molten metal this mod registers -- and water, which melting ice and snow produces, has no
 * molten-metal row. The casting table is upstream's only by-hand fill-a-bucket path for a smeltery
 * fluid; the drain itself is emptying-only ({@code BlockSmelteryIO#onBlockActivated} calls
 * {@code tryEmptyContainerAndStow}, never {@code interactWithFluidHandler}), and a seared tank is
 * the one block that fills a bucket on a right-click.
 *
 * <p>Both of the #286 tests fail on the pre-#286 code: {@link ForgeweaveFluids} registered no bucket item at
 * all, so {@code Fluid#getBucket} answered {@code Items.AIR} and
 * {@code LiquidBlock#pickupBlock}'s {@code new ItemStack(fluid.getBucket())} came back empty --
 * a molten source block simply could not be picked up.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class FluidBucketGameTests {

    /**
     * The round trip on one representative fluid: fill an empty spot with a molten iron source,
     * pick it up into a bucket, then empty that bucket back and get the source block again. Runs
     * the two real halves of the wiring -- {@code LiquidBlock#pickupBlock} reads
     * {@code Fluid#getBucket} (the {@code BaseFlowingFluid.Properties#bucket} half) and
     * {@code BucketItem#emptyContents} places {@code BucketItem#content} (the {@code new
     * BucketItem(still, ...)} half), so a one-way-only wiring fails here.
     */
    @GameTest(template = "empty")
    public static void moltenIronBucketFillsAndPlacesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(pos);
        LiquidBlock liquid = ForgeweaveFluids.IRON.block().get();

        helper.setBlock(pos, liquid);
        BlockState source = level.getBlockState(absolute);

        ItemStack filled = liquid.pickupBlock(null, level, absolute, source);
        helper.assertTrue(!filled.isEmpty(),
                "expected picking up a molten iron source to hand back a bucket, got an empty stack "
                        + "(the fluid has no bucket wired to Fluid#getBucket)");
        helper.assertTrue(filled.is(ForgeweaveFluids.IRON.bucket().get()),
                "expected a molten iron bucket, got " + BuiltInRegistries.ITEM.getKey(filled.getItem()));
        helper.assertBlockNotPresent(liquid, pos);

        boolean placed = ((BucketItem) filled.getItem()).emptyContents(null, level, absolute, null);
        helper.assertTrue(placed, "expected emptying the molten iron bucket to place the fluid back");
        helper.assertTrue(level.getFluidState(absolute).getType() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron back in the world, got " + level.getFluidState(absolute).getType());
        helper.assertTrue(level.getFluidState(absolute).isSource(),
                "expected the placed molten iron to be a source block, not a flowing one");

        helper.succeed();
    }

    /**
     * Coverage half: every registered molten fluid -- not just the representative one above -- has a
     * non-empty bucket wired both ways, so the next fluid added cannot ship bucketless the way all
     * twenty did before #286.
     */
    @GameTest(template = "empty")
    public static void everyMoltenFluidHasABucket(GameTestHelper helper) {
        List<String> broken = ForgeweaveFluids.all().stream()
                // Empty stack == the fluid answers Items.AIR for its bucket, which is exactly what a
                // failed pickup hands the player. The other two clauses pin the wiring in both
                // directions: fluid -> bucket (pickup) and bucket -> fluid (placing it back).
                .filter(fluid -> new ItemStack(fluid.still().get().getBucket()).isEmpty()
                        || fluid.still().get().getBucket() != fluid.bucket().get()
                        || fluid.bucket().get().content != fluid.still().get())
                .map(ForgeweaveFluids.MoltenMetal::name)
                .toList();

        helper.assertTrue(broken.isEmpty(), "molten fluids without a bucket wired both ways: " + broken);
        helper.succeed();
    }

    // ------------------------------------------------------------------ #604: filling a bucket

    /**
     * The #604 report itself: an empty bucket on a casting table, poured with water, comes out a
     * water bucket. Fails on the pre-#604 per-fluid rows -- there is no {@code bucket_water.json} and
     * there never could be one derived from {@link ForgeweaveFluids}, so the table refused the pour
     * outright and the water stayed stuck in the smeltery.
     *
     * <p>The molten-metal half of the same one row stays covered by {@link
     * CastingGameTests#pouringMoltenIronOverAnEmptyBucketFillsIt}; this is the vanilla-fluid half it
     * could never reach. Budget: 1000 mB at 6 mB/tick is 167 ticks of pouring plus the flat 5-tick cool.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void anEmptyBucketOnACastingTableFillsWithWater(GameTestHelper helper) {
        CastingBlockEntity table = castingRig(helper, Fluids.WATER);
        insert(helper, table, new ItemStack(Items.BUCKET));
        helper.<FaucetBlockEntity>getBlockEntity(FAUCET).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(Items.WATER_BUCKET),
                    "expected a filled water bucket in the output slot, found " + table.output());
            helper.assertTrue(table.input().isEmpty(), "the empty bucket is consumed by the pour");
        });
    }

    /**
     * Upstream's {@code BlockTank#onBlockActivated}: a seared tank right-clicks both ways, so a
     * bucket is filled from it and emptied back into it.
     */
    @GameTest(template = "empty")
    public static void aSearedTankFillsABucketAndTakesItBack(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(1, 1, 1);
        helper.setBlock(tankPos, ForgeweaveBlocks.SEARED_TANK.get());
        SearedTankBlockEntity tank = helper.getBlockEntity(tankPos);
        tank.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        Player player = rightClick(helper, tankPos, new ItemStack(Items.BUCKET));
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET),
                "expected the tank to fill the bucket, hand holds " + player.getMainHandItem());
        helper.assertValueEqual(tank.tank().getFluidAmount(), 0, "millibuckets left in the tank");

        player = rightClick(helper, tankPos, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET),
                "expected the tank to take the water back, hand holds " + player.getMainHandItem());
        helper.assertValueEqual(tank.tank().getFluidAmount(), 1000, "millibuckets back in the tank");
        helper.succeed();
    }

    /**
     * Upstream's {@code BlockSmelteryIO#onBlockActivated}: a filled container right-clicked on a
     * drain is tipped into the smeltery, and an empty one takes nothing back out -- the melt leaves
     * through a faucet, not by hand. The drain had no use handler at all before #604, so a water
     * bucket used against it placed a water block on the wall instead.
     */
    @GameTest(template = "smeltery")
    public static void aDrainTakesABucketInButNeverGivesOneBack(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.setBlock(DRAIN, ForgeweaveBlocks.SEARED_DRAIN.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockPos core = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity smeltery = helper.getBlockEntity(core);
        helper.assertTrue(smeltery.isFormed(), "expected the smeltery to form: " + smeltery.lastResult().getString());

        Player player = rightClick(helper, DRAIN, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET),
                "expected the drain to take the water, hand holds " + player.getMainHandItem());
        helper.assertValueEqual(smeltery.tank().getFluidAmount(), 1000, "millibuckets in the smeltery");

        player = rightClick(helper, DRAIN, new ItemStack(Items.BUCKET));
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET),
                "upstream never fills a bucket off a drain, hand holds " + player.getMainHandItem());
        helper.assertValueEqual(smeltery.tank().getFluidAmount(), 1000, "millibuckets still in the smeltery");
        helper.assertBlock(DRAIN, block -> block == ForgeweaveBlocks.SEARED_DRAIN.get(),
                "expected the drain to still be there rather than a water block over it");
        helper.succeed();
    }

    // ------------------------------------------------------------------ fixtures

    /** The +X wall block on the core's own layer, the slot {@link SmelteryIoGameTests} puts a duct in. */
    private static final BlockPos DRAIN = new BlockPos(2, 2, 1);

    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting table below. */
    private static CastingBlockEntity castingRig(GameTestHelper helper, net.minecraft.world.level.material.Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    /** Puts {@code stack} in the casting table the way a player does -- through the real right-click path. */
    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(casting.input().is(stack.getItem()), "expected the right-click to put the " + stack + " in");
    }

    /** A real block right-click with {@code held} in the main hand; returns the player so the hand can be read. */
    private static Player rightClick(GameTestHelper helper, BlockPos pos, ItemStack held) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos absolute = helper.absolutePos(pos);
        helper.getLevel().getBlockState(absolute).useItemOn(player.getMainHandItem(), helper.getLevel(), player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        return player;
    }
}
