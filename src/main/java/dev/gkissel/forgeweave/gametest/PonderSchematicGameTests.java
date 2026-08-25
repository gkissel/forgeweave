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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * Issues #664 and #700: the smeltery Ponder scenes must teach structures the real scan accepts.
 * Scene playback is client-only, but each schematic ({@code assets/forgeweave/ponder/*.nbt}, the
 * finished structure the scene reveals in stages) is plain data -- so these tests rebuild it
 * block-for-block on the dedicated GameTest server, place the cores last (exercising the real
 * {@code SmelteryControllerBlock#onPlace} trigger, same as {@code SmelteryGameTests}), and assert
 * every multiblock forms at the size its scene narrates. If the schematic generator and
 * {@code SmelteryScan} ever drift apart, this fails instead of players discovering the tutorial
 * builds an invalid smeltery.
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

    /** #700: the faucet-and-channel scene's smeltery forms, with its drain found in the walls. */
    @GameTest(template = "smeltery")
    public static void ponderCastingSchematicFormsASmelteryWithItsDrain(GameTestHelper helper) {
        List<BlockPos> cores = build(helper, "casting");
        helper.assertValueEqual(cores.size(), 1, "cores in casting.nbt");
        assertFormed(helper, cores.get(0), 1, 1, 3);
        helper.assertBlockPresent(ForgeweaveBlocks.SEARED_DRAIN.get(), new BlockPos(5, 3, 4).offset(OFFSET));
        helper.assertBlockPresent(ForgeweaveBlocks.FAUCET.get(), new BlockPos(5, 3, 3).offset(OFFSET));
        helper.succeed();
    }

    /** Places every block of the schematic, cores last; returns the core positions in schematic order. */
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
            if (state.getBlock() instanceof SmelteryControllerBlock) {
                cores.add(at);
                coreStates.add(state);
            } else {
                helper.setBlock(at, state);
            }
        }
        helper.assertFalse(cores.isEmpty(), "the ponder schematic " + schematic + " must contain a core");
        for (int i = 0; i < cores.size(); i++) {
            helper.setBlock(cores.get(i), coreStates.get(i));
        }
        return cores;
    }

    private static void assertFormed(GameTestHelper helper, BlockPos corePos, int width, int depth, int height) {
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        SmelteryStructure structure = core.structure();
        helper.assertTrue(structure != null,
                "expected the ponder schematic's smeltery at " + corePos + " to form: " + core.lastResult().getString());
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
