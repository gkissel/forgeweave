package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    @GameTest(template = "empty")
    public static void patternAndMaterialProduceMatchingPart(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);

        menu.getSlot(0).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        // Stone's cobblestone crafting item is worth 2 shard-units each (PartBuilderRecipes); a
        // pickaxe head costs 4, so 2 cobblestone exactly covers it with no shard change.
        menu.getSlot(1).set(new ItemStack(Items.COBBLESTONE, 2));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(2).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part, got " + output);
        helper.assertTrue(
                materialId("stone").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the pickaxe head's material to be forgeweave:stone, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        // Simulates taking the crafted item: the pattern is reusable (upstream 1.12 behavior), only
        // the material is consumed.
        menu.getSlot(2).onTake(player, output);
        helper.assertFalse(menu.getSlot(0).getItem().isEmpty(), "expected the pattern to remain (reusable)");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the material to be fully consumed");
        helper.assertTrue(menu.getSlot(3).getItem().isEmpty(), "expected no shard change for an exact-value craft");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void logProducesHeadAndShardChange(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PartBuilderMenu menu = openMenu(helper, pos, player);

        menu.getSlot(0).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        // A log is worth 8 shard-units (4 upstream ingots); a pickaxe head costs 4, so one log
        // covers it with 4 shard-units (4 shards) left over -- the upstream second-output behavior.
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 1));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(2).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part, got " + output);
        helper.assertTrue(materialId("wood").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the pickaxe head's material to be forgeweave:wood, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        menu.getSlot(2).onTake(player, output);
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected the log to be fully consumed");

        ItemStack change = menu.getSlot(3).getItem();
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

        menu.getSlot(0).set(new ItemStack(ForgeweaveItems.PATTERN_TOOL_HANDLE.get()));
        // A tool handle costs 2 shard-units; 2 wood shards (1 unit each) covers it exactly.
        ItemStack shards = new ItemStack(ForgeweaveItems.SHARD.get(), 2);
        shards.set(ForgeweaveDataComponents.MATERIAL.get(), materialId("wood"));
        menu.getSlot(1).set(shards);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(2).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_TOOL_HANDLE.get()),
                "expected a tool handle part, got " + output);
        helper.assertTrue(materialId("wood").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the tool handle's material to be forgeweave:wood, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));

        menu.getSlot(2).onTake(player, output);
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "expected both shards to be consumed");
        helper.assertTrue(menu.getSlot(3).getItem().isEmpty(), "expected no further change for an exact-value craft");

        helper.succeed();
    }
}
