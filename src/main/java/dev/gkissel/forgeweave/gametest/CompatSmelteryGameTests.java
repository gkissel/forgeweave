package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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
import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #873 (M6 epic #824's JC3 reversal): every compat metal now gets a molten fluid + bucket, and
 * melting/casting recipes carrying the same {@code neoforge:conditions} as its material. Two paths:
 *
 * <ul>
 *   <li>The <b>negative</b> path is proven directly against a real, shipped compat metal (bronze):
 *       neither {@code mekanism} nor {@code immersiveengineering} is a build/test dependency (see
 *       {@code SteelAndTagGatedGameTests}'s own javadoc), so bronze's molten fluid still registers
 *       (the NeoForge platform constraint every fluid in {@link ForgeweaveFluids} lives under) but its
 *       bucket is unlisted and its melting/casting recipes never load.
 *   <li>The <b>positive</b> round trip cannot use a real compat metal for the same reason
 *       {@code ConditionalMaterialGameTests} cannot -- a GameTest server can fake a {@code c:} tag but
 *       not a modid or another mod's item id (docs/research/m6-material-expansion-references.md
 *       &sect;1.4). It reuses that class's gametest-fixture-provider approach (issue #826/#853): a
 *       modid-blind {@code item_exists minecraft:diamond} condition, this time on a melting/casting
 *       recipe pair (see {@code src/gametest/resources/README.md}) riding one of this issue's own real
 *       fluids, {@code forgeweave:molten_bronze} -- proof the mechanism itself (a compat fluid's
 *       recipes gate open and round-trip an item) works, independent of any specific provider mod.
 * </ul>
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CompatSmelteryGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);

    /**
     * The negative path: bronze's molten fluid exists (unconditional Java registration) but is
     * unobtainable and unlisted, and neither of its real melting/casting recipes registered.
     */
    @GameTest(template = "empty")
    public static void absentProviderHidesTheCompatBucketAndItsRecipes(GameTestHelper helper) {
        Fluid bronze = ForgeweaveFluids.BRONZE.still().get();
        helper.assertTrue(bronze != null, "expected forgeweave:molten_bronze to be registered unconditionally");

        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.VANILLA_SET, true, helper.getLevel().registryAccess());
        List<ItemStack> displayed = new ArrayList<>();
        ForgeweaveCreativeTab.addGeneralItems(parameters, (stack, visibility) -> displayed.add(stack), true);
        boolean bronzeBucketShown = displayed.stream().anyMatch(stack -> stack.is(ForgeweaveFluids.BRONZE.bucket().get()));
        helper.assertTrue(!bronzeBucketShown,
                "expected the bronze bucket to be hidden from the creative tab without mekanism/immersiveengineering");

        Registry<MeltingRecipe> melting = helper.getLevel().registryAccess().registryOrThrow(MeltingRecipe.REGISTRY);
        ResourceKey<MeltingRecipe> meltingKey = ResourceKey.create(MeltingRecipe.REGISTRY, id("bronze_ingot"));
        helper.assertTrue(melting.get(meltingKey) == null,
                "expected bronze_ingot's melting recipe to be absent (mekanism:ingot_bronze condition unmet)");

        Registry<CastingRecipe> casting = helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY);
        ResourceKey<CastingRecipe> castingKey = ResourceKey.create(CastingRecipe.REGISTRY, id("ingot_bronze"));
        helper.assertTrue(casting.get(castingKey) == null,
                "expected ingot_bronze's casting recipe to be absent (mekanism:ingot_bronze condition unmet)");

        helper.succeed();
    }

    /**
     * The positive melting half, via the gametest fixture provider (src/gametest/resources/README.md):
     * the tag-planted stand-in ingot (minecraft:nether_brick under c:ingots/bronze) melts, live in a
     * lava-fuelled smeltery, into 144 mB of molten_bronze -- proof {@code
     * gametest_compat_smeltery_present.json}'s condition gated open and {@code MeltingRecipe#find}
     * actually resolves it, not just that the JSON parses.
     */
    @GameTest(template = "smeltery", timeoutTicks = 3200)
    public static void fixtureRecipeMeltsTheStandInIngot(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SearedTankBlockEntity wallTank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        wallTank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        helper.assertTrue(core.insertForMelting(new ItemStack(Items.NETHER_BRICK)).isEmpty(),
                "expected the stand-in ingot to go into the smeltery");

        Fluid bronze = ForgeweaveFluids.BRONZE.still().get();
        helper.succeedWhen(() -> {
            helper.assertTrue(core.tank().getFluid().getFluid() == bronze,
                    "expected molten_bronze, tank holds " + core.tank().getFluid().getFluid());
            helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten bronze in the tank");
        });
    }

    /**
     * The positive casting half: molten_bronze (poured directly, same isolation
     * {@code TrackBAlloyGameTests}' own casting-only tests use so this does not re-prove melting)
     * casts back out as a gold ingot at a casting table -- proof {@code
     * gametest_compat_smeltery_present.json}'s casting row gated open too.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void fixtureRecipeCastsBackToAGoldIngot(GameTestHelper helper) {
        Fluid bronze = ForgeweaveFluids.BRONZE.still().get();

        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(bronze, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        CastingBlockEntity table = helper.getBlockEntity(CASTING);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        table.interact(player, InteractionHand.MAIN_HAND);

        helper.<FaucetBlockEntity>getBlockEntity(FAUCET).activate();

        helper.succeedWhen(() -> helper.assertTrue(table.output().is(Items.GOLD_INGOT),
                "expected the fixture casting recipe to produce a gold ingot, found " + table.output()));
    }

    /**
     * Issue #954: a diamond-tier Track A metal's melting temperature (1400, the table's
     * {@code minecraft:incorrect_for_diamond_tool} bucket) sits strictly between lava (1300) and
     * blazing blood (1500) -- {@code gametest_track_a_diamond_tier.json} rides the same modid-blind
     * {@code item_exists minecraft:diamond} condition {@code gametest_compat_smeltery_present.json}
     * uses above, so this exercises a real <em>conditioned</em> recipe rather than one of {@code
     * SmelteryFuelGameTests}'s unconditional ladder fixtures. Both halves run on one structure so the
     * negative half can never pass vacuously.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void diamondTierTrackAMetalFailsUnderLavaMeltsUnderBlazingBlood(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        Fluid platinum = ForgeweaveFluids.PLATINUM.still().get();
        assertCannotMeltFixture(helper, Fluids.LAVA, Items.PRISMARINE_SHARD, "lava (1300)");
        assertMeltsFixtureTo(helper, ForgeweaveFluids.BLAZING_BLOOD.still().get(), 1500, Items.PRISMARINE_SHARD,
                platinum, "blazing blood");
        helper.succeed();
    }

    /**
     * Issue #954: awakened draconium's melting temperature (1800, the draconic override above the
     * general netherite-tier rule) sits strictly between molten magma (1700) and brimspar (1900) --
     * same conditioned-fixture shape as the diamond tier test above.
     */
    @GameTest(template = "smeltery", timeoutTicks = 100)
    public static void awakenedDraconiumFailsUnderMagmaMeltsUnderBrimspar(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        Fluid draconiumAwakened = ForgeweaveFluids.DRACONIUM_AWAKENED.still().get();
        assertCannotMeltFixture(helper, ForgeweaveFluids.MOLTEN_MAGMA.still().get(), Items.SHULKER_SHELL, "molten magma (1700)");
        assertMeltsFixtureTo(helper, ForgeweaveFluids.BRIMSPAR.still().get(), 1900, Items.SHULKER_SHELL,
                draconiumAwakened, "brimspar");
        helper.succeed();
    }

    /**
     * The colder half of a #954 boundary check: the core is torn down and re-placed on the caller's
     * existing walls with only {@code colder} in the wall tank (a block entity carries its
     * in-progress burn across a wall-tank swap, the same reason {@code SmelteryFuelGameTests}'s own
     * {@code refuelledSmeltery} tears down between runs), and makes no melt progress at all on
     * {@code fixture}.
     */
    private static void assertCannotMeltFixture(GameTestHelper helper, Fluid colder, Item fixture, String fuelName) {
        SmelteryControllerBlockEntity core = refuelledSmeltery(helper, colder);
        helper.assertTrue(core.insertForMelting(new ItemStack(fixture)).isEmpty(),
                "expected the fixture item to go into the smeltery");
        helper.assertTrue(!core.meltTick(), fuelName + " must not heat this Track A fixture recipe");
        helper.assertTrue(core.meltProgress(0) == 0f, "expected no melt progress at all under " + fuelName);
    }

    /**
     * The hotter half: the core is re-placed with {@code hotter} alone in the wall tank and melts
     * {@code fixture} through to {@code expectedFluid} at the fixture recipe's 144 mB.
     */
    private static void assertMeltsFixtureTo(GameTestHelper helper, Fluid hotter, int expectedTemperature,
            Item fixture, Fluid expectedFluid, String fuelName) {
        SmelteryControllerBlockEntity core = refuelledSmeltery(helper, hotter);
        helper.assertValueEqual(core.currentTemperature(), expectedTemperature,
                "smeltery temperature with " + fuelName + " in the wall tank");
        helper.assertTrue(core.insertForMelting(new ItemStack(fixture)).isEmpty(),
                "expected the fixture item to go into the smeltery");

        while (core.meltProgress(0) < 1.0f) {
            helper.assertTrue(core.meltTick(), "expected " + fuelName + " to keep heating the fixture recipe");
        }
        core.meltTick(); // finish-only tick.

        helper.assertValueEqual(core.tank().getFluidAmount(), 144, "molten output from the fixture recipe");
        helper.assertTrue(core.tank().getFluid().getFluid() == expectedFluid,
                "expected " + expectedFluid + ", the rung below can never reach this recipe");
    }

    /**
     * Re-places the core on the caller's walls with {@code fuel} alone in the wall tank. Mirrors
     * {@code SmelteryFuelGameTests}'s own private helper of the same shape.
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

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private CompatSmelteryGameTests() {}
}
