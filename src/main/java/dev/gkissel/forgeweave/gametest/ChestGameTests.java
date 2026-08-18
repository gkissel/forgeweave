package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
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
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
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
 * <p>Issue #305's self-expanding capacity is covered from {@link #fillingTheLastSlotGrowsCapacityByOneSlot}
 * onward: upstream {@code TileTinkerChest}'s one-slot-at-a-time growth and shrink (restored by parity
 * audit T45, issue #476, once the GUI scrolled instead of paging), the 256-slot cap, filters still
 * holding on a grown chest, scrolling the window through {@code ChestMenu}, and a save/load round
 * trip well past the first screenful.
 *
 * <p>Issue #478 (parity audit T47) adds the break/place round trip: a harvested chest carries its
 * contents on the dropped item instead of spilling them, placing that item back restores both the
 * contents and the capacity that reaches them, and {@code chestsKeepInventory=false} is still the
 * old spill.
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

    // ------------------------------------------------------------------ issue #506 (T75): right-click insert

    /** Upstream {@code BlockToolTable#onBlockActivated}: a held item that fits is inserted without opening the GUI. */
    @GameTest(template = "empty")
    public static void rightClickWithAHeldItemInsertsItWithoutOpeningTheChest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PART);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()));

        boolean inserted = chest.insertHeldItem(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(inserted, "expected the held part to be inserted");
        helper.assertTrue(chest.container().getItem(0).is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected the part to land in the chest");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "expected the held stack to be consumed");

        helper.succeed();
    }

    /** A rejected item (the chest's filter refuses it outright) leaves the hand untouched, so the GUI opens instead. */
    @GameTest(template = "empty")
    public static void rightClickWithARejectedItemInsertsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PART);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()));

        boolean inserted = chest.insertHeldItem(player, InteractionHand.MAIN_HAND);

        helper.assertFalse(inserted, "expected a pattern to be rejected by the Part Chest's filter");
        helper.assertTrue(chest.container().isEmpty(), "expected nothing to land in the chest");
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1,
                "expected the held stack to be untouched");

        helper.succeed();
    }

    /** Upstream's own rule: even a partial insert (only some of the stack fits) still counts as a hit. */
    @GameTest(template = "empty")
    public static void rightClickWithAPartialFitStillInsertsWhatFits(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PART);
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.SHARD.get(), 63)); // one short of a full stack
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.SHARD.get(), 2));

        boolean inserted = chest.insertHeldItem(player, InteractionHand.MAIN_HAND);

        helper.assertTrue(inserted, "expected the partial fit to still count as an insert");
        helper.assertValueEqual(chest.container().getItem(0).getCount(), 64, "expected the existing stack to top up");
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1,
                "expected only the leftover shard to remain in hand");

        helper.succeed();
    }

    /** An empty hand does nothing (there is nothing to insert), so the GUI still opens as before. */
    @GameTest(template = "empty")
    public static void rightClickWithAnEmptyHandInsertsNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PART);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.assertFalse(chest.insertHeldItem(player, InteractionHand.MAIN_HAND),
                "expected an empty hand to insert nothing");

        helper.succeed();
    }

    // ------------------------------------------------------------------ issue #477 (T46): chest rules

    /** Upstream {@code TilePatternChest}: only one of each part pattern, not unlimited duplicates. */
    @GameTest(template = "empty")
    public static void patternChestRejectsADuplicatePatternButAcceptsADifferentOne(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PATTERN);
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));

        helper.assertFalse(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get())),
                "expected a second pickaxe head pattern to be rejected as a duplicate");
        helper.assertTrue(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.PATTERN_SHOVEL_HEAD.get())),
                "expected a different pattern to still be accepted");

        helper.succeed();
    }

    /** Stack size 1 (upstream {@code TilePatternChest(MAX_INVENTORY, 1)}): even the first stack can't exceed 1. */
    @GameTest(template = "empty")
    public static void patternChestLimitsStacksToOne(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PATTERN);

        helper.assertValueEqual(chest.container().getMaxStackSize(), 1, "Pattern/Cast Chest slot stack limit");
        helper.assertTrue(chest.container().getMaxStackSize(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get(), 64)) == 1,
                "expected the chest to clamp even a 64-stack pattern down to 1");

        helper.succeed();
    }

    /** Cast-chest mode (upstream's {@code ICast} branch, now that #100/#222 shipped casts). */
    @GameTest(template = "empty")
    public static void patternChestAcceptsCastsAndDoesNotMixThemWithPatterns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PATTERN);

        helper.assertTrue(chest.container().canPlaceItem(0, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get())),
                "expected an empty Pattern Chest to accept a cast too");
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()));

        helper.assertFalse(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())),
                "expected a cast chest to reject a pattern");
        helper.assertFalse(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get())),
                "expected a cast chest to reject a duplicate cast");
        helper.assertTrue(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.CAST_SHOVEL_HEAD.get())),
                "expected a cast chest to accept a different cast");
        helper.assertTrue(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.CLAY_CASTS.get("cast_shovel_head").get())),
                "expected a cast chest to accept a clay cast too (upstream's clayCast also implements ICast)");

        helper.succeed();
    }

    /** Same-stack-only rule for the Part Chest (upstream's {@code i == slot} check). */
    @GameTest(template = "empty")
    public static void partChestOnlyAcceptsADuplicateIntoItsOwnSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PART);
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()));

        helper.assertTrue(chest.container().canPlaceItem(0, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "expected the Part Chest to keep stacking a duplicate into the slot it already occupies");
        helper.assertFalse(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "expected the Part Chest to refuse spreading a duplicate into a different slot");
        helper.assertTrue(chest.container().canPlaceItem(1, new ItemStack(ForgeweaveItems.PART_SHOVEL_HEAD.get())),
                "expected the Part Chest to still accept a different part in a new slot");

        helper.succeed();
    }

    /** Upstream {@code TilePatternChest#getName}: "Cast Chest" once it holds a cast. */
    @GameTest(template = "empty")
    public static void patternChestDisplayNameSwitchesToCastChestOnceItHoldsACast(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos, ChestKind.PATTERN);

        helper.assertTrue(chest.getDisplayName().getString().equals("Pattern Chest"),
                "expected an empty chest to keep the default name");

        chest.container().setItem(0, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()));
        helper.assertTrue(chest.getDisplayName().getString().equals("Cast Chest"),
                "expected a chest holding a cast to rename itself");

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

    // -------------------------------------------------- issue #305 / audit T45: self-expanding capacity

    private static ChestBlockEntity placeChest(GameTestHelper helper, BlockPos pos) {
        return placeChest(helper, pos, ChestKind.PART);
    }

    private static ChestBlockEntity placeChest(GameTestHelper helper, BlockPos pos, ChestKind kind) {
        helper.setBlock(pos, kind == ChestKind.PATTERN ? ForgeweaveBlocks.PATTERN_CHEST.get() : ForgeweaveBlocks.PART_CHEST.get());
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

    /** Upstream {@code TileTinkerChest}: the chest is always its contents plus exactly one free slot. */
    @GameTest(template = "empty")
    public static void fillingTheLastSlotGrowsCapacityByOneSlot(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));

        helper.assertValueEqual(chest.container().getContainerSize(), 1, "starting capacity");
        fill(chest, 0, 1);
        helper.assertValueEqual(chest.container().getContainerSize(), 2, "capacity after the first item");
        fill(chest, 1, 30);
        helper.assertValueEqual(chest.container().getContainerSize(), 31, "capacity after thirty items");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capacityNeverGrowsPast256(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));

        fill(chest, 0, 255);
        helper.assertValueEqual(chest.container().getContainerSize(), 256, "capacity one slot short of the cap");

        // The 256th slot (index 255) is the last slot the chest will ever have; filling it must not
        // push capacity past the cap.
        chest.container().setItem(255, new ItemStack(ForgeweaveItems.SHARD.get()));
        helper.assertValueEqual(chest.container().getContainerSize(), 256, "capacity is capped at 256");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyingTheLastSlotShrinksCapacityBackDown(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));

        fill(chest, 0, 10);
        helper.assertValueEqual(chest.container().getContainerSize(), 11, "capacity after ten items");

        chest.container().setItem(9, ItemStack.EMPTY);
        helper.assertValueEqual(chest.container().getContainerSize(), 10, "capacity after the last item left");

        // A hole below a filled slot keeps the capacity; only trailing empties collapse.
        chest.container().setItem(5, ItemStack.EMPTY);
        helper.assertValueEqual(chest.container().getContainerSize(), 10, "a hole in the middle keeps the capacity");
        for (int slot = 8; slot >= 0; slot--) {
            chest.container().setItem(slot, ItemStack.EMPTY);
        }
        helper.assertValueEqual(chest.container().getContainerSize(), 1, "an emptied chest is back to one slot");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void filtersStillHoldOnAGrownChest(GameTestHelper helper) {
        ChestBlockEntity chest = placeChest(helper, new BlockPos(1, 1, 1));
        fill(chest, 0, 60); // well past the 24-slot window the GUI shows at a time

        helper.assertTrue(chest.container().canPlaceItem(60, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "expected the Part Chest's filter to still accept a part on its newly grown slot");
        helper.assertFalse(chest.container().canPlaceItem(60, new ItemStack(ForgeweaveItems.PATTERN_BLANK.get())),
                "expected the Part Chest's filter to still reject a pattern on its newly grown slot");

        helper.succeed();
    }

    /**
     * Parity audit T45 (issue #476): every one of the chest's slots is in the menu the whole time,
     * and scrolling moves which of them the GUI shows -- upstream {@code GuiScalingChest} over
     * {@code GuiDynInventory}. Replaces the page-button test issue #305 shipped.
     */
    @GameTest(template = "empty")
    public static void scrollingMovesTheWindowOverTheGrownChest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChestBlockEntity chest = placeChest(helper, pos);
        fill(chest, 0, ChestMenu.VISIBLE_SLOTS + ChestMenu.COLUMNS); // one row more than fits
        chest.container().setItem(ChestMenu.COLUMNS, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()));

        ChestMenu menu = new ChestMenu(ChestKind.PART, 0, player.getInventory(), chest.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
        helper.assertValueEqual(menu.capacity(), ChestMenu.VISIBLE_SLOTS + ChestMenu.COLUMNS + 1,
                "the menu's synced capacity");
        helper.assertValueEqual(ChestMenu.maxScrollRow(menu.capacity()), 2, "how far this chest scrolls");

        // Unscrolled: the second row's first slot holds the pickaxe head, drawn on the second row.
        helper.assertTrue(menu.getSlot(ChestMenu.COLUMNS).getItem().is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected the second row's first slot to hold the pickaxe head");
        helper.assertValueEqual(menu.getSlot(ChestMenu.COLUMNS).y, ChestMenu.slotY(ChestMenu.COLUMNS),
                "the second row's y before scrolling");

        menu.scrollTo(1);
        helper.assertValueEqual(menu.getSlot(ChestMenu.COLUMNS).y, ChestMenu.slotY(0),
                "scrolling one row must lift the second row to the top of the window");
        helper.assertFalse(menu.getSlot(0).isActive(), "the scrolled-away first row must not be drawn");
        helper.assertTrue(menu.getSlot(ChestMenu.VISIBLE_SLOTS).isActive(),
                "the row scrolled into view must now be drawn");
        // Never drawn either way: the chest has not grown that far.
        helper.assertFalse(menu.getSlot(ChestBlockEntity.MAX_SLOTS - 1).isActive(),
                "a slot past the chest's capacity must stay hidden");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveLoadRoundTripPreservesContentsAndCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ChestBlockEntity chest = placeChest(helper, pos);
        fillFullStacks(chest, 0, 60); // well past one screenful

        CompoundTag saved = chest.saveWithId(helper.getLevel().registryAccess());
        BlockEntity reloaded = BlockEntity.loadStatic(pos, chest.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(reloaded instanceof ChestBlockEntity, "expected the saved tag to still decode as a chest");
        ChestBlockEntity reloadedChest = (ChestBlockEntity) reloaded;

        helper.assertValueEqual(reloadedChest.container().getContainerSize(), 61,
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

    // ------------------------------------------------------------------ issue #478 (audit T47): chests keep their inventory

    /**
     * Stocks a Part Chest well past one screenful. Upstream {@code BlockToolTable#keepInventory} +
     * {@code BlockTable#writeDataOntoItemstack}: a harvested Pattern or Part Chest writes its
     * contents onto the dropped item rather than spilling them, behind {@code
     * Config.chestsKeepInventory} (default on). Here that ride-along is the vanilla
     * {@link DataComponents#CONTAINER} component the chest loot tables copy off the block entity --
     * the same {@code minecraft:copy_components} mechanism the retextured tables already use.
     */
    private static ChestBlockEntity stockedChest(GameTestHelper helper, BlockPos pos) {
        ChestBlockEntity chest = placeChest(helper, pos);
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()));
        chest.container().setItem(53, new ItemStack(ForgeweaveItems.SHARD.get(), 7)); // well past one screenful
        chest.container().setItem(60, new ItemStack(ForgeweaveItems.PART_TOUGH_TOOL_ROD.get()));
        return chest;
    }

    /** The one dropped chest item, asserting the total number of dropped entities along the way. */
    private static ItemStack theOneDroppedChest(GameTestHelper helper, BlockPos pos, int expectedDrops) {
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(pos)).inflate(4.0D));
        helper.assertValueEqual(dropped.size(), expectedDrops, "number of dropped item entities");
        ItemStack stack = dropped.stream()
                .map(ItemEntity::getItem)
                .filter(item -> item.is(ForgeweaveBlocks.PART_CHEST.get().asItem()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
        helper.assertFalse(stack.isEmpty(), "expected the broken chest to drop itself");
        return stack;
    }

    @GameTest(template = "empty")
    public static void harvestedChestCarriesItsContentsOnTheDroppedItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        stockedChest(helper, pos);

        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

        // Only the chest itself: nothing spilled alongside it, or the contents would be duplicated.
        ItemStack stack = theOneDroppedChest(helper, pos, 1);
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        helper.assertTrue(contents != null, "expected the dropped chest to carry its contents");
        helper.assertTrue(contents.getStackInSlot(0).is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected slot 0's pickaxe head to ride along on the item");
        helper.assertValueEqual(contents.getStackInSlot(53).getCount(), 7, "the shard stack's size");
        helper.assertTrue(contents.getStackInSlot(60).is(ForgeweaveItems.PART_TOUGH_TOOL_ROD.get()),
                "expected the far tool rod to ride along at its own slot");

        helper.succeed();
    }

    /** The other half: placing that item back restores the contents, and with them the grown capacity. */
    @GameTest(template = "empty")
    public static void placingAKeptChestRestoresItsContentsAndCapacity(GameTestHelper helper) {
        BlockPos broken = new BlockPos(1, 2, 1);
        stockedChest(helper, broken);
        helper.getLevel().destroyBlock(helper.absolutePos(broken), true);
        ItemStack stack = theOneDroppedChest(helper, broken, 1);

        BlockPos placed = new BlockPos(3, 2, 1);
        ChestBlockEntity chest = placeChest(helper, placed);
        chest.applyComponentsFromItemStack(stack); // exactly what BlockItem does on placement

        helper.assertTrue(chest.container().getItem(0).is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected slot 0's pickaxe head back");
        helper.assertValueEqual(chest.container().getItem(53).getCount(), 7, "the restored shard stack's size");
        helper.assertTrue(chest.container().getItem(60).is(ForgeweaveItems.PART_TOUGH_TOOL_ROD.get()),
                "expected the far tool rod back at its own slot");
        helper.assertValueEqual(chest.container().getContainerSize(), 62,
                "capacity has to grow back to reach the restored slot 60, plus its free slot");

        helper.succeed();
    }

    /** With the option off, upstream's chest is a plain container again: the contents spill. */
    @GameTest(template = "empty")
    public static void chestsKeepInventoryOffSpillsTheContentsInstead(GameTestHelper helper) {
        ForgeweaveConfig.CHESTS_KEEP_INVENTORY.set(false);
        try {
            BlockPos pos = new BlockPos(1, 2, 1);
            stockedChest(helper, pos);

            helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

            ItemStack stack = theOneDroppedChest(helper, pos, 4); // the chest plus its three stacks
            helper.assertTrue(stack.get(DataComponents.CONTAINER) == null,
                    "expected no contents on the item when the option is off -- they spilled instead");
        } finally {
            ForgeweaveConfig.CHESTS_KEEP_INVENTORY.set(true);
        }

        helper.succeed();
    }

    /**
     * A creative break drops no loot at all, so the contents would simply vanish. Vanilla's shulker
     * box spawns the packed item itself in that case ({@code ShulkerBoxBlock#playerWillDestroy});
     * this does the same rather than reproducing upstream's own silent loss there.
     */
    @GameTest(template = "empty")
    public static void creativeBreakStillHandsBackTheContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        stockedChest(helper, pos);
        BlockPos absolute = helper.absolutePos(pos);

        helper.getBlockState(pos).getBlock().playerWillDestroy(helper.getLevel(), absolute,
                helper.getBlockState(pos), helper.makeMockPlayer(GameType.CREATIVE));

        ItemStack stack = theOneDroppedChest(helper, pos, 1);
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        helper.assertTrue(contents != null, "expected the creative break to hand back a packed chest");
        helper.assertTrue(contents.getStackInSlot(0).is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected the packed chest to carry slot 0's pickaxe head");

        helper.succeed();
    }

    /**
     * The restore path refills slots by index rather than sequentially, so a lone item at slot 200
     * has to grow the capacity all the way there in one go to become reachable again. (The
     * save/load path can't hit this: {@code SimpleContainer}'s list is positionless and its reload
     * compacts everything back down to slot 0 -- see {@code m3_3_pattern_chest_inventory.snbt}.)
     */
    @GameTest(template = "empty")
    public static void restoringALoneFarOffItemGrowsCapacityAllTheWayBack(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        ChestBlockEntity chest = placeChest(helper, pos);
        fillFullStacks(chest, 0, 201); // grown one slot at a time to 202
        for (int slot = 0; slot < 200; slot++) {
            chest.container().setItem(slot, ItemStack.EMPTY); // slot 200 is still filled, so nothing shrinks
        }
        helper.assertValueEqual(chest.container().getContainerSize(), 202, "capacity before breaking");

        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        ItemStack stack = theOneDroppedChest(helper, pos, 1);

        ChestBlockEntity restored = placeChest(helper, new BlockPos(3, 2, 1));
        restored.applyComponentsFromItemStack(stack);

        helper.assertValueEqual(restored.container().getContainerSize(), 202, "capacity after restoring");
        helper.assertTrue(restored.container().getItem(200).is(ForgeweaveItems.SHARD.get()),
                "expected the lone far-off shard back at slot 200");

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
