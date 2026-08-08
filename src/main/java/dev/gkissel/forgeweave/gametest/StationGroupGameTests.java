package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.StationGroup;

/**
 * Covers issue #78's verification: the station-group resolution behind the GUI tab row, and the Part
 * Builder's pattern-chest button sidebar. The GUI itself is manual (see the PR's checklist); these
 * tests pin the two things that are decided server-side and would silently drift otherwise.
 *
 * <p>Every rule asserted here is upstream 1.12's -- see {@link StationGroup}'s class javadoc for
 * where each one comes from in {@code ContainerTinkerStation#detectedTinkerStationParts} and
 * {@code ContainerPartBuilder}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class StationGroupGameTests {

    private static final BlockPos CENTER = new BlockPos(1, 1, 1);

    private static void place(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
    }

    /** {@link StationGroup} works in absolute world coordinates; GameTest positions are relative. */
    private static List<BlockPos> resolveAt(GameTestHelper helper, BlockPos relative) {
        return StationGroup.resolve(helper.getLevel(), helper.absolutePos(relative));
    }

    private static boolean has(GameTestHelper helper, List<BlockPos> members, BlockPos relative) {
        return members.contains(helper.absolutePos(relative));
    }

    @GameTest(template = "empty")
    public static void groupCollectsEveryAdjacentStationKind(GameTestHelper helper) {
        place(helper, CENTER, ForgeweaveBlocks.PART_BUILDER.get());
        place(helper, CENTER.north(), ForgeweaveBlocks.CRAFTING_STATION.get());
        place(helper, CENTER.west(), ForgeweaveBlocks.TOOL_STATION.get());
        place(helper, CENTER.south(), ForgeweaveBlocks.STENCIL_TABLE.get());
        place(helper, CENTER.east(), ForgeweaveBlocks.PATTERN_CHEST.get());

        List<BlockPos> members = resolveAt(helper, CENTER);
        helper.assertTrue(members.size() == 5, "expected all five adjacent stations in the group, got " + members.size());
        helper.assertTrue(has(helper, members, CENTER), "the opened station itself should be a member");
        helper.assertTrue(has(helper, members, CENTER.north()), "the Crafting Station should be a member");
        helper.assertTrue(has(helper, members, CENTER.west()), "the Tool Station should be a member");
        helper.assertTrue(has(helper, members, CENTER.south()), "the Stencil Table should be a member");
        helper.assertTrue(has(helper, members, CENTER.east()), "the Pattern Chest should be a member");

        // Upstream sorts by descending gui number, which puts the Crafting Station first and the
        // Stencil Table last -- the tab row's left-to-right order.
        helper.assertTrue(members.get(0).equals(helper.absolutePos(CENTER.north())),
                "the Crafting Station should sort first");
        helper.assertTrue(members.get(members.size() - 1).equals(helper.absolutePos(CENTER.south())),
                "the Stencil Table should sort last");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groupStopsAtDiagonalsAndOtherLayers(GameTestHelper helper) {
        place(helper, CENTER, ForgeweaveBlocks.PART_BUILDER.get());
        place(helper, CENTER.east().south(), ForgeweaveBlocks.TOOL_STATION.get()); // diagonal
        place(helper, CENTER.above(), ForgeweaveBlocks.STENCIL_TABLE.get()); // stacked

        List<BlockPos> members = resolveAt(helper, CENTER);
        helper.assertTrue(members.size() == 1, "a lone station should be its own whole group, got " + members.size());
        helper.assertFalse(has(helper, members, CENTER.east().south()), "a diagonal station is not connected");
        helper.assertFalse(has(helper, members, CENTER.above()), "a stacked station is not connected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groupKeepsOneStationPerKind(GameTestHelper helper) {
        place(helper, CENTER, ForgeweaveBlocks.PART_BUILDER.get());
        place(helper, CENTER.east(), ForgeweaveBlocks.PART_BUILDER.get());
        place(helper, CENTER.north(), ForgeweaveBlocks.CRAFTING_STATION.get());

        List<BlockPos> members = resolveAt(helper, CENTER);
        helper.assertTrue(members.size() == 2,
                "two Part Builders should contribute one tab, expected 2 members, got " + members.size());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tabRowNeedsACraftingStation(GameTestHelper helper) {
        place(helper, CENTER, ForgeweaveBlocks.PART_BUILDER.get());
        place(helper, CENTER.east(), ForgeweaveBlocks.TOOL_STATION.get());

        BlockPos absolute = helper.absolutePos(CENTER);
        helper.assertTrue(StationGroup.tabsFor(helper.getLevel(), absolute).isEmpty(),
                "upstream shows no tabs until the group contains a Crafting Station");

        place(helper, CENTER.north(), ForgeweaveBlocks.CRAFTING_STATION.get());
        StationGroup tabs = StationGroup.tabsFor(helper.getLevel(), absolute);
        helper.assertTrue(tabs.members().size() == 3, "expected three tabs, got " + tabs.members().size());
        helper.assertTrue(tabs.selected() == 2,
                "the opened Part Builder should be the selected tab (index 2 of crafting/tool/builder), got "
                        + tabs.selected());
        helper.succeed();
    }

    // ---------------------------------------------------------------- pattern chest sidebar

    /**
     * The Part Builder's full "part crafter" workshop: a Pattern Chest feeding the sidebar, plus the
     * Stencil Table and Crafting Station upstream's {@code partCrafter} check also demands.
     */
    private static PartBuilderMenu openPartCrafter(GameTestHelper helper, Player player, boolean withCraftingStation) {
        place(helper, CENTER, ForgeweaveBlocks.PART_BUILDER.get());
        place(helper, CENTER.east(), ForgeweaveBlocks.PATTERN_CHEST.get());
        place(helper, CENTER.south(), ForgeweaveBlocks.STENCIL_TABLE.get());
        if (withCraftingStation) {
            place(helper, CENTER.north(), ForgeweaveBlocks.CRAFTING_STATION.get());
        }
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(CENTER);
        return new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(CENTER)),
                blockEntity.findSideInventory());
    }

    @GameTest(template = "empty")
    public static void patternButtonSwapsTheLoadedPattern(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openPartCrafter(helper, player, true);
        ChestBlockEntity chest = helper.getBlockEntity(CENTER.east());
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_SHOVEL_HEAD.get()));

        helper.assertTrue(menu.partCrafter, "an attached Pattern Chest plus Stencil Table and Crafting Station "
                + "should turn the side panel into the pattern sidebar");
        // Pickaxe head (id 0) is in the chest; shovel head (id 1) is the loaded one -- upstream keeps
        // the loaded pattern on the row so the current selection never vanishes mid-use.
        helper.assertTrue(menu.patternButtons().equals(List.of(0, 1)),
                "expected buttons for the chest's pattern and the loaded one, got " + menu.patternButtons());

        helper.assertTrue(menu.clickMenuButton(player, 0), "clicking the pickaxe-head button should succeed");
        helper.assertTrue(menu.getSlot(PartBuilderMenu.PATTERN_SLOT).getItem()
                        .is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()),
                "the clicked pattern should be loaded into the pattern slot");
        helper.assertTrue(chest.container().getItem(0).is(ForgeweaveItems.PATTERN_SHOVEL_HEAD.get()),
                "upstream exchanges: the previously loaded pattern goes back into the chest slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patternSidebarNeedsTheWholeWorkshop(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openPartCrafter(helper, player, false);
        ChestBlockEntity chest = helper.getBlockEntity(CENTER.east());
        chest.container().setItem(0, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));

        helper.assertFalse(menu.partCrafter,
                "without a Crafting Station the chest stays an ordinary side inventory, as upstream has it");
        helper.assertTrue(menu.patternButtons().isEmpty(), "no sidebar means no pattern buttons");
        helper.assertFalse(menu.clickMenuButton(player, 0), "and the button ids do nothing");
        helper.succeed();
    }
}
