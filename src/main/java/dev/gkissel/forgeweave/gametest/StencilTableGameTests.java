package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.StencilTableBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig; // #276
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

    /**
     * Issue #276, upstream 1.12's {@code reuseStencils} (its default is on): a pattern that already
     * carries a shape can go back in and be reshaped into another, consumed 1:1 exactly as a blank
     * one is. Both flag states, since the whole point of the option is the "off" side.
     *
     * <p>Synchronous on purpose -- this mutates a global config value, and GameTests in one batch
     * tick concurrently, so the set/assert/restore has to complete inside a single test method for
     * no other test to ever observe the flipped value.
     */
    @GameTest(template = "empty")
    public static void aStampedPatternCanBeReshapedOnlyWhileReuseStencilsIsOn(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StencilTableMenu menu = openMenu(helper, pos, player);
        ItemStack stamped = new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get());

        ForgeweaveConfig.REUSE_STENCILS.set(false);
        try {
            helper.assertFalse(menu.getSlot(StencilTableMenu.INPUT_SLOT).mayPlace(stamped),
                    "a stamped pattern must be refused by the input slot while reuseStencils is off");
        } finally {
            ForgeweaveConfig.REUSE_STENCILS.set(true);
        }

        helper.assertTrue(menu.getSlot(StencilTableMenu.INPUT_SLOT).mayPlace(stamped),
                "a stamped pattern must be accepted by the input slot while reuseStencils is on");
        helper.assertTrue(menu.getSlot(StencilTableMenu.INPUT_SLOT).mayPlace(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())),
                "a blank pattern must be accepted regardless of reuseStencils");

        // The reshape itself, end to end: stamped pickaxe head in, axe head out, input consumed.
        menu.getSlot(StencilTableMenu.INPUT_SLOT).set(stamped);
        menu.clickMenuButton(player, StencilTableMenu.PATTERNS.indexOf(ForgeweaveItems.PATTERN_AXE_HEAD));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(StencilTableMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PATTERN_AXE_HEAD.get()),
                "expected the axe head pattern reshaped out of a pickaxe head pattern, got " + output);
        menu.getSlot(StencilTableMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(StencilTableMenu.INPUT_SLOT).getItem().isEmpty(),
                "expected the reshaped pattern to be consumed 1:1, like a blank one");

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

    /**
     * Issue #756 regression: the adjacent Pattern Chest keeps self-expanding ({@link ChestBlockEntity}
     * class javadoc) as items reach it -- including through this very side panel, and through any
     * other insertion source (here simulated directly on the block entity's container, matching
     * automation or a second player). Before the fix, {@code StencilTableMenu} captured the chest's
     * slot count once at menu-open time, so a slot the chest grew into afterward had no {@code Slot}
     * in the already-open menu at all and was unreachable until the Stencil Table's GUI was closed
     * and reopened. This exercises the production {@code createMenu} path (not the raw constructor
     * {@link #openMenu} uses) and never reconstructs the menu, so it only passes if the same open
     * menu instance already has a working slot for the newly-grown capacity.
     */
    @GameTest(template = "empty")
    public static void chestGrowthDuringAnOpenMenuStaysReachableWithoutReopening(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos chestPos = pos.relative(Direction.EAST);
        helper.setBlock(pos, ForgeweaveBlocks.STENCIL_TABLE.get());
        helper.setBlock(chestPos, ForgeweaveBlocks.PATTERN_CHEST.get());
        StencilTableBlockEntity stencilTable = helper.getBlockEntity(pos);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        StencilTableMenu menu = (StencilTableMenu) stencilTable.createMenu(0, player.getInventory(), player);
        helper.assertFalse(menu.sideSlots.isEmpty(), "expected the adjacent Pattern Chest to be exposed as a side inventory");

        // Fills the chest's only (empty) slot, which grows its capacity by one -- the chest always
        // keeps exactly one trailing free slot (ChestBlockEntity class javadoc).
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        helper.assertTrue(chest.container().getContainerSize() == 2, "expected filling the chest's only slot to "
                + "grow its capacity to 2, got " + chest.container().getContainerSize());

        helper.assertTrue(menu.sideSlots.size() > 1, "expected the still-open Stencil Table menu to already have "
                + "a Slot for the chest's newly-grown capacity, not just after reopening the GUI -- got only "
                + menu.sideSlots.size() + " side slot(s)");

        Slot grownSlot = menu.sideSlots.get(1);
        ItemStack blank = new ItemStack(ForgeweaveItems.PATTERN_BLANK.get());
        helper.assertTrue(grownSlot.mayPlace(blank), "expected the newly-grown slot to accept a pattern");
        grownSlot.set(blank);
        helper.assertTrue(chest.container().getItem(1).is(ForgeweaveItems.PATTERN_BLANK.get()),
                "expected placing into the newly-grown slot, through the still-open menu, to reach the chest itself");

        helper.succeed();
    }
}
