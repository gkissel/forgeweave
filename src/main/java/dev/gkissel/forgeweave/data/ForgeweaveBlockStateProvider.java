package dev.gkissel.forgeweave.data;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.mojang.math.Quadrant;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ItemModelOutput;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.PropertyValueList;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ConnectedGlassBlock;
import dev.gkissel.forgeweave.block.EnergizedTankBlock;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.FoliageType;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedChannelBlock;
import dev.gkissel.forgeweave.block.SearedChannelBlock.ChannelConnection;
import dev.gkissel.forgeweave.block.SearedChuteBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.block.SmelteryCore;
import dev.gkissel.forgeweave.block.SearedTier;
import dev.gkissel.forgeweave.client.model.RetexturedTableGeometry;
import dev.gkissel.forgeweave.client.model.RetexturedTableItemModel;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Forgeweave's one model provider: every blockstate and block model, and every client item, the
 * block items' here and every other item's in {@link ForgeweaveItemModelProvider} (26.1's {@code
 * ModelProvider} writes both halves in one pass and checks that no Forgeweave block or item is left
 * without a definition).
 *
 * <p>The table-shaped stations -- the Part Builder (docs/SCOPE.md M1 issue #9), the Tool Station
 * (issue #10), the Crafting Station (issue #40) and the Stencil Table (issue #44) -- are tabletop + 4
 * legs, hollow underside, rather than a solid cube (issue #43). The block models themselves are
 * hand-authored JSON under {@code models/block/} -- not datagen'd -- so this provider only wires
 * blockstate rotation and the item model parent onto those existing files. Their blockstate variants
 * carry the {@code forgeweave:retextured_table} type and their client items the
 * {@code forgeweave:retextured_table} item model ({@code dev.gkissel.forgeweave.client.model}), which
 * is what swaps the table into the crafting wood. The element geometry in both JSONs is a
 * near-literal transcription of upstream 1.12's {@code models/block/table.json} (NOTICE.md), with
 * every face's texture variable consolidated onto a single {@code #texture} slot (upstream splits
 * top/side/leg/legBottom) so the whole table retextures as one piece from the crafting wood.
 *
 * <p>The seared brick block family (docs/SCOPE.md M2 issue #93) is plain {@code cube_all}
 * geometry, one derived texture per variant -- see {@link #cubeAllBlock}. Its stairs and slabs
 * (docs/SCOPE.md M3.4-5 issue #274) reuse those same textures via vanilla's own stairs/slab model
 * shapes -- see {@link #searedStairs} and {@link #searedSlab}.
 *
 * <p>Every block item keeps its own {@code models/item/<name>} model parenting the block model, as
 * the 1.21.1 provider wrote it, and its client item points at that model. 26.1 models carry no
 * {@code render_type} any more: the chunk layer is worked out per quad from the sprite's alpha, so
 * the 1.21.1 cutout entries are simply gone, and the two translucent families (the coloured slime
 * blocks and the clear stained glass) mark their faces {@code force_translucent} instead.
 */
public class ForgeweaveBlockStateProvider extends ModelProvider {
    public ForgeweaveBlockStateProvider(PackOutput output) {
        super(output, Forgeweave.MODID);
    }

    // ponytail: set at the top of registerModels, which datagen calls once per run.
    private Consumer<BlockModelDefinitionGenerator> blockStates;
    private ItemModelOutput items;
    private BiConsumer<Identifier, ModelInstance> models;

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        this.blockStates = blockModels.blockStateOutput;
        this.items = blockModels.itemModelOutput;
        this.models = blockModels.modelOutput;
        registerStatesAndModels();
        new ForgeweaveItemModelProvider(itemModels).registerModels();
    }

    @Override
    public String getName() {
        return "Forgeweave block and item models";
    }

    private void registerStatesAndModels() {
        retexturedTable(ForgeweaveBlocks.PART_BUILDER.get(), modLoc("block/part_builder"));
        retexturedTable(ForgeweaveBlocks.TOOL_STATION.get(), modLoc("block/tool_station"));
        retexturedTable(ForgeweaveBlocks.TOOL_FORGE.get(), modLoc("block/tool_forge"));
        retexturedTable(ForgeweaveBlocks.CRAFTING_STATION.get(), modLoc("block/crafting_station"));
        retexturedTable(ForgeweaveBlocks.STENCIL_TABLE.get(), modLoc("block/stencil_table"));

        // The Modifier Worktable (issue #1057): a fixed stone table, so no retexture loader.
        Identifier modifierWorktableModel = modLoc("block/modifier_worktable");
        horizontalBlock(ForgeweaveBlocks.MODIFIER_WORKTABLE.get(), modifierWorktableModel);
        simpleBlockItem(ForgeweaveBlocks.MODIFIER_WORKTABLE.get(), modifierWorktableModel);

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
        cubeAllBlock("slimy_mud_blue", ForgeweaveBlocks.SLIMY_MUD_BLUE.get()); // #635

        // #429 -- graveyard and consecrated soil, same plain cube_all geometry, one derived texture
        // each (NOTICE.md). Their onEntityWalk behavior is code-side only, nothing model-visible.
        cubeAllBlock("graveyard_soil", ForgeweaveBlocks.GRAVEYARD_SOIL.get());
        cubeAllBlock("consecrated_soil", ForgeweaveBlocks.CONSECRATED_SOIL.get());

        // #502 (T71 parity audit): mud brick block, upstream's one BlockDecoGround state -- same
        // plain cube_all geometry as graveyard/consecrated soil above, one derived texture (NOTICE.md).
        cubeAllBlock("mud_brick_block", ForgeweaveBlocks.MUD_BRICK_BLOCK.get());

        // The seared brick block family (docs/SCOPE.md M2 issue #93): plain cube_all blocks, one
        // derived texture per variant (NOTICE.md) -- unlike the tables above, these have no custom
        // geometry, so one cube_all model covers both the blockstate and the block-item model.
        // Every seared wall/floor block follows its core's tier (SearedTier): the standard tier is
        // upstream's own texture, every other tier is scripts/generate_seared_tier_textures.py's
        // recolour of it in that core's hue, so the whole shell reads as one piece with the core.
        tieredCubeAll("seared_stone", ForgeweaveBlocks.SEARED_STONE.get());
        tieredCubeAll("seared_cobblestone", ForgeweaveBlocks.SEARED_COBBLESTONE.get());
        tieredCubeAll("seared_paver", ForgeweaveBlocks.SEARED_PAVER.get());
        tieredCubeAll("seared_bricks", ForgeweaveBlocks.SEARED_BRICKS.get());
        tieredCubeAll("seared_cracked_bricks", ForgeweaveBlocks.SEARED_CRACKED_BRICKS.get());
        tieredCubeAll("seared_fancy_bricks", ForgeweaveBlocks.SEARED_FANCY_BRICKS.get());
        tieredCubeAll("seared_square_bricks", ForgeweaveBlocks.SEARED_SQUARE_BRICKS.get());
        tieredCubeAll("seared_triangle_bricks", ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS.get());
        tieredCubeAll("seared_small_bricks", ForgeweaveBlocks.SEARED_SMALL_BRICKS.get());
        tieredCubeAll("seared_road", ForgeweaveBlocks.SEARED_ROAD.get());
        tieredCubeAll("seared_tile", ForgeweaveBlocks.SEARED_TILE.get());
        tieredCubeAll("seared_creeper", ForgeweaveBlocks.SEARED_CREEPER.get());

        // Seared stairs + slabs (docs/SCOPE.md M3.4-5 issue #274): vanilla's own stairs/slab
        // blockstate generators, one derived texture per variant reused from the parent cube_all block
        // above -- see ForgeweaveBlocks for the parity notes.
        searedStairs("seared_stairs_stone", ForgeweaveBlocks.SEARED_STAIRS_STONE.get(), "seared_stone");
        searedStairs("seared_stairs_cobblestone", ForgeweaveBlocks.SEARED_STAIRS_COBBLESTONE.get(), "seared_cobblestone");
        searedStairs("seared_stairs_paver", ForgeweaveBlocks.SEARED_STAIRS_PAVER.get(), "seared_paver");
        searedStairs("seared_stairs_bricks", ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get(), "seared_bricks");
        searedStairs("seared_stairs_cracked_bricks", ForgeweaveBlocks.SEARED_STAIRS_CRACKED_BRICKS.get(), "seared_cracked_bricks");
        searedStairs("seared_stairs_fancy_bricks", ForgeweaveBlocks.SEARED_STAIRS_FANCY_BRICKS.get(), "seared_fancy_bricks");
        searedStairs("seared_stairs_square_bricks", ForgeweaveBlocks.SEARED_STAIRS_SQUARE_BRICKS.get(), "seared_square_bricks");
        searedStairs("seared_stairs_triangle_bricks", ForgeweaveBlocks.SEARED_STAIRS_TRIANGLE_BRICKS.get(), "seared_triangle_bricks");
        searedStairs("seared_stairs_small_bricks", ForgeweaveBlocks.SEARED_STAIRS_SMALL_BRICKS.get(), "seared_small_bricks");
        searedStairs("seared_stairs_road", ForgeweaveBlocks.SEARED_STAIRS_ROAD.get(), "seared_road");
        searedStairs("seared_stairs_tile", ForgeweaveBlocks.SEARED_STAIRS_TILE.get(), "seared_tile");
        searedStairs("seared_stairs_creeper", ForgeweaveBlocks.SEARED_STAIRS_CREEPER.get(), "seared_creeper");

        searedSlab("seared_slab_stone", ForgeweaveBlocks.SEARED_SLAB_STONE.get(), "seared_stone");
        searedSlab("seared_slab_cobblestone", ForgeweaveBlocks.SEARED_SLAB_COBBLESTONE.get(), "seared_cobblestone");
        searedSlab("seared_slab_paver", ForgeweaveBlocks.SEARED_SLAB_PAVER.get(), "seared_paver");
        searedSlab("seared_slab_bricks", ForgeweaveBlocks.SEARED_SLAB_BRICKS.get(), "seared_bricks");
        searedSlab("seared_slab_cracked_bricks", ForgeweaveBlocks.SEARED_SLAB_CRACKED_BRICKS.get(), "seared_cracked_bricks");
        searedSlab("seared_slab_fancy_bricks", ForgeweaveBlocks.SEARED_SLAB_FANCY_BRICKS.get(), "seared_fancy_bricks");
        searedSlab("seared_slab_square_bricks", ForgeweaveBlocks.SEARED_SLAB_SQUARE_BRICKS.get(), "seared_square_bricks");
        searedSlab("seared_slab_triangle_bricks", ForgeweaveBlocks.SEARED_SLAB_TRIANGLE_BRICKS.get(), "seared_triangle_bricks");
        searedSlab("seared_slab_small_bricks", ForgeweaveBlocks.SEARED_SLAB_SMALL_BRICKS.get(), "seared_small_bricks");
        searedSlab("seared_slab_road", ForgeweaveBlocks.SEARED_SLAB_ROAD.get(), "seared_road");
        searedSlab("seared_slab_tile", ForgeweaveBlocks.SEARED_SLAB_TILE.get(), "seared_tile");
        searedSlab("seared_slab_creeper", ForgeweaveBlocks.SEARED_SLAB_CREEPER.get(), "seared_creeper");

        // The smeltery multiblock (docs/SCOPE.md M2 issue #95). Upstream 1.12's smeltery_controller
        // blockstate is the vanilla "orientable" shape with seared brick on every face but the front,
        // and the front swapping between a lit and an unlit texture on its `active` property
        // (NOTICE.md) -- reproduced here for both core tiers. The Nether Core additionally gets its
        // own side/top texture (issue #143: the tiers must read as distinct from any angle, not just
        // the front) instead of the shared seared brick.
        coreBlock("standard_core", ForgeweaveBlocks.STANDARD_CORE.get(), "seared_bricks");
        netherCoreBlock();
        // #845 -- the End and Deep Core, same shared coreBlock() helper and the same "own side/top
        // texture" treatment #143 gave the Nether Core, so all four tiers keep reading as distinct
        // from any angle.
        coreBlock("end_core", ForgeweaveBlocks.END_CORE.get(), "end_core_side");
        coreBlock("deep_core", ForgeweaveBlocks.DEEP_CORE.get(), "deep_core_side");
        // #442 -- upstream's seared_furnace_controller blockstate is the same orientable shape over
        // seared brick, front swapping on `active` (NOTICE.md).
        coreBlock("seared_furnace_controller", ForgeweaveBlocks.SEARED_FURNACE_CONTROLLER.get(), "seared_bricks");
        // T44/#475 -- upstream's tinker_tank_controller blockstate is the same orientable shape over
        // seared brick, front swapping on `active` (NOTICE.md). Upstream's active model layers a
        // translucent overlay over a background texture in two model elements; the derived active
        // texture is that composite, which renders identically through the shared helper below.
        coreBlock("seared_reservoir_controller", ForgeweaveBlocks.SEARED_RESERVOIR_CONTROLLER.get(), "seared_bricks");

        // Upstream's seared_tank blockstate: one cube per tank type, side and top textures per type.
        tieredTank("seared_tank", ForgeweaveBlocks.SEARED_TANK.get(), "seared_tank_side", "seared_tank_top");
        tieredTank("seared_gauge", ForgeweaveBlocks.SEARED_GAUGE.get(), "seared_gauge_side", "seared_window_top");
        tieredTank("seared_window", ForgeweaveBlocks.SEARED_WINDOW.get(), "seared_window_side", "seared_window_top");

        // Plain seared glass (docs/SCOPE.md M3.3 issue #289): cube_all like the seared brick family;
        // its alpha-cutout panes pick the cutout layer by themselves on 26.1.
        tieredCubeAll("seared_glass", ForgeweaveBlocks.SEARED_GLASS.get());

        // The drain has distinct front and back faces, so it needs the full six-face cube rather than
        // "orientable" (which would repeat the side texture on the back).
        tieredDrainLike("seared_drain", ForgeweaveBlocks.SEARED_DRAIN.get(), "seared_drain_front");

        // #277 -- the duct is the drain's geometry with a different front, so it takes the same
        // six-face cube; the 1.20 clone's own duct model does exactly this (its duct_active and
        // drain_active share a template and differ only in the "drain" texture slot, NOTICE.md).
        tieredDrainLike("seared_duct", ForgeweaveBlocks.SEARED_DUCT.get(), "seared_duct_front");

        // #972 (M8, D-M8-11) -- the energized tank. Original Forgeweave art in the standard
        // textures/block folder (no 1.12 equivalent exists to derive from), and not tiered: one look
        // at every core tier, see the block's own registration comment. Two variants, because the
        // overdrive button shows which way it is pressed.
        energizedTank();

        // #277 -- the chute's trough hangs off three faces plus the top and bottom, so its model is
        // hand-authored JSON under models/block/, transcribed from the 1.20 clone's own
        // block/template/chute (NOTICE.md) with the texture slots pointed at derived/block/*. That
        // model keeps upstream's orientation (its back on north, i.e. authored facing south), so the
        // rotations here are upstream's blockstates/seared_chute.json y-values rather than
        // horizontalBlock's north-facing convention.
        Identifier chuteModel = modLoc("block/seared_chute");
        tiered(ForgeweaveBlocks.SEARED_CHUTE.get(), tier -> tier == SmelteryCore.STANDARD ? chuteModel
                : model("block/seared_chute_" + tier.getSerializedName(), chuteModel, textures(
                        "particle", tierTexture("seared_chute_side", tier),
                        "top", tierTexture("seared_chute_top", tier),
                        "side", tierTexture("seared_chute_side", tier),
                        "back", tierTexture("seared_drain_back", tier),
                        "bricks", tierTexture("seared_bricks", tier))),
                state -> (int) state.getValue(SearedChuteBlock.FACING).toYRot());

        // #441 (parity audit T9) -- the seared channel. Its blockstate is upstream 1.12's own
        // multipart channel.json (NOTICE.md): one centre part switched on `down`, and one part per
        // side per connection state, the side models rotated onto north/east/south/west. The models
        // themselves are hand-authored JSON under models/block/channel/, transcribed from upstream's
        // channel/{center,center_out,side,side_in,side_out}.json. Upstream's fifth `lever` state --
        // a cosmetic backing plate drawn when a lever or button is stuck to a channel's side -- is
        // deliberately absent; upstream itself dropped it adapting the block to modern Minecraft.
        Identifier channelCentre = modLoc("block/channel/center");
        Identifier channelCentreOut = modLoc("block/channel/center_out");
        Identifier channelSide = modLoc("block/channel/side");
        Identifier channelSideIn = modLoc("block/channel/side_in");
        Identifier channelSideOut = modLoc("block/channel/side_out");
        MultiPartGenerator channel = MultiPartGenerator.multiPart(ForgeweaveBlocks.SEARED_CHANNEL.get())
                .with(new ConditionBuilder().term(SearedChannelBlock.DOWN, false), part(channelCentre, 0, 0, false))
                .with(new ConditionBuilder().term(SearedChannelBlock.DOWN, true), part(channelCentreOut, 0, 0, false));
        for (var side : SearedChannelBlock.SIDES.entrySet()) {
            // The side models are authored on the north face; upstream's own blockstate rotates
            // them north:0, east:90, south:180, west:270, which is toYRot() turned half round.
            int rotation = ((int) side.getKey().toYRot() + 180) % 360;
            for (ChannelConnection connection : ChannelConnection.values()) {
                Identifier model = switch (connection) {
                    case NONE -> channelSide;
                    case IN -> channelSideIn;
                    case OUT -> channelSideOut;
                };
                channel = channel.with(new ConditionBuilder().term(side.getValue(), connection), part(model, 0, rotation, true));
            }
        }
        blockStates.accept(channel);
        simpleBlockItem(ForgeweaveBlocks.SEARED_CHANNEL.get(), modLoc("block/channel/item"));

        // #100 -- casting (docs/SCOPE.md M2 issue #100). All four models are hand-authored JSON under
        // models/block/, transcribed from upstream 1.12's own casting_table/casting_basin/faucet/
        // faucet_top models (NOTICE.md) with the texture slots pointed at derived/block/*; the
        // rotations below are upstream's blockstates/faucet.json y-values, with the faucet model
        // authored facing south.
        Identifier castingTable = modLoc("block/casting_table");
        simpleBlock(ForgeweaveBlocks.CASTING_TABLE.get(), castingTable);
        simpleBlockItem(ForgeweaveBlocks.CASTING_TABLE.get(), castingTable);

        Identifier castingBasin = modLoc("block/casting_basin");
        simpleBlock(ForgeweaveBlocks.CASTING_BASIN.get(), castingBasin);
        simpleBlockItem(ForgeweaveBlocks.CASTING_BASIN.get(), castingBasin);

        Identifier faucet = modLoc("block/faucet");
        Identifier faucetTop = modLoc("block/faucet_top");
        forAllStates(ForgeweaveBlocks.FAUCET.get(), state -> switch (state.getValue(FaucetBlock.FACING)) {
            case UP -> variant(faucetTop, 0, 0);
            case NORTH -> variant(faucet, 0, 180);
            case EAST -> variant(faucet, 0, 270);
            case WEST -> variant(faucet, 0, 90);
            default -> variant(faucet, 0, 0);
        });
        simpleBlockItem(ForgeweaveBlocks.FAUCET.get(), faucet);

        // The Wooden Hopper (docs/SCOPE.md M5, issue #822): parents vanilla's own two hopper models
        // (block/hopper for the down-facing orientation, block/hopper_side for the four horizontal
        // ones) and swaps their three texture variables for the derived wood-themed sprites
        // (NOTICE.md) -- the geometry itself is unmodified vanilla. The item model is a separate flat
        // sprite registered in ForgeweaveItemModelProvider, matching vanilla's own hopper item.
        Map<String, Material> hopperTextures = textures(
                "top", modLoc("derived/block/wooden_hopper_top"),
                "side", modLoc("derived/block/wooden_hopper_outside"),
                "inside", modLoc("derived/block/wooden_hopper_inside"),
                "particle", modLoc("derived/block/wooden_hopper_outside"));
        Identifier woodenHopperDown = model("block/wooden_hopper", mcLoc("block/hopper"), hopperTextures);
        Identifier woodenHopperSide = model("block/wooden_hopper_side", mcLoc("block/hopper_side"), hopperTextures);
        forAllStates(ForgeweaveBlocks.WOODEN_HOPPER.get(), state -> switch (state.getValue(HopperBlock.FACING)) {
            case DOWN -> variant(woodenHopperDown, 0, 0);
            case NORTH -> variant(woodenHopperSide, 0, 0);
            case SOUTH -> variant(woodenHopperSide, 0, 180);
            case WEST -> variant(woodenHopperSide, 0, 270);
            case EAST -> variant(woodenHopperSide, 0, 90);
            default -> variant(woodenHopperDown, 0, 0);
        });

        // #104 -- cobalt + ardite nether ore (docs/SCOPE.md M2 issue #104): plain cube_all geometry,
        // like the seared brick family, with one composited derived texture per ore (NOTICE.md).
        cubeAllBlock("cobalt_ore", ForgeweaveBlocks.COBALT_ORE.get());
        cubeAllBlock("ardite_ore", ForgeweaveBlocks.ARDITE_ORE.get());

        // #903 -- brimspar ore: same plain cube_all geometry, but from the standard textures/block/
        // folder like the Track B ores below -- vanilla-derived art (scripts/generate_track_b_ore_textures.py),
        // not an upstream-derived sprite.
        cubeAllBlockOriginal("brimspar_ore", ForgeweaveBlocks.BRIMSPAR_ORE.get());

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
        // #843 (closes #180) -- queen's slime and hepatizon storage blocks (the 1.20-branch material
        // gap), same recolor-of-manyullyn treatment as rose_gold_block/amethyst_bronze_block above.
        cubeAllBlock("queens_slime_block", ForgeweaveBlocks.QUEENS_SLIME_BLOCK.get());
        cubeAllBlock("hepatizon_block", ForgeweaveBlocks.HEPATIZON_BLOCK.get());
        cubeAllBlock("firewood", ForgeweaveBlocks.FIREWOOD.get());

        cubeAllBlock("amethyst_bronze_block", ForgeweaveBlocks.AMETHYST_BRONZE_BLOCK.get());

        // #839 -- Track B's ore family (M6 epic #824): plain cube_all geometry like the rest of this
        // provider's ore/storage blocks, but from the standard textures/block/ folder -- original,
        // procedurally-generated art (scripts/generate_track_b_ore_textures.py), not a derived sprite.
        for (TrackBOre ore : TrackBOre.ALL) {
            cubeAllBlockOriginal(ore.oreBlockId(), ForgeweaveBlocks.trackBOre(ore.id()).get());
            cubeAllBlockOriginal(ore.storageBlockId(), ForgeweaveBlocks.trackBStorageBlock(ore.id()).get());
            // #929 -- fulmenite has no raw-storage block (TrackBOre#dropsCrystal); its crystal item
            // model is registered in ForgeweaveItemModelProvider instead, like brimspar's crystal.
            if (!ore.dropsCrystal()) {
                cubeAllBlockOriginal(ore.rawBlockId(), ForgeweaveBlocks.trackBRawBlock(ore.id()).get());
            }
        }

        // #840 -- Track B's 18 alloy tool materials' storage blocks: same original,
        // procedurally-generated art as the ore family above (scripts/generate_track_b_alloy_textures.py),
        // alloy-only so just the one storage block each, no ore/raw block.
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            cubeAllBlockOriginal(alloy.blockId(), ForgeweaveBlocks.trackBAlloyBlock(alloy.id()).get());
        }

        // #275/#951 -- clear glass (cutout, like seared glass above) and its 16 clear stained glass
        // colors (translucent, matching upstream's BlockClearStainedGlass#getBlockLayer). Both draw
        // with upstream's connected textures; see connectedGlassBlock.
        connectedGlassBlock(ForgeweaveBlocks.CLEAR_GLASS.get(), connectedGlassModels("clear_glass", false));
        // All 16 colors share one model set: one texture, tinted apart per block instance by
        // ForgeweaveGlassColors, exactly as upstream's own blockstate has one file for all 16 metas.
        // The item side of that tint is the client item's constant tint (connectedGlassBlock).
        Map<String, Identifier> stained = connectedGlassModels("clear_stained_glass", true);
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            connectedGlassBlock(color.block().get(), stained, ItemModelUtils.constantTint(ARGB.opaque(color.tint())));
        }

        slimeIslandBlocks(); // #449 (parity audit T18)
        moltenFluidBlocks();
    }

    /**
     * Every smeltery fluid's {@code LiquidBlock}: one particle-only model for every {@code level},
     * the way vanilla's water and lava do it, since the fluid renderer draws the fluid itself.
     * Without it the model bakery logged a missing model per fluid per level, 2352 WARNs on a
     * world with Forgeweave alone (playtest 1.0.0 line 8).
     */
    private void moltenFluidBlocks() {
        for (ForgeweaveFluids.MoltenMetal fluid : ForgeweaveFluids.all()) {
            Block block = fluid.block().get();
            Identifier model = model("block/" + name(block), null, textures("particle", fluid.stillTexture()));
            simpleBlock(block, model);
        }
    }

    /**
     * The slime island's world blocks (#449, parity audit T18), all textured from the clone
     * (NOTICE.md). Three of the four shapes are plain: dirt and congealed slime are {@code cube_all},
     * leaves are vanilla's tinted {@code block/leaves}, and the plants are vanilla's
     * {@code block/tinted_cross}. Slime grass is the one composite -- upstream's
     * {@code blockstates/slime_grass.json} draws it as vanilla's own grass model with the dirt
     * texture on the sides and bottom, a greyscale top, and a greyscale side overlay, both of the
     * latter tinted by foliage colour ({@code ForgeweaveFoliageColors}), which is exactly what
     * parenting {@code minecraft:block/grass_block} gives.
     *
     * <p>One shared texture per tinted shape rather than one per colour, the same "one greyscale
     * texture + per-instance tint" idiom the clear stained glass already uses -- and upstream's own,
     * since {@code SlimeColorizer} is what paints all of these. In the inventory the tint is the
     * foliage type's flat colour, a constant tint on the client item.
     */
    private void slimeIslandBlocks() {
        for (ForgeweaveBlocks.SlimeSoil soil : ForgeweaveBlocks.slimeSoils()) {
            String dirtName = name(soil.dirt().get());
            cubeAllBlock(dirtName, soil.dirt().get());

            String grassName = name(soil.grass().get());
            Identifier grass = model("block/" + grassName, mcLoc("block/grass_block"), textures(
                    "particle", modLoc("derived/block/" + dirtName),
                    "bottom", modLoc("derived/block/" + dirtName),
                    "side", modLoc("derived/block/" + dirtName),
                    "top", modLoc("derived/block/slime_grass_top"),
                    "overlay", modLoc("derived/block/slime_grass_side_overlay")));
            simpleBlock(soil.grass().get(), grass);
            simpleBlockItem(soil.grass().get(), grass, foliageTint(soil.foliage()));
        }

        // Every colour's congealed slime is a plain cube_all off its own derived sprite; the
        // coloured slime blocks (#635) are the same cube_all but translucent, which is the render
        // layer vanilla's own slime block uses and upstream's BlockSlime inherits.
        for (ForgeweaveBlocks.SlimeFamily family : ForgeweaveBlocks.slimeFamilies()) {
            cubeAllBlock(name(family.congealed().get()), family.congealed().get());
            if (family.slimeBlock() != null) {
                String slimeName = name(family.slimeBlock().get());
                Identifier slime = model("block/" + slimeName, mcLoc("block/cube_all"),
                        Map.of("all", new Material(modLoc("derived/block/" + slimeName), true)));
                simpleBlock(family.slimeBlock().get(), slime);
                simpleBlockItem(family.slimeBlock().get(), slime);
            }
        }

        for (ForgeweaveBlocks.SlimePlants plants : ForgeweaveBlocks.slimePlants()) {
            ItemTintSource foliage = foliageTint(plants.foliage());
            String leavesName = name(plants.leaves().get());
            Identifier leaves = model("block/" + leavesName, mcLoc("block/leaves"),
                    textures("all", modLoc("derived/block/slime_leaves")));
            simpleBlock(plants.leaves().get(), leaves);
            simpleBlockItem(plants.leaves().get(), leaves, foliage);

            slimePlantBlock(plants.tallGrass().get(), "slime_tall_grass", foliage);
            slimePlantBlock(plants.fern().get(), "slime_fern", foliage);

            // #488 (parity audit T57): the sapling is a plain cross with its own coloured texture
            // (upstream ships slimesapling_blue/purple rather than tinting one greyscale sprite),
            // and each vine stage is vanilla's own multipart vine wired to a greyscale slime sprite.
            String saplingName = name(plants.sapling().get());
            Identifier saplingSprite = modLoc("derived/block/" + saplingName);
            simpleBlock(plants.sapling().get(), model("block/" + saplingName, mcLoc("block/cross"),
                    textures("cross", saplingSprite)));
            flatBlockItem(plants.sapling().get(), saplingSprite);

            if (!plants.vines().isEmpty()) {
                slimeVineBlock(plants.vine().get(), "slime_vine", foliage);
                slimeVineBlock(plants.vineMid().get(), "slime_vine_mid", foliage);
                slimeVineBlock(plants.vineEnd().get(), "slime_vine_end", foliage);
            }
        }
    }

    /** The inventory half of {@code ForgeweaveFoliageColors}: the foliage type's flat colour. */
    private static ItemTintSource foliageTint(FoliageType foliage) {
        return ItemModelUtils.constantTint(ARGB.opaque(foliage.color()));
    }

    /**
     * One slime vine stage: vanilla's {@code blockstates/vine.json} multipart verbatim -- one
     * rotation of {@code block/vine} per lit face, plus the same model again for the faceless state
     * so an unattached vine still renders -- over a slime vine sprite.
     */
    private void slimeVineBlock(Block block, String texture, ItemTintSource tint) {
        String blockName = name(block);
        Identifier sprite = modLoc("derived/block/" + texture);
        Identifier model = model("block/" + blockName, mcLoc("block/vine"), textures(
                "particle", sprite,
                "vine", sprite));
        MultiPartGenerator builder = MultiPartGenerator.multiPart(block);
        int[] rotations = {0, 90, 180, 270};
        BooleanProperty[] faces = {VineBlock.NORTH, VineBlock.EAST, VineBlock.SOUTH, VineBlock.WEST};
        for (int i = 0; i < faces.length; i++) {
            builder = builder
                    .with(new ConditionBuilder().term(faces[i], true), part(model, 0, rotations[i], rotations[i] != 0))
                    .with(faceless(), part(model, 0, rotations[i], rotations[i] != 0));
        }
        builder = builder
                .with(new ConditionBuilder().term(VineBlock.UP, true), part(model, 270, 0, true))
                .with(faceless(), part(model, 270, 0, true));
        blockStates.accept(builder);
        flatBlockItem(block, sprite, tint);
    }

    /** A vine with no face lit at all, which vanilla's multipart still draws on every side. */
    private static ConditionBuilder faceless() {
        return new ConditionBuilder().term(VineBlock.UP, false).term(VineBlock.NORTH, false).term(VineBlock.EAST, false)
                .term(VineBlock.SOUTH, false).term(VineBlock.WEST, false);
    }

    /** A slimy tall grass or fern: a tinted cross in the world, a flat tinted sprite in the hand. */
    private void slimePlantBlock(Block block, String texture, ItemTintSource tint) {
        String blockName = name(block);
        Identifier sprite = modLoc("derived/block/" + texture);
        simpleBlock(block, model("block/" + blockName, mcLoc("block/tinted_cross"), textures("cross", sprite)));
        flatBlockItem(block, sprite, tint);
    }

    private static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
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
        Identifier model = model("block/" + name, modLoc("block/chest"), textures(
                "side", modLoc("derived/block/" + name + "_side"),
                "front", modLoc("derived/block/" + name + "_front"),
                "top", modLoc("derived/block/" + name + "_top"),
                "drawer_front", modLoc("derived/block/" + name + "_drawer_front"),
                "drawer_side", modLoc("derived/block/" + name + "_drawer_side")));
        horizontalBlock(block, model);
        simpleBlockItem(block, model);
    }

    /**
     * The Nether Core is {@link #coreBlock} plus {@link SmelteryControllerBlock#HOT}: the same
     * orientable shape over the same side and idle front, with the designer's v2 lit front
     * ({@code nether_core_v2_front_active}) while the fuel is hot. Hot implies formed, so a hot idle
     * front is never shown and simply reuses the normal one.
     */
    private void netherCoreBlock() {
        Identifier side = modLoc("derived/block/nether_core_side");
        Identifier inactive = orientable("nether_core", side, modLoc("derived/block/nether_core_front_inactive"));
        Map<Boolean, Map<Boolean, Identifier>> models = new HashMap<>();
        for (boolean hot : new boolean[] {false, true}) {
            String active = hot ? "nether_core_v2_front_active" : "nether_core_front_active";
            Map<Boolean, Identifier> byActive = new HashMap<>();
            byActive.put(false, inactive);
            byActive.put(true, orientable(hot ? "nether_core_v2_active" : "nether_core_active", side, modLoc("derived/block/" + active)));
            models.put(hot, byActive);
        }
        forAllStates(ForgeweaveBlocks.NETHER_CORE.get(), state -> variant(
                models.get(state.getValue(SmelteryControllerBlock.HOT)).get(state.getValue(SmelteryControllerBlock.ACTIVE)),
                0, horizontalRotation(state)));
        simpleBlockItem(ForgeweaveBlocks.NETHER_CORE.get(), models.get(false).get(false));
    }

    private void coreBlock(String name, Block block, String sideTexture) {
        Identifier side = modLoc("derived/block/" + sideTexture);
        Identifier inactive = orientable(name, side, modLoc("derived/block/" + name + "_front_inactive"));
        Identifier active = orientable(name + "_active", side, modLoc("derived/block/" + name + "_front_active"));
        forAllStates(block, state -> variant(state.getValue(SmelteryControllerBlock.ACTIVE) ? active : inactive,
                0, horizontalRotation(state)));
        simpleBlockItem(block, inactive);
    }

    /** Vanilla's {@code block/orientable} with the side texture on the top as well, 1.21.1's {@code orientable(name, side, front, side)}. */
    private Identifier orientable(String name, Identifier side, Identifier front) {
        return model("block/" + name, mcLoc("block/orientable"), textures("side", side, "front", front, "top", side));
    }

    /** {@code derived/block/<base>} at the standard tier, {@code derived/block/<base>_<tier>} otherwise (SearedTier). */
    private Identifier tierTexture(String base, SmelteryCore tier) {
        return modLoc("derived/block/" + base + (tier == SmelteryCore.STANDARD ? "" : "_" + tier.getSerializedName()));
    }

    /** {@code name} at the standard tier, {@code name_<tier>} otherwise -- the model file name per tier. */
    private static String tierName(String name, SmelteryCore tier) {
        return tier == SmelteryCore.STANDARD ? name : name + "_" + tier.getSerializedName();
    }

    /**
     * One variant per {@link SearedTier#TIER} value (times {@code rotationY} for a facing block),
     * with the standard tier's model as the block item. Models are built once per tier, not per
     * state, so a facing block's four rotations share them.
     */
    private void tiered(Block block, Function<SmelteryCore, Identifier> model, ToIntFunction<BlockState> rotationY) {
        Map<SmelteryCore, Identifier> models = new EnumMap<>(SmelteryCore.class);
        for (SmelteryCore tier : SmelteryCore.values()) {
            models.put(tier, model.apply(tier));
        }
        forAllStates(block, state -> variant(models.get(state.getValue(SearedTier.TIER)), 0, rotationY.applyAsInt(state)));
        simpleBlockItem(block, models.get(SmelteryCore.STANDARD));
    }

    private void tieredCubeAll(String name, Block block) {
        tiered(block, tier -> cubeAll("block/" + tierName(name, tier), tierTexture(name, tier)), state -> 0);
    }

    /**
     * The energized tank (#972): a plain six-face cube whose side texture swaps on
     * {@link EnergizedTankBlock#OVERDRIVE}, so the button on the block reads as pressed or not. The
     * block item takes the un-pressed model, which is the state a freshly placed tank is in.
     */
    private void energizedTank() {
        Identifier top = modLoc("block/energized_tank_top");
        Identifier off = cubeBottomTop("energized_tank", modLoc("block/energized_tank_side"), top);
        Identifier on = cubeBottomTop("energized_tank_overdrive", modLoc("block/energized_tank_side_overdrive"), top);
        forAllStates(ForgeweaveBlocks.ENERGIZED_TANK.get(),
                state -> variant(state.getValue(EnergizedTankBlock.OVERDRIVE) ? on : off, 0, 0));
        simpleBlockItem(ForgeweaveBlocks.ENERGIZED_TANK.get(), off);
    }

    /**
     * The tank family: side and top textures per type. #145: the derived side/top textures carry
     * real alpha-cutout windows (0 and 255 alpha values), which 26.1 sorts onto the cutout layer by
     * itself.
     */
    private void tieredTank(String name, Block block, String sideTexture, String topTexture) {
        tiered(block, tier -> cubeBottomTop(tierName(name, tier), tierTexture(sideTexture, tier), tierTexture(topTexture, tier)),
                state -> 0);
    }

    /** {@code block/cube_bottom_top} with the same texture on the top and the bottom. */
    private Identifier cubeBottomTop(String name, Identifier side, Identifier end) {
        return model("block/" + name, mcLoc("block/cube_bottom_top"), textures("side", side, "bottom", end, "top", end));
    }

    /**
     * The drain and duct: distinct front and back faces over seared brick sides, so the full six-face
     * cube rather than "orientable" (which would repeat the side texture on the back); rotated the
     * way horizontalBlock rotates a north-authored model.
     */
    private void tieredDrainLike(String name, Block block, String frontTexture) {
        tiered(block, tier -> {
            Identifier side = tierTexture("seared_bricks", tier);
            return model("block/" + tierName(name, tier), mcLoc("block/cube"), textures(
                    "down", side,
                    "up", side,
                    "north", tierTexture(frontTexture, tier),
                    "south", tierTexture("seared_drain_back", tier),
                    "east", side,
                    "west", side,
                    "particle", side));
        }, ForgeweaveBlockStateProvider::horizontalRotation);
    }

    private void cubeAllBlock(String name, Block block) {
        Identifier model = cubeAll("block/" + name, modLoc("derived/block/" + name));
        simpleBlock(block, model);
        simpleBlockItem(block, model);
    }

    /**
     * A plain cube_all block textured from the standard (non-derived) {@code textures/block/}
     * folder, for original Forgeweave art rather than an upstream-derived sprite -- e.g. Track B's
     * ore family (issue #839), whose textures are procedurally generated, not ported.
     */
    private void cubeAllBlockOriginal(String name, Block block) {
        Identifier model = cubeAll("block/" + name, modLoc("block/" + name));
        simpleBlock(block, model);
        simpleBlockItem(block, model);
    }

    private Identifier cubeAll(String path, Identifier texture) {
        return model(path, mcLoc("block/cube_all"), textures("all", texture));
    }

    /** A seared stairs block (issue #274), textured from its parent seared block's derived texture. */
    private void searedStairs(String name, StairBlock block, String parentTexture) {
        Map<String, Material> texture = sameTexture(modLoc("derived/block/" + parentTexture));
        Identifier stairs = model("block/" + name, mcLoc("block/stairs"), texture);
        Identifier inner = model("block/" + name + "_inner", mcLoc("block/inner_stairs"), texture);
        Identifier outer = model("block/" + name + "_outer", mcLoc("block/outer_stairs"), texture);
        blockStates.accept(BlockModelGenerators.createStairs(block,
                BlockModelGenerators.plainVariant(inner), BlockModelGenerators.plainVariant(stairs),
                BlockModelGenerators.plainVariant(outer)));
        simpleBlockItem(block, stairs);
    }

    /**
     * A seared slab block (issue #274), textured from its parent seared block's derived texture. The
     * double-slab model reuses the parent cube_all block model registered by {@link #tieredCubeAll}.
     */
    private void searedSlab(String name, SlabBlock block, String parentTexture) {
        Map<String, Material> texture = sameTexture(modLoc("derived/block/" + parentTexture));
        Identifier bottom = model("block/" + name, mcLoc("block/slab"), texture);
        Identifier top = model("block/" + name + "_top", mcLoc("block/slab_top"), texture);
        blockStates.accept(BlockModelGenerators.createSlab(block, BlockModelGenerators.plainVariant(bottom),
                BlockModelGenerators.plainVariant(top), BlockModelGenerators.plainVariant(modLoc("block/" + parentTexture))));
        simpleBlockItem(block, bottom);
    }

    /** The bottom/top/side triple vanilla's stairs and slab models bind, all one texture. */
    private static Map<String, Material> sameTexture(Identifier texture) {
        return textures("bottom", texture, "top", texture, "side", texture);
    }

    /**
     * One connected-texture model, ported 1:1 from Mantle 1.12's {@code models/block/connected_*.json}
     * (NOTICE.md): the suffix those files carry after {@code connected_}, then the eleven-sprite frame
     * each of the six faces binds. All of them sit on {@code block/tinted_cube}, Forgeweave's port of
     * the Mantle parent they inherit upstream, and take {@code normal} as their particle.
     */
    private record ConnectedModel(String suffix, String down, String up, String north, String south,
            String west, String east) {}

    /**
     * Mantle's nine connected models, transcribed. Nine cover all 64 neighbour combinations because
     * the blockstate rotates them: see {@link #CONNECTED_VARIANTS}.
     */
    private static final List<ConnectedModel> CONNECTED_MODELS = List.of(
            new ConnectedModel("0", "normal", "normal", "normal", "normal", "normal", "normal"),
            new ConnectedModel("1", "normal", "udlr", "u", "u", "u", "u"),
            new ConnectedModel("2", "udlr", "udlr", "ud", "ud", "ud", "ud"),
            new ConnectedModel("2_angle", "r", "udlr", "ul", "ur", "u", "udlr"),
            new ConnectedModel("3_t1", "udlr", "udlr", "udr", "udl", "udlr", "ud"),
            new ConnectedModel("3_t2", "lr", "udlr", "ulr", "ulr", "udlr", "udlr"),
            new ConnectedModel("3_angle", "ur", "udlr", "ul", "udlr", "ur", "udlr"),
            new ConnectedModel("4_angle", "udlr", "udlr", "udlr", "udl", "udlr", "udr"),
            new ConnectedModel("4_cross", "udlr", "udlr", "udlr", "udlr", "udlr", "udlr"));

    /** Which of {@link #CONNECTED_MODELS} a neighbour combination draws, and at what rotation. */
    private record ConnectedVariant(String suffix, int x, int y) {}

    /**
     * Upstream's {@code blockstates/clear_stained_glass.json} variant table, transcribed 1:1
     * (NOTICE.md). The key is the six neighbour flags as bits in the order upstream writes them --
     * down, east, north, south, up, west -- and the values are its model and its {@code x}/{@code y}
     * rotations, normalised into the 0-270 range 1.21 accepts (upstream writes some as negatives).
     * All 64 combinations are listed there, so no fallback is needed here.
     */
    private static final Map<String, ConnectedVariant> CONNECTED_VARIANTS = Map.ofEntries(
            Map.entry("000000", new ConnectedVariant("0", 0, 0)),
            Map.entry("000001", new ConnectedVariant("1", 90, 270)),
            Map.entry("000010", new ConnectedVariant("1", 0, 0)),
            Map.entry("000011", new ConnectedVariant("2_angle", 0, 180)),
            Map.entry("000100", new ConnectedVariant("1", 270, 0)),
            Map.entry("000101", new ConnectedVariant("2_angle", 270, 90)),
            Map.entry("000110", new ConnectedVariant("2_angle", 0, 90)),
            Map.entry("000111", new ConnectedVariant("3_angle", 0, 90)),
            Map.entry("001000", new ConnectedVariant("1", 90, 0)),
            Map.entry("001001", new ConnectedVariant("2_angle", 90, 270)),
            Map.entry("001010", new ConnectedVariant("2_angle", 0, 270)),
            Map.entry("001011", new ConnectedVariant("3_angle", 90, 270)),
            Map.entry("001100", new ConnectedVariant("2", 270, 0)),
            Map.entry("001101", new ConnectedVariant("3_t1", 270, 0)),
            Map.entry("001110", new ConnectedVariant("3_t2", 0, 90)),
            Map.entry("001111", new ConnectedVariant("4_angle", 270, 0)),
            Map.entry("010000", new ConnectedVariant("1", 90, 90)),
            Map.entry("010001", new ConnectedVariant("2", 270, 90)),
            Map.entry("010010", new ConnectedVariant("2_angle", 0, 0)),
            Map.entry("010011", new ConnectedVariant("3_t2", 0, 0)),
            Map.entry("010100", new ConnectedVariant("2_angle", 270, 0)),
            Map.entry("010101", new ConnectedVariant("3_t2", 270, 0)),
            Map.entry("010110", new ConnectedVariant("3_angle", 0, 0)),
            Map.entry("010111", new ConnectedVariant("4_angle", 270, 270)),
            Map.entry("011000", new ConnectedVariant("2_angle", 90, 0)),
            Map.entry("011001", new ConnectedVariant("3_t2", 90, 0)),
            Map.entry("011010", new ConnectedVariant("3_angle", 90, 0)),
            Map.entry("011011", new ConnectedVariant("4_angle", 270, 90)),
            Map.entry("011100", new ConnectedVariant("3_t1", 270, 180)),
            Map.entry("011101", new ConnectedVariant("4_cross", 90, 0)),
            Map.entry("011110", new ConnectedVariant("4_angle", 270, 180)),
            Map.entry("011111", new ConnectedVariant("4_cross", 0, 0)),
            Map.entry("100000", new ConnectedVariant("1", 180, 0)),
            Map.entry("100001", new ConnectedVariant("2_angle", 180, 180)),
            Map.entry("100010", new ConnectedVariant("2", 0, 0)),
            Map.entry("100011", new ConnectedVariant("3_t1", 0, 0)),
            Map.entry("100100", new ConnectedVariant("2_angle", 180, 90)),
            Map.entry("100101", new ConnectedVariant("3_angle", 180, 180)),
            Map.entry("100110", new ConnectedVariant("3_t1", 0, 270)),
            Map.entry("100111", new ConnectedVariant("4_angle", 0, 270)),
            Map.entry("101000", new ConnectedVariant("2_angle", 180, 270)),
            Map.entry("101001", new ConnectedVariant("3_angle", 270, 180)),
            Map.entry("101010", new ConnectedVariant("3_t1", 0, 90)),
            Map.entry("101011", new ConnectedVariant("4_angle", 0, 0)),
            Map.entry("101100", new ConnectedVariant("3_t2", 180, 90)),
            Map.entry("101101", new ConnectedVariant("4_angle", 90, 0)),
            Map.entry("101110", new ConnectedVariant("4_cross", 0, 90)),
            Map.entry("101111", new ConnectedVariant("4_cross", 0, 0)),
            Map.entry("110000", new ConnectedVariant("2_angle", 180, 0)),
            Map.entry("110001", new ConnectedVariant("3_t2", 180, 0)),
            Map.entry("110010", new ConnectedVariant("3_t1", 0, 180)),
            Map.entry("110011", new ConnectedVariant("4_cross", 0, 0)),
            Map.entry("110100", new ConnectedVariant("3_angle", 270, 0)),
            Map.entry("110101", new ConnectedVariant("4_angle", 90, 270)),
            Map.entry("110110", new ConnectedVariant("4_angle", 0, 180)),
            Map.entry("110111", new ConnectedVariant("4_cross", 0, 0)),
            Map.entry("111000", new ConnectedVariant("3_angle", 180, 0)),
            Map.entry("111001", new ConnectedVariant("4_angle", 90, 90)),
            Map.entry("111010", new ConnectedVariant("4_angle", 0, 90)),
            Map.entry("111011", new ConnectedVariant("4_cross", 0, 0)),
            Map.entry("111100", new ConnectedVariant("4_angle", 90, 180)),
            Map.entry("111101", new ConnectedVariant("4_cross", 0, 0)),
            Map.entry("111110", new ConnectedVariant("4_cross", 0, 0)),
            Map.entry("111111", new ConnectedVariant("4_cross", 0, 0)));

    /** The sides in the bit order {@link #CONNECTED_VARIANTS} keys use, which is upstream's own. */
    private static final List<BooleanProperty> CONNECTED_SIDES = List.of(
            ConnectedGlassBlock.CONNECTED_DOWN, ConnectedGlassBlock.CONNECTED_EAST,
            ConnectedGlassBlock.CONNECTED_NORTH, ConnectedGlassBlock.CONNECTED_SOUTH,
            ConnectedGlassBlock.CONNECTED_UP, ConnectedGlassBlock.CONNECTED_WEST);

    /**
     * Builds one glass's nine connected models over its eleven sprites under
     * {@code derived/block/connected/&lt;set&gt;/}, indexed the same way {@link #CONNECTED_MODELS} is.
     * A translucent glass marks its six faces {@code force_translucent}, 26.1's replacement for the
     * 1.21.1 {@code minecraft:translucent} render type; a cutout one leaves the layer to its alpha.
     *
     * <p>Upstream keeps the sprite names in the blockstate file and the face bindings in Mantle's
     * shared models, which 1.21 has no equivalent for -- a blockstate cannot retexture a model any
     * more. So each glass gets its own nine concrete models instead, which is the same nine bindings
     * with the texture paths already resolved.
     */
    private Map<String, Identifier> connectedGlassModels(String set, boolean translucent) {
        Map<String, Identifier> glass = new LinkedHashMap<>();
        for (ConnectedModel model : CONNECTED_MODELS) {
            Map<String, Material> faces = new LinkedHashMap<>();
            faces.put("particle", new Material(connectedSprite(set, "normal")));
            faces.put("down", new Material(connectedSprite(set, model.down()), translucent));
            faces.put("up", new Material(connectedSprite(set, model.up()), translucent));
            faces.put("north", new Material(connectedSprite(set, model.north()), translucent));
            faces.put("south", new Material(connectedSprite(set, model.south()), translucent));
            faces.put("west", new Material(connectedSprite(set, model.west()), translucent));
            faces.put("east", new Material(connectedSprite(set, model.east()), translucent));
            glass.put(model.suffix(), model("block/connected/" + set + "/" + model.suffix(), modLoc("block/tinted_cube"), faces));
        }
        return glass;
    }

    private Identifier connectedSprite(String set, String frame) {
        return modLoc("derived/block/connected/" + set + "/" + frame);
    }

    /**
     * One connected-texture glass block (issues #275, #951): 64 variants, one per combination of the
     * six {@code connected_*} flags {@link ConnectedGlassBlock} sets from its same-block neighbours,
     * each pointing at one of {@code models} at upstream's own rotation.
     *
     * <p>The item model is the isolated frame, {@code connected/&lt;set&gt;/0} -- upstream's blockstate
     * gives {@code inventory} the same {@code mantle:connected_0} it gives a pane with no neighbours.
     */
    private void connectedGlassBlock(Block block, Map<String, Identifier> models, ItemTintSource... tints) {
        forAllStates(block, state -> {
            StringBuilder key = new StringBuilder(CONNECTED_SIDES.size());
            for (BooleanProperty side : CONNECTED_SIDES) {
                key.append(state.getValue(side) ? '1' : '0');
            }
            ConnectedVariant variant = CONNECTED_VARIANTS.get(key.toString());
            return variant(models.get(variant.suffix()), variant.x(), variant.y());
        });
        simpleBlockItem(block, models.get("0"), tints);
    }

    // ---- 1.21.1 BlockStateProvider / ModelProvider equivalents ----

    /**
     * A table station (issue #43): {@link #horizontalBlock}'s variants, each wrapped in the
     * {@code forgeweave:retextured_table} blockstate type that swaps the table into the crafting
     * wood, and a {@code forgeweave:retextured_table} client item over the usual block-item model.
     */
    private void retexturedTable(Block block, Identifier model) {
        forAllStatesUnbaked(block, state -> MultiVariant.of(new CustomBlockStateModelBuilder.Simple(
                new RetexturedTableGeometry.Unbaked(variant(model, 0, horizontalRotation(state))))).toUnbaked());
        items.accept(block.asItem(), new RetexturedTableItemModel.Unbaked(blockItemModel(block, model)));
    }

    /** NeoForge 1.21.1's {@code horizontalBlock}: a north-authored model turned to face {@code facing}. */
    private void horizontalBlock(Block block, Identifier model) {
        forAllStates(block, state -> variant(model, 0, horizontalRotation(state)));
    }

    private static int horizontalRotation(BlockState state) {
        return ((int) state.getValue(HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360;
    }

    /** One model for every state, the {@code ""} variant. */
    private void simpleBlock(Block block, Identifier model) {
        forAllStatesUnbaked(block, state -> BlockModelGenerators.plainVariant(model).toUnbaked(), true);
    }

    /** {@code models/item/<block>} parenting {@code model}, and the block item's client item on it. */
    private void simpleBlockItem(Block block, Identifier model, ItemTintSource... tints) {
        items.accept(block.asItem(), ItemModelUtils.tintedModel(blockItemModel(block, model), tints));
    }

    private Identifier blockItemModel(Block block, Identifier model) {
        return model("item/" + name(block), model, Map.of());
    }

    /** A block item drawn as a flat {@code item/generated} sprite rather than its block model. */
    private void flatBlockItem(Block block, Identifier sprite, ItemTintSource... tints) {
        Identifier model = model("item/" + name(block), mcLoc("item/generated"), textures("layer0", sprite));
        items.accept(block.asItem(), ItemModelUtils.tintedModel(model, tints));
    }

    /** One variant per block state, keyed by every property the way 1.21.1's {@code forAllStates} keyed it. */
    private void forAllStates(Block block, Function<BlockState, Variant> variant) {
        forAllStatesUnbaked(block, state -> BlockModelGenerators.variant(variant.apply(state)).toUnbaked());
    }

    private void forAllStatesUnbaked(Block block, Function<BlockState, BlockStateModel.Unbaked> model) {
        forAllStatesUnbaked(block, model, false);
    }

    private void forAllStatesUnbaked(Block block, Function<BlockState, BlockStateModel.Unbaked> model, boolean single) {
        blockStates.accept(new BlockModelDefinitionGenerator() {
            @Override
            public BlockStateModelDispatcher create() {
                Map<String, BlockStateModel.Unbaked> variants = new HashMap<>();
                if (single) {
                    variants.put("", model.apply(block.defaultBlockState()));
                } else {
                    for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                        variants.put(new PropertyValueList(state.getValues().toList()).getKey(), model.apply(state));
                    }
                }
                return new BlockStateModelDispatcher(Optional.of(new BlockStateModelDispatcher.SimpleModelSelectors(variants)),
                        Optional.empty());
            }

            @Override
            public Block block() {
                return block;
            }
        });
    }

    private static Variant variant(Identifier model, int x, int y) {
        return new Variant(model).withXRot(Quadrant.parseJson(x)).withYRot(Quadrant.parseJson(y));
    }

    private static MultiVariant part(Identifier model, int x, int y, boolean uvLock) {
        return BlockModelGenerators.variant(variant(model, x, y).withUvLock(uvLock));
    }

    /** {@code {"parent": parent, "textures": textures}} at {@code forgeweave:<path>}, 1.21.1's {@code withExistingParent(...).texture(...)}. */
    private Identifier model(String path, @Nullable Identifier parent, Map<String, Material> textures) {
        Identifier id = modLoc(path);
        JsonObject json = new JsonObject();
        if (parent != null) {
            json.addProperty("parent", parent.toString());
        }
        if (!textures.isEmpty()) {
            JsonObject slots = new JsonObject();
            textures.forEach((slot, material) -> slots.add(slot, Material.CODEC.encodeStart(JsonOps.INSTANCE, material).getOrThrow()));
            json.add("textures", slots);
        }
        models.accept(id, () -> json);
        return id;
    }

    /** Alternating slot name / texture id pairs, as plain (not force-translucent) materials. */
    private static Map<String, Material> textures(Object... slotsAndTextures) {
        Map<String, Material> textures = new LinkedHashMap<>();
        for (int i = 0; i < slotsAndTextures.length; i += 2) {
            textures.put((String) slotsAndTextures[i], new Material((Identifier) slotsAndTextures[i + 1]));
        }
        return textures;
    }

    private static Identifier modLoc(String path) {
        return Identifier.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static Identifier mcLoc(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
