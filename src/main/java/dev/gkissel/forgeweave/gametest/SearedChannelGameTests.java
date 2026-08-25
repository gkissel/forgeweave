package dev.gkissel.forgeweave.gametest;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedChannelBlock;
import dev.gkissel.forgeweave.block.SearedChannelBlock.ChannelConnection;
import dev.gkissel.forgeweave.block.SearedChannelBlockEntity;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #441 (parity audit T9) on a headless dedicated server: a seared channel's connection states,
 * its sideways and downward flow, and its redstone gate.
 *
 * <p>Everything asserted here is upstream 1.12's own behaviour ({@code BlockChannel}, {@code
 * TileChannel}) -- in particular the placement direction, which the 1.20 generation reversed:
 * placing a channel against an existing one makes the <em>existing</em> one output into the new one,
 * so a run built by walking away from the smeltery flows the way it was built.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SearedChannelGameTests {
    private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
    private static final BlockPos NEXT = new BlockPos(1, 2, 2);
    private static final int SOME = 200;

    // ------------------------------------------------------------------ flow

    /** A channel takes fluid on its top and hands it out of an {@code out} side. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aChannelCarriesFluidSideways(GameTestHelper helper) {
        helper.setBlock(SOURCE, connected(Direction.SOUTH, ChannelConnection.OUT));
        helper.setBlock(NEXT, connected(Direction.NORTH, ChannelConnection.IN));

        fillTop(helper, SOURCE, SOME);
        SearedChannelBlockEntity downstream = helper.getBlockEntity(NEXT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!downstream.fluid().isEmpty(),
                        "expected the second channel to have received water"))
                .thenSucceed();
    }

    /** With every side and the bottom shut, the fluid stays put -- a channel is a trough, not a leak. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aChannelWithNoOutputHoldsItsFluid(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());
        helper.setBlock(SOURCE.below(), ForgeweaveBlocks.SEARED_TANK.get());

        fillTop(helper, SOURCE, SOME);
        SearedChannelBlockEntity source = helper.getBlockEntity(SOURCE);
        SearedTankBlockEntity below = helper.getBlockEntity(SOURCE.below());
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(below.tank().isEmpty(),
                            "expected a shut bottom to send nothing down");
                    helper.assertTrue(!source.fluid().isEmpty(), "expected the fluid to stay in the channel");
                })
                .thenSucceed();
    }

    /** A channel takes only as much as its {@value SearedChannelBlockEntity#CAPACITY} mB buffer holds. */
    @GameTest(template = "empty")
    public static void aChannelBuffersOnlyUpstreamsCapacity(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());

        IFluidHandler top = requireTop(helper, SOURCE);
        helper.assertValueEqual(top.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE),
                SearedChannelBlockEntity.CAPACITY, "the channel's accepted fill");
        helper.succeed();
    }

    /** An {@code out} side accepts nothing, so two channels never push fluid back and forth. */
    @GameTest(template = "empty")
    public static void anOutputSideAcceptsNoFluid(GameTestHelper helper) {
        helper.setBlock(SOURCE, connected(Direction.SOUTH, ChannelConnection.OUT));

        IFluidHandler out = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(SOURCE), Direction.SOUTH);
        helper.assertTrue(out != null, "expected an output side to still expose a handler");
        helper.assertValueEqual(out.fill(new FluidStack(Fluids.WATER, SOME), IFluidHandler.FluidAction.EXECUTE), 0,
                "an output side's fill");
        helper.succeed();
    }

    /** The bottom pours into whatever is below, which is how a casting basin gets filled. */
    @GameTest(template = "empty")
    public static void anOpenBottomPoursIntoACastingBasin(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get().defaultBlockState()
                .setValue(SearedChannelBlock.DOWN, true));
        helper.setBlock(SOURCE.below(), ForgeweaveBlocks.CASTING_BASIN.get());

        // Molten gold rather than water: a casting basin only takes a fluid some recipe wants.
        requireTop(helper, SOURCE).fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), SOME),
                IFluidHandler.FluidAction.EXECUTE);
        // #715: driven directly rather than waited on. A fill is locked for the tick it landed in, so
        // the first step only unlocks it and the second is the one that pours.
        SearedChannelBlockEntity channel = helper.getBlockEntity(SOURCE);
        channel.flowStep();
        channel.flowStep();
        CastingBlockEntity basin = helper.getBlockEntity(SOURCE.below());
        helper.assertValueEqual(basin.tank().getFluidAmount(), FaucetBlockEntity.LIQUID_TRANSFER,
                "one flow step's worth of gold poured into the basin");
        helper.succeed();
    }

    // ------------------------------------------------------------------ redstone

    /** Upstream's redstone gate: power opens the bottom, losing power closes it again. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void redstonePowerOpensAndClosesTheBottom(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());
        helper.setBlock(SOURCE.below(), ForgeweaveBlocks.CASTING_BASIN.get());
        helper.assertTrue(!helper.getBlockState(SOURCE).getValue(SearedChannelBlock.DOWN),
                "expected a fresh channel's bottom to be shut");

        helper.setBlock(NEXT, Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.DOWN),
                        "expected redstone power to open the channel's bottom"))
                .thenExecute(() -> helper.setBlock(NEXT, Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(!helper.getBlockState(SOURCE).getValue(SearedChannelBlock.DOWN),
                        "expected losing power to shut the channel's bottom again"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------ placement and clicking

    /**
     * Upstream 1.12's placement direction: the channel already in the world becomes the output and
     * the new one the input. (The 1.20 generation reversed this; 1.12 parity is the default.)
     */
    @GameTest(template = "empty")
    public static void placingAChannelAgainstAnotherMakesTheExistingOneOutput(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());

        placeChannelAgainst(helper, SOURCE, Direction.SOUTH);

        helper.assertTrue(helper.getBlockState(NEXT).getBlock() instanceof SearedChannelBlock,
                "expected a second channel to have been placed");
        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.OUT, "the existing channel's facing side");
        helper.assertValueEqual(helper.getBlockState(NEXT).getValue(SearedChannelBlock.NORTH),
                ChannelConnection.IN, "the new channel's facing side");
        helper.succeed();
    }

    /** Upstream's {@code ItemChannel}: stacking a channel under one opens the upper one's bottom. */
    @GameTest(template = "empty")
    public static void placingAChannelUnderneathOpensTheUpperBottom(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());
        // The game-test arena wraps a 1x1x1 template in barrier blocks; clear the spot to build in.
        helper.setBlock(SOURCE.below(), Blocks.AIR);

        placeChannelAgainst(helper, SOURCE, Direction.DOWN);

        helper.assertTrue(helper.getBlockState(SOURCE.below()).getBlock() instanceof SearedChannelBlock,
                "expected a channel below");
        helper.assertTrue(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.DOWN),
                "expected stacking underneath to open the upper channel's bottom");
        helper.succeed();
    }

    /**
     * Clicking an arm walks it round upstream's cycle. With another channel to output into, one
     * click on the south arm turns it from {@code none} straight to {@code out}, and the channel it
     * faces mirrors that as {@code in}.
     */
    @GameTest(template = "empty")
    public static void clickingAnArmCyclesItsConnection(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());
        helper.setBlock(NEXT, ForgeweaveBlocks.SEARED_CHANNEL.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        clickArm(helper, SOURCE, Direction.SOUTH, player);
        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.OUT, "the clicked arm after one click");
        helper.assertValueEqual(helper.getBlockState(NEXT).getValue(SearedChannelBlock.NORTH),
                ChannelConnection.IN, "the facing channel's mirrored side");

        clickArm(helper, SOURCE, Direction.SOUTH, player);
        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.IN, "the clicked arm after two clicks");

        clickArm(helper, SOURCE, Direction.SOUTH, player);
        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.NONE, "the clicked arm after three clicks");
        helper.succeed();
    }

    /**
     * Issue #595, upstream's {@code TileChannel#interact}: a side with nothing beyond it is not a
     * connection, so the click goes to the downspout instead. Without this a bare channel could only
     * be opened by hitting its 6x4x6 centre.
     */
    @GameTest(template = "empty")
    public static void clickingASideWithNothingBeyondItTogglesTheDownspout(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());
        helper.setBlock(NEXT, Blocks.AIR);
        helper.setBlock(SOURCE.below(), ForgeweaveBlocks.CASTING_BASIN.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        clickArm(helper, SOURCE, Direction.SOUTH, player);

        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.NONE, "a side facing air");
        helper.assertTrue(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.DOWN),
                "expected the click to have fallen through to the downspout");
        helper.succeed();
    }

    /**
     * Upstream's {@code handleBlockUpdate} with {@code didPlace}: putting something that holds fluid
     * beside a channel plumbs it, so a casting table dropped next to a run needs no clicking at all.
     */
    @GameTest(template = "empty")
    public static void placingAFluidHolderBesideAChannelOpensThatSide(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());
        helper.setBlock(NEXT, Blocks.AIR);

        helper.setBlock(NEXT, ForgeweaveBlocks.CASTING_TABLE.get());

        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.OUT, "the side the casting table appeared on");
        helper.succeed();
    }

    /** A side whose target stops holding fluid closes, not only one whose target became air. */
    @GameTest(template = "empty")
    public static void replacingTheNeighbourWithStoneClosesTheConnection(GameTestHelper helper) {
        helper.setBlock(SOURCE, connected(Direction.SOUTH, ChannelConnection.OUT));
        helper.setBlock(NEXT, ForgeweaveBlocks.CASTING_BASIN.get());

        helper.setBlock(NEXT, Blocks.STONE);

        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.NONE, "the connection after its target became solid stone");
        helper.succeed();
    }

    /**
     * Upstream keeps the top face for the downspout even with a channel in hand ({@code facing !=
     * EnumFacing.UP}), so wiring a drop never drops a stray block on the run.
     */
    @GameTest(template = "empty")
    public static void clickingTheTopWithAChannelInHandTogglesRatherThanPlaces(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForgeweaveBlocks.SEARED_CHANNEL.get());
        helper.setBlock(SOURCE.below(), ForgeweaveBlocks.CASTING_BASIN.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.SEARED_CHANNEL.get()));

        BlockPos target = helper.absolutePos(SOURCE);
        helper.useBlock(SOURCE, player,
                new BlockHitResult(Vec3.atLowerCornerOf(target).add(0.5, 0.5, 0.5), Direction.UP, target, false));

        helper.assertTrue(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.DOWN),
                "expected the top click to have opened the downspout");
        helper.succeed();
    }

    /** Breaking what a channel fed closes the connection, rather than leaving it pointing at air. */
    @GameTest(template = "empty")
    public static void breakingTheNeighbourClosesTheConnection(GameTestHelper helper) {
        helper.setBlock(SOURCE, connected(Direction.SOUTH, ChannelConnection.OUT));
        helper.setBlock(NEXT, ForgeweaveBlocks.CASTING_BASIN.get());

        helper.setBlock(NEXT, Blocks.AIR);

        helper.assertValueEqual(helper.getBlockState(SOURCE).getValue(SearedChannelBlock.SOUTH),
                ChannelConnection.NONE, "the connection after its target was broken");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static BlockState connected(Direction side, ChannelConnection connection) {
        return ForgeweaveBlocks.SEARED_CHANNEL.get().defaultBlockState()
                .setValue(SearedChannelBlock.SIDES.get(side), connection);
    }

    private static IFluidHandler requireTop(GameTestHelper helper, BlockPos pos) {
        IFluidHandler handler = handler(helper, pos, Direction.UP);
        helper.assertTrue(handler != null, "expected a channel to expose a fluid handler on its top");
        return handler;
    }

    @Nullable
    private static IFluidHandler handler(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), side);
    }

    private static void fillTop(GameTestHelper helper, BlockPos pos, int amount) {
        requireTop(helper, pos).fill(new FluidStack(Fluids.WATER, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    /** Places a channel item against {@code face} of the channel at {@code pos}, as a player would. */
    private static void placeChannelAgainst(GameTestHelper helper, BlockPos pos, Direction face) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ForgeweaveItems.SEARED_CHANNEL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos target = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(target).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5),
                face, target, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** Right-clicks the arm on {@code side} of a channel, the way a player aiming at it would. */
    private static void clickArm(GameTestHelper helper, BlockPos pos, Direction side, Player player) {
        BlockPos target = helper.absolutePos(pos);
        Vec3 aim = Vec3.atLowerCornerOf(target)
                .add(0.5 + side.getStepX() * 0.45, 0.4, 0.5 + side.getStepZ() * 0.45);
        helper.useBlock(pos, player, new BlockHitResult(aim, side.getOpposite(), target, false));
    }
}
