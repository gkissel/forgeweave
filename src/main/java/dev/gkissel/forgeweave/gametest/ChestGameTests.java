package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
 *
 * <p>Issue #305's self-expanding capacity is covered from {@link #fillingTheLastSlotGrowsCapacityByOnePage}
 * onward: growth/shrink at each {@code ChestBlockEntity.CAPACITY_STEPS} boundary, the 256-slot cap,
 * filters still holding on a grown chest, paging through {@code ChestMenu}, and a save/load round
 * trip that crosses a page boundary.
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

    // ------------------------------------------------------------------ issue #305: self-expanding capacity

    private static ChestBlockEntity placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ForgeweaveBlocks.PART_CHEST.get());
        return helper.getBlockEntity(pos);
    }

    private static void fill(ChestBlockEntity chest, int from, int toExclusive) {
        for (int i = from; i < toExclusive; i++) {
            chest.container().setItem(i, new ItemStack(ForgeweaveItems.SHARD.get()));
        }
    }

    /**
     * Full-size (64) stacks rather than {@link #fill}'s singles: a save/load round trip decodes
     * through {@code SimpleContainer#fromTag}, which refills via {@code addItem} -- same-type stacks
     * under their max size merge into one slot there (the save-compat fixture this issue adds notes
     * the same thing), which would silently collapse this test's slot count. A full stack can't
     * accept another item's, so each one keeps its own slot.
     */
    private static void fillFullStacks(ChestBlockEntity chest, int from, int toExclusive) {
        for (int i = from; i < toExclusive; i++) {
            chest.container().setItem(i, new ItemStack(ForgeweaveItems.SHARD.get(), 64));
        }
    }

    @GameTest(template = "empty")
    public static void fillingTheLastSlotGrowsCapacityByOnePage(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));

        helper.assertValueEqual(chest.container().getContainerSize(), 54, "starting capacity");
        fill(chest, 0, 53); // every slot but the last one of page 1
        helper.assertValueEqual(chest.container().getContainerSize(), 54, "capacity before the last slot fills");

        chest.container().setItem(53, new ItemStack(ForgeweaveItems.SHARD.get())); // the N+1th item
        helper.assertValueEqual(chest.container().getContainerSize(), 108, "capacity after the last slot fills");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capacityNeverGrowsPast256(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));

        // Walk every growth boundary in turn: 54 -> 108 -> 162 -> 216 -> 256.
        for (int lastSlot : new int[] {53, 107, 161, 215}) {
            chest.container().setItem(lastSlot, new ItemStack(ForgeweaveItems.SHARD.get()));
        }
        helper.assertValueEqual(chest.container().getContainerSize(), 256, "capacity after four growth steps");

        // The 256th slot (index 255) is the last slot the chest will ever have; filling it must not
        // push capacity past the cap.
        chest.container().setItem(255, new ItemStack(ForgeweaveItems.SHARD.get()));
        helper.assertValueEqual(chest.container().getContainerSize(), 256, "capacity is capped at 256");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyingTheGrownPageShrinksCapacityBackDown(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));

        chest.container().setItem(53, new ItemStack(ForgeweaveItems.SHARD.get()));
        helper.assertValueEqual(chest.container().getContainerSize(), 108, "capacity after growing once");

        chest.container().setItem(53, ItemStack.EMPTY);
        helper.assertValueEqual(chest.container().getContainerSize(), 54, "capacity after the grown page emptied out");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filtersStillHoldOnAGrownChest(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));
        fill(chest, 0, 54); // grows capacity to 108, opening the second page

        helper.assertTrue(chest.container().canPlaceItem(60, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "expected the Part Chest's filter to still accept a part on its newly grown second page");
        helper.assertFalse(chest.container().canPlaceItem(60, new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())),
                "expected the Part Chest's filter to still reject a pattern on its newly grown second page");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nextPageButtonExposesTheGrownPageThroughTheMenu(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChestBlockEntity chest = placeChest(helper, pos);
        ChestMenu menu = new ChestMenu(ChestKind.PART, 0, player.getInventory(), chest.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));

        helper.assertFalse(menu.clickMenuButton(player, ChestMenu.BUTTON_NEXT_PAGE),
                "expected the next-page button to refuse a single-page chest");

        fill(chest, 0, 54); // grows capacity to 108
        chest.container().setItem(54, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()));

        helper.assertTrue(menu.clickMenuButton(player, ChestMenu.BUTTON_NEXT_PAGE),
                "expected the next-page button to accept once the chest has grown a second page");
        helper.assertValueEqual(menu.currentPage(), 1, "the menu's page after clicking next");
        helper.assertTrue(menu.getSlot(0).getItem().is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected the menu's slot 0 to now show the second page's absolute slot 54");

        helper.assertTrue(menu.clickMenuButton(player, ChestMenu.BUTTON_PREVIOUS_PAGE),
                "expected the previous-page button to accept back to page 0");
        helper.assertValueEqual(menu.currentPage(), 0, "the menu's page after clicking previous");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveLoadRoundTripAcrossAPageBoundaryPreservesContentsAndCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos);
        fillFullStacks(chest, 0, 60); // crosses the 54-slot boundary, growing capacity to 108

        CompoundTag saved = chest.saveWithId(helper.getLevel().registryAccess());
        BlockEntity reloaded = BlockEntity.loadStatic(pos, chest.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(reloaded instanceof ChestBlockEntity, "expected the saved tag to still decode as a chest");
        ChestBlockEntity reloadedChest = (ChestBlockEntity) reloaded;

        helper.assertValueEqual(reloadedChest.container().getContainerSize(), 108,
                "capacity must survive a save/load round trip");
        int count = 0;
        for (int i = 0; i < reloadedChest.container().getContainerSize(); i++) {
            if (!reloadedChest.container().getItem(i).isEmpty()) {
                count++;
            }
        }
        helper.assertValueEqual(count, 60, "every one of the 60 stored shards must survive the round trip");

        helper.succeed();
    }

    /**
     * Issue #342: the in-world hitbox has to follow the cabinet model (upstream's
     * {@code BlockToolTable.BOUNDS_Chest}), not the full cube both chests shipped with -- so the gap
     * between the legs, under the body, must be empty while the body itself is solid.
     */
    @GameTest(template = "empty")
    public static void bothChestsHaveTheCabinetHitboxRatherThanACube(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (var chest : List.of(ForgeweaveBlocks.PATTERN_CHEST.get(), ForgeweaveBlocks.PART_CHEST.get())) {
            helper.setBlock(pos, chest);
            VoxelShape shape = helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos));

            helper.assertFalse(
                    Shapes.joinIsNotEmpty(shape, Shapes.box(0.4D, 0.05D, 0.4D, 0.6D, 0.15D, 0.6D), BooleanOp.AND),
                    "expected the space between " + chest + "'s legs to be open, not a full cube");
            helper.assertTrue(
                    Shapes.joinIsNotEmpty(shape, Shapes.box(0.4D, 0.5D, 0.4D, 0.6D, 0.6D, 0.6D), BooleanOp.AND),
                    "expected " + chest + "'s body to still be solid");
        }

        helper.succeed();
    }
}
