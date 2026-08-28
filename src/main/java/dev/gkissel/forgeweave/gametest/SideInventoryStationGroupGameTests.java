package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CraftingStationBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.menu.CraftingStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * Covers parity audit T74 (issue #505): upstream 1.12's {@code ContainerCraftingStation} excludes any
 * neighbor that is itself part of the same {@code tinkerStationBlocks} group from its generic
 * side-inventory scan -- such a neighbor gets its own workshop tab instead ({@code
 * StationGroup#tabsFor}) -- and also skips anything named by {@code craftingStationBlacklist}.
 * {@link dev.gkissel.forgeweave.block.SideInventory#findExternal} is the port; see that class's
 * javadoc for why {@code find} (Part Builder/Stencil Table's Pattern Chest lookup) keeps the old,
 * non-excluding behavior instead.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SideInventoryStationGroupGameTests {

    private static final BlockPos STATION_POS = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void patternChestInTheSameStationGroupIsNotACraftingStationSideInventory(GameTestHelper helper) {
        BlockPos chestPos = STATION_POS.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.setBlock(chestPos, ForgeweaveBlocks.PATTERN_CHEST.get());
        helper.setBlock(STATION_POS, ForgeweaveBlocks.CRAFTING_STATION.get());

        CraftingStationBlockEntity blockEntity = helper.getBlockEntity(STATION_POS);
        CraftingStationMenu menu = new CraftingStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(STATION_POS)), blockEntity.findSideInventory());

        helper.assertTrue(menu.sideInventorySlotCount == 0,
                "a Pattern Chest that is part of the station's own workshop group should get a tab, not a side "
                        + "panel too, expected 0 side-inventory slots, got " + menu.sideInventorySlotCount);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patternChestInTheSameStationGroupIsNotAToolStationSideInventory(GameTestHelper helper) {
        BlockPos chestPos = STATION_POS.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.setBlock(chestPos, ForgeweaveBlocks.PATTERN_CHEST.get());
        helper.setBlock(STATION_POS, ForgeweaveBlocks.TOOL_STATION.get());

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION_POS);
        ToolStationMenu menu = new ToolStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(STATION_POS)),
                blockEntity.findSideInventory(), blockEntity.isForge(), blockEntity.isArmorStation());

        helper.assertTrue(menu.sideInventorySlotCount == 0,
                "a Pattern Chest that is part of the station's own workshop group should not be the Tool "
                        + "Station's side panel either, expected 0 side-inventory slots, got " + menu.sideInventorySlotCount);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blacklistedNeighborIsNotACraftingStationSideInventory(GameTestHelper helper) {
        BlockPos chestPos = STATION_POS.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(STATION_POS, ForgeweaveBlocks.CRAFTING_STATION.get());
        if (helper.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND));
        }

        ForgeweaveConfig.CRAFTING_STATION_BLACKLIST.set(List.of("minecraft:chest"));
        try {
            CraftingStationBlockEntity blockEntity = helper.getBlockEntity(STATION_POS);
            CraftingStationMenu menu = new CraftingStationMenu(0, player.getInventory(), blockEntity.container(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(STATION_POS)), blockEntity.findSideInventory());

            helper.assertTrue(menu.sideInventorySlotCount == 0,
                    "a blacklisted neighbor's registry name should be skipped, expected 0 side-inventory slots, got "
                            + menu.sideInventorySlotCount);
        } finally {
            ForgeweaveConfig.CRAFTING_STATION_BLACKLIST.set(List.of());
        }
        helper.succeed();
    }
}
