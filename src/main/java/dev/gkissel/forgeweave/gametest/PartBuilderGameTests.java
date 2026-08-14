package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.StationMenu;

/**
 * Covers docs/SCOPE.md M1 issue #9's verification: pattern + material -> correct part item, and
 * that the pattern stays in the slot afterward (upstream 1.12 stencils are reusable; see
 * {@link PartBuilderMenu} class javadoc). Exercises the real menu (not a duplicate of its logic).
 *
 * <p>Also covers issue #45's value-based crafting: logs/shards as input and the shard change
 * deposited into the second output slot (see {@code PartBuilderRecipes}'s class javadoc for the
 * shard-unit value table this math is built on).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PartBuilderGameTests {

    private static PartBuilderMenu openMenu(GameTestHelper helper, BlockPos pos, Player player) {
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        return new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    @GameTest(template = "empty")
    public static void patternAndMaterialProduceMatchingPart(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        // Stone's cobblestone crafting item is worth 2 shard-units each (PartBuilderRecipes); a
        // pickaxe head costs 4, so 2 cobblestone exactly covers it with no shard change.
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(Items.COBBLESTONE, 2));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part, got " + output);
        helper.assertTrue(
                materialId("stone").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the pickaxe head's material to be forgeweave:stone, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        // Simulates taking the crafted item: the pattern is reusable (upstream 1.12 behavior), only
        // the material is consumed.
        menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertFalse(menu.getSlot(PartBuilderMenu.PATTERN_SLOT).getItem().isEmpty(),
                "expected the pattern to remain (reusable)");
        helper.assertTrue(menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem().isEmpty(),
                "expected the material to be fully consumed");
        helper.assertTrue(menu.getSlot(PartBuilderMenu.CHANGE_SLOT).getItem().isEmpty(),
                "expected no shard change for an exact-value craft");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void logProducesHeadAndShardChange(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        // A log is worth 8 shard-units (4 upstream ingots); a pickaxe head costs 4, so one log
        // covers it with 4 shard-units (4 shards) left over -- the upstream second-output behavior.
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(Items.OAK_LOG, 1));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part, got " + output);
        helper.assertTrue(materialId("wood").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the pickaxe head's material to be forgeweave:wood, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem().isEmpty(),
                "expected the log to be fully consumed");

        ItemStack change = menu.getSlot(PartBuilderMenu.CHANGE_SLOT).getItem();
        helper.assertTrue(change.is(ForgeweaveItems.SHARD.get()), "expected shard change, got " + change);
        helper.assertTrue(change.getCount() == 4, "expected 4 wood shards of change, got " + change.getCount());
        helper.assertTrue(materialId("wood").equals(change.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected wood shard change, got " + change.get(ForgeweaveDataComponents.MATERIAL.get()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shardsAreUsableAsCraftingInput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_TOOL_HANDLE.get()));
        // A tool handle costs 2 shard-units; 2 wood shards (1 unit each) covers it exactly.
        ItemStack shards = new ItemStack(ForgeweaveItems.SHARD.get(), 2);
        shards.set(ForgeweaveDataComponents.MATERIAL.get(), materialId("wood"));
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(shards);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_TOOL_HANDLE.get()),
                "expected a tool handle part, got " + output);
        helper.assertTrue(materialId("wood").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the tool handle's material to be forgeweave:wood, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem().isEmpty(),
                "expected both shards to be consumed");
        helper.assertTrue(menu.getSlot(PartBuilderMenu.CHANGE_SLOT).getItem().isEmpty(),
                "expected no further change for an exact-value craft");

        helper.succeed();
    }

    /**
     * Issue #306 regression: upstream's second material input ({@code
     * ContainerPartBuilder#input2}/{@code ToolBuilder#tryBuildToolPart}) contributes to the cost
     * match too, not just the first slot -- a 2-cost part built from 1 shard in each slot.
     */
    @GameTest(template = "empty")
    public static void secondMaterialSlotContributesToTheCombinedCost(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_TOOL_HANDLE.get()));
        // A tool handle costs 2 shard-units; neither slot alone has enough (1 unit each), but split
        // 1+1 across both slots they combine to exactly cover it.
        ItemStack shard1 = new ItemStack(ForgeweaveItems.SHARD.get(), 1);
        shard1.set(ForgeweaveDataComponents.MATERIAL.get(), materialId("wood"));
        ItemStack shard2 = new ItemStack(ForgeweaveItems.SHARD.get(), 1);
        shard2.set(ForgeweaveDataComponents.MATERIAL.get(), materialId("wood"));
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(shard1);
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT_2).set(shard2);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_TOOL_HANDLE.get()),
                "expected a tool handle part built from the combined 1+1 shards, got " + output);

        menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).onTake(player, output);
        helper.assertTrue(menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem().isEmpty(),
                "expected the first slot's shard to be consumed");
        helper.assertTrue(menu.getSlot(PartBuilderMenu.MATERIAL_SLOT_2).getItem().isEmpty(),
                "expected the second slot's shard to be consumed");
        helper.assertTrue(menu.getSlot(PartBuilderMenu.CHANGE_SLOT).getItem().isEmpty(),
                "expected no further change for an exact-value combined craft");

        helper.succeed();
    }

    /**
     * Issue #378: the two messages upstream's {@code GuiPartBuilder} shows and this station had
     * neither of ({@code :143-189}). Both are asked of {@link PartBuilderRecipes#rejection}, which is
     * where the choice between them lives; the screen only takes its panel over with the answer.
     *
     * <p>The classification is upstream's own. {@code invalid_pattern} is thrown by
     * {@code ToolBuilder#tryBuildToolPart:410} as a {@code TinkerGuiException}, i.e. a craft that was
     * attempted and refused -- an <b>error</b>. {@code useless_tool_part} is derived by the GUI from
     * what is already on the output slot ({@code :152-157}) and calls {@code warning} instead.
     */
    @GameTest(template = "empty")
    public static void thePartBuilderExplainsABadPatternAndAnUnusableOutput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        helper.assertTrue(PartBuilderRecipes.rejection(registries, ItemStack.EMPTY, ItemStack.EMPTY).isEmpty(),
                "an empty station has nothing to complain about");

        // The menu's slot filter turns a blank pattern away, but the block's inventory is a real
        // Container -- a hopper feeding the pattern slot never sees Slot#mayPlace.
        ItemStack blank = new ItemStack(ForgeweaveItems.PATTERN_BLANK.get());
        helper.assertFalse(menu.getSlot(PartBuilderMenu.PATTERN_SLOT).mayPlace(blank),
                "the slot filter is the first line of defence and must still turn a blank pattern away");
        StationMenu.Rejection badPattern =
                PartBuilderRecipes.rejection(registries, blank, ItemStack.EMPTY).orElseThrow();
        helper.assertTrue(badPattern.message().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("gui.forgeweave.part_builder.invalid_pattern"),
                "expected the invalid_pattern message, got " + badPattern.message());
        helper.assertFalse(badPattern.warning(),
                "upstream throws invalid_pattern as a TinkerGuiException, which is its error class");

        // A part whose material no datapack defines builds nothing. Reachable through the shard
        // branch of PartBuilderRecipes#materialValue, which trusts the id a shard carries.
        ItemStack part = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        part.set(ForgeweaveDataComponents.MATERIAL.get(), materialId("unobtainium"));
        StationMenu.Rejection useless = PartBuilderRecipes
                .rejection(registries, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()), part).orElseThrow();
        helper.assertTrue(useless.message().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("gui.forgeweave.part_builder.useless_tool_part"),
                "expected the useless_tool_part message, got " + useless.message());
        helper.assertTrue(useless.warning(),
                "upstream reaches useless_tool_part through warning(), not error() (GuiPartBuilder:156)");

        // A part of a material that exists is not useless.
        ItemStack good = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        good.set(ForgeweaveDataComponents.MATERIAL.get(), materialId("stone"));
        helper.assertTrue(PartBuilderRecipes
                        .rejection(registries, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()), good).isEmpty(),
                "a stone pickaxe head is exactly what this station is for");

        helper.succeed();
    }
}
