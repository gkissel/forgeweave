package dev.gkissel.forgeweave.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * The Nether Core's "v2" look ({@link SmelteryControllerBlock#HOT}): lit while the fuel in the wall
 * tank is hotter than {@link SmelteryControllerBlock#HOT_TEMPERATURE}, off otherwise, following the
 * tank within the core's own heartbeat.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class NetherCoreHotGameTests {

    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void magmaInTheTankLightsTheNetherCoresHotLook(GameTestHelper helper) {
        fuelledNetherSmeltery(helper, ForgeweaveFluids.MOLTEN_MAGMA.still().get());

        helper.succeedWhen(() -> helper.assertBlockProperty(SmelteryGameTests.CORE_POS, SmelteryControllerBlock.HOT, true));
    }

    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void lavaIsNotHotEnoughForTheNetherCoresHotLook(GameTestHelper helper) {
        fuelledNetherSmeltery(helper, Fluids.LAVA);

        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> helper.assertBlockProperty(SmelteryGameTests.CORE_POS, SmelteryControllerBlock.HOT, false))
                .thenSucceed();
    }

    @GameTest(template = "smeltery", timeoutTicks = 300)
    public static void drainingTheHotFuelPutsTheNetherCoreBackToItsNormalLook(GameTestHelper helper) {
        SearedTankBlockEntity tank = fuelledNetherSmeltery(helper, ForgeweaveFluids.MOLTEN_MAGMA.still().get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(SmelteryGameTests.CORE_POS, SmelteryControllerBlock.HOT, true))
                .thenExecute(() -> tank.tank().drain(SearedTankBlockEntity.CAPACITY, IFluidHandler.FluidAction.EXECUTE))
                .thenWaitUntil(() -> helper.assertBlockProperty(SmelteryGameTests.CORE_POS, SmelteryControllerBlock.HOT, false))
                .thenSucceed();
    }

    /** A formed 1x1x2 Nether Core smeltery whose wall tank holds a full tank of {@code fuel}. */
    private static SearedTankBlockEntity fuelledNetherSmeltery(GameTestHelper helper, Fluid fuel) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(fuel, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.NETHER_CORE.get());
        return tank;
    }
}
