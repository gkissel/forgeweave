package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
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
 *
 * <p>#903 added the three fixture recipes that let the whole six-rung ladder be checked one step at a
 * time -- 1600, 1800 and 2000, sitting between each adjacent pair of fuel temperatures (twinalloy 910,
 * lava 1300, blazing blood 1500, molten magma 1700, molten brimspar 1900, pyrealloy 2100). Each
 * "reaches a recipe the rung below cannot" test runs both halves on one structure, so the negative
 * half can never pass vacuously.
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

    /**
     * #897 rung 4: pyrealloy ({@code smeltery_fuel/pyrealloy.json}, no {@code temperature} override so
     * it burns at its fluid's own 2100 -- {@code ForgeweaveFluids#PYREALLOY}) clears the same
     * 1400-degree fixture recipe lava's 1300 can never reach, the same way blazing blood does above.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void pyrealloyFuelledSmelteryMeltsARecipeLavaCannotReach(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(ForgeweaveFluids.PYREALLOY.still().get(), SearedTankBlockEntity.CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertValueEqual(core.currentTemperature(), 2100, "smeltery temperature with pyrealloy in the wall tank");

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.BLAZE_ROD)).isEmpty(),
                "expected the blaze rod to go into the smeltery");

        while (core.meltProgress(0) < 1.0f) {
            helper.assertTrue(core.meltTick(), "expected pyrealloy to keep heating the 1400-degree fixture recipe");
        }
        core.meltTick(); // finish-only tick.

        helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten iron from the 1400-degree fixture recipe");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron, lava's own 1300 degrees can never reach this recipe");
        helper.succeed();
    }

    /**
     * #897's whole reason for a temperature ladder: {@code meltTick} adds {@code (temperature - 300)
     * / 100} progress per melt tick, so a hotter fuel melts the <em>same</em> recipe strictly faster
     * rather than only reaching more of them. An iron nugget needs 936 progress
     * ({@code MeltingRecipe#heatRequired}, from its derived 417-degree recipe temperature), which lava
     * covers 10 at a time and pyrealloy 18 at a time -- 94 melt ticks against 52, a 1.8x speedup.
     *
     * <p>The core is torn down and re-placed between the two runs ({@link #ticksToMeltAnIronNugget}):
     * a block entity carries its in-progress burn -- both the locked-in {@code fuelTemperature} and
     * the ticks left on it -- across a wall-tank swap, so measuring both fuels on one core would
     * credit pyrealloy with lava's leftover burn.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void pyrealloyMeltsTheSameRecipeInFewerTicksThanLava(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);

        int lavaTicks = ticksToMeltAnIronNugget(helper, Fluids.LAVA);
        int pyrealloyTicks = ticksToMeltAnIronNugget(helper, ForgeweaveFluids.PYREALLOY.still().get());

        helper.assertValueEqual(lavaTicks, 94, "melt ticks under lava (1300 degrees, 10 progress per tick)");
        helper.assertValueEqual(pyrealloyTicks, 52, "melt ticks under pyrealloy (2100 degrees, 18 progress per tick)");
        helper.assertTrue(pyrealloyTicks < lavaTicks,
                "pyrealloy must melt the same nugget in fewer ticks than lava, got " + pyrealloyTicks + " vs " + lavaTicks);
        helper.succeed();
    }

    /**
     * #903 rung 4: a vanilla magma block melts into molten magma. {@code melting_recipe/magma_block.json}
     * carries an explicit 1000-degree {@code temperature} rather than letting {@link MeltingRecipe}
     * derive one -- a block-sized amount of a 1700-degree fluid would derive to 1700, which is the
     * fluid's <em>own</em> burn temperature and would make the rung unreachable without already
     * holding it. 1000 puts it inside lava's reach and outside twinalloy's, so lava is what bootstraps
     * the ladder's mined half.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aMagmaBlockMeltsIntoMoltenMagma(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.MAGMA_BLOCK)).isEmpty(),
                "expected the magma block to go into the smeltery");

        meltToCompletion(helper, core, "lava");

        helper.assertValueEqual(core.tank().getFluidAmount(), 1000, "molten magma from one magma block");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.MOLTEN_MAGMA.still().get(),
                "expected molten magma, got " + core.tank().getFluid().getFluid());
        helper.succeed();
    }

    /**
     * #903 rung 5: a brimspar crystal melts into molten brimspar at one ingot's worth per crystal.
     * That recipe takes no explicit {@code temperature}, so it derives from the fluid's 1900 at an
     * ingot-sized amount -- {@link MeltingRecipe#calcTemperature} puts an ingot exactly halfway up the
     * fluid's headroom above ambient, i.e. 1100, which lava clears. Same bootstrap shape as the magma
     * rung above: mining the ore is the gate, not owning the fuel it produces.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aBrimsparCrystalMeltsIntoMoltenBrimspar(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        helper.assertTrue(core.insertForMelting(new ItemStack(ForgeweaveItems.BRIMSPAR_CRYSTAL.get())).isEmpty(),
                "expected the brimspar crystal to go into the smeltery");

        meltToCompletion(helper, core, "lava");

        helper.assertValueEqual(core.tank().getFluidAmount(), MeltingRecipe.VALUE_INGOT,
                "molten brimspar from one crystal");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.BRIMSPAR.still().get(),
                "expected molten brimspar, got " + core.tank().getFluid().getFluid());
        helper.succeed();
    }

    /**
     * #903, the ladder's whole point at rung 4: molten magma's 1700 clears the 1600-degree GameTest
     * fixture recipe ({@code gametest_above_blazing_blood.json}) that blazing blood's 1500 cannot.
     * Both halves run on one structure so the negative is not vacuous -- the same fuel, the same
     * fixture, only the temperature differs.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void moltenMagmaReachesARecipeBlazingBloodCannot(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        assertCannotMelt(helper, ForgeweaveFluids.BLAZING_BLOOD.still().get(), Items.GHAST_TEAR, "blazing blood (1500)");
        assertMeltsToIron(helper, ForgeweaveFluids.MOLTEN_MAGMA.still().get(), 1700, Items.GHAST_TEAR, "molten magma");
        helper.succeed();
    }

    /** #903 rung 5: brimspar's 1900 clears the 1800-degree fixture molten magma's 1700 cannot. */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void brimsparReachesARecipeMoltenMagmaCannot(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        assertCannotMelt(helper, ForgeweaveFluids.MOLTEN_MAGMA.still().get(), Items.PHANTOM_MEMBRANE, "molten magma (1700)");
        assertMeltsToIron(helper, ForgeweaveFluids.BRIMSPAR.still().get(), 1900, Items.PHANTOM_MEMBRANE, "molten brimspar");
        helper.succeed();
    }

    /** #903 rung 6: pyrealloy's 2100 clears the 2000-degree fixture brimspar's 1900 cannot. */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void pyrealloyReachesARecipeBrimsparCannot(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        assertCannotMelt(helper, ForgeweaveFluids.BRIMSPAR.still().get(), Items.NETHER_STAR, "molten brimspar (1900)");
        assertMeltsToIron(helper, ForgeweaveFluids.PYREALLOY.still().get(), 2100, Items.NETHER_STAR, "pyrealloy");
        helper.succeed();
    }

    /**
     * Issue #847 (M6 epic #824, JC7): {@link ForgeweaveConfig#MELT_SPEED_MULTIPLIER} scales {@code
     * meltTick}'s whole-number progress step, so doubling it doubles lava's 10-progress-per-tick step
     * to 20. The iron nugget's fixed 936 progress requirement ({@link
     * #pyrealloyMeltsTheSameRecipeInFewerTicksThanLava}'s javadoc) needs 94 ticks at 10/tick (930 &lt;
     * 936 &le; 940) and 47 ticks at 20/tick (920 &lt; 936 &le; 940) -- exactly half, since 94 is even.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void meltSpeedMultiplierHalvesMeltTicks(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);

        int baselineTicks = ticksToMeltAnIronNugget(helper, Fluids.LAVA);
        helper.assertValueEqual(baselineTicks, 94, "melt ticks under lava at the default 1.0 multiplier");

        ForgeweaveConfig.MELT_SPEED_MULTIPLIER.set(2.0D);
        int doubledSpeedTicks;
        try {
            doubledSpeedTicks = ticksToMeltAnIronNugget(helper, Fluids.LAVA);
        } finally {
            ForgeweaveConfig.MELT_SPEED_MULTIPLIER.set(1.0D);
        }

        helper.assertValueEqual(doubledSpeedTicks, 47, "melt ticks under lava at a 2.0 melt-speed multiplier");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** Drives melt ticks until the first slot finishes, then the finish-only tick that fills the tank. */
    private static void meltToCompletion(GameTestHelper helper, SmelteryControllerBlockEntity core, String fuelName) {
        while (core.meltProgress(0) < 1.0f) {
            helper.assertTrue(core.meltTick(), "expected " + fuelName + " to keep heating the smeltery's slot");
        }
        core.meltTick();
    }

    /**
     * Re-places the core on the caller's walls with {@code fuel} alone in the wall tank. A block entity
     * carries its in-progress burn -- the locked-in temperature <em>and</em> the ticks left on it --
     * across a wall-tank swap, so measuring two fuels on one core would credit the second with the
     * first's leftover burn (the same reason {@link #ticksToMeltAnIronNugget} tears down between runs).
     */
    private static SmelteryControllerBlockEntity refuelledSmeltery(GameTestHelper helper, Fluid fuel) {
        helper.setBlock(SmelteryGameTests.CORE_POS, Blocks.AIR);

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        tank.tank().fill(new FluidStack(fuel, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /** The colder half of a rung comparison: this fuel makes no progress at all on the fixture. */
    private static void assertCannotMelt(GameTestHelper helper, Fluid colder, net.minecraft.world.item.Item fixture, String fuelName) {
        SmelteryControllerBlockEntity core = refuelledSmeltery(helper, colder);
        helper.assertTrue(core.insertForMelting(new ItemStack(fixture)).isEmpty(),
                "expected the fixture item to go into the smeltery");
        helper.assertTrue(!core.meltTick(), fuelName + " must not heat this fixture recipe");
        helper.assertTrue(core.meltProgress(0) == 0f, "expected no melt progress at all under " + fuelName);
    }

    /** The hotter half: this fuel burns at {@code expectedTemperature} and melts the fixture through to iron. */
    private static void assertMeltsToIron(GameTestHelper helper, Fluid hotter, int expectedTemperature,
            net.minecraft.world.item.Item fixture, String fuelName) {
        SmelteryControllerBlockEntity core = refuelledSmeltery(helper, hotter);
        helper.assertValueEqual(core.currentTemperature(), expectedTemperature,
                "smeltery temperature with " + fuelName + " in the wall tank");
        helper.assertTrue(core.insertForMelting(new ItemStack(fixture)).isEmpty(),
                "expected the fixture item to go into the smeltery");

        meltToCompletion(helper, core, fuelName);

        helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten iron from the fixture recipe");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron, the rung below can never reach this recipe");
    }

    /**
     * Melt ticks a single iron nugget needs with {@code fuel} in the wall tank, measured on a freshly
     * placed core (see {@link #pyrealloyMeltsTheSameRecipeInFewerTicksThanLava}). Walls are the
     * caller's; only the core and the tank's contents are replaced.
     */
    private static int ticksToMeltAnIronNugget(GameTestHelper helper, Fluid fuel) {
        helper.setBlock(SmelteryGameTests.CORE_POS, Blocks.AIR);

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        tank.tank().fill(new FluidStack(fuel, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_NUGGET)).isEmpty(),
                "expected the iron nugget to go into the smeltery");

        int ticks = 0;
        while (core.meltProgress(0) < 1.0f) {
            helper.assertTrue(core.meltTick(), "expected " + fuel + " to keep heating the iron nugget");
            ticks++;
        }
        return ticks;
    }


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
