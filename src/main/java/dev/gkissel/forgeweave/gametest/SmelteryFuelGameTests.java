package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * docs/SCOPE.md M2 issue #97's verification on a headless dedicated server: lava is consumed exactly
 * at the clone's own mB-per-cycle and cycle-duration numbers, an idle-but-fuelled smeltery never
 * touches its tank, and a datapack fuel hotter than lava unlocks a recipe lava cannot reach.
 *
 * <p>The last test leans on the GameTest-only datapack in {@code src/gametest/resources} (see its
 * README): a 5000-degree fuel riding inert {@code minecraft:water}, and issue #96's 1400-degree
 * fixture recipe that no shipped M2 recipe needs and lava alone can never melt.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryFuelGameTests {

    /**
     * Pins the clone constants (NOTICE.md): {@code registerSmelteryFuel(new FluidStack(LAVA, 50), 100)}
     * -- 50 mB drained per cycle, one cycle lasting 100 melt ticks (400 real ticks, {@link
     * SmelteryControllerBlockEntity#MELT_INTERVAL_TICKS}). An iron ore recipe (1872 melt-progress
     * needed, far more than one cycle covers) keeps the smeltery working across both checkpoints:
     * still mid-cycle-one at melt tick 95, into cycle two by melt tick 125.
     *
     * <p>#715: the melt ticks are driven by calling {@link SmelteryControllerBlockEntity#meltTick()}
     * directly (as {@link #aFinishOnlyTickConsumesNoFuel} does) rather than sampling the scheduler
     * at a real-tick offset -- under CI load the scheduled tick lands a cycle late and the second
     * sample read 50 instead of 100. The mB-per-cycle and melt-ticks-per-cycle numbers are what the
     * clone pins; the real-tick cadence is {@code MELT_INTERVAL_TICKS}'s own business.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void lavaIsConsumedAtTheCloneRate(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_ORE)).isEmpty(),
                "expected the iron ore to go into the smeltery");

        meltTicks(helper, core, 95);
        helper.assertValueEqual(drainedLava(helper), 50,
                "lava drained after less than one fuel cycle's worth of melt ticks");

        meltTicks(helper, core, 30); // 125 in total: one cycle finished, a second burn under way.
        helper.assertValueEqual(drainedLava(helper), 100, "lava drained one cycle into the second burn");
        helper.succeed();
    }

    /** Drives {@code count} melt ticks, each of which must find heating work to do. */
    private static void meltTicks(GameTestHelper helper, SmelteryControllerBlockEntity core, int count) {
        for (int i = 0; i < count; i++) {
            helper.assertTrue(core.meltTick(), "expected melt tick " + i + " to still be heating the iron ore");
        }
    }

    /** SCOPE.md M2's idle-tick invariant, the fuel half of it: a formed, fuelled, empty smeltery never drains its tank. */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void noFuelIsConsumedWhileNothingIsMelting(GameTestHelper helper) {
        lavaFuelledSmeltery(helper);

        helper.runAfterDelay(100, () -> {
            helper.assertValueEqual(drainedLava(helper), 0, "an idle smeltery must never drain its fuel tank");
            helper.succeed();
        });
    }

    /**
     * The other half of temperature gating: a datapack fuel hotter than lava melts a recipe lava
     * cannot reach, without any Forgeweave code change -- the M2 acceptance test for issue #97's
     * "hotter fuel unlocks higher-temperature recipes".
     */
    @GameTest(template = "smeltery", timeoutTicks = 1000)
    public static void aHotterDatapackFuelMeltsARecipeLavaCannotReach(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.WATER, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertValueEqual(core.currentTemperature(), 5000, "smeltery temperature with the GameTest superfuel in the wall tank");

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.BLAZE_ROD)).isEmpty(),
                "expected the blaze rod to go into the smeltery");

        helper.succeedWhen(() -> {
            helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten iron from the 1400-degree fixture recipe");
            helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                    "expected molten iron, lava's own 1300 degrees can never reach this recipe");
        });
    }

    /**
     * #287 regression: upstream's {@code TileHeatingStructure#heatItems} only sets its "did we heat
     * something" flag in the temperature-increment branch, never in the branch that finishes a melt.
     * A melt tick whose only work is filling the tank must not also burn a fuel tick -- driven by
     * calling {@link SmelteryControllerBlockEntity#meltTick()} directly rather than waiting on the
     * scheduler, so the assertion sits on the exact finishing tick instead of a timing-dependent
     * sample.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aFinishOnlyTickConsumesNoFuel(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_NUGGET)).isEmpty(),
                "expected the iron nugget to go into the smeltery");

        // Drive the slot right up to (but not through) its finishing tick.
        while (core.meltProgress(0) < 1.0f) {
            core.meltTick();
        }

        int burnTicksBefore = core.fuelBurnTicksRemaining();
        helper.assertTrue(burnTicksBefore > 0, "expected an in-progress burn heading into the finishing tick");

        core.meltTick(); // finish-only tick: fills the tank, nothing left to increment.

        helper.assertValueEqual(core.tank().getFluidAmount(), MeltingRecipe.VALUE_NUGGET,
                "expected the nugget to finish melting");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron in the tank");
        helper.assertValueEqual(core.fuelBurnTicksRemaining(), burnTicksBefore,
                "a finish-only tick must not consume fuel (#287)");
        helper.succeed();
    }

    /**
     * #377: the fuel gauge shows what the wall tank holds even when that is something the smeltery
     * cannot burn, which is what upstream's {@code getFuelDisplay} tail loop does and what makes its
     * "not a valid smeltery fuel" tooltip line reachable. Before this, the display fluid was sourced
     * only from a tank that {@code holdsFuel}, so a tank full of the wrong thing synced as empty and
     * the screen could only say "No fuel found" -- the least useful of the three possible answers.
     *
     * <p>Molten iron is the wrong thing here rather than water, because the GameTest datapack
     * deliberately registers water as a 5000-degree superfuel (see the class javadoc).
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void anUnburnableFluidStillReachesTheFuelGauge(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        FluidStack unburnable = new FluidStack(ForgeweaveFluids.IRON.still().get(), 1000);
        tank.tank().fill(unburnable, IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertTrue(SmelteryFuel.find(helper.getLevel().registryAccess(), unburnable.getFluid()).isEmpty(),
                "this test is pointless unless molten iron really is unburnable");

        // The scan the screen's stillValid keeps alive is what refreshes the gauge; force one.
        helper.assertValueEqual(core.currentTemperature(), 0, "an unburnable fluid must not heat the smeltery");
        helper.assertTrue(core.fuelDisplayFluid().getFluid() == unburnable.getFluid(),
                "the fuel gauge must show the unburnable fluid, not nothing: " + core.fuelDisplayFluid());
        helper.assertValueEqual(core.fuelDisplayFluid().getAmount(), 1000, "the gauge's fill for the unburnable fluid");
        helper.succeed();
    }

    /**
     * #844 deliverable 2: a real shipped fuel, not the GameTest-only superfuel above, still reaches a
     * recipe lava cannot. Blazing blood's own {@link net.neoforged.neoforge.fluids.FluidType}
     * temperature (1500, set on issue #270) sits above lava's 1300 and above the 1400-degree fixture
     * recipe {@link #aHotterDatapackFuelMeltsARecipeLavaCannotReach} already proves lava cannot touch
     * -- reusing that fixture rather than inventing a new one, since blazing blood earning the exact
     * same reach with a real fuel (50 mB / 100-tick cycle, mirroring {@code lava.json}'s own shape) is
     * the point of #844's {@code smeltery_fuel/blazing_blood.json}.
     *
     * <p>Blazing blood's much smaller heat headroom over the fixture recipe than the GameTest
     * superfuel's (1200 vs. 4700) means the real-tick wall-clock melt the sibling test above waits out
     * would need roughly 3000 real ticks -- driven directly via {@link
     * SmelteryControllerBlockEntity#meltTick()} instead, the same way {@link
     * #aFinishOnlyTickConsumesNoFuel} does, so the assertion does not depend on a timeout margin.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void blazingBloodFuelledSmelteryMeltsARecipeLavaCannotReach(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(ForgeweaveFluids.BLAZING_BLOOD.still().get(), SearedTankBlockEntity.CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertValueEqual(core.currentTemperature(), 1500, "smeltery temperature with blazing blood in the wall tank");

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.BLAZE_ROD)).isEmpty(),
                "expected the blaze rod to go into the smeltery");

        while (core.meltProgress(0) < 1.0f) {
            helper.assertTrue(core.meltTick(), "expected blazing blood to keep heating the 1400-degree fixture recipe");
        }
        core.meltTick(); // finish-only tick.

        helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten iron from the 1400-degree fixture recipe");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron, lava's own 1300 degrees can never reach this recipe");
        helper.succeed();
    }

    /**
     * #894 -- TAIGA survey deliverable 2: twinalloy is Track B's mapped equivalent of TAIGA's
     * Dilithium, the one TAIGA fluid that is both a real ore-sourced tool material there and
     * registered via {@code TinkerRegistry.registerSmelteryFuel} (NOTICE.md-free: TAIGA is
     * inspiration-only under CLAUDE.md, no code or numbers copied). {@code smeltery_fuel/twinalloy.json}
     * ships no {@code temperature} override, the same way {@code lava.json}/{@code blazing_blood.json}
     * do not, so it burns at twinalloy's own already-registered 910 (dev.gkissel.forgeweave.fluid.
     * ForgeweaveFluids#TWINALLOY) -- below lava's 1300, mirroring TAIGA's own dilithium fuel sitting
     * cooler than its magma fuel rather than hotter. This pins that burn temperature directly.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void twinalloyFuelledSmelteryReachesItsOwnTemperature(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(ForgeweaveFluids.TWINALLOY.still().get(), SearedTankBlockEntity.CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertValueEqual(core.currentTemperature(), 910, "smeltery temperature with twinalloy in the wall tank");
        helper.succeed();
    }

    /**
     * #894 -- the deliberate flip side of {@link #blazingBloodFuelledSmelteryMeltsARecipeLavaCannotReach}:
     * twinalloy's 910 degrees sits below lava's 1300, so it must not reach the same 1400-degree fixture
     * recipe blazing blood proves it can. This is the concrete evidence behind the PR's progression
     * claim that twinalloy unlocks nothing new -- it is a cheap early convenience fuel (melted from
     * amethyst shards), not a headroom tier, matching TAIGA's own dilithium sitting cooler than magma.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void twinalloyCannotReachTheLavaExclusiveFixtureRecipe(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(ForgeweaveFluids.TWINALLOY.still().get(), SearedTankBlockEntity.CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.BLAZE_ROD)).isEmpty(),
                "expected the blaze rod to go into the smeltery");

        helper.assertTrue(!core.meltTick(), "twinalloy's 910 degrees must not heat the 1400-degree fixture recipe");
        helper.assertTrue(core.meltProgress(0) == 0f, "expected no melt progress at all under twinalloy");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** The 1x1x2 minimum smeltery of {@link SmelteryGameTests}, with its one wall tank full of lava. */
    private static SmelteryControllerBlockEntity lavaFuelledSmeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    private static int drainedLava(GameTestHelper helper) {
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        return SearedTankBlockEntity.CAPACITY - tank.tank().getFluidAmount();
    }
}
