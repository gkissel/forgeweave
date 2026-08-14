package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

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
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Covers issue #222's verification for the M3 part cast extension (docs/SCOPE.md M3 issue
 * #151/#159/#160/#161's roster): the same {@link CastingGameTests} coverage the five M1 casts
 * already have, run once against a representative M3 part rather than all twenty -- the casting
 * flow itself (faucet transaction, casting fluid handler, recipe lookup, cooling tick) is already
 * exercised exhaustively there and doesn't vary per part, only the {@code casting_recipe} data does.
 *
 * <p>{@code hammer_head} is the representative: it is one of the M3 roster's {@code
 * LARGE_HEAD_COST} parts (1152 mB, {@code PartBuilderRecipes#LARGE_HEAD_COST}), the size class the
 * issue asks to double-check against upstream's table/basin split. Upstream 1.12 never routes tool
 * parts of any size through the basin -- {@code TinkerRegistry#registerToolPart} always calls
 * {@code addCastForItem}, which only ever lands in the table registry
 * ({@code TinkerRegistry#tableCastRegistry}); the basin is reserved for bare blocks (its own
 * explicit {@code registerBasinCasting} calls in {@code TinkerSmeltery}, e.g. the ingot-to-block
 * recipes {@link CastingGameTests#theBasinCastsAMetalBlock} covers). So every M3 part -- large ones
 * included -- casts at the table, exactly like the M1 set; there is no large-part basin variant to
 * add. Both tests below use {@code station: "table"}, matching every {@code hammer_head_*.json} and
 * {@code cast_hammer_head.json} this issue adds.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class M3CastingGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /** SCOPE.md M2's acceptance step 2, extended to the M3 roster: pour gold over a crafted part, get its cast. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pouringGoldOverAnM3PartCreatesItsCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.GOLD.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.PART_HAMMER_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_HAMMER_HEAD.get()),
                    "expected the finished hammer head cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the part is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    /**
     * The cast survives casting a metal part from it and comes out with the poured metal's material.
     * A large-head-cost part's 1152 mB (see class javadoc) takes longer to pour and cool than the
     * 288 mB M1 tests need -- {@link CastingGameTests#theBasinCastsACobaltBlockViaTheDrainFaucet}
     * sets the same precedent for a cobalt pour of comparable size (1296 mB, 1600 ticks).
     *
     * <p>Budget: 1152 mB = eight 144-mB transactions at 6 mB/tick = 192 ticks of pouring, plus
     * cobalt's 24 + (950-300)*1152/1600 = 492 cooling ticks -- and the part does land on tick 683,
     * measured. Everything past that floor is {@link CastingGameTests#STALL_ALLOWANCE_TICKS}, which
     * is where this test's #269 flake came from: a 1600-tick budget is only 0.79 s of wall clock
     * past the floor on a free-running GameTestServer, and the deadline runs on game time while the
     * pour runs on scheduled block ticks that a chunk can stop receiving.
     */
    @GameTest(template = "empty", timeoutTicks = 683 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void anM3CastSurvivesCastingAndProducesTheMetalPart(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.COBALT.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_HAMMER_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_HAMMER_HEAD.get()),
                    "expected a cobalt hammer head, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "cobalt")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to carry the cobalt material");
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_HAMMER_HEAD.get()),
                    "expected the cast to survive its own casting cycle");
        });
    }

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting table below (CastingGameTests#rig). */
    private static CastingBlockEntity rig(GameTestHelper helper, net.minecraft.world.level.material.Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        Block casting = ForgeweaveBlocks.CASTING_TABLE.get();
        helper.setBlock(CASTING, casting);
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    private static FaucetBlockEntity faucet(GameTestHelper helper) {
        return helper.getBlockEntity(FAUCET);
    }

    /** Puts {@code stack} in the casting table the way a player does -- through the real right-click path. */
    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        Item expected = stack.getItem();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(casting.input().is(expected), "expected the right-click to put the " + expected + " in");
    }
}
