package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Blockstate, block model, and item model for the Part Builder (docs/SCOPE.md M1 issue #9). A
 * plain top/side/bottom cube rather than upstream's compound "tabletop + 4 legs" model
 * ({@code models/block/table.json}) -- upstream's own top/side textures are near-solid-color
 * placeholders, so replicating the leg geometry wasn't worth the complexity for the same visual
 * payoff; the top and side textures are still the derived upstream art (NOTICE.md), and the bottom
 * face reuses vanilla's oak log texture, matching what upstream's own model does for that face.
 */
public class ForgeweaveBlockStateProvider extends BlockStateProvider {
    public ForgeweaveBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        BlockModelBuilder model = models().cubeBottomTop("part_builder",
                modLoc("block/part_builder_side"),
                ResourceLocation.withDefaultNamespace("block/oak_log"),
                modLoc("block/part_builder_top"));

        horizontalBlock(ForgeweaveBlocks.PART_BUILDER.get(), model);
        simpleBlockItem(ForgeweaveBlocks.PART_BUILDER.get(), model);
    }
}
