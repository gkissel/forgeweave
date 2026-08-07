package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlock;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlock;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Covers issue #43's component round-trip: a station item crafted with a specific wood ({@code
 * TEXTURE} component set) applies that wood to the placed block entity ({@link PartBuilderBlock}/
 * {@link ToolStationBlock}'s {@code setPlacedBy}), and picking the block back up (creative
 * middle-click) returns an item stack with the same wood recorded. The actual retexture rendering
 * ({@code RetexturedTableBakedModel}) is manual-verify per the issue and docs/SCOPE.md's regression
 * rule -- GameTests don't run a renderer.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class RetexturedTableGameTests {

    @GameTest(template = "empty")
    public static void partBuilderRetainsCraftedWoodThroughPlacementAndPickBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ResourceLocation spruceLog = BuiltInRegistries.BLOCK.getKey(Blocks.SPRUCE_LOG);

        ItemStack crafted = new ItemStack(ForgeweaveItems.PART_BUILDER.get());
        crafted.set(ForgeweaveDataComponents.TEXTURE.get(), spruceLog);

        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        ForgeweaveBlocks.PART_BUILDER.get().setPlacedBy(
                helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), null, crafted);

        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        helper.assertTrue(blockEntity.getTexture() == Blocks.SPRUCE_LOG,
                "expected the block entity to store spruce_log, got " + blockEntity.getTexture());

        ItemStack pickedBlock = ForgeweaveBlocks.PART_BUILDER.get()
                .getCloneItemStack(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos));
        helper.assertTrue(spruceLog.equals(pickedBlock.get(ForgeweaveDataComponents.TEXTURE.get())),
                "expected the picked item to carry spruce_log, got " + pickedBlock.get(ForgeweaveDataComponents.TEXTURE.get()));

        // ForgeweaveBlockLootSubProvider's break-drop loot table copies exactly this map (source:
        // block_entity) onto the dropped item via minecraft:copy_components.
        helper.assertTrue(spruceLog.equals(blockEntity.collectComponents().get(ForgeweaveDataComponents.TEXTURE.get())),
                "expected collectComponents() to expose spruce_log for the break-drop loot function, got "
                        + blockEntity.collectComponents().get(ForgeweaveDataComponents.TEXTURE.get()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toolStationDefaultsToOakWhenCraftedWithoutAWoodComponent(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());

        // Creative-tab/pick-block item: no TEXTURE component set.
        ItemStack unspecified = new ItemStack(ForgeweaveItems.TOOL_STATION.get());
        ForgeweaveBlocks.TOOL_STATION.get().setPlacedBy(
                helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), null, unspecified);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        helper.assertTrue(blockEntity.getTexture() == Blocks.OAK_PLANKS,
                "expected the default (unspecified input) texture to be oak_planks, got " + blockEntity.getTexture());

        helper.succeed();
    }
}
