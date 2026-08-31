package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryCore;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #845's own test strategy: the End and Deep Core melt at their stated multipliers, the
 * pour-to-transform mechanic runs both steps (Nether -> End -> Deep), pouring the wrong fluid or the
 * right fluid on the wrong tier is a no-op, the cost is consumed exactly once, and a transformed core
 * keeps its saved state rather than dropping the smeltery.
 *
 * <p>{@link #pouringDragonBreathOverANetherCoreTransformsItIntoAnEndCore} is the one test that goes
 * through a real {@link dev.gkissel.forgeweave.block.FaucetBlockEntity} above the core -- proving the
 * chosen mechanism (the core block entity's own {@link
 * SmelteryControllerBlockEntity#transformHandler()} fluid-handler capability, reached through the
 * faucet's existing "pour into whatever fluid handler is below it" plumbing, exactly like the drain
 * and the casting table) actually works end to end. Every other test drives the same capability
 * directly, the same determinism tradeoff {@link SmelteryMeltingGameTests} already makes throughout
 * (see e.g. its {@code aFinishedMeltThatCannotFitIsMarkedStalled}).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryCoreTransformGameTests {

    /**
     * SCOPE.md M2 acceptance step 4's pattern, extended to the top two tiers: the same iron ore that
     * yields 216 mB under a Standard Core (1.5x) and 288 mB under a Nether Core (2x) yields 360 mB
     * under an End Core (2.5x, issue #845).
     */
    @GameTest(template = "smeltery", timeoutTicks = 3200)
    public static void ironOreMeltsAtEndCoresTwoAndAHalfTimesYield(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper, ForgeweaveBlocks.END_CORE.get());
        insert(helper, core);

        helper.succeedWhen(() -> assertTankHolds(helper, core,
                (int) (MeltingRecipe.VALUE_INGOT * SmelteryCore.END.yieldMultiplier())));
    }

    /** As above, the Deep Core's 3x (issue #845's top tier). */
    @GameTest(template = "smeltery", timeoutTicks = 3200)
    public static void ironOreMeltsAtDeepCoresThreeTimesYield(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper, ForgeweaveBlocks.DEEP_CORE.get());
        insert(helper, core);

        helper.succeedWhen(() -> assertTankHolds(helper, core,
                (int) (MeltingRecipe.VALUE_INGOT * SmelteryCore.DEEP.yieldMultiplier())));
    }

    /**
     * The chosen mechanism, proven end to end: a formed Nether Core smeltery with a real faucet
     * sitting directly above the core, fed from a seared tank of molten dragon breath. Right-clicking
     * the faucet is enough -- no bespoke "pour onto any block" code exists, this is the exact same
     * faucet -> fluid-handler plumbing the drain and the casting table already use, just with the
     * core itself as the target this time.
     *
     * <p>Built on a 1-tall interior ({@link #transformRig}) so the block directly above the core is
     * open air rather than another course of wall bricks, which is where the faucet has to sit.
     * 1008 mB (seven 144 mB faucet transactions) is comfortably past the recipe's 1000 mB threshold;
     * the faucet trickles 6 mB/tick, so budget roughly 168 ticks of pouring.
     */
    @GameTest(template = "smeltery", timeoutTicks = 400)
    public static void pouringDragonBreathOverANetherCoreTransformsItIntoAnEndCore(GameTestHelper helper) {
        BlockPos corePos = transformRig(helper, ForgeweaveBlocks.NETHER_CORE.get(), ForgeweaveFluids.DRAGON_BREATH.still().get(), 1008);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.useBlock(TRANSFORM_FAUCET, helper.makeMockPlayer(GameType.SURVIVAL)))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(corePos).is(ForgeweaveBlocks.END_CORE.get()),
                        "expected the Nether Core to have become an End Core"))
                .thenSucceed();
    }

    /**
     * The second step of the ladder, driven directly for determinism (see the class javadoc): deep
     * blood over an End Core yields a Deep Core.
     */
    @GameTest(template = "smeltery")
    public static void pouringDeepBloodOverAnEndCoreTransformsItIntoADeepCore(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = formedSmeltery(helper, ForgeweaveBlocks.END_CORE.get(), 2);

        core.transformHandler().fill(new FluidStack(ForgeweaveFluids.DEEP_BLOOD.still().get(), 2000), IFluidHandler.FluidAction.EXECUTE);

        helper.assertTrue(helper.getBlockState(SmelteryGameTests.CORE_POS).is(ForgeweaveBlocks.DEEP_CORE.get()),
                "expected the End Core to have become a Deep Core");
        helper.succeed();
    }

    /** Dragon breath is the End tier's fluid, not the Standard tier's -- pouring it there is a no-op. */
    @GameTest(template = "smeltery")
    public static void pouringDragonBreathOverAStandardCoreDoesNothing(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = formedSmeltery(helper, ForgeweaveBlocks.STANDARD_CORE.get(), 2);

        int accepted = core.transformHandler().fill(new FluidStack(ForgeweaveFluids.DRAGON_BREATH.still().get(), 1500),
                IFluidHandler.FluidAction.EXECUTE);

        helper.assertValueEqual(accepted, 0, "expected the Standard Core to refuse dragon breath outright");
        helper.assertTrue(helper.getBlockState(SmelteryGameTests.CORE_POS).is(ForgeweaveBlocks.STANDARD_CORE.get()),
                "expected the Standard Core to still be a Standard Core");
        helper.succeed();
    }

    /** Deep blood is the Deep tier's fluid, not the Nether tier's -- pouring it there is a no-op too. */
    @GameTest(template = "smeltery")
    public static void pouringDeepBloodOverANetherCoreDoesNothing(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = formedSmeltery(helper, ForgeweaveBlocks.NETHER_CORE.get(), 2);

        int accepted = core.transformHandler().fill(new FluidStack(ForgeweaveFluids.DEEP_BLOOD.still().get(), 3000),
                IFluidHandler.FluidAction.EXECUTE);

        helper.assertValueEqual(accepted, 0, "expected the Nether Core to refuse deep blood outright");
        helper.assertTrue(helper.getBlockState(SmelteryGameTests.CORE_POS).is(ForgeweaveBlocks.NETHER_CORE.get()),
                "expected the Nether Core to still be a Nether Core");
        helper.succeed();
    }

    /**
     * Pouring well past the 1000 mB threshold in one call still only transforms the core once: the
     * fresh End Core has no {@code CoreTransformRecipe} row of its own for dragon breath, so a second
     * helping of the exact same fluid that just transformed it does nothing further.
     */
    @GameTest(template = "smeltery")
    public static void theTransformCostIsConsumedExactlyOnce(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = formedSmeltery(helper, ForgeweaveBlocks.NETHER_CORE.get(), 2);

        core.transformHandler().fill(new FluidStack(ForgeweaveFluids.DRAGON_BREATH.still().get(), 1500), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(helper.getBlockState(SmelteryGameTests.CORE_POS).is(ForgeweaveBlocks.END_CORE.get()),
                "expected the first pour to have produced an End Core");

        SmelteryControllerBlockEntity endCore = helper.getBlockEntity(SmelteryGameTests.CORE_POS);
        int acceptedAgain = endCore.transformHandler()
                .fill(new FluidStack(ForgeweaveFluids.DRAGON_BREATH.still().get(), 1500), IFluidHandler.FluidAction.EXECUTE);

        helper.assertValueEqual(acceptedAgain, 0, "expected the now-End Core to refuse a second helping of dragon breath");
        helper.assertTrue(helper.getBlockState(SmelteryGameTests.CORE_POS).is(ForgeweaveBlocks.END_CORE.get()),
                "expected the core to still be exactly an End Core, not anything further");
        helper.succeed();
    }

    /**
     * Issue #845's structure-survival requirement: a transformed core keeps what was actually inside
     * it, not just a freshly-rescanned empty shell. Molten iron already in the tank and the lava
     * burning in the wall tank both carry across the Nether Core -> End Core swap.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aTransformedCoreKeepsItsTankContentsAndFuelState(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper, ForgeweaveBlocks.NETHER_CORE.get());
        core.tank().fill(new FluidStack(ForgeweaveFluids.IRON.still().get(), 500), IFluidHandler.FluidAction.EXECUTE);

        core.transformHandler().fill(new FluidStack(ForgeweaveFluids.DRAGON_BREATH.still().get(), 1000), IFluidHandler.FluidAction.EXECUTE);

        helper.assertTrue(helper.getBlockState(SmelteryGameTests.CORE_POS).is(ForgeweaveBlocks.END_CORE.get()),
                "expected the core to have transformed into an End Core");
        SmelteryControllerBlockEntity endCore = helper.getBlockEntity(SmelteryGameTests.CORE_POS);
        helper.assertTrue(endCore.isFormed(), "expected the transformed core to still be a formed smeltery");
        helper.assertValueEqual(endCore.tank().getFluidAmount(), 500, "expected the molten iron to have carried across the transform");
        helper.assertTrue(endCore.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                "expected the tank's fluid identity to have carried across too");
        helper.assertValueEqual(endCore.currentTemperature(), Fluids.LAVA.getFluidType().getTemperature(),
                "expected the wall tank's lava fuel to still be feeding the transformed core");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static final BlockPos TRANSFORM_FAUCET = SmelteryGameTests.CORE_POS.above();
    private static final BlockPos TRANSFORM_SOURCE = TRANSFORM_FAUCET.south();

    /** A formed smeltery with {@code coreBlock}, no fuel and no tank contents beyond the wall tank issue #95 requires. */
    private static SmelteryControllerBlockEntity formedSmeltery(GameTestHelper helper, Block coreBlock, int height) {
        SmelteryGameTests.buildWalls(helper, 1, 1, height);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, coreBlock);
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /**
     * {@link SmelteryMeltingGameTests}'s own 1x1x2 rig, reused here for the yield and
     * structure-survival tests -- those need real melting/fuel, which a 1-tall interior does not
     * change, so there is no reason to duplicate that shape at height 1 too.
     */
    private static SmelteryControllerBlockEntity lavaFuelledSmeltery(GameTestHelper helper, Block coreBlock) {
        SmelteryControllerBlockEntity core = formedSmeltery(helper, coreBlock, 2);
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(core.currentTemperature(), Fluids.LAVA.getFluidType().getTemperature(),
                "smeltery temperature with lava in the wall tank");
        return core;
    }

    /**
     * A 1-tall-interior smeltery (so the block directly above the core, {@link #TRANSFORM_FAUCET}, is
     * open air rather than another wall course) with a seared tank of {@code fluid} beside that
     * faucet position and the faucet itself in place, facing the tank -- built but not yet activated.
     */
    private static BlockPos transformRig(GameTestHelper helper, Block coreBlock, Fluid fluid, int amount) {
        formedSmeltery(helper, coreBlock, 1);

        helper.setBlock(TRANSFORM_SOURCE, ForgeweaveBlocks.SEARED_TANK.get());
        SearedTankBlockEntity source = helper.getBlockEntity(TRANSFORM_SOURCE);
        source.tank().fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);

        helper.setBlock(TRANSFORM_FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState().setValue(FaucetBlock.FACING, Direction.SOUTH));
        return SmelteryGameTests.CORE_POS;
    }

    private static void insert(GameTestHelper helper, SmelteryControllerBlockEntity core) {
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_ORE)).isEmpty(),
                "expected iron ore to go into the smeltery");
    }

    private static void assertTankHolds(GameTestHelper helper, SmelteryControllerBlockEntity core, int amount) {
        helper.assertValueEqual(core.tank().getFluidAmount(), amount, "molten iron in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron but the tank holds " + core.tank().getFluid().getFluid());
    }
}
