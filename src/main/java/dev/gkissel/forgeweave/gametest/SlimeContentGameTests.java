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
import dev.gkissel.forgeweave.block.SlimeColour;
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
 * <p>Crystal sources follow the maintainer decision of 2026-08-14 (issue #339), which revised
 * #232's shortcuts: green and magma now take upstream 1.12's real path -- craft slimy mud, then
 * furnace-smelt the mud into the crystal ({@code TinkerTools#registerSmeltingRecipes}, 0.75 xp).
 * Blue -- with no world source until the world-content milestone (#181) -- keeps #232's interim
 * green crystal plus lapis.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SlimeContentGameTests {

    /** Upstream {@code slimy_mud_green.json} 1:1: 4 slime balls + sand + dirt, shapeless. */
    @GameTest(template = "empty")
    public static void greenSlimyMudCraftsFromSlimeBallsSandAndDirt(GameTestHelper helper) {
        assertCrafts(helper, List.of(
                        new ItemStack(Items.SLIME_BALL), new ItemStack(Items.SLIME_BALL), new ItemStack(Items.SLIME_BALL),
                        new ItemStack(Items.SLIME_BALL), new ItemStack(Items.SAND), new ItemStack(Items.DIRT)),
                ForgeweaveItems.SLIMY_MUD_GREEN.get());
        helper.succeed();
    }

    /**
     * Upstream {@code slimy_mud_magma.json} 1:1 again (issue #635): 2 magma slime balls + 2 magma
     * cream + soul sand + netherrack. #339 had to fill all four slots with magma cream for want of a
     * magma slime ball; there is one now.
     */
    @GameTest(template = "empty")
    public static void magmaSlimyMudCraftsFromMagmaSlimeBallsCreamSoulSandAndNetherrack(GameTestHelper helper) {
        assertCrafts(helper, List.of(
                        new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.MAGMA)),
                        new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.MAGMA)),
                        new ItemStack(Items.MAGMA_CREAM), new ItemStack(Items.MAGMA_CREAM),
                        new ItemStack(Items.SOUL_SAND), new ItemStack(Items.NETHERRACK)),
                ForgeweaveItems.SLIMY_MUD_MAGMA.get());
        helper.succeed();
    }

    /** Upstream {@code slimy_mud_blue.json} 1:1 (issue #635): 4 blue slime balls + sand + dirt. */
    @GameTest(template = "empty")
    public static void blueSlimyMudCraftsFromBlueSlimeBallsSandAndDirt(GameTestHelper helper) {
        ItemStack ball = new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.BLUE));
        assertCrafts(helper, List.of(ball.copy(), ball.copy(), ball.copy(), ball.copy(),
                        new ItemStack(Items.SAND), new ItemStack(Items.DIRT)),
                ForgeweaveItems.SLIMY_MUD_BLUE.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void greenSlimyMudSmeltsIntoGreenCrystal(GameTestHelper helper) {
        assertSmeltsInto(helper, new ItemStack(ForgeweaveItems.SLIMY_MUD_GREEN.get()),
                ForgeweaveItems.GREEN_SLIME_CRYSTAL.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void magmaSlimyMudSmeltsIntoMagmaCrystal(GameTestHelper helper) {
        assertSmeltsInto(helper, new ItemStack(ForgeweaveItems.SLIMY_MUD_MAGMA.get()),
                ForgeweaveItems.MAGMA_SLIME_CRYSTAL.get());
        helper.succeed();
    }

    /**
     * #339 -- the #232 shortcuts are gone: neither vanilla block smelts into a crystal any more, so
     * the mud path above is the only way to one.
     */
    @GameTest(template = "empty")
    public static void vanillaSlimeAndMagmaBlocksNoLongerSmeltIntoCrystals(GameTestHelper helper) {
        assertSmeltsIntoNothing(helper, new ItemStack(Items.SLIME_BLOCK));
        assertSmeltsIntoNothing(helper, new ItemStack(Items.MAGMA_BLOCK));
        helper.succeed();
    }

    /**
     * #635 reverts #232's interim "green crystal + lapis": blue slimy mud smelts into the blue slime
     * crystal, the same upstream path green and magma already take, and the lapis craft is gone.
     */
    @GameTest(template = "empty")
    public static void blueSlimyMudSmeltsIntoBlueCrystal(GameTestHelper helper) {
        assertSmeltsInto(helper, new ItemStack(ForgeweaveItems.SLIMY_MUD_BLUE.get()),
                ForgeweaveItems.BLUE_SLIME_CRYSTAL.get());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void greenCrystalAndLapisNoLongerCraftABlueCrystal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 1, List.of(
                new ItemStack(ForgeweaveItems.GREEN_SLIME_CRYSTAL.get()), new ItemStack(Items.LAPIS_LAZULI)));

        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).isEmpty(),
                "green slime crystal + lapis should no longer craft anything (issue #635)");
        helper.succeed();
    }

    /**
     * The slime family's crafting loop, upstream's four recipes per colour: four balls make a
     * congealed block and it gives them back, nine make a slime block and it gives them back
     * (issue #635). Green's slime-block half is vanilla's own pair and is not re-shipped.
     */
    @GameTest(template = "empty")
    public static void everySlimeColourCraftsRoundTripThroughItsBlocks(GameTestHelper helper) {
        for (ForgeweaveBlocks.SlimeFamily family : ForgeweaveBlocks.slimeFamilies()) {
            ItemStack ball = new ItemStack(ForgeweaveItems.slimeBall(family.colour()));
            assertCrafts(helper, List.of(ball.copy(), ball.copy(), ball.copy(), ball.copy()),
                    family.congealed().get().asItem());
            assertCraftsCount(helper, List.of(new ItemStack(family.congealed().get())), ball.getItem(), 4);
            if (family.slimeBlock() == null) {
                continue;
            }
            assertCrafts(helper, List.of(ball.copy(), ball.copy(), ball.copy(), ball.copy(), ball.copy(),
                    ball.copy(), ball.copy(), ball.copy(), ball.copy()), family.slimeBlock().get().asItem());
            assertCraftsCount(helper, List.of(new ItemStack(family.slimeBlock().get())), ball.getItem(), 9);
        }
        helper.succeed();
    }

    /**
     * Upstream's {@code ShapedFallbackRecipe}: nine slime balls of mixed colours make a slime block --
     * a vanilla one, at {@code matchVanillaSlimeblock}'s default of off. Nine of one colour stay with
     * that colour's own recipe, which the round trip above covers (issue #635).
     */
    @GameTest(template = "empty")
    public static void mixedSlimeBallsCraftAVanillaSlimeBlock(GameTestHelper helper) {
        assertCrafts(helper, List.of(
                        new ItemStack(Items.SLIME_BALL), new ItemStack(Items.SLIME_BALL), new ItemStack(Items.SLIME_BALL),
                        new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.BLUE)),
                        new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.PURPLE)),
                        new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.BLOOD)),
                        new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.MAGMA)),
                        new ItemStack(ForgeweaveItems.slimeBall(SlimeColour.PINK)),
                        new ItemStack(Items.SLIME_BALL)),
                Items.SLIME_BLOCK);
        helper.succeed();
    }

    /**
     * Upstream {@code TinkerSmeltery#registerAlloys}' knightslime ratio, 1:1 again now that #635 gave
     * it purple slime: 72 iron + 125 molten purple slime + 144 seared stone -> 72 knightslime.
     * #232's green {@code molten_slime} substitute is gone.
     */
    @GameTest(template = "smeltery")
    public static void knightslimeAlloysAtUpstreamsRatio(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        pour(core, ForgeweaveFluids.IRON.still().get(), 72);
        pour(core, ForgeweaveFluids.PURPLE_SLIME.still().get(), 125);
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
        pour(core, ForgeweaveFluids.PURPLE_SLIME.still().get(), 2 * 125);
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

    private static void assertSmeltsIntoNothing(GameTestHelper helper, ItemStack input) {
        helper.assertTrue(helper.getLevel().getRecipeManager()
                        .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), helper.getLevel()).isEmpty(),
                "expected no furnace recipe for " + input + " -- the #232 shortcut should be gone");
    }

    /** Resolves {@code ingredients} as a shapeless 3x2 crafting grid and asserts the single result. */
    private static void assertCrafts(GameTestHelper helper, List<ItemStack> ingredients,
                                     net.minecraft.world.item.Item expected) {
        assertCraftsCount(helper, ingredients, expected, 1);
    }

    /**
     * Crafts {@code ingredients} in the smallest grid that holds them -- 1x1, 2x2, 3x2 or 3x3, which
     * is every shape the slime family's recipes use -- and asserts the result.
     */
    private static void assertCraftsCount(GameTestHelper helper, List<ItemStack> ingredients,
                                          net.minecraft.world.item.Item expected, int count) {
        int width = switch (ingredients.size()) {
            case 1 -> 1;
            case 4 -> 2;
            default -> 3;
        };
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(width, ingredients.size() / width, ingredients);
        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(expected) && crafted.getCount() == count,
                "expected " + ingredients + " to craft " + count + " " + expected + ", got " + crafted);
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
