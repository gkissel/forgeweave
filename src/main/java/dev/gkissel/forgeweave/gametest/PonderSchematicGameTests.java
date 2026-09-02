package dev.gkissel.forgeweave.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedFurnaceBlockEntity;
import dev.gkissel.forgeweave.block.SearedFurnaceControllerBlock;
import dev.gkissel.forgeweave.block.SearedReservoirBlockEntity;
import dev.gkissel.forgeweave.block.SearedReservoirControllerBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * Issues #664, #700 and #891: the multiblock Ponder scenes must teach structures the real scans
 * accept. Scene playback is client-only, but each schematic ({@code assets/forgeweave/ponder/*.nbt},
 * the finished structure the scene reveals in stages) is plain data -- so these tests rebuild it
 * block-for-block on the dedicated GameTest server, place the controllers last (exercising the real
 * {@code onPlace} trigger, same as {@code SmelteryGameTests}), and assert every multiblock forms at
 * the size its scene narrates. If the schematic generator and {@code SmelteryScan},
 * {@code SearedFurnaceScan} or {@code SearedReservoirScan} ever drift apart, this fails instead of
 * players discovering the tutorial builds an invalid structure.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PonderSchematicGameTests {

    /** Raised one block so the schematic's decorative base plate stays off the template's own floor level. */
    private static final BlockPos OFFSET = new BlockPos(0, 1, 0);

    @GameTest(template = "smeltery")
    public static void ponderSchematicFormsARealSmeltery(GameTestHelper helper) {
        List<BlockPos> cores = build(helper, "smeltery");
        helper.assertValueEqual(cores.size(), 1, "cores in smeltery.nbt");
        assertFormed(helper, cores.get(0), 1, 1, 2);
        helper.succeed();
    }

    /**
     * #754: a playtest defect found the scene's structure was seared bricks apart from one tank and
     * one core cell, even though the callouts describe distinct components. The generator now also
     * places a drain and a seared glass pane in the walls; pin their presence so a future regeneration
     * cannot silently drop back to an all-bricks wall.
     */
    @GameTest(template = "smeltery")
    public static void ponderSchematicShowsDistinctWallBlocksNotJustBricks(GameTestHelper helper) {
        build(helper, "smeltery");
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_DRAIN.get(), new BlockPos(2, 3, 1).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_GLASS.get(), new BlockPos(1, 3, 2).offset(OFFSET));
        helper.succeed();
    }

    /** #700: the size-variants scene -- the smallest smeltery and a 3x3x3 one on the same plate, both real. */
    @GameTest(template = "smeltery")
    public static void ponderSizesSchematicFormsBothSmelteries(GameTestHelper helper) {
        List<BlockPos> cores = build(helper, "smeltery_sizes");
        helper.assertValueEqual(cores.size(), 2, "cores in smeltery_sizes.nbt");
        // The generator places the small (south-west) smeltery first.
        assertFormed(helper, cores.get(0), 1, 1, 2);
        assertFormed(helper, cores.get(1), 3, 3, 3);
        helper.succeed();
    }

    /** #754: the large smeltery's walls also carry a drain and a seared glass pane, not just bricks. */
    @GameTest(template = "smeltery")
    public static void ponderSizesSchematicLargeSmelteryShowsDistinctWallBlocks(GameTestHelper helper) {
        build(helper, "smeltery_sizes");
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_DRAIN.get(), new BlockPos(7, 3, 0).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_GLASS.get(), new BlockPos(4, 2, 1).offset(OFFSET));
        helper.succeed();
    }

    /**
     * #700's #369 beat: the sizes scene swaps a top-layer wall block of the large smeltery for
     * seared stairs and says the walls end there. Pin that against the real scan -- stairs and slabs
     * are ceiling-only blocks of the seared furnace, never smeltery walls, so the wall walk stops one
     * layer short and the interior shrinks from three high to two.
     */
    @GameTest(template = "smeltery")
    public static void searedStairsInAWallEndThePonderSizesSmelteryBelowThem(GameTestHelper helper) {
        List<BlockPos> cores = build(helper, "smeltery_sizes");
        BlockPos largeCore = cores.get(1);
        assertFormed(helper, largeCore, 3, 3, 3);

        BlockPos stairsDemo = new BlockPos(4, 4, 3).offset(OFFSET); // ForgeweaveSmelteryScenes.STAIRS_DEMO
        SmelteryControllerBlockEntity core = helper.getBlockEntity(largeCore);
        helper.setBlock(stairsDemo, ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get().defaultBlockState());
        core.updateStructure();
        assertFormed(helper, largeCore, 3, 3, 2);

        helper.setBlock(stairsDemo, ForgeweaveBlocks.SEARED_BRICKS.get().defaultBlockState());
        core.updateStructure();
        assertFormed(helper, largeCore, 3, 3, 3);
        helper.succeed();
    }

    /**
     * #700: the faucet-and-channel scene's smeltery forms, with its drain found in the walls.
     * #754: also pin the tank, channel, table and basin -- the whole point of this scene is that it
     * is not just seared bricks.
     */
    @GameTest(template = "smeltery")
    public static void ponderCastingSchematicFormsASmelteryWithItsDrain(GameTestHelper helper) {
        List<BlockPos> cores = build(helper, "casting");
        helper.assertValueEqual(cores.size(), 1, "cores in casting.nbt");
        assertFormed(helper, cores.get(0), 1, 1, 3);
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_DRAIN.get(), new BlockPos(5, 3, 4).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_TANK.get(), new BlockPos(5, 4, 4).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.FAUCET.get(), new BlockPos(5, 3, 3).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_CHANNEL.get(), new BlockPos(5, 2, 3).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.CASTING_TABLE.get(), new BlockPos(3, 1, 3).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.CASTING_BASIN.get(), new BlockPos(5, 1, 2).offset(OFFSET));
        helper.succeed();
    }

    /**
     * #891: the core tiers scene's smeltery forms 1x1x2 even with the transform faucet and its
     * source tank standing on the wall ring above the core -- the scan stops below that layer.
     */
    @GameTest(template = "smeltery")
    public static void ponderCoreTiersSchematicFormsASmelteryUnderItsFaucet(GameTestHelper helper) {
        List<BlockPos> cores = build(helper, "core_tiers");
        helper.assertValueEqual(cores.size(), 1, "cores in core_tiers.nbt");
        assertFormed(helper, cores.get(0), 1, 1, 2);
        helper.assertBlockPresent(ForgeweaveBlocks.FAUCET.get(), cores.get(0).above());
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_TANK.get(), cores.get(0).above().east());
        helper.succeed();
    }

    /** #891: the seared furnace scene's closed box forms under {@code SearedFurnaceScan}, tank in the corner and slab in the ceiling. */
    @GameTest(template = "smeltery")
    public static void ponderSearedFurnaceSchematicFormsARealFurnace(GameTestHelper helper) {
        List<BlockPos> controllers = build(helper, "seared_furnace");
        helper.assertValueEqual(controllers.size(), 1, "controllers in seared_furnace.nbt");
        SearedFurnaceBlockEntity furnace = helper.getBlockEntity(controllers.get(0));
        assertFormed(helper, furnace.structure(), furnace.lastResult(), 1, 1, 2);
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_TANK.get(), new BlockPos(1, 2, 1).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_SLAB_BRICKS.get(), new BlockPos(2, 4, 2).offset(OFFSET));
        helper.succeed();
    }

    /** #891: the seared reservoir scene's closed box forms under {@code SearedReservoirScan} with no tank, its drain, faucet and table in place. */
    @GameTest(template = "smeltery")
    public static void ponderSearedReservoirSchematicFormsARealReservoir(GameTestHelper helper) {
        List<BlockPos> controllers = build(helper, "seared_reservoir");
        helper.assertValueEqual(controllers.size(), 1, "controllers in seared_reservoir.nbt");
        SearedReservoirBlockEntity reservoir = helper.getBlockEntity(controllers.get(0));
        assertFormed(helper, reservoir.structure(), reservoir.lastResult(), 1, 1, 2);
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_DRAIN.get(), new BlockPos(1, 2, 2).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.FAUCET.get(), new BlockPos(0, 2, 2).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.CASTING_TABLE.get(), new BlockPos(0, 1, 2).offset(OFFSET));
        helper.succeed();
    }

    /** Places every block of the schematic, controllers last; returns the controller positions in schematic order. */
    private static List<BlockPos> build(GameTestHelper helper, String schematic) {
        CompoundTag root = readSchematic(schematic);
        HolderGetter<Block> blocks = helper.getLevel().holderLookup(Registries.BLOCK);
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
        }

        List<BlockPos> cores = new ArrayList<>();
        List<BlockState> coreStates = new ArrayList<>();
        for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) tag;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            BlockPos at = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)).offset(OFFSET);
            BlockState state = states.get(block.getInt("state"));
            Block placed = state.getBlock();
            if (placed instanceof SmelteryControllerBlock || placed instanceof SearedFurnaceControllerBlock
                    || placed instanceof SearedReservoirControllerBlock) {
                cores.add(at);
                coreStates.add(state);
            } else {
                helper.setBlock(at, state);
            }
        }
        helper.assertFalse(cores.isEmpty(), "the ponder schematic " + schematic + " must contain a controller");
        for (int i = 0; i < cores.size(); i++) {
            helper.setBlock(cores.get(i), coreStates.get(i));
        }
        return cores;
    }

    private static void assertFormed(GameTestHelper helper, BlockPos corePos, int width, int depth, int height) {
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        assertFormed(helper, core.structure(), core.lastResult(), width, depth, height);
    }

    private static void assertFormed(GameTestHelper helper, SmelteryStructure structure, Component lastResult, int width, int depth, int height) {
        helper.assertTrue(structure != null,
                "expected the ponder schematic's structure to form: " + lastResult.getString());
        helper.assertValueEqual(structure.width(), width, "interior width");
        helper.assertValueEqual(structure.depth(), depth, "interior depth");
        helper.assertValueEqual(structure.height(), height, "interior height");
    }

    private static CompoundTag readSchematic(String name) {
        try (InputStream in = Forgeweave.class.getResourceAsStream("/assets/forgeweave/ponder/" + name + ".nbt")) {
            if (in == null) {
                throw new IllegalStateException("assets/forgeweave/ponder/" + name + ".nbt is missing from the jar");
            }
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
