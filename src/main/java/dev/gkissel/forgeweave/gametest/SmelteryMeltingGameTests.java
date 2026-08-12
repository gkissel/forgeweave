package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
import dev.gkissel.forgeweave.block.SmelteryCore;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * docs/SCOPE.md M2 issue #96's verification on a headless dedicated server: a vanilla ore melts at
 * its base amount, an item Forgeweave has never heard of melts because a {@code c:} tag names it, a
 * recipe hotter than the available fuel never melts, and a smeltery with nothing in it is on no tick
 * list at all.
 *
 * <p>The last two lean on the GameTest-only datapack in {@code src/gametest/resources} (see its
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

    /** A recipe above what the fuel can reach never progresses, and the core stops ticking rather than spinning on it. */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aRecipeHotterThanTheFuelDoesNotMelt(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.BLAZE_ROD);

        helper.runAfterDelay(100, () -> {
            helper.assertValueEqual(core.tank().getFluidAmount(), 0, "fluid in a smeltery that cannot reach 1400");
            helper.assertTrue(!core.meltingItems().get(0).isEmpty(), "expected the unmeltable item to still be sitting there");
            helper.assertTrue(!isTicking(helper), "expected the core to stop ticking on a recipe it cannot heat");
            helper.succeed();
        });
    }

    /**
     * SCOPE.md M2's performance budget ("spark profile confirms idle smeltery ~= zero tick") as an
     * assertion: a formed, fuelled, empty smeltery is on no tick list, and putting something meltable
     * in it is what arms one.
     */
    @GameTest(template = "smeltery")
    public static void anIdleSmelteryIsOnNoTickList(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);

        helper.assertTrue(!isTicking(helper), "a formed but empty smeltery must not be scheduled to tick");
        insert(helper, core, Items.RAW_IRON);
        helper.assertTrue(isTicking(helper), "inserting something meltable must arm the melt tick");
        helper.succeed();
    }

    /** An item with no melting recipe is refused rather than parked in a slot forever. */
    @GameTest(template = "smeltery")
    public static void anUnmeltableItemIsRefused(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);

        ItemStack rejected = core.insertForMelting(new ItemStack(Items.STICK));

        helper.assertValueEqual(rejected.getCount(), 1, "the stick should have come straight back");
        helper.assertTrue(core.meltingItems().stream().allMatch(ItemStack::isEmpty), "and nothing should be in the smeltery");
        helper.assertTrue(!isTicking(helper), "and it should not have armed a tick");
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

    private static boolean isTicking(GameTestHelper helper) {
        Block core = ForgeweaveBlocks.STANDARD_CORE.get();
        return helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(SmelteryGameTests.CORE_POS), core);
    }
}
