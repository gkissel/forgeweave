package dev.gkissel.forgeweave.data;

import net.minecraft.core.registries.BuiltInRegistries;
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

        // The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66, reshaped by issue #342):
        // upstream 1.12's cabinet geometry, not a cube -- see chestBlock.
        chestBlock("pattern_chest", ForgeweaveBlocks.PATTERN_CHEST.get());
        chestBlock("part_chest", ForgeweaveBlocks.PART_CHEST.get());

        // Grout (docs/SCOPE.md M2 issue #93; issue #129): plain cube_all geometry, same as the
        // seared brick family below.
        cubeAllBlock("grout", ForgeweaveBlocks.GROUT.get());

        // #339 -- the slimy muds, same plain cube_all geometry as grout, one derived texture each.
        cubeAllBlock("slimy_mud_green", ForgeweaveBlocks.SLIMY_MUD_GREEN.get());
        cubeAllBlock("slimy_mud_magma", ForgeweaveBlocks.SLIMY_MUD_MAGMA.get());

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

        // Plain seared glass (docs/SCOPE.md M3.3 issue #289): cube_all like the seared brick family,
        // but cutout like the tank family -- see cubeAllCutoutBlock.
        cubeAllCutoutBlock("seared_glass", ForgeweaveBlocks.SEARED_GLASS.get());

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

        // #233 -- pig iron's storage block and firewood, both plain cube_all with one derived
        // texture each (block_pigiron.png; firewood.png + its animation mcmeta, NOTICE.md).
        cubeAllBlock("pig_iron_block", ForgeweaveBlocks.PIG_IRON_BLOCK.get());
        cubeAllBlock("firewood", ForgeweaveBlocks.FIREWOOD.get());

        cubeAllBlock("amethyst_bronze_block", ForgeweaveBlocks.AMETHYST_BRONZE_BLOCK.get());

        // #275 -- clear glass (cutout, like seared glass above) and its 16 clear stained glass colors
        // (translucent, matching upstream's BlockClearStainedGlass#getBlockLayer -- see
        // cubeAllTranslucentBlock). Every color shares the one derived clear_stained_glass texture,
        // tinted per block instance by ForgeweaveGlassColors.
        cubeAllCutoutBlock("clear_glass", ForgeweaveBlocks.CLEAR_GLASS.get());
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            cubeAllTranslucentBlock(color.block().get());
        }
    }

    /**
     * The two chests (issue #342). Both share one hand-authored geometry file,
     * {@code models/block/chest.json} -- a near-literal transcription of upstream 1.12's
     * {@code models/block/patternchest.json} (NOTICE.md): a 14x12x14 body lifted on four legs under a
     * full-width top slab, with two drawer fronts and handles standing proud of the front face. The
     * five texture slots stay unbound there and are filled per block here, exactly the way upstream's
     * own {@code blockstates/tooltables.json} points both its {@code patternchest} and {@code
     * partchest} variants at that one model and overrides only the textures.
     */
    private void chestBlock(String name, Block block) {
        ModelFile model = models().withExistingParent(name, modLoc("block/chest"))
                .texture("side", modLoc("derived/block/" + name + "_side"))
                .texture("front", modLoc("derived/block/" + name + "_front"))
                .texture("top", modLoc("derived/block/" + name + "_top"))
                .texture("drawer_front", modLoc("derived/block/" + name + "_drawer_front"))
                .texture("drawer_side", modLoc("derived/block/" + name + "_drawer_side"));
        horizontalBlock(block, model);
        simpleBlockItem(block, model);
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

    /**
     * A cube_all block whose texture carries real alpha-cutout transparency (issue #289's seared
     * glass) -- same {@code minecraft:cutout} render_type deviation as {@link #tankBlock}, for the
     * same reason (NeoForge 1.21 declares chunk render type on the model itself).
     */
    private void cubeAllCutoutBlock(String name, Block block) {
        ModelFile model = models().cubeAll(name, modLoc("derived/block/" + name)).renderType("minecraft:cutout");
        simpleBlockWithItem(block, model);
    }

    /**
     * A cube_all block sharing the one derived {@code clear_stained_glass} texture across every color
     * (issue #275) -- each block still gets its own model file, named off its own registry id, but
     * they all point at the same texture and are tinted apart client-side ({@code
     * ForgeweaveGlassColors}). {@code minecraft:translucent} matches upstream's {@code
     * BlockClearStainedGlass#getBlockLayer}, same NeoForge 1.21 model-level render_type deviation as
     * {@link #tankBlock}/{@link #cubeAllCutoutBlock}.
     */
    private void cubeAllTranslucentBlock(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        ModelFile model = models().cubeAll(name, modLoc("derived/block/clear_stained_glass")).renderType("minecraft:translucent");
        simpleBlockWithItem(block, model);
    }
}
