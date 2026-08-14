package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.minecraft.advancements.AdvancementHolder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * docs/SCOPE.md M2 issue #98's verification on a headless dedicated server, and its release gate
 * "auto-alloy ratios (manyullyn, rose gold, netherite)": each shipped alloy forms at exactly its
 * ratio and at exactly twice it, fluids that alloy into nothing are left alone, a tank short of a
 * single application does not alloy, and the whole thing happens off tank changes rather than a
 * tick of its own -- alloying schedules nothing (#290 gave a *formed* smeltery its own reason to
 * keep a light tick alive regardless, the once-a-second item-pickup sweep, so "no tick" is no
 * longer the invariant this class's tests lean on).
 *
 * <p>Fluid goes into the tank directly here rather than through a melt, because the point under test
 * is the alloying pass and not the melting one; {@link #twoMeltedIngotsAlloyOnTheMeltCompleting} is
 * the one that runs the whole chain from items.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SmelteryAlloyGameTests {

    /** Upstream's ratio: one ingot of cobalt plus one of ardite is one ingot of manyullyn. */
    @GameTest(template = "smeltery")
    public static void cobaltAndArditeAlloyIntoManyullyn(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.COBALT.still().get(), MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.ARDITE.still().get(), MeltingRecipe.VALUE_INGOT);

        assertTankHoldsOnly(helper, core, ForgeweaveFluids.MANYULLYN.still().get(), MeltingRecipe.VALUE_INGOT);
        helper.succeed();
    }

    /** The same ratio at 2x: nothing about alloying is per-ingot, it applies the whole multiple available. */
    @GameTest(template = "smeltery")
    public static void manyullynScalesToTheWholeMultipleAvailable(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.COBALT.still().get(), 2 * MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.ARDITE.still().get(), 2 * MeltingRecipe.VALUE_INGOT);

        assertTankHoldsOnly(helper, core, ForgeweaveFluids.MANYULLYN.still().get(), 2 * MeltingRecipe.VALUE_INGOT);
        helper.succeed();
    }

    /** Vanilla smithing's own economics in fluid form: 4 scrap + 4 gold ingots = 1 netherite ingot. */
    @GameTest(template = "smeltery")
    public static void scrapAndGoldAlloyIntoNetheriteAtVanillasRatio(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.NETHERITE_SCRAP.still().get(), 4 * MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.GOLD.still().get(), 4 * MeltingRecipe.VALUE_INGOT);

        assertTankHoldsOnly(helper, core, ForgeweaveFluids.NETHERITE.still().get(), MeltingRecipe.VALUE_INGOT);
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void netheriteScalesToTheWholeMultipleAvailable(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.NETHERITE_SCRAP.still().get(), 8 * MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.GOLD.still().get(), 8 * MeltingRecipe.VALUE_INGOT);

        assertTankHoldsOnly(helper, core, ForgeweaveFluids.NETHERITE.still().get(), 2 * MeltingRecipe.VALUE_INGOT);
        helper.succeed();
    }

    /** Forgeweave's own ratio (no 1.12 counterpart): volume-preserving, one of each in, two out. */
    @GameTest(template = "smeltery")
    public static void copperAndGoldAlloyIntoRoseGold(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.COPPER.still().get(), MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.GOLD.still().get(), MeltingRecipe.VALUE_INGOT);

        assertTankHoldsOnly(helper, core, ForgeweaveFluids.ROSE_GOLD.still().get(), 2 * MeltingRecipe.VALUE_INGOT);
        helper.succeed();
    }

    @GameTest(template = "smeltery")
    public static void roseGoldScalesToTheWholeMultipleAvailable(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.COPPER.still().get(), 2 * MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.GOLD.still().get(), 2 * MeltingRecipe.VALUE_INGOT);

        assertTankHoldsOnly(helper, core, ForgeweaveFluids.ROSE_GOLD.still().get(), 4 * MeltingRecipe.VALUE_INGOT);
        helper.succeed();
    }

    /**
     * #291: upstream resolves alloy contention by explicit registration order (1.12's {@code
     * TinkerRegistry.getAlloys()} is a {@code LinkedList} in registration order; 1.20 datagens rose
     * gold before netherite), not by the alphabetical order Forgeweave's datapack registry iterates
     * in -- which put netherite ("n") ahead of rose gold ("r") and let it claim the gold first.
     *
     * <p>Gold is poured last, after copper and netherite scrap are already sitting in the tank, so
     * both recipes become simultaneously matchable on the very pour that triggers the alloying pass
     * -- pouring gold earlier would let whichever recipe only needs copper+gold or scrap+gold alloy
     * immediately on its own pour, never actually contending with the other for iteration order.
     *
     * <p>The amounts are chosen so both recipes actually contend for the same gold rather than one
     * of them simply failing to match: 4 ingots of gold is exactly netherite's one-application need,
     * so if netherite goes first it drains all of it and rose gold (needing only 1 gold per
     * application) is left with none. If rose gold goes first, it takes only as much gold as its
     * copper allows (1 ingot's worth) and leaves netherite the rest to alloy from.
     */
    @GameTest(template = "smeltery")
    public static void goldContentionResolvesRoseGoldBeforeNetherite(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.COPPER.still().get(), MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.NETHERITE_SCRAP.still().get(), 4 * MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.GOLD.still().get(), 4 * MeltingRecipe.VALUE_INGOT);

        helper.assertValueEqual(amountOf(core, ForgeweaveFluids.ROSE_GOLD.still().get()), 2 * MeltingRecipe.VALUE_INGOT,
                "rose gold must claim its share of the gold before netherite exhausts it");
        helper.assertValueEqual(amountOf(core, ForgeweaveFluids.NETHERITE.still().get()), 108,
                "netherite still forms from whatever gold rose gold left behind");
        helper.succeed();
    }

    /**
     * The issue's "non-matching fluids do not combine": iron and manyullyn are not any recipe's
     * inputs, so a smeltery holding both keeps holding both, in the order they went in.
     */
    @GameTest(template = "smeltery")
    public static void fluidsWithNoAlloyRecipeAreLeftAlone(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.IRON.still().get(), MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.MANYULLYN.still().get(), MeltingRecipe.VALUE_INGOT);

        List<FluidStack> fluids = core.tank().fluids();
        helper.assertValueEqual(fluids.size(), 2, "fluids left in a smeltery with nothing to alloy");
        helper.assertTrue(fluids.get(0).getFluid() == ForgeweaveFluids.IRON.still().get(), "iron should still be the drain fluid");
        helper.assertTrue(fluids.get(1).getFluid() == ForgeweaveFluids.MANYULLYN.still().get(), "manyullyn should still be layered on top");
        helper.succeed();
    }

    /**
     * Less than one application's worth of an input alloys nothing at all -- upstream's {@code
     * matches} returning 0 rather than part of a recipe. One millibucket of ardite is under the
     * shipped ratio's two, so the cobalt stays cobalt.
     */
    @GameTest(template = "smeltery")
    public static void aTankShortOfOneApplicationDoesNotAlloy(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.COBALT.still().get(), MeltingRecipe.VALUE_INGOT);
        pour(core, ForgeweaveFluids.ARDITE.still().get(), 1);

        helper.assertValueEqual(core.tank().fluids().size(), 2, "fluids left in a smeltery short of the ratio");
        helper.assertValueEqual(core.tank().getFluidAmount(), MeltingRecipe.VALUE_INGOT + 1, "total fluid, untouched");
        helper.succeed();
    }

    /**
     * The remainder rule, which is why the shipped ratios are written in upstream's minimal units
     * rather than in ingots: a tank holding one and a half ingots of cobalt against one of ardite
     * alloys the ingot it can and leaves the half-ingot of cobalt sitting on top of it.
     */
    @GameTest(template = "smeltery")
    public static void alloyingConsumesOnlyWholeApplicationsAndLeavesTheRemainder(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.COBALT.still().get(), MeltingRecipe.VALUE_INGOT + 72);
        pour(core, ForgeweaveFluids.ARDITE.still().get(), MeltingRecipe.VALUE_INGOT);

        helper.assertValueEqual(core.tank().getFluidAmount(), MeltingRecipe.VALUE_INGOT + 72, "one ingot of manyullyn plus the leftover cobalt");
        helper.assertValueEqual(amountOf(core, ForgeweaveFluids.MANYULLYN.still().get()), MeltingRecipe.VALUE_INGOT, "manyullyn made");
        helper.assertValueEqual(amountOf(core, ForgeweaveFluids.COBALT.still().get()), 72, "cobalt left over");
        helper.assertValueEqual(amountOf(core, ForgeweaveFluids.ARDITE.still().get()), 0, "ardite, all of it consumed");
        helper.succeed();
    }

    /**
     * The no-polling half of the SCOPE.md M2 gate, run end to end from items: a copper ingot and a
     * gold ingot melt in a lava-fuelled smeltery, and the moment the second one finishes the tank
     * alloys itself into rose gold, with alloying itself never scheduling a tick of its own (it hangs
     * off {@code onTankChanged}, not a poll).
     *
     * <p>#290 retired this test's "no tick left scheduled behind it" half: a formed smeltery now
     * always keeps its once-a-second item-pickup heartbeat armed regardless of melt work, so the tank
     * contents (rose gold, and nothing else) are what actually prove the melt-then-alloy chain worked.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void twoMeltedIngotsAlloyOnTheMeltCompleting(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        fuel(helper);
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.COPPER_INGOT)).isEmpty(), "expected the copper ingot to go in");
        helper.assertTrue(core.insertForMelting(new ItemStack(Items.GOLD_INGOT)).isEmpty(), "expected the gold ingot to go in");

        helper.succeedWhen(() -> {
            helper.assertValueEqual(amountOf(core, ForgeweaveFluids.ROSE_GOLD.still().get()), 2 * MeltingRecipe.VALUE_INGOT,
                    "rose gold from two melted ingots");
            helper.assertValueEqual(core.tank().fluids().size(), 1, "and nothing else left in the tank");
        });
    }

    /**
     * #110's {@code first_alloy}, with issue #98's chosen attribution: alloying has no player in its
     * call chain, so every player near the core when one forms is credited (see {@code
     * SmelteryControllerBlockEntity#grantFirstAlloy}).
     */
    @GameTest(template = "smeltery", timeoutTicks = 1200)
    public static void anAlloyGrantsFirstAlloyToThePlayerWatchingIt(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        ServerPlayer player = playerAtTheSmeltery(helper);

        // grantFirstAlloy finds its audience through an entity-index query, so the alloy must not
        // form before the index can serve the freshly spawned player (see SpawnCapture -- same
        // defect class as magnetic's zero pull).
        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, player))
                .thenExecute(() -> {
                    pour(core, ForgeweaveFluids.COBALT.still().get(), MeltingRecipe.VALUE_INGOT);
                    pour(core, ForgeweaveFluids.ARDITE.still().get(), MeltingRecipe.VALUE_INGOT);

                    helper.assertTrue(isGranted(helper, player, "smeltery/first_alloy"),
                            "expected an alloy forming next to a player to grant the advancement");
                })
                .thenSucceed();
    }

    /** And the other side of it: pouring in something that alloys with nothing grants nothing. */
    @GameTest(template = "smeltery", timeoutTicks = 1200)
    public static void fluidThatDoesNotAlloyGrantsNothing(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        ServerPlayer player = playerAtTheSmeltery(helper);

        // Same pre-wait as the positive test: with the player invisible to the index, "granted
        // nothing" would pass vacuously instead of proving iron alloys with nothing.
        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, player))
                .thenExecute(() -> {
                    pour(core, ForgeweaveFluids.IRON.still().get(), MeltingRecipe.VALUE_INGOT);

                    helper.assertFalse(isGranted(helper, player, "smeltery/first_alloy"),
                            "expected a lone molten metal to grant nothing");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ helpers

    /** The 1x1x2 minimum smeltery of {@link SmelteryGameTests} with a Standard Core, formed and empty. */
    private static SmelteryControllerBlockEntity smeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /**
     * A real {@link ServerPlayer} standing at the smeltery. {@link
     * GameTestHelper#makeMockServerPlayerInLevel()} spawns at world spawn, which is nowhere near the
     * test structure -- and proximity is the whole of issue #98's advancement attribution, so the
     * player has to actually be moved there.
     */
    private static ServerPlayer playerAtTheSmeltery(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(SmelteryGameTests.CORE_POS);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static void fuel(GameTestHelper helper) {
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
    }

    /** Puts fluid straight into the smeltery's tank, the way a faucet pouring through a drain would. */
    private static void pour(SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        core.tank().fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    private static int amountOf(SmelteryControllerBlockEntity core, Fluid fluid) {
        return core.tank().fluids().stream().filter(held -> held.getFluid() == fluid)
                .mapToInt(FluidStack::getAmount).sum();
    }

    private static void assertTankHoldsOnly(GameTestHelper helper, SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == fluid,
                "expected " + fluid + " but the tank holds " + core.tank().getFluid().getFluid());
        helper.assertValueEqual(core.tank().getFluidAmount(), amount, "alloy in the tank");
    }

    private static boolean isGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private SmelteryAlloyGameTests() {}
}
