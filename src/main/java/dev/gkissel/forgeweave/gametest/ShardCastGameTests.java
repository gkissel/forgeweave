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
 * Covers issue #471/T40: upstream 1.12's {@code Shard} extends {@code ToolPart} (itself a {@code
 * MaterialItem}) and never overrides {@code canUseMaterial}, so {@code
 * TinkerSmeltery#registerToolpartMeltingCasting} treats it exactly like any other tool part -- a
 * reusable gold cast plus (behind {@code Config.claycasts}, default on) a single-use clay
 * counterpart, both moulded at the fixed {@code Material.VALUE_Ingot * 2} = 288 mB every cast takes
 * to create ({@code castCreationFluids}/{@code clayCreationFluids}), then pouring a metal through
 * either one at the shard's own {@code Material.VALUE_Shard} = 72 mB cost. Same shape as {@link
 * M3CastingGameTests} and {@link GemCastGameTests}, run against the shard instead.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ShardCastGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /** Pour gold over a shard (any material -- the cast's mould ignores the material component) to get the reusable gold cast. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pouringGoldOverAShardCreatesTheShardCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.GOLD.still().get());
        insert(helper, table, ToolAssembly.part(ForgeweaveItems.SHARD.get(), "iron"));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_SHARD.get()),
                    "expected the finished shard cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the shard is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    /**
     * The gold cast survives casting a metal shard from it and comes out with the poured metal's
     * material.
     *
     * <p>Budget: 72 mB at {@value FaucetBlockEntity#LIQUID_TRANSFER} mB/tick is 12 ticks of pouring
     * plus {@code CastingRecipe#cooldownTicks}' 24 + (769-300)*72/1600 = 45 cooling ticks for iron's
     * 769-degree fluid, a floor of 57; the rest is {@link CastingGameTests#STALL_ALLOWANCE_TICKS} (#269).
     */
    @GameTest(template = "empty", timeoutTicks = 57 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void theShardCastSurvivesCastingAndProducesAMetalShard(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_SHARD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.SHARD.get()),
                    "expected an iron shard, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the shard to carry the iron material");
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_SHARD.get()),
                    "expected the cast to survive its own casting cycle");
        });
    }

    /**
     * #292/#387's clay counterpart: pour molten clay over a shard for the single-use clay cast.
     * Same 288 mB pour as {@link ClayCastGameTests#pouringMoltenClayOverAPartMouldsItsClayCast}'s
     * floor of 144.
     */
    @GameTest(template = "empty", timeoutTicks = 144 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void pouringClayOverAShardCreatesTheClayShardCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.MOLTEN_CLAY.still().get());
        insert(helper, table, ToolAssembly.part(ForgeweaveItems.SHARD.get(), "iron"));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CLAY_CASTS.get("cast_shard").get()),
                    "expected the finished clay shard cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the shard is consumed, so nothing lands in the output slot");
        });
    }

    /**
     * Unlike the gold cast, the clay cast is single-use: it is consumed producing the metal shard.
     * Same 57-tick floor as {@link #theShardCastSurvivesCastingAndProducesAMetalShard}.
     */
    @GameTest(template = "empty", timeoutTicks = 57 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void theClayShardCastIsConsumedProducingAMetalShard(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.IRON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CLAY_CASTS.get("cast_shard").get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.SHARD.get()),
                    "expected an iron shard, found " + table.output());
            helper.assertTrue(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron")
                            .equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the shard to carry the iron material");
            helper.assertTrue(table.input().isEmpty(), "expected the single-use clay cast to be consumed");
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
