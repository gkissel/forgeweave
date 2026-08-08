package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CraftingStationBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.menu.CraftingStationMenu;

/**
 * Covers docs/SCOPE.md M1 issue #40's verification: the crafting grid persists across closing and
 * reopening the menu (the block entity's container, not a transient one -- see {@link
 * CraftingStationBlockEntity} javadoc), it produces the actual vanilla recipe result via the real
 * recipe manager, and an adjacent chest's inventory is visible through the menu's side-inventory
 * slots ({@code CraftingStationBlockEntity#findSideInventory}).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CraftingStationGameTests {

    private static CraftingStationMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.CRAFTING_STATION.get());
        CraftingStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new CraftingStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
    }

    @GameTest(template = "empty")
    public static void gridContentsPersistAcrossReopeningTheMenu(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(pos, ForgeweaveBlocks.CRAFTING_STATION.get());
        CraftingStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        ContainerLevelAccess access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos));

        CraftingStationMenu firstMenu = new CraftingStationMenu(0, player.getInventory(), blockEntity.container(), access, null);
        firstMenu.getSlot(0).set(new ItemStack(Items.OAK_PLANKS));
        firstMenu.removed(player); // simulates closing the GUI

        CraftingStationMenu secondMenu = new CraftingStationMenu(1, player.getInventory(), blockEntity.container(), access, null);
        helper.assertTrue(secondMenu.getSlot(0).getItem().is(Items.OAK_PLANKS),
                "expected the grid contents to survive reopening the menu, got " + secondMenu.getSlot(0).getItem());

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingGridResolvesTheRealVanillaRecipe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingStationMenu menu = openMenu(helper, pos, player);

        // Two planks stacked in one column matches vanilla's real stick recipe (shape "#","#") --
        // going through the actual RecipeManager, not a duplicated recipe table.
        menu.getSlot(0).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlot(3).set(new ItemStack(Items.OAK_PLANKS));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(CraftingStationMenu.GRID_SLOTS).getItem();
        helper.assertTrue(output.is(Items.STICK) && output.getCount() == 4,
                "expected 4 sticks from two stacked planks, got " + output);

        menu.getSlot(CraftingStationMenu.GRID_SLOTS).onTake(player, output);
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                "expected both planks to be consumed by taking the result");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adjacentChestInventoryIsExposedThroughTheMenu(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = stationPos.east();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(stationPos, ForgeweaveBlocks.CRAFTING_STATION.get());

        if (!(helper.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            helper.fail("expected a chest block entity next to the station");
            return;
        }
        chest.setItem(0, new ItemStack(Items.DIAMOND));

        CraftingStationBlockEntity blockEntity = helper.getBlockEntity(stationPos);
        CraftingStationMenu menu = new CraftingStationMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(stationPos)), blockEntity.findSideInventory());

        helper.assertTrue(menu.sideInventorySlotCount > 0, "expected the adjacent chest to be detected as a side inventory");

        boolean foundDiamond = false;
        for (int i = CraftingStationMenu.CONTAINER_SLOTS; i < CraftingStationMenu.CONTAINER_SLOTS + menu.sideInventorySlotCount; i++) {
            if (menu.getSlot(i).getItem().is(Items.DIAMOND)) {
                foundDiamond = true;
                break;
            }
        }
        helper.assertTrue(foundDiamond, "expected the chest's diamond to be visible through a side-inventory slot");

        helper.succeed();
    }
}
