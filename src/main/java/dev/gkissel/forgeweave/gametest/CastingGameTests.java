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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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
