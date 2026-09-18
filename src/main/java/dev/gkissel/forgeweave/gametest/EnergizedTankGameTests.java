package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.EnergizedTankBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.menu.EnergizedTankMenu;

/**
 * The energized tank in a real formed smeltery on a headless dedicated server (docs/SCOPE.md M8,
 * D-M8-11; issue #972). No compat mod is needed for any of this: the block takes a plain NeoForge
 * energy capability, so a test fills its buffer directly the way a Mekanism or Powah generator
 * would. The one thing these tests cannot cover is an actual generator driving it through a cable,
 * which is a release-checklist line on #975 and exists to catch a capability-side surprise rather
 * than a logic error.
 *
 * <p>The arithmetic these exercise -- the cost formula and the hottest-tank selection -- is pinned
 * on its own in {@code EnergizedHeatTest}. What runs here is the wiring: the scan accepting the
 * block as a wall, the smeltery reading its temperature, and the charge landing on the right tank.
 *
 * <p>Melt ticks are driven by calling {@link SmelteryControllerBlockEntity#meltTick()} directly
 * rather than sampling the scheduler, for #715's reason (a scheduled tick lands a cycle late under
 * CI load and an exact count reads wrong).
 *
 * <p>All of these reuse {@link SmelteryGameTests}'s 1x1x2 rig, whose walls are the two layers above
 * {@code y = 1}. {@link #TANK_A} and {@link #TANK_B} are two wall slots the rig leaves as plain
 * seared brick, so replacing them with energized tanks needs nothing from the shared helper.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EnergizedTankGameTests {

    /** A wall slot in the 1x1x2 rig, opposite the core. */
    private static final BlockPos TANK_A = new BlockPos(2, 2, 1);
    /** A second wall slot, so two tanks can compete. */
    private static final BlockPos TANK_B = new BlockPos(1, 2, 2);

    /** Comfortably more than any test here spends, so nothing runs dry mid-melt by accident. */
    private static final int FULL = ForgeweaveConfig.ENERGIZED_TANK_BUFFER_DEFAULT;

    /** Lava's own burn temperature, the rung this suite works against ({@code SmelteryFuelGameTests}). */
    private static final int LAVA_TEMPERATURE = 1300;

    /** Blazing blood's, one rung up. */
    private static final int BLAZING_BLOOD_TEMPERATURE = 1500;

    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aChargedTankMeltsAtItsFuelSamplesTemperature(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = energizedSmeltery(helper, Fluids.LAVA, FULL);
        helper.assertValueEqual(core.currentTemperature(), LAVA_TEMPERATURE,
                "smeltery temperature from an energized tank holding a lava sample");

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_ORE)).isEmpty(),
                "expected the iron ore to go into the smeltery");
        meltToCompletion(helper, core);

        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.IRON.still().get(),
                "expected molten iron, got " + core.tank().getFluid().getFluid());
        helper.assertTrue(spent(helper, TANK_A) > 0, "the tank that heated the melt must have paid for it");
        helper.assertValueEqual(tank(helper, TANK_A).sample().getFluidAmount(),
                EnergizedTankBlockEntity.SAMPLE_CAPACITY, "the fuel sample is never consumed");
        helper.succeed();
    }

    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void anEmptyBufferMeltsNothingRatherThanMeltingSlowly(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = energizedSmeltery(helper, Fluids.LAVA, 0);
        helper.assertValueEqual(core.currentTemperature(), 0,
                "an energized tank with an empty buffer contributes no heat at all");

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_ORE)).isEmpty(),
                "expected the iron ore to go into the smeltery");
        helper.assertFalse(core.meltTick(), "an empty buffer must not heat anything");
        helper.assertTrue(core.meltProgress(0) == 0f, "not partial heat, not slow heat: none");
        helper.succeed();
    }

    /**
     * The other half of temperature gating: a sample only unlocks what its own fuel unlocks. The
     * blaze-rod fixture wants 1400 degrees ({@code src/gametest/resources}'s {@code
     * gametest_above_lava}), which is above lava's 1300 and below blazing blood's 1500 -- so both
     * halves run on one structure and the negative can never pass vacuously.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aRecipeGatedAboveTheSampleStaysUnmelted(GameTestHelper helper) {
        SmelteryControllerBlockEntity cold = energizedSmeltery(helper, Fluids.LAVA, FULL);
        helper.assertTrue(cold.insertForMelting(new ItemStack(Items.BLAZE_ROD)).isEmpty(),
                "expected the blaze rod to go into the smeltery");
        helper.assertFalse(cold.meltTick(), "a lava sample must not reach the 1400-degree fixture recipe");
        helper.assertTrue(cold.meltProgress(0) == 0f, "expected no progress at all under a lava sample");

        SmelteryControllerBlockEntity hot =
                energizedSmeltery(helper, ForgeweaveFluids.BLAZING_BLOOD.still().get(), FULL);
        helper.assertValueEqual(hot.currentTemperature(), BLAZING_BLOOD_TEMPERATURE,
                "smeltery temperature from a blazing blood sample");
        helper.assertTrue(hot.insertForMelting(new ItemStack(Items.BLAZE_ROD)).isEmpty(),
                "expected the blaze rod to go into the smeltery");
        // Topped up as it goes: this test is about what temperature unlocks, not about how long one
        // buffer lasts, and the 1400-degree fixture is a long enough melt to outrun a full one.
        // Endurance is what anEmptyBufferMeltsNothingRatherThanMeltingSlowly covers.
        meltToCompletion(helper, hot, TANK_A);
        helper.assertValueEqual(hot.tank().getFluidAmount(), 144, "molten iron from the 1400-degree fixture");
        helper.succeed();
    }

    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void onlyTheHotterOfTwoTanksIsCharged(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        placeTank(helper, TANK_A, Fluids.LAVA, FULL);
        placeTank(helper, TANK_B, ForgeweaveFluids.BLAZING_BLOOD.still().get(), FULL);
        SmelteryControllerBlockEntity core = formedCore(helper);

        helper.assertValueEqual(core.currentTemperature(), BLAZING_BLOOD_TEMPERATURE,
                "the hotter of the two samples sets the smeltery's heat");
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_ORE)).isEmpty(),
                "expected the iron ore to go into the smeltery");
        for (int i = 0; i < 20; i++) {
            helper.assertTrue(core.meltTick(), "expected melt tick " + i + " to keep heating the iron ore");
        }

        helper.assertTrue(spent(helper, TANK_B) > 0, "the hotter tank must be the one paying");
        helper.assertValueEqual(spent(helper, TANK_A), 0, "the cooler tank contributes nothing and spends nothing");
        helper.succeed();
    }

    /**
     * Overdrive multiplies both sides by the same factor, so the same melt costs the same total
     * energy and takes half as many ticks. Both runs are measured on one structure, the second
     * replacing the first's core and tank, so nothing but the button moves.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void overdriveDoublesBothTheCostAndTheProgress(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        Run plain = measure(helper, false);
        Run overdriven = measure(helper, true);

        helper.assertValueEqual(overdriven.costPerTick(), plain.costPerTick() * 2,
                "the default overdrive cost factor is 2.0");
        helper.assertTrue(overdriven.ticks() * 2 >= plain.ticks() && overdriven.ticks() * 2 <= plain.ticks() + 2,
                "overdrive must halve the melt ticks (within the step's own rounding): "
                        + plain.ticks() + " plain vs " + overdriven.ticks() + " overdriven");
        helper.succeed();
    }

    /** One measured melt of a single iron nugget: how many melt ticks it took and what a tick cost. */
    private record Run(int ticks, int costPerTick) {}

    private static Run measure(GameTestHelper helper, boolean overdrive) {
        helper.setBlock(SmelteryGameTests.CORE_POS, Blocks.AIR);
        placeTank(helper, TANK_A, Fluids.LAVA, FULL);
        if (overdrive) {
            // Pressed through the menu button, which is the only way a player can press it (#1018),
            // so this test measures the real path rather than the field behind it.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue(openMenu(helper, TANK_A, player)
                            .clickMenuButton(player, EnergizedTankMenu.OVERDRIVE_BUTTON),
                    "expected the menu to accept the overdrive button");
            helper.assertTrue(tank(helper, TANK_A).overdrive(), "and the press to land on the saved flag");
        }
        SmelteryControllerBlockEntity core = formedCore(helper);
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_NUGGET)).isEmpty(),
                "expected the iron nugget to go into the smeltery");

        int ticks = 0;
        while (core.meltProgress(0) < 1.0f) {
            helper.assertTrue(core.meltTick(), "expected the energized tank to keep heating the iron nugget");
            ticks++;
        }
        return new Run(ticks, tank(helper, TANK_A).costPerMeltTick());
    }

    /**
     * The toggle-off path (D-M8-5). A tank already standing in a smeltery keeps its sample, its
     * buffer and its overdrive setting while the toggle is off -- it just stops heating -- and
     * turning the toggle back on restores the heat with no reload. Synchronous throughout, for
     * {@code ContentFamilyGameTests}' reason: a config value is global and GameTests in one batch
     * tick concurrently.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void theToggleOffLeavesAPlacedTankDormantRatherThanBroken(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = energizedSmeltery(helper, Fluids.LAVA, FULL);
        tank(helper, TANK_A).toggleOverdrive();
        helper.assertValueEqual(core.currentTemperature(), LAVA_TEMPERATURE,
                "the tank heats the smeltery while the toggle is on, or this test proves nothing");

        ForgeweaveConfig.ENERGIZED_TANK.set(false);
        try {
            helper.assertValueEqual(core.currentTemperature(), 0,
                    "a dormant tank must contribute no heat");
            helper.assertValueEqual(tank(helper, TANK_A).costPerMeltTick(), 0,
                    "and must not be able to spend anything either");
            EnergizedTankBlockEntity dormant = tank(helper, TANK_A);
            helper.assertValueEqual(dormant.sample().getFluidAmount(), EnergizedTankBlockEntity.SAMPLE_CAPACITY,
                    "a dormant tank keeps its fuel sample");
            helper.assertValueEqual(dormant.buffer().getEnergyStored(), FULL, "and keeps its buffer");
            helper.assertTrue(dormant.overdrive(), "and keeps its overdrive setting");
        } finally {
            ForgeweaveConfig.ENERGIZED_TANK.set(true);
        }

        helper.assertValueEqual(core.currentTemperature(), LAVA_TEMPERATURE,
                "turning the toggle back on must restore the heat with no reload");
        helper.succeed();
    }

    /**
     * An energized tank is a wall tank in its own right, so one is enough to form a smeltery with no
     * seared tank anywhere in the walls -- a smeltery heated entirely by energy has no liquid fuel
     * tank to satisfy upstream's own {@code hasTank} check with.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void anEnergizedTankAloneSatisfiesTheWallTankRequirement(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        // The rig's own seared tank goes back to plain brick, leaving the energized tank as the only one.
        helper.setBlock(SmelteryGameTests.TANK_POS, ForgeweaveBlocks.SEARED_BRICKS.get());
        placeTank(helper, TANK_A, Fluids.LAVA, FULL);

        SmelteryControllerBlockEntity core = formedCore(helper);
        helper.assertValueEqual(core.currentTemperature(), LAVA_TEMPERATURE,
                "and it heats the smeltery it just formed");
        helper.succeed();
    }

    // ------------------------------------------------------------------ the screen (#1018)

    /**
     * The menu is the screen's only window onto the tank, so what it reports is what a player reads:
     * the sample, the buffer, the heat, the cost and the overdrive state. Pressing its one button
     * flips the saved flag and doubles the cost; that the progress doubles with it is
     * {@link #overdriveDoublesBothTheCostAndTheProgress}, which presses the same button.
     */
    @GameTest(template = "empty")
    public static void theMenuExposesTheSampleTheBufferAndTheOverdriveState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        placeTank(helper, pos, Fluids.LAVA, FULL / 2);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EnergizedTankMenu menu = openMenu(helper, pos, player);

        helper.assertTrue(menu.sample(helper.getLevel()).is(Fluids.LAVA), "expected the lava sample in the menu");
        helper.assertValueEqual(menu.sample(helper.getLevel()).getAmount(),
                EnergizedTankBlockEntity.SAMPLE_CAPACITY, "the sample amount the menu reports");
        helper.assertValueEqual(menu.energy(helper.getLevel()), FULL / 2, "the buffer the menu reports");
        helper.assertValueEqual(menu.energyCapacity(helper.getLevel()), FULL, "the buffer capacity the menu reports");
        helper.assertValueEqual(menu.temperature(helper.getLevel()), LAVA_TEMPERATURE,
                "the menu reports the same heat every other temperature display shows");
        helper.assertFalse(menu.overdrive(helper.getLevel()), "a fresh tank has overdrive off");
        helper.assertTrue(menu.active(), "and energized tanks are switched on for this test");

        int plainCost = menu.costPerMeltTick(helper.getLevel());
        helper.assertTrue(plainCost > 0, "a lava sample must cost something per melt tick");
        helper.assertTrue(menu.clickMenuButton(player, EnergizedTankMenu.OVERDRIVE_BUTTON),
                "expected the menu to accept the overdrive button");
        helper.assertTrue(tank(helper, pos).overdrive(), "the button flips the tank's own saved flag");
        helper.assertTrue(menu.overdrive(helper.getLevel()), "and the menu reports it flipped");
        helper.assertValueEqual(menu.costPerMeltTick(helper.getLevel()), plainCost * 2,
                "the default overdrive cost factor is 2.0");

        // Pressing it again turns it off, and a button id nobody put there changes nothing.
        helper.assertTrue(menu.clickMenuButton(player, EnergizedTankMenu.OVERDRIVE_BUTTON), "expected a second press");
        helper.assertFalse(tank(helper, pos).overdrive(), "which turns overdrive back off");
        helper.assertFalse(menu.clickMenuButton(player, 99), "a forged button id must be refused");
        helper.assertFalse(tank(helper, pos).overdrive(), "and must change nothing");
        helper.succeed();
    }

    /** The empty-hand press #1014 shipped is gone: an empty hand opens the screen and nothing else. */
    @GameTest(template = "empty")
    public static void anEmptyHandNoLongerFlipsOverdrive(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        placeTank(helper, pos, Fluids.LAVA, FULL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        helper.useBlock(pos, player);
        helper.assertFalse(tank(helper, pos).overdrive(),
                "an empty-hand use must not touch overdrive any more -- the button is on the screen");
        helper.succeed();
    }

    /**
     * A fluid container still goes to the sample rather than to the screen, so filling and draining a
     * tank never has a GUI in the way. Both directions on one tank: a lava bucket fills the empty
     * sample, and the empty bucket it leaves behind drains it again.
     */
    @GameTest(template = "empty")
    public static void aBucketStillFillsAndDrainsTheSampleWithoutOpeningTheScreen(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.ENERGIZED_TANK.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));

        helper.useBlock(pos, player);
        helper.assertValueEqual(tank(helper, pos).sample().getFluidAmount(),
                EnergizedTankBlockEntity.SAMPLE_CAPACITY, "a lava bucket fills the fuel sample");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET),
                "and leaves an empty bucket, got " + player.getMainHandItem());
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
                "filling the sample must not open the tank's screen");

        helper.useBlock(pos, player);
        helper.assertValueEqual(tank(helper, pos).sample().getFluidAmount(), 0,
                "and the empty bucket drains it again");
        helper.assertTrue(player.getMainHandItem().is(Items.LAVA_BUCKET),
                "giving the lava back, got " + player.getMainHandItem());
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
                "draining the sample must not open the tank's screen either");
        helper.succeed();
    }

    /**
     * A dormant tank still opens and still reads out its preserved sample and buffer (D-M8-5), but
     * its button is dead on both sides: the screen greys it out from {@link EnergizedTankMenu#active}
     * and the server refuses the press even if one arrives anyway.
     */
    @GameTest(template = "empty")
    public static void aDormantTankOpensAndRefusesTheOverdriveButton(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        placeTank(helper, pos, Fluids.LAVA, FULL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EnergizedTankMenu menu = openMenu(helper, pos, player);

        ForgeweaveConfig.ENERGIZED_TANK.set(false);
        try {
            helper.assertFalse(menu.active(), "the screen must know the block is switched off");
            helper.assertValueEqual(menu.sample(helper.getLevel()).getAmount(),
                    EnergizedTankBlockEntity.SAMPLE_CAPACITY, "a dormant tank still shows its sample");
            helper.assertValueEqual(menu.energy(helper.getLevel()), FULL, "and still shows its buffer");
            helper.assertFalse(menu.clickMenuButton(player, EnergizedTankMenu.OVERDRIVE_BUTTON),
                    "and refuses the overdrive button");
            helper.assertFalse(tank(helper, pos).overdrive(), "so the saved flag stays as it was");
        } finally {
            ForgeweaveConfig.ENERGIZED_TANK.set(true);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ rig

    /** The tank's menu as the server builds it, the shape {@code ChestGameTests#openMenu} uses. */
    private static EnergizedTankMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        BlockPos absolute = helper.absolutePos(pos);
        return new EnergizedTankMenu(0, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), absolute), absolute);
    }


    /** The 1x1x2 rig with one energized tank at {@link #TANK_A}, sampled and charged as given. */
    private static SmelteryControllerBlockEntity energizedSmeltery(GameTestHelper helper, Fluid sample, int energy) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.setBlock(SmelteryGameTests.CORE_POS, Blocks.AIR);
        placeTank(helper, TANK_A, sample, energy);
        return formedCore(helper);
    }

    /** A fresh energized tank at {@code pos} holding a full bucket of {@code sample} and {@code energy} FE. */
    private static void placeTank(GameTestHelper helper, BlockPos pos, Fluid sample, int energy) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ForgeweaveBlocks.ENERGIZED_TANK.get());
        EnergizedTankBlockEntity tank = tank(helper, pos);
        tank.sample().fill(new FluidStack(sample, EnergizedTankBlockEntity.SAMPLE_CAPACITY),
                IFluidHandler.FluidAction.EXECUTE);
        tank.buffer().receiveEnergy(energy, false);
    }

    private static EnergizedTankBlockEntity tank(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    /** How much of a tank's buffer has been spent since it was filled to {@link #FULL}. */
    private static int spent(GameTestHelper helper, BlockPos pos) {
        return FULL - tank(helper, pos).buffer().getEnergyStored();
    }

    /** The core, placed last so the scan runs off the real placement event. */
    private static SmelteryControllerBlockEntity formedCore(GameTestHelper helper) {
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /** {@code SmelteryFuelGameTests#meltToCompletion}'s shape: heat to 1.0, then the finish-only tick. */
    private static void meltToCompletion(GameTestHelper helper, SmelteryControllerBlockEntity core) {
        meltToCompletion(helper, core, null);
    }

    /**
     * As above, keeping {@code topUp}'s buffer full as it goes when one is given -- for a melt long
     * enough to outrun a full buffer, where the point of the test is the temperature rather than the
     * endurance.
     */
    private static void meltToCompletion(GameTestHelper helper, SmelteryControllerBlockEntity core,
            BlockPos topUp) {
        while (core.meltProgress(0) < 1.0f) {
            if (topUp != null) {
                tank(helper, topUp).buffer().receiveEnergy(FULL, false);
            }
            helper.assertTrue(core.meltTick(), "expected the energized tank to keep heating the smeltery's slot");
        }
        core.meltTick();
    }
}
