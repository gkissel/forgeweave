package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.MaterialForm;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Issue #992 (M8-8, docs/SCOPE.md D-M8-6): the dust ladder melts, and it melts off {@code c:dusts/<id>}
 * rather than the Forgeweave item. Steel stands in for the whole roster -- the rows are generated from
 * one table by {@code scripts/generate_material_forms.py}, and {@code MaterialFormsTest} walks every
 * material's three rows on the datapack side, so what needs proving in a running server is that the
 * melting path itself works and that the tag is what it keys on.
 *
 * <p>The tag half reuses the fixture shape M2's ladder promise already uses (see
 * {@code src/gametest/resources/README.md}): a gametest-only datapack plants
 * {@code minecraft:rabbit_foot} in a dust tag and pairs it with a melting row of the exact shape the
 * generator emits, so an item Forgeweave has never heard of melts with no Forgeweave code and no
 * recipe of its own. The fixture uses its own material id rather than {@code steel}: a file in the
 * gametest tree at a shipped tag's path shadows that tag instead of merging with it, since both
 * trees fold into one resource pack for this run, so borrowing {@code c:dusts/steel} would knock
 * {@code forgeweave:steel_dust} back out of it and break the test above.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MaterialFormMeltingGameTests {

    /** The three dusts melt at the ladder amounts: a dust as its ingot, a small dust as a third, a tiny dust as a nugget. */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void theDustLadderMeltsAtItsLadderAmounts(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = litSmeltery(helper);

        insert(helper, core, ForgeweaveItems.materialForm("steel", MaterialForm.DUST).get());
        insert(helper, core, ForgeweaveItems.materialForm("steel", MaterialForm.SMALL_DUST).get());
        insert(helper, core, ForgeweaveItems.materialForm("steel", MaterialForm.TINY_DUST).get());

        // 144 + 48 + 16: an ingot, a third of one, and a nugget.
        int expected = MeltingRecipe.VALUE_INGOT + MaterialForm.SMALL_DUST.meltAmount()
                + MeltingRecipe.VALUE_NUGGET;

        helper.succeedWhen(() -> {
            helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
            helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.STEEL.still().get(),
                    "expected molten steel, the tank holds " + core.tank().getFluid().getFluid());
            helper.assertValueEqual(core.tank().getFluidAmount(), expected, "molten steel from the three dusts");
        });
    }

    /**
     * The tag-not-item rule, the reason D-M8-6 keys dust melting off {@code c:dusts/<id>}: an item
     * only a datapack tag names melts into the same fluid at the same amount. Another mod's dust of
     * a Forgeweave material therefore melts in a Forgeweave smeltery with no second recipe.
     */
    @GameTest(template = "smeltery", timeoutTicks = 1600)
    public static void aStubItemInADustTagMeltsTheSameWay(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = litSmeltery(helper);
        insert(helper, core, Items.RABBIT_FOOT);

        helper.succeedWhen(() -> {
            helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
            helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.STEEL.still().get(),
                    "expected molten steel from an item only a dust tag names, the tank holds "
                            + core.tank().getFluid().getFluid());
            helper.assertValueEqual(core.tank().getFluidAmount(), MeltingRecipe.VALUE_INGOT,
                    "a tagged stub dust melts at the dust amount");
        });
    }

    /**
     * Playtest checklist 1.0.0, line 55, the plate family: each shipped crafting recipe is looked up
     * from a real crafting grid and assembled, using glowveil for the whole ingot roster. 2 ingots in
     * a row make 1 plate, 4 ingots in a square make 1 double plate, 2 ingots stacked make 2 rods, 4
     * ingots in a cross make 1 gear and 3 nuggets in a row make 1 wire. Each grid must resolve to the
     * recipe named for that form, so two forms can never share a pattern.
     */
    @GameTest(template = "empty")
    public static void thePlateFamilyCraftsFromIngotsAndNuggets(GameTestHelper helper) {
        Item ingot = ForgeweaveItems.trackBAlloyIngot("glowveil").get();
        Item nugget = ForgeweaveItems.trackBAlloyNugget("glowveil").get();

        assertCrafts(helper, MaterialForm.PLATE, 1, grid(ingot, "##"));
        assertCrafts(helper, MaterialForm.DOUBLE_PLATE, 1, grid(ingot, "##", "##"));
        assertCrafts(helper, MaterialForm.ROD, 2, grid(ingot, "#", "#"));
        assertCrafts(helper, MaterialForm.GEAR, 1, grid(ingot, " # ", "# #", " # "));
        assertCrafts(helper, MaterialForm.WIRE, 1, grid(nugget, "###"));
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 55, the dust ladder both ways: 3 small dusts make 1 dust and 1
     * dust breaks into 3 small dusts, 3 tiny dusts make 1 small dust and 1 small dust breaks into 3
     * tiny dusts. Each step is checked on a real crafting grid against its own shipped recipe.
     */
    @GameTest(template = "empty")
    public static void theDustLadderCraftsBothWays(GameTestHelper helper) {
        Item dust = ForgeweaveItems.materialForm("glowveil", MaterialForm.DUST).get();
        Item smallDust = ForgeweaveItems.materialForm("glowveil", MaterialForm.SMALL_DUST).get();
        Item tinyDust = ForgeweaveItems.materialForm("glowveil", MaterialForm.TINY_DUST).get();

        assertCrafts(helper, "glowveil_dust", dust, 1, grid(smallDust, "###"));
        assertCrafts(helper, "glowveil_small_dust_from_dust", smallDust, 3, grid(dust, "#"));
        assertCrafts(helper, "glowveil_small_dust_from_tiny_dust", smallDust, 1, grid(tinyDust, "###"));
        assertCrafts(helper, "glowveil_tiny_dust", tinyDust, 3, grid(smallDust, "#"));
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** A crafting grid with {@code item} wherever a row has {@code #} and nothing elsewhere. */
    private static CraftingInput grid(Item item, String... rows) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String row : rows) {
            for (char cell : row.toCharArray()) {
                stacks.add(cell == '#' ? new ItemStack(item) : ItemStack.EMPTY);
            }
        }
        return CraftingInput.of(rows[0].length(), rows.length, stacks);
    }

    /** {@link #assertCrafts(GameTestHelper, String, Item, int, CraftingInput)} for a glowveil form whose recipe is named after it. */
    private static void assertCrafts(GameTestHelper helper, MaterialForm form, int count, CraftingInput input) {
        assertCrafts(helper, form.itemId("glowveil"), ForgeweaveItems.materialForm("glowveil", form).get(), count, input);
    }

    /** The grid resolves to the shipped recipe {@code forgeweave:<recipe>} and assembles {@code count} of {@code result}. */
    private static void assertCrafts(GameTestHelper helper, String recipe, Item result, int count, CraftingInput input) {
        RecipeHolder<CraftingRecipe> holder = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new AssertionError("expected a crafting recipe to match the grid for " + recipe));
        helper.assertValueEqual(holder.id().identifier(), Identifier.fromNamespaceAndPath(Forgeweave.MODID, recipe),
                "the recipe the grid resolves to");
        ItemStack output = holder.value().assemble(input);
        helper.assertTrue(output.is(result), "expected " + result + " from " + recipe + ", got " + output);
        helper.assertValueEqual(output.getCount(), count, "items crafted by " + recipe);
    }

    /**
     * A formed smeltery with a full lava tank behind it. A 2x1x2 interior rather than
     * {@code SteelAndTagGatedGameTests}' 1x1x2 minimum, because a smeltery has one melt slot per
     * interior block and {@link #theDustLadderMeltsAtItsLadderAmounts} needs three at once.
     */
    private static SmelteryControllerBlockEntity litSmeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 2, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos, SmelteryControllerBlockEntity.class);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS, SearedTankBlockEntity.class);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        return core;
    }

    private static void insert(GameTestHelper helper, SmelteryControllerBlockEntity core, Item item) {
        helper.assertTrue(core.insertForMelting(new ItemStack(item)).isEmpty(),
                "expected " + item + " to go into the smeltery");
    }
}
