package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    // ------------------------------------------------------------------ helpers

    /**
     * A formed smeltery with a full lava tank behind it. A 2x1x2 interior rather than
     * {@code SteelAndTagGatedGameTests}' 1x1x2 minimum, because a smeltery has one melt slot per
     * interior block and {@link #theDustLadderMeltsAtItsLadderAmounts} needs three at once.
     */
    private static SmelteryControllerBlockEntity litSmeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 2, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        return core;
    }

    private static void insert(GameTestHelper helper, SmelteryControllerBlockEntity core,
            net.minecraft.world.item.Item item) {
        helper.assertTrue(core.insertForMelting(new ItemStack(item)).isEmpty(),
                "expected " + item + " to go into the smeltery");
    }
}
