package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Blockstate, block model, and item model for the Part Builder (docs/SCOPE.md M1 issue #9) and the
 * Tool Station (issue #10). Plain top/side/bottom cubes rather than upstream's compound
 * "tabletop + 4 legs" model ({@code models/block/table.json}) -- upstream's own top/side textures
 * are near-solid-color placeholders, so replicating the leg geometry wasn't worth the complexity
 * for the same visual payoff; the top textures are still the derived upstream art (NOTICE.md).
 * Maintainer note (issue #43, filed after this PR): these simplified cubes are expected to be
 * replaced by proper table models with the 1.20 clone's wood textures -- not done here.
 *
 * <p>Both blocks share the same side texture: upstream's Part Builder and Tool Station both use
 * {@code table_side.png} for their sides too ({@code models/block/toolstation.json}: {@code "side":
 * "tconstruct:blocks/table_side"}), so the Tool Station reuses {@code part_builder_side.png}
 * directly instead of duplicating the file. The Part Builder's bottom face reuses vanilla's oak log
 * texture (what upstream's own model does for that face); the Tool Station's bottom face reuses oak
 * planks instead, matching upstream's {@code tool_station.json} ({@code "bottom":
 * "minecraft:blocks/planks_oak"}) -- the two upstream models genuinely differ here.
 */
public class ForgeweaveBlockStateProvider extends BlockStateProvider {
    public ForgeweaveBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        BlockModelBuilder partBuilderModel = models().cubeBottomTop("part_builder",
                modLoc("block/part_builder_side"),
                ResourceLocation.withDefaultNamespace("block/oak_log"),
                modLoc("block/part_builder_top"));

        horizontalBlock(ForgeweaveBlocks.PART_BUILDER.get(), partBuilderModel);
        simpleBlockItem(ForgeweaveBlocks.PART_BUILDER.get(), partBuilderModel);

        BlockModelBuilder toolStationModel = models().cubeBottomTop("tool_station",
                modLoc("block/part_builder_side"),
                ResourceLocation.withDefaultNamespace("block/oak_planks"),
                modLoc("block/tool_station_top"));

        horizontalBlock(ForgeweaveBlocks.TOOL_STATION.get(), toolStationModel);
        simpleBlockItem(ForgeweaveBlocks.TOOL_STATION.get(), toolStationModel);
    }
}
