package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
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
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * docs/SCOPE.md M3.2 issue #233's verification: the pig iron chain (rotten flesh melts into real
 * blood, clay melts into molten clay, and the three alloy at upstream's exact 144 + 40 + 72 = 144
 * ratio in a live smeltery), the firewood crafting recipe, and both new materials' trait wiring
 * through real Tool Station assembly. Trait <em>behaviors</em> (baconlicious's bacon, tasty's bite,
 * autosmelt's smelted drops) are already covered by {@link StatefulTraitGameTests} and
 * {@link MiningTraitGameTests}; what this file proves is that the shipped material JSONs actually
 * grant them.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PigIronFirewoodGameTests {

    /** Upstream's {@code registerMelting(Items.ROTTEN_FLESH, blood, 40)}: one rotten flesh is 40 mB of blood. */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void rottenFleshMeltsIntoBlood(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.ROTTEN_FLESH);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.BLOOD.still().get(), 40));
    }

    /** Upstream's {@code addKnownOreFluid("clay", VALUE_Ingot)}: a clay ball is one ingot's worth, 144 mB. */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void clayBallMeltsIntoMoltenClay(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.CLAY_BALL);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.MOLTEN_CLAY.still().get(),
                MeltingRecipe.VALUE_INGOT));
    }

    /**
     * And {@code addKnownOreFluid("blockClay", VALUE_BrickBlock)}: the four-ball block is 576 mB,
     * not 4x144 by accident. A 576 mB melt at clay's 700 degrees needs ~250 melt ticks (~1000 game
     * ticks) under lava, so per #249's budget discipline (>= 2x the floor) this gets the same 2600
     * budget as the manyullyn storage-block melt.
     */
    @GameTest(template = "smeltery", timeoutTicks = 2600)
    public static void clayBlockMeltsAtUpstreamsBrickBlockValue(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelledSmeltery(helper);
        insert(helper, core, Items.CLAY);

        helper.succeedWhen(() -> assertTankHolds(helper, core, ForgeweaveFluids.MOLTEN_CLAY.still().get(), 576));
    }

    /**
     * The maintainer decision on issue #233 made executable: upstream's exact pig iron alloy, 144 mB
     * iron + 40 mB blood + 72 mB molten clay = 144 mB pig iron, forming in a real smeltery's tank the
     * moment the last input arrives.
     */
    @GameTest(template = "smeltery")
    public static void ironBloodAndClayAlloyIntoPigIron(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.IRON.still().get(), 144);
        pour(core, ForgeweaveFluids.BLOOD.still().get(), 40);
        pour(core, ForgeweaveFluids.MOLTEN_CLAY.still().get(), 72);

        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left after the pig iron alloy");
        assertTankHolds(helper, core, ForgeweaveFluids.PIG_IRON.still().get(), 144);
        helper.succeed();
    }

    /**
     * The firewood recipe (issue #233): two blaze powders, a plank and the lava-bucket stand-in for
     * upstream's lavawood step craft one firewood block, resolved through the real RecipeManager.
     */
    @GameTest(template = "empty")
    public static void firewoodCraftsFromBlazePowderPlankAndLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput grid = CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.BLAZE_POWDER),
                new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.BLAZE_POWDER),
                new ItemStack(Items.LAVA_BUCKET)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(match -> match.value().assemble(grid, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.FIREWOOD.get()),
                "expected 2 blaze powder + plank + lava bucket to craft firewood, got " + crafted);
        helper.succeed();
    }

    /**
     * Pig iron's material wiring: a tool assembled entirely from pig iron carries baconlicious (the
     * head's trait) and tasty (every part's), matching upstream's {@code pigiron.addTrait} calls.
     */
    @GameTest(template = "empty")
    public static void pigIronPartsExposeBaconliciousAndTasty(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "pig_iron", "pig_iron", "pig_iron");

        List<ResourceLocation> traits = pickaxe.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(traitId("baconlicious")),
                "expected a pig iron tool to carry baconlicious, got " + traits);
        helper.assertTrue(traits.contains(traitId("tasty")),
                "expected a pig iron tool to carry tasty, got " + traits);
        helper.succeed();
    }

    /** And firewood's: a firewood-headed tool carries autosmelt (upstream's {@code firewood.addTrait(autosmelt)}). */
    @GameTest(template = "empty")
    public static void firewoodPartsExposeAutosmelt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "firewood", "wood", "wood");

        List<ResourceLocation> traits = pickaxe.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(traitId("autosmelt")),
                "expected a firewood-headed tool to carry autosmelt, got " + traits);
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** The 1x1x2 minimum smeltery of {@link SmelteryGameTests} with a Standard Core, formed and empty. */
    private static SmelteryControllerBlockEntity smeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /** As {@link #smeltery}, with the wall tank full of lava so melting can run. */
    private static SmelteryControllerBlockEntity lavaFuelledSmeltery(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        return core;
    }

    /** Puts fluid straight into the smeltery's tank, the way a faucet pouring through a drain would. */
    private static void pour(SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        core.tank().fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void insert(GameTestHelper helper, SmelteryControllerBlockEntity core, net.minecraft.world.item.Item item) {
        helper.assertTrue(core.insertForMelting(new ItemStack(item)).isEmpty(),
                "expected " + item + " to go into the smeltery");
    }

    private static void assertTankHolds(GameTestHelper helper, SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        helper.assertValueEqual(core.tank().getFluidAmount(), amount, "fluid in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == fluid,
                "expected " + fluid + " but the tank holds " + core.tank().getFluid().getFluid());
    }

    private PigIronFirewoodGameTests() {}
}
