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
import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ChestKind;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ChestMenu;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * Covers docs/SCOPE.md M1 issue #66's verification: each chest's slots only accept the item type
 * they're named for (upstream {@code isItemValidForSlot}, NOTICE.md), and an adjacent station's
 * side-inventory panel picks up a chest through the {@code IItemHandler} capability {@link
 * ChestBlockEntity#registerCapabilities} exposes, the same way it already does for a vanilla chest
 * ({@code CraftingStationGameTests#adjacentChestInventoryIsExposedThroughTheMenu}).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ChestGameTests {

    private static ChestMenu openMenu(GameTestHelper helper, ChestKind kind, BlockPos pos, Player player) {
        helper.setBlock(pos, kind == ChestKind.PATTERN
                ? ForgeweaveBlocks.PATTERN_CHEST.get()
                : ForgeweaveBlocks.PART_CHEST.get());
        ChestBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new ChestMenu(kind, 0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
    }

    @GameTest(template = "empty")
    public static void patternChestAcceptsPatternsAndRejectsParts(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChestMenu menu = openMenu(helper, ChestKind.PATTERN, pos, player);

        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())),
                "expected the Pattern Chest to accept a blank pattern");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get())),
                "expected the Pattern Chest to accept a part pattern");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "expected the Pattern Chest to reject a tool part");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partChestAcceptsPartsAndRejectsPatterns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChestMenu menu = openMenu(helper, ChestKind.PART, pos, player);

        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "expected the Part Chest to accept a tool part");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ForgeweaveItems.SHARD.get())),
                "expected the Part Chest to accept a shard (also a PartItem)");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())),
                "expected the Part Chest to reject a pattern");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patternChestNextToPartBuilderExposesItsPatternsThroughTheStationMenu(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = stationPos.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.setBlock(chestPos, ForgeweaveBlocks.PATTERN_CHEST.get());
        helper.setBlock(stationPos, ForgeweaveBlocks.PART_BUILDER.get());

        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));

        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(stationPos);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(stationPos)), blockEntity.findSideInventory());

        helper.assertTrue(menu.sideInventorySlotCount > 0, "expected the adjacent Pattern Chest to be detected as a side inventory");

        boolean foundPattern = false;
        for (int i = PartBuilderMenu.CONTAINER_SLOTS; i < PartBuilderMenu.CONTAINER_SLOTS + menu.sideInventorySlotCount; i++) {
            if (menu.getSlot(i).getItem().is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get())) {
                foundPattern = true;
                break;
            }
        }
        helper.assertTrue(foundPattern, "expected the Pattern Chest's pattern to be visible through a side-inventory slot");

        helper.succeed();
    }
}
