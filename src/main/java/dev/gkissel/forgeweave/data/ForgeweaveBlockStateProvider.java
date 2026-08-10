package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Blockstate and item model for the Part Builder (docs/SCOPE.md M1 issue #9), the Tool Station
 * (issue #10), the Crafting Station (issue #40), and the Stencil Table (issue #44): table-shaped (tabletop + 4 legs, hollow
 * underside) rather than a solid cube (issue #43). The block models themselves are hand-authored
 * JSON under {@code models/block/} using the
 * {@code forgeweave:retextured_table} custom geometry loader ({@code
 * dev.gkissel.forgeweave.client.model}) -- not datagen'd, since NeoForge's model-builder DSL has no
 * first-class support for custom-loader models -- so this provider only wires blockstate rotation
 * and the item model parent onto those existing files. The element geometry in both JSONs is a
 * near-literal transcription of upstream 1.12's {@code models/block/table.json} (NOTICE.md), with
 * every face's texture variable consolidated onto a single {@code #texture} slot (upstream splits
 * top/side/leg/legBottom) so the whole table retextures as one piece from the crafting wood.
 *
 * <p>The seared brick block family (docs/SCOPE.md M2 issue #93) is plain {@code cube_all}
 * geometry, one derived texture per variant -- see {@link #cubeAllBlock}.
 */
public class ForgeweaveBlockStateProvider extends BlockStateProvider {
    public ForgeweaveBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile partBuilderModel = models().getExistingFile(modLoc("block/part_builder"));
        horizontalBlock(ForgeweaveBlocks.PART_BUILDER.get(), partBuilderModel);
        simpleBlockItem(ForgeweaveBlocks.PART_BUILDER.get(), partBuilderModel);

        ModelFile toolStationModel = models().getExistingFile(modLoc("block/tool_station"));
        horizontalBlock(ForgeweaveBlocks.TOOL_STATION.get(), toolStationModel);
        simpleBlockItem(ForgeweaveBlocks.TOOL_STATION.get(), toolStationModel);

        ModelFile craftingStationModel = models().getExistingFile(modLoc("block/crafting_station"));
        horizontalBlock(ForgeweaveBlocks.CRAFTING_STATION.get(), craftingStationModel);
        simpleBlockItem(ForgeweaveBlocks.CRAFTING_STATION.get(), craftingStationModel);

        ModelFile stencilTableModel = models().getExistingFile(modLoc("block/stencil_table"));
        horizontalBlock(ForgeweaveBlocks.STENCIL_TABLE.get(), stencilTableModel);
        simpleBlockItem(ForgeweaveBlocks.STENCIL_TABLE.get(), stencilTableModel);

        // The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66): plain facing-aware cubes
        // (ChestBlock javadoc), generated via the vanilla "orientable" model (front/side/top faces,
        // same shape furnaces use) instead of hand-authored table JSON.
        ModelFile patternChestModel = models().orientable("pattern_chest",
                modLoc("derived/block/pattern_chest_side"), modLoc("derived/block/pattern_chest_front"), modLoc("derived/block/pattern_chest_top"));
        horizontalBlock(ForgeweaveBlocks.PATTERN_CHEST.get(), patternChestModel);
        simpleBlockItem(ForgeweaveBlocks.PATTERN_CHEST.get(), patternChestModel);

        ModelFile partChestModel = models().orientable("part_chest",
                modLoc("derived/block/part_chest_side"), modLoc("derived/block/part_chest_front"), modLoc("derived/block/part_chest_top"));
        horizontalBlock(ForgeweaveBlocks.PART_CHEST.get(), partChestModel);
        simpleBlockItem(ForgeweaveBlocks.PART_CHEST.get(), partChestModel);

        // The seared brick block family (docs/SCOPE.md M2 issue #93): plain cube_all blocks, one
        // derived texture per variant (NOTICE.md) -- unlike the tables above, these have no custom
        // geometry, so simpleBlockWithItem covers both the blockstate and the block-item model.
        cubeAllBlock("seared_stone", ForgeweaveBlocks.SEARED_STONE.get());
        cubeAllBlock("seared_cobblestone", ForgeweaveBlocks.SEARED_COBBLESTONE.get());
        cubeAllBlock("seared_paver", ForgeweaveBlocks.SEARED_PAVER.get());
        cubeAllBlock("seared_bricks", ForgeweaveBlocks.SEARED_BRICKS.get());
        cubeAllBlock("seared_cracked_bricks", ForgeweaveBlocks.SEARED_CRACKED_BRICKS.get());
        cubeAllBlock("seared_fancy_bricks", ForgeweaveBlocks.SEARED_FANCY_BRICKS.get());
        cubeAllBlock("seared_square_bricks", ForgeweaveBlocks.SEARED_SQUARE_BRICKS.get());
        cubeAllBlock("seared_triangle_bricks", ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS.get());
        cubeAllBlock("seared_small_bricks", ForgeweaveBlocks.SEARED_SMALL_BRICKS.get());
        cubeAllBlock("seared_road", ForgeweaveBlocks.SEARED_ROAD.get());
        cubeAllBlock("seared_tile", ForgeweaveBlocks.SEARED_TILE.get());
        cubeAllBlock("seared_creeper", ForgeweaveBlocks.SEARED_CREEPER.get());
    }

    private void cubeAllBlock(String name, Block block) {
        ModelFile model = models().cubeAll(name, modLoc("derived/block/" + name));
        simpleBlockWithItem(block, model);
    }
}
