package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;

import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Blockstate and item model for the Part Builder (docs/SCOPE.md M1 issue #9) and the Tool Station
 * (issue #10): table-shaped (tabletop + 4 legs, hollow underside) rather than a solid cube (issue
 * #43). The block models themselves are hand-authored JSON under {@code models/block/} using the
 * {@code forgeweave:retextured_table} custom geometry loader ({@code
 * dev.gkissel.forgeweave.client.model}) -- not datagen'd, since NeoForge's model-builder DSL has no
 * first-class support for custom-loader models -- so this provider only wires blockstate rotation
 * and the item model parent onto those existing files. The element geometry in both JSONs is a
 * near-literal transcription of upstream 1.12's {@code models/block/table.json} (NOTICE.md), with
 * every face's texture variable consolidated onto a single {@code #texture} slot (upstream splits
 * top/side/leg/legBottom) so the whole table retextures as one piece from the crafting wood.
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
    }
}
