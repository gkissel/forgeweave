package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;

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

        ModelFile toolForgeModel = models().getExistingFile(modLoc("block/tool_forge"));
        horizontalBlock(ForgeweaveBlocks.TOOL_FORGE.get(), toolForgeModel);
        simpleBlockItem(ForgeweaveBlocks.TOOL_FORGE.get(), toolForgeModel);

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

        // Grout (docs/SCOPE.md M2 issue #93; issue #129): plain cube_all geometry, same as the
        // seared brick family below.
        cubeAllBlock("grout", ForgeweaveBlocks.GROUT.get());

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

        // The smeltery multiblock (docs/SCOPE.md M2 issue #95). Upstream 1.12's smeltery_controller
        // blockstate is the vanilla "orientable" shape with seared brick on every face but the front,
        // and the front swapping between a lit and an unlit texture on its `active` property
        // (NOTICE.md) -- reproduced here for both core tiers. The Nether Core additionally gets its
        // own side/top texture (issue #143: the tiers must read as distinct from any angle, not just
        // the front) instead of the shared seared brick.
        coreBlock("standard_core", ForgeweaveBlocks.STANDARD_CORE.get(), "seared_bricks");
        coreBlock("nether_core", ForgeweaveBlocks.NETHER_CORE.get(), "nether_core_side");

        // Upstream's seared_tank blockstate: one cube per tank type, side and top textures per type.
        tankBlock("seared_tank", ForgeweaveBlocks.SEARED_TANK.get(), "seared_tank_side", "seared_tank_top");
        tankBlock("seared_gauge", ForgeweaveBlocks.SEARED_GAUGE.get(), "seared_gauge_side", "seared_window_top");
        tankBlock("seared_window", ForgeweaveBlocks.SEARED_WINDOW.get(), "seared_window_side", "seared_window_top");

        // The drain has distinct front and back faces, so it needs the full six-face cube rather than
        // "orientable" (which would repeat the side texture on the back).
        ResourceLocation drainSide = modLoc("derived/block/seared_bricks");
        ModelFile drainModel = models().cube("seared_drain", drainSide, drainSide,
                        modLoc("derived/block/seared_drain_front"), modLoc("derived/block/seared_drain_back"), drainSide, drainSide)
                .texture("particle", drainSide);
        horizontalBlock(ForgeweaveBlocks.SEARED_DRAIN.get(), drainModel);
        simpleBlockItem(ForgeweaveBlocks.SEARED_DRAIN.get(), drainModel);

        // #100 -- casting (docs/SCOPE.md M2 issue #100). All four models are hand-authored JSON under
        // models/block/, transcribed from upstream 1.12's own casting_table/casting_basin/faucet/
        // faucet_top models (NOTICE.md) with the texture slots pointed at derived/block/*; the
        // rotations below are upstream's blockstates/faucet.json y-values, with the faucet model
        // authored facing south.
        ModelFile castingTable = models().getExistingFile(modLoc("block/casting_table"));
        simpleBlock(ForgeweaveBlocks.CASTING_TABLE.get(), castingTable);
        simpleBlockItem(ForgeweaveBlocks.CASTING_TABLE.get(), castingTable);

        ModelFile castingBasin = models().getExistingFile(modLoc("block/casting_basin"));
        simpleBlock(ForgeweaveBlocks.CASTING_BASIN.get(), castingBasin);
        simpleBlockItem(ForgeweaveBlocks.CASTING_BASIN.get(), castingBasin);

        ModelFile faucet = models().getExistingFile(modLoc("block/faucet"));
        ModelFile faucetTop = models().getExistingFile(modLoc("block/faucet_top"));
        getVariantBuilder(ForgeweaveBlocks.FAUCET.get()).forAllStates(state -> switch (state.getValue(FaucetBlock.FACING)) {
            case UP -> ConfiguredModel.builder().modelFile(faucetTop).build();
            case NORTH -> ConfiguredModel.builder().modelFile(faucet).rotationY(180).build();
            case EAST -> ConfiguredModel.builder().modelFile(faucet).rotationY(270).build();
            case WEST -> ConfiguredModel.builder().modelFile(faucet).rotationY(90).build();
            default -> ConfiguredModel.builder().modelFile(faucet).build();
        });
        simpleBlockItem(ForgeweaveBlocks.FAUCET.get(), faucet);

        // #104 -- cobalt + ardite nether ore (docs/SCOPE.md M2 issue #104): plain cube_all geometry,
        // like the seared brick family, with one composited derived texture per ore (NOTICE.md).
        cubeAllBlock("cobalt_ore", ForgeweaveBlocks.COBALT_ORE.get());
        cubeAllBlock("ardite_ore", ForgeweaveBlocks.ARDITE_ORE.get());

        // #206 -- storage blocks for cobalt/ardite/manyullyn/rose gold: plain cube_all geometry like
        // the ore blocks above, one derived texture per metal (NOTICE.md).
        cubeAllBlock("cobalt_block", ForgeweaveBlocks.COBALT_BLOCK.get());
        cubeAllBlock("ardite_block", ForgeweaveBlocks.ARDITE_BLOCK.get());
        cubeAllBlock("manyullyn_block", ForgeweaveBlocks.MANYULLYN_BLOCK.get());
        cubeAllBlock("rose_gold_block", ForgeweaveBlocks.ROSE_GOLD_BLOCK.get());
        cubeAllBlock("steel_block", ForgeweaveBlocks.STEEL_BLOCK.get());
        cubeAllBlock("knightslime_block", ForgeweaveBlocks.KNIGHTSLIME_BLOCK.get()); // #232
    }

    private void coreBlock(String name, Block block, String sideTexture) {
        ResourceLocation side = modLoc("derived/block/" + sideTexture);
        ModelFile inactive = models().orientable(name, side, modLoc("derived/block/" + name + "_front_inactive"), side);
        ModelFile active = models().orientable(name + "_active", side, modLoc("derived/block/" + name + "_front_active"), side);
        horizontalBlock(block, state -> state.getValue(SmelteryControllerBlock.ACTIVE) ? active : inactive);
        simpleBlockItem(block, inactive);
    }

    private void tankBlock(String name, Block block, String sideTexture, String topTexture) {
        ResourceLocation top = modLoc("derived/block/" + topTexture);
        // #145: the derived side/top textures carry real alpha-cutout windows (0 and 255 alpha
        // values). NeoForge 1.21 declares a block's chunk render type on the model itself rather
        // than through the legacy ItemBlockRenderTypes.setRenderLayer Java map -- mixing the two
        // left the block with an empty render-type set and made it disappear entirely.
        ModelFile model = models().cubeBottomTop(name, modLoc("derived/block/" + sideTexture), top, top)
                .renderType("minecraft:cutout");
        simpleBlockWithItem(block, model);
    }

    private void cubeAllBlock(String name, Block block) {
        ModelFile model = models().cubeAll(name, modLoc("derived/block/" + name));
        simpleBlockWithItem(block, model);
    }
}
