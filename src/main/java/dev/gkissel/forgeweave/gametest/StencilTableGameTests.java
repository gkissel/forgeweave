package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.StencilTableBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Covers docs/SCOPE.md M1 issue #44's verification: blank pattern + selection -> chosen part
 * pattern, blank consumed; selecting nothing produces no output even with a blank present.
 *
 * <p>Issue #306 adds the adjacent Pattern Chest's side inventory (upstream's {@code
 * ContainerStencilTable}) and shift-clicking a stamped pattern into it.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class StencilTableGameTests {

    private static StencilTableMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.STENCIL_TABLE.get());
        StencilTableBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new StencilTableMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
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

    /**
     * Issue #306 regression: an adjacent Pattern Chest ({@code ContainerStencilTable}'s {@code
     * TilePatternChest} detection) is exposed as the same side inventory the other stations already
     * share.
     */
    @GameTest(template = "empty")
    public static void adjacentPatternChestIsExposedAsASideInventory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos.relative(Direction.EAST), ForgeweaveBlocks.PATTERN_CHEST.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StencilTableMenu menu = openMenu(helper, pos, player);

        helper.assertTrue(menu.sideInventorySlotCount > 0, "expected the adjacent Pattern Chest's slots to be "
                + "exposed as a side inventory, got " + menu.sideInventorySlotCount);
        helper.assertFalse(menu.sideSlots.isEmpty(), "expected side-panel slots to be built for the chest");

        helper.succeed();
    }

    /**
     * Issue #306 regression: shift-clicking a stamped pattern out of the output slot lands it in the
     * adjacent Pattern Chest (upstream {@code ContainerStencilTable#transferStackInSlot}), and still
     * consumes the input blank the same way taking the output normally does.
     */
    @GameTest(template = "empty")
    public static void shiftClickingTheOutputStampsThePatternIntoTheAdjacentChest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos chestPos = pos.relative(Direction.EAST);
        helper.setBlock(chestPos, ForgeweaveBlocks.PATTERN_CHEST.get());
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StencilTableMenu menu = openMenu(helper, pos, player);

        menu.getSlot(StencilTableMenu.INPUT_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()));
        int pickaxeIndex = StencilTableMenu.PATTERNS.indexOf(ForgeweaveItems.PATTERN_PICKAXE_HEAD);
        menu.clickMenuButton(player, pickaxeIndex);
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem().is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()),
                "expected the pickaxe head pattern in the output before the shift-click, got "
                        + menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem());

        menu.quickMoveStack(player, StencilTableMenu.OUTPUT_SLOT);

        helper.assertTrue(menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "expected the shift-click to take the output out of its slot");
        helper.assertTrue(menu.getSlot(StencilTableMenu.INPUT_SLOT).getItem().isEmpty(),
                "expected the blank pattern to be consumed, same as taking the output normally");

        boolean foundInChest = false;
        for (int i = 0; i < chest.container().getContainerSize(); i++) {
            if (chest.container().getItem(i).is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get())) {
                foundInChest = true;
                break;
            }
        }
        helper.assertTrue(foundInChest, "expected the stamped pattern to land in the adjacent Pattern Chest");

        helper.succeed();
    }
}
