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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #232's verification (docs/SCOPE.md M3.2): the three slime crystals' recipes, the
 * knightslime alloy at upstream's ratio in a real smeltery, and the slime-family materials wired so
 * assembled parts expose their traits. The embossing reagent revert is deliberately not here: it
 * ships with issue #248's fourth reagent slot, which the full 4-reagent parity recipe needs.
 *
 * <p>Crystal sources are the maintainer decisions recorded on the issue: green and magma
 * furnace-smelt from their vanilla blocks (upstream smelts congealed slime, which Forgeweave does
 * not ship), and blue -- with no world source until the world-content milestone -- crafts from a
 * green crystal plus lapis.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SlimeContentGameTests {

    @GameTest(template = "empty")
    public static void slimeBlockSmeltsIntoGreenCrystal(GameTestHelper helper) {
        assertSmeltsInto(helper, new ItemStack(Items.SLIME_BLOCK), ForgeweaveItems.GREEN_SLIME_CRYSTAL.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void magmaBlockSmeltsIntoMagmaCrystal(GameTestHelper helper) {
        assertSmeltsInto(helper, new ItemStack(Items.MAGMA_BLOCK), ForgeweaveItems.MAGMA_SLIME_CRYSTAL.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blueCrystalCraftsFromGreenCrystalAndLapis(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 1, List.of(
                new ItemStack(ForgeweaveItems.GREEN_SLIME_CRYSTAL.get()), new ItemStack(Items.LAPIS_LAZULI)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.BLUE_SLIME_CRYSTAL.get()) && crafted.getCount() == 1,
                "expected green slime crystal + lapis to craft a blue slime crystal, got " + crafted);
        helper.succeed();
    }

    /**
     * Upstream {@code TinkerSmeltery#registerAlloys}' knightslime ratio with the maintainer-decided
     * green-slime substitution: 72 iron + 125 molten slime + 144 seared stone -> 72 knightslime.
     */
    @GameTest(template = "smeltery")
    public static void knightslimeAlloysAtUpstreamsRatio(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.IRON.still().get(), 72);
        pour(core, ForgeweaveFluids.SLIME.still().get(), 125);
        pour(core, ForgeweaveFluids.SEARED_STONE.still().get(), 144);

        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left after alloying");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.KNIGHTSLIME.still().get(),
                "expected molten knightslime, the tank holds " + core.tank().getFluid().getFluid());
        helper.assertValueEqual(core.tank().getFluidAmount(), 72, "knightslime made from one application");
        helper.succeed();
    }

    /** The same ratio at 2x -- alloying applies the whole multiple available, like every shipped alloy. */
    @GameTest(template = "smeltery")
    public static void knightslimeScalesToTheWholeMultipleAvailable(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.IRON.still().get(), 2 * 72);
        pour(core, ForgeweaveFluids.SLIME.still().get(), 2 * 125);
        pour(core, ForgeweaveFluids.SEARED_STONE.still().get(), 2 * 144);

        helper.assertValueEqual(core.tank().getFluidAmount(), 2 * 72, "knightslime from two applications");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.KNIGHTSLIME.still().get(),
                "expected molten knightslime, the tank holds " + core.tank().getFluid().getFluid());
        helper.succeed();
    }

    /** Slime's one general trait rides every part: an all-slime tool exposes {@code slimey_green}. */
    @GameTest(template = "empty")
    public static void slimeToolExposesSlimeyGreen(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "slime", "slime", "slime");

        helper.assertTrue(traits(pickaxe).contains(trait("slimey_green")),
                "an all-slime tool must carry slimey_green, got " + traits(pickaxe));
        helper.succeed();
    }

    /**
     * Magma slime scopes {@code superheat} to the head and {@code flammable} to everything else
     * (upstream {@code TinkerMaterials}: {@code addTrait(superheat, HEAD); addTrait(flammable)}), so
     * an all-magmaslime tool exposes both ids -- the head-scoped list replaces the general one for
     * the head part only.
     */
    @GameTest(template = "empty")
    public static void magmaslimeToolExposesSuperheatAndFlammable(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                "magmaslime", "magmaslime", "magmaslime");

        helper.assertTrue(traits(pickaxe).contains(trait("superheat")),
                "a magmaslime head must carry superheat, got " + traits(pickaxe));
        helper.assertTrue(traits(pickaxe).contains(trait("flammable")),
                "magmaslime binding/handle parts must carry flammable, got " + traits(pickaxe));
        helper.succeed();
    }

    /** Knightslime: {@code crumbling} on the head, {@code unnatural} on the other parts (upstream scoping). */
    @GameTest(template = "empty")
    public static void knightslimeToolExposesCrumblingAndUnnatural(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                "knightslime", "knightslime", "knightslime");

        helper.assertTrue(traits(pickaxe).contains(trait("crumbling")),
                "a knightslime head must carry crumbling, got " + traits(pickaxe));
        helper.assertTrue(traits(pickaxe).contains(trait("unnatural")),
                "knightslime binding/handle parts must carry unnatural, got " + traits(pickaxe));
        helper.succeed();
    }

    /** Blue slime wired like its siblings: an all-blueslime tool exposes {@code slimey_blue}. */
    @GameTest(template = "empty")
    public static void blueslimeToolExposesSlimeyBlue(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                "blueslime", "blueslime", "blueslime");

        helper.assertTrue(traits(pickaxe).contains(trait("slimey_blue")),
                "an all-blueslime tool must carry slimey_blue, got " + traits(pickaxe));
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static void assertSmeltsInto(GameTestHelper helper, ItemStack input, net.minecraft.world.item.Item expected) {
        ServerLevel level = helper.getLevel();
        ItemStack smelted = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                .map(match -> match.value().assemble(new SingleRecipeInput(input), level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(smelted.is(expected),
                "expected furnace-smelting " + input + " to give " + expected + ", got " + smelted);
    }

    /** The 1x1x2 minimum smeltery of {@link SmelteryGameTests}, formed and empty. */
    private static SmelteryControllerBlockEntity smeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /** Puts fluid straight into the smeltery's tank, the way a faucet pouring through a drain would. */
    private static void pour(SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        core.tank().fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    private static List<ResourceLocation> traits(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
    }

    private static ResourceLocation trait(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private SlimeContentGameTests() {}
}
