package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.StencilTableBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Covers docs/SCOPE.md M1 issue #44's verification: blank pattern + selection -> chosen part
 * pattern, blank consumed; selecting nothing produces no output even with a blank present.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class StencilTableGameTests {

    private static StencilTableMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.STENCIL_TABLE.get());
        StencilTableBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new StencilTableMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
    }

    @GameTest(template = "empty")
    public static void selectingAPatternConvertsTheBlankAndConsumesIt(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StencilTableMenu menu = openMenu(helper, pos, player);

        menu.getSlot(StencilTableMenu.INPUT_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()));
        int pickaxeIndex = StencilTableMenu.PATTERNS.indexOf(ForgeweaveItems.PATTERN_PICKAXE_HEAD);
        menu.clickMenuButton(player, pickaxeIndex);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()),
                "expected the pickaxe head pattern in the output, got " + output);

        menu.getSlot(StencilTableMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(StencilTableMenu.INPUT_SLOT).getItem().isEmpty(),
                "expected the blank pattern to be consumed by taking the output");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noSelectionProducesNoOutputEvenWithABlankPresent(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StencilTableMenu menu = openMenu(helper, pos, player);

        menu.getSlot(StencilTableMenu.INPUT_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()));
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "expected no output without a pattern selection, got " + menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem());

        helper.succeed();
    }
}
