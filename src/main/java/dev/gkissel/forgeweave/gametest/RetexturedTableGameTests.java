package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
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

    /**
     * Playtest defect, issue #755: the Tool Station is crafted from a pattern over a vanilla
     * crafting table (its ore-dict {@code workbench} ingredient, matching upstream), but that recipe
     * used to be a {@code RetexturedShapedRecipe} whose {@code assemble} copies the TEXTURE component
     * off the first {@code BlockItem} ingredient it finds -- with no other block in the grid, that was
     * always the crafting table itself. Every crafted Tool Station therefore carried
     * {@code TEXTURE=minecraft:crafting_table}, and placing it retextured the block's bottom/leg
     * faces with the crafting table's own sprite instead of oak planks. Crafts through the real
     * {@code RecipeManager} (the actual repro, not a hand-built {@code ItemStack}) and asserts the
     * result carries no TEXTURE component at all, so a freshly placed station falls through to
     * {@link ToolStationBlockEntity}'s oak default.
     */
    @GameTest(template = "empty")
    public static void toolStationRecipeDoesNotCopyTheCraftingTableAsAWood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(1, 2,
                List.of(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()), new ItemStack(Blocks.CRAFTING_TABLE)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.TOOL_STATION.get()),
                "expected pattern + crafting table to craft a Tool Station, got " + crafted);
        helper.assertTrue(crafted.get(ForgeweaveDataComponents.TEXTURE.get()) == null,
                "expected the crafted Tool Station to carry no TEXTURE component, got "
                        + crafted.get(ForgeweaveDataComponents.TEXTURE.get()));

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.TOOL_STATION.get());
        ForgeweaveBlocks.TOOL_STATION.get().setPlacedBy(
                helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), null, crafted);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        helper.assertTrue(blockEntity.getTexture() == Blocks.OAK_PLANKS,
                "expected the placed station crafted from a crafting table to still default to "
                        + "oak_planks, got " + blockEntity.getTexture());

        helper.succeed();
    }

    /**
     * Covers issue #79's multiplayer texture sync bug: on a dedicated server, an already-tracking
     * client only learns of a block entity change through {@code getUpdateTag}/{@code
     * handleUpdateTag} (the {@code sendBlockUpdated} call in {@link
     * dev.gkissel.forgeweave.block.WoodTexturedBlockEntity#notifyTextureChanged} triggers that
     * exchange). Before the fix, {@code getUpdateTag} used {@code BlockEntity}'s default (an empty
     * tag), so a freshly-tracked block entity on the "client" side of this round-trip would never
     * pick up the texture. This drives that exact exchange without a real network connection: take
     * the update tag off a block entity with a non-default texture, and feed it into a fresh block
     * entity's {@code handleUpdateTag} the way {@code ClientPacketListener#handleBlockEntityData}
     * would.
     */
    @GameTest(template = "empty")
    public static void updateTagRoundTripRestoresTexture(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());

        PartBuilderBlockEntity source = helper.getBlockEntity(pos);
        source.setTexture(Blocks.SPRUCE_LOG);

        CompoundTag updateTag = source.getUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(!updateTag.isEmpty(), "expected a non-empty update tag once a non-default texture is set");

        PartBuilderBlockEntity restored = new PartBuilderBlockEntity(source.getBlockPos(), source.getBlockState());
        restored.handleUpdateTag(updateTag, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getTexture() == Blocks.SPRUCE_LOG,
                "expected the update tag round-trip to restore spruce_log, got " + restored.getTexture());

        helper.succeed();
    }
}
