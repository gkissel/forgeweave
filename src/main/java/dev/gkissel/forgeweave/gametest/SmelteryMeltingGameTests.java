package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryCore;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * docs/SCOPE.md M2 issue #96's verification on a headless dedicated server: a vanilla ore melts at
 * its base amount, an item Forgeweave has never heard of melts because a {@code c:} tag names it, a
 * recipe hotter than the available fuel never melts, and an unformed smeltery is on no tick list at
 * all. Issue #290 adds the once-a-second dropped-item pickup and the full-tank stall state on top.
 *
 * <p>Some of these lean on the GameTest-only datapack in {@code src/gametest/resources} (see its
 * README): {@code minecraft:brick} stands in for a modded copper ingot, and a 1400-degree recipe
 * stands in for the metals that need more than lava, which arrive with issue #97's fuel system.
 *
 * <p>Melting is slow on purpose -- upstream's own numbers put an ingot's worth of iron at about
 * 750 ticks in a lava-fuelled smeltery -- so the melting tests give themselves room to run.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryMeltingGameTests {

    /**
     * SCOPE.md M2: "melting recipes hold base amounts, the core multiplies" -- so the recipe itself
     * ({@code iron_ore.json}) still holds one raw iron's worth, 144 mB, but a Standard Core (#99)
     * scales that to its 1.5x, 216 mB, because iron ore is an ore-class ({@code ore: true}) input.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void vanillaIronOreMeltsAtStandardCoresOneAndAHalfTimesYield(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.IRON_ORE);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.IRON.still().get(),
                (int) (MeltingRecipe.VALUE_INGOT * SmelteryCore.STANDARD.yieldMultiplier())));
    }

    /**
     * The other half of "ore blocks melt as their raw-drop equivalent": vanilla copper ore drops 2-5
     * raw copper (expected 3.5), so its base amount is 504 mB, not the {@code c:ores/copper} default
     * of 144 -- live proof that {@code MeltingRecipe#find} prefers the item-keyed override over the
     * tag both recipes match. The Standard Core (#99) then scales that base by 1.5x, to 756 mB.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void vanillaCopperOreMeltsAtItsPerItemOverrideTimesTheCoreYield(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.COPPER_ORE);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.COPPER.still().get(),
                (int) (504 * SmelteryCore.STANDARD.yieldMultiplier())));
    }

    /**
     * docs/SCOPE.md M2 acceptance test step 4 ("replace the Standard Core with a Nether Core... and
     * observe 2x yields"): the same iron ore that yields 216 mB under a Standard Core (see
     * {@link #vanillaIronOreMeltsAtStandardCoresOneAndAHalfTimesYield}) yields 288 mB, its 2x, under a
     * Nether Core. Issue #99.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void vanillaIronOreMeltsAtNetherCoresTwiceYield(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper, ForgeweaveBlocks.NETHER_CORE.get());
        insert(helper, core, Items.IRON_ORE);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.IRON.still().get(),
                (int) (MeltingRecipe.VALUE_INGOT * SmelteryCore.NETHER.yieldMultiplier())));
    }

    /**
     * SCOPE.md M2: "core tier is the ONLY yield axis; ingot re-melts 1:1" -- so an iron ingot under a
     * Nether Core (2x on ore-class inputs) still melts back to exactly one ingot's worth, because
     * {@code iron_ingot.json} is not {@code ore: true}. Proves the flag, not just the multiplier,
     * gates correctly. Issue #99.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void ingotReMeltStaysOneToOneEvenUnderANetherCore(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper, ForgeweaveBlocks.NETHER_CORE.get());
        insert(helper, core, Items.IRON_INGOT);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.IRON.still().get(), MeltingRecipe.VALUE_INGOT));
    }

    /**
     * The M2 ladder promise made executable: nothing in Forgeweave names {@code minecraft:brick}, and
     * no recipe was written for it. It melts because the GameTest datapack put it in
     * {@code c:ingots/copper} and Forgeweave's copper ingot recipe keys off that tag -- which is
     * exactly what a modded metal does.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void anItemAModAddedToACTagMeltsWithNoForgeweaveRecipe(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.BRICK);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.COPPER.still().get(), MeltingRecipe.VALUE_INGOT));
    }

    /**
     * docs/SCOPE.md M2 issue #104: mined cobalt ore drops one raw cobalt (#103's raw-ore item split),
     * which melts via {@code raw_cobalt.json}'s {@code c:raw_materials/cobalt} tag row at its 144 mB
     * base, scaled by the Standard Core's 1.5x like any other ore-class input.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void rawCobaltMeltsAtStandardCoresOneAndAHalfTimesYield(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, ForgeweaveItems.RAW_COBALT.get());

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.COBALT.still().get(),
                (int) (MeltingRecipe.VALUE_INGOT * SmelteryCore.STANDARD.yieldMultiplier())));
    }

    /**
     * "No separate silk-touch yield axis" (SCOPE.md M2), proven on the ore block itself rather than
     * its raw drop: the cobalt ore block's own item form melts at the exact same base 144 mB as
     * {@link #rawCobaltMeltsAtStandardCoresOneAndAHalfTimesYield} via {@code cobalt_ore.json}'s
     * {@code c:ores/cobalt} tag row (issue #104), not a distinct bonus amount.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void arditeOreBlockMeltsAtTheSameBaseAsItsRawDrop(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, ForgeweaveItems.ARDITE_ORE.get());

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.ARDITE.still().get(),
                (int) (MeltingRecipe.VALUE_INGOT * SmelteryCore.STANDARD.yieldMultiplier())));
    }

    /**
     * #184 -- upstream's tool-part melting: an iron pickaxe head melts back into exactly the 288 mB
     * of molten iron that cast it. Run under a <em>Nether</em> Core (2x on ore-class inputs) so the
     * amount also proves the melt-back is not ore-class: upstream returns a part's exact value with
     * no smeltery bonus, and this returns two ingots' worth, not four.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void aMetalToolPartMeltsBackIntoItsOwnMaterialAtItsExactCost(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper, ForgeweaveBlocks.NETHER_CORE.get());
        helper.assertTrue(core.insertForMelting(ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron")).isEmpty(),
                "expected an iron pickaxe head to go into the smeltery");

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.IRON.still().get(),
                MeltingRecipe.VALUE_INGOT * 2));
    }

    /** #184's other half: a material with no molten form has nothing to melt back into. */
    @GameTest(template = "smeltery")
    public static void aWoodenToolPartDoesNotMelt(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);

        ItemStack rejected = core.insertForMelting(ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "wood"));

        helper.assertValueEqual(rejected.getCount(), 1, "the wooden pickaxe head should have come straight back");
        helper.assertTrue(core.meltingItems().stream().allMatch(ItemStack::isEmpty), "and nothing should be in the smeltery");
        helper.succeed();
    }

    /**
     * #206 regression: the basin's new storage-block casting round-trips through the smeltery too --
     * a manyullyn block (docs/SCOPE.md M2 metals) melts back at its flat 1296 mB, the same as any
     * other storage block, with no ore-class multiplier (it carries no {@code ore: true} flag, unlike
     * the ore/raw-material rows above). A full block melts at its fluid's own temperature rather than
     * an ingot's halfway point ({@link dev.gkissel.forgeweave.recipe.MeltingRecipe#calcTemperature}),
     * so this needs a longer timeout than the ingot/ore tests above: manyullyn's 1000-degree fluid
     * costs {@code (1000 - 300) * 8 / 10 = 560} melt ticks, each {@value
     * dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity#MELT_INTERVAL_TICKS} game ticks.
     */
    @GameTest(template = "smeltery", timeoutTicks = 2600)
    public static void manyullynBlockMeltsBackAtItsFlatStorageBlockAmount(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, ForgeweaveItems.MANYULLYN_BLOCK.get());

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.MANYULLYN.still().get(), 1296));
    }

    /**
     * A recipe above what the fuel can reach never progresses (#96, deflaked in #210).
     *
     * <p>Scheduled block ticks are not anchored to the test clock -- a chunk that drops off the tick
     * list mid-run delivers them late. So this asserts with {@code succeedWhen} (retried every tick
     * until the timeout) after the observation window, rather than one-shot sampling a fixed tick,
     * which is how #210's single merge-queue failure happened.
     *
     * <p>#290 retired this test's other half, "the core stops ticking rather than spinning on it": a
     * <em>formed</em> smeltery now always keeps a once-a-second heartbeat alive regardless of melt
     * work, for the item-pickup sweep (see {@code SmelteryControllerBlockEntity#armMeltTick}), so
     * "not ticking" is no longer a signal that the too-hot recipe stopped retrying. The fluid amount
     * and the item still sitting there are what actually prove it never melted.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aRecipeHotterThanTheFuelDoesNotMelt(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.BLAZE_ROD);

        helper.runAfterDelay(100, () -> helper.succeedWhen(() -> {
            helper.assertValueEqual(core.tank().getFluidAmount(), 0, "fluid in a smeltery that cannot reach 1400");
            helper.assertTrue(!core.meltingItems().get(0).isEmpty(), "expected the unmeltable item to still be sitting there");
        }));
    }

    /**
     * SCOPE.md M2's performance budget ("spark profile confirms idle smeltery ~= zero tick"), as
     * narrowed by #290: only an <em>unformed</em> core is on no tick list at all. A formed one always
     * arms at least the once-a-second item-pickup heartbeat (upstream's own {@code
     * interactWithEntitiesInside} has no fuel or melt-work gate, only "is the structure active"), and
     * putting something meltable in on top of that is what tightens the cadence to the melt tick.
     */
    @GameTest(template = "smeltery")
    public static void formingASmelteryArmsOnlyTheItemPickupHeartbeatUntilSomethingMelts(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        helper.assertTrue(!isTicking(helper), "an unformed core (no structure yet) must not be scheduled to tick");

        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertTrue(isTicking(helper), "forming a smeltery must arm the item-pickup heartbeat (#290)");

        insert(helper, core, Items.RAW_IRON);
        helper.assertTrue(isTicking(helper), "inserting something meltable must still be ticking");
        helper.succeed();
    }

    /** An item with no melting recipe is refused rather than parked in a slot forever. */
    @GameTest(template = "smeltery")
    public static void anUnmeltableItemIsRefused(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);

        ItemStack rejected = core.insertForMelting(new ItemStack(Items.STICK));

        helper.assertValueEqual(rejected.getCount(), 1, "the stick should have come straight back");
        helper.assertTrue(core.meltingItems().stream().allMatch(ItemStack::isEmpty), "and nothing should be in the smeltery");
        helper.succeed();
    }

    /**
     * #290: upstream's {@code TileSmeltery#interactWithEntitiesInside}, once a second, picks up a
     * dropped item entity sitting inside the formed interior and inserts it for melting -- no GUI
     * open, no player interaction, nothing but the item resting inside the structure.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void aDroppedItemInsideAFormedSmelteryIsPickedUpAndMelted(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        ItemEntity dropped = dropInsideSmeltery(helper, new ItemStack(Items.IRON_ORE));

        helper.succeedWhen(() -> {
            helper.assertTrue(dropped.isRemoved(), "expected the smeltery to consume the dropped item entity");
            assertTankHolds(helper, core, ForgeweaveFluids.IRON.still().get(),
                    (int) (MeltingRecipe.VALUE_INGOT * SmelteryCore.STANDARD.yieldMultiplier()));
        });
    }

    /**
     * #290: a dropped item with no melting recipe is left alone rather than vanishing into a slot it
     * cannot use -- the same refusal {@link #anUnmeltableItemIsRefused} proves for a direct insert,
     * now proven for the pickup sweep's own entry point.
     */
    @GameTest(template = "smeltery", timeoutTicks = 60)
    public static void aDroppedUnmeltableItemIsLeftOnTheGround(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        ItemEntity dropped = dropInsideSmeltery(helper, new ItemStack(Items.STICK));

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!dropped.isRemoved(), "expected an unmeltable dropped item to survive the sweep");
            helper.assertTrue(core.meltingItems().stream().allMatch(ItemStack::isEmpty), "and nothing should be in the smeltery");
            helper.succeed();
        });
    }

    /**
     * #290: upstream's overheat state -- a melt that finished but could not drain into a full tank --
     * exposed as {@link SmelteryControllerBlockEntity#meltStalled(int)}. Driven the same
     * deterministic way #287's {@code aFinishOnlyTickConsumesNoFuel} drives a finishing tick: straight
     * calls to {@link SmelteryControllerBlockEntity#meltTick()} rather than waiting on the scheduler,
     * so the assertion sits on the exact finishing tick instead of a timing-dependent sample.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aFinishedMeltThatCannotFitIsMarkedStalled(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        // Leave only 10 mB free -- less than the nugget's 16 mB result, so the finishing tick cannot fit it.
        core.tank().fill(new FluidStack(ForgeweaveFluids.IRON.still().get(), core.tank().getCapacity() - 10),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_NUGGET)).isEmpty(),
                "expected the iron nugget to go into the smeltery");

        while (core.meltProgress(0) < 1.0f) {
            core.meltTick();
        }
        helper.assertTrue(!core.meltStalled(0), "expected no stall before the finishing tick even attempted");

        core.meltTick(); // finish-only tick: the tank has no room, so the melt cannot complete.

        helper.assertTrue(core.meltStalled(0), "expected a full tank to mark the finished slot stalled (#290)");
        helper.assertTrue(!core.meltingItems().get(0).isEmpty(), "expected the nugget to still be sitting there, unmelted");
        helper.succeed();
    }

    /** #290: draining room back into the tank lets a stalled melt finish, which clears the flag. */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void aStalledMeltClearsOnceTheTankHasRoomAgain(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        core.tank().fill(new FluidStack(ForgeweaveFluids.IRON.still().get(), core.tank().getCapacity() - 10),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.IRON_NUGGET)).isEmpty(),
                "expected the iron nugget to go into the smeltery");

        while (core.meltProgress(0) < 1.0f) {
            core.meltTick();
        }
        core.meltTick();
        helper.assertTrue(core.meltStalled(0), "expected the slot to be stalled ahead of the drain");

        core.tank().drain(50, IFluidHandler.FluidAction.EXECUTE);
        core.meltTick();

        helper.assertTrue(!core.meltStalled(0), "expected the stall to clear once the melt actually finished");
        helper.assertTrue(core.meltingItems().get(0).isEmpty(), "expected the nugget to have finished melting");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** The 1x1x2 minimum smeltery of {@link SmelteryGameTests}, with a Standard Core and its one wall tank full of lava. */
    private static SmelteryControllerBlockEntity lavaFuelledSmeltery(GameTestHelper helper) {
        return lavaFuelledSmeltery(helper, ForgeweaveBlocks.STANDARD_CORE.get());
    }

    /** As above, with {@code coreBlock} in place of the Standard Core -- #99's Standard/Nether yield comparisons need both tiers. */
    private static SmelteryControllerBlockEntity lavaFuelledSmeltery(GameTestHelper helper, Block coreBlock) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, coreBlock);

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        helper.assertValueEqual(core.currentTemperature(), Fluids.LAVA.getFluidType().getTemperature(),
                "smeltery temperature with lava in the wall tank");
        return core;
    }

    private static void insert(GameTestHelper helper, SmelteryControllerBlockEntity core, net.minecraft.world.item.Item item) {
        helper.assertTrue(core.insertForMelting(new ItemStack(item)).isEmpty(),
                "expected " + item + " to go into the smeltery");
    }

    private static void assertTankHolds(GameTestHelper helper, SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        helper.assertValueEqual(core.tank().getFluidAmount(), amount, "molten metal in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == fluid,
                "expected " + fluid + " but the tank holds " + core.tank().getFluid().getFluid());
    }

    /** Spawns {@code stack} as a dropped item entity inside {@link SmelteryGameTests#buildWalls}'s 1x1x2 interior (relative (1, 2, 1)). */
    private static ItemEntity dropInsideSmeltery(GameTestHelper helper, ItemStack stack) {
        Vec3 pos = helper.absoluteVec(new BlockPos(1, 2, 1).getCenter());
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z, stack);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static boolean isTicking(GameTestHelper helper) {
        Block core = ForgeweaveBlocks.STANDARD_CORE.get();
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(SmelteryGameTests.CORE_POS), core);
    }
}
