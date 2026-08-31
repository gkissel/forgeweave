package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Issue #822 (docs/SCOPE.md M5): the Wooden Hopper's entire behavioral delta from a vanilla hopper
 * is a doubled transfer cooldown (upstream 1.12's {@code TileWoodenHopper#setTransferCooldown}, 8
 * ticks -> 16). This places a vanilla hopper and a Wooden Hopper side by side, each stocked with far
 * more items than either could drain within the test's tick budget, and after that same fixed
 * number of ticks counts how many items each pushed into the chest below it -- proving the wooden
 * one moves items at exactly half the vanilla rate rather than merely "slower".
 *
 * <p>Both hoppers start with a fresh {@code cooldownTime} of -1 (unset), so both fire their very
 * first transfer on the same first tick; every transfer after that recurs every 8 ticks (vanilla) or
 * 16 ticks (wooden). {@link #TICK_BUDGET}'s 64 ticks was chosen so that boundary lines up cleanly:
 * 8 vanilla transfers (ticks 1, 9, 17, ..., 57) against 4 wooden transfers (ticks 1, 17, 33, 49) --
 * an exact 2:1 ratio, not just "vanilla ahead."
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class WoodenHopperGameTests {

    private static final int TICK_BUDGET = 64;

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void woodenHopperTransfersAtHalfVanillaSpeed(GameTestHelper helper) {
        BlockPos vanillaHopperPos = new BlockPos(1, 2, 1);
        BlockPos vanillaChestPos = vanillaHopperPos.below();
        BlockPos woodenHopperPos = new BlockPos(4, 2, 1);
        BlockPos woodenChestPos = woodenHopperPos.below();

        helper.setBlock(vanillaChestPos, Blocks.CHEST);
        helper.setBlock(vanillaHopperPos, Blocks.HOPPER);
        helper.<HopperBlockEntity>getBlockEntity(vanillaHopperPos).setItem(0, new ItemStack(Items.REDSTONE, 64));

        helper.setBlock(woodenChestPos, Blocks.CHEST);
        helper.setBlock(woodenHopperPos, ForgeweaveBlocks.WOODEN_HOPPER.get());
        helper.<HopperBlockEntity>getBlockEntity(woodenHopperPos).setItem(0, new ItemStack(Items.REDSTONE, 64));

        helper.startSequence()
                .thenIdle(TICK_BUDGET)
                .thenExecute(() -> {
                    int vanillaTransferred = countItems(helper, vanillaChestPos);
                    int woodenTransferred = countItems(helper, woodenChestPos);

                    helper.assertTrue(vanillaTransferred > 0,
                            "expected the vanilla hopper to have transferred at least one item");
                    helper.assertTrue(woodenTransferred > 0,
                            "expected the Wooden Hopper to have transferred at least one item");
                    helper.assertValueEqual(vanillaTransferred, woodenTransferred * 2,
                            "expected the vanilla hopper (8-tick cooldown) to move exactly twice as many "
                                    + "items as the Wooden Hopper (16-tick cooldown) over " + TICK_BUDGET + " ticks");
                })
                .thenSucceed();
    }

    private static int countItems(GameTestHelper helper, BlockPos chestPos) {
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            total += chest.getItem(i).getCount();
        }
        return total;
    }
}
