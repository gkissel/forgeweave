package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Covers issue #272's (M3.4-3) vanilla-reachable path: upstream 1.12 ships three {@code CastCustom}
 * metas beyond ingot/nugget -- gem, plate, gear ({@code TinkerSmeltery}). Plate and gear are formed
 * from tag-gated {@code c:plates}/{@code c:gears} items that no vanilla or bundled item carries, so
 * neither has a runnable path here (see the PR body for the upstream citation); the gem cast does,
 * the same {@link M3CastingGameTests} shape run against emerald instead of a tool part: pour gold
 * over an emerald to mould the cast, then pour molten emerald through it to get an emerald back.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class GemCastGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /** SCOPE.md-equivalent acceptance step for #272: pour gold over an emerald, get the gem cast. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pouringGoldOverAnEmeraldCreatesTheGemCast(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.GOLD.still().get());
        insert(helper, table, new ItemStack(Items.EMERALD));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_GEM.get()),
                    "expected the finished gem cast in the input slot, found " + table.input());
            helper.assertTrue(table.output().isEmpty(), "the emerald is consumed, so nothing lands in the output slot");
            helper.assertTrue(table.tank().isEmpty(), "and the pour is spent");
        });
    }

    /**
     * The cast survives casting molten emerald through it and comes back out as a plain emerald --
     * upstream's {@code Material.VALUE_Gem} = 666 mB, this file's own gem cast staying gold-only and
     * reusable like every other cast (no material data component: an emerald isn't a Forgeweave part).
     *
     * <p>Budget: 666 mB at {@value FaucetBlockEntity#LIQUID_TRANSFER} mB/tick = 111 ticks of pouring,
     * plus {@code CastingRecipe#cooldownTicks}' 24 + (999-300)*666/1600 = 314 cooling ticks, a floor
     * of 425. Everything past that is {@link CastingGameTests#STALL_ALLOWANCE_TICKS} (#269).
     */
    @GameTest(template = "empty", timeoutTicks = 425 + CastingGameTests.STALL_ALLOWANCE_TICKS)
    public static void theGemCastSurvivesCastingAndProducesAnEmerald(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.EMERALD.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_GEM.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(Items.EMERALD),
                    "expected an emerald, found " + table.output());
            helper.assertTrue(table.input().is(ForgeweaveItems.CAST_GEM.get()),
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
