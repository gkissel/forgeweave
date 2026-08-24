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
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryStructure;

/**
 * Issue #664: the smeltery Ponder scene must teach a structure the real scan accepts. Scene
 * playback is client-only, but its schematic ({@code assets/forgeweave/ponder/smeltery.nbt}, the
 * finished structure the scene reveals in stages) is plain data -- so this test rebuilds it
 * block-for-block on the dedicated GameTest server, places the core last (exercising the real
 * {@code SmelteryControllerBlock#onPlace} trigger, same as {@code SmelteryGameTests}), and asserts
 * the multiblock forms. If the schematic generator and {@code SmelteryScan} ever drift apart, this
 * fails instead of players discovering the tutorial builds an invalid smeltery.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PonderSchematicGameTests {

    @GameTest(template = "smeltery")
    public static void ponderSchematicFormsARealSmeltery(GameTestHelper helper) {
        CompoundTag root = readSchematic();

        HolderGetter<Block> blocks = helper.getLevel().holderLookup(Registries.BLOCK);
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(blocks, palette.getCompound(i)));
        }

        // Same idiom as SmelteryGameTests: everything else first, the core last, so the scan runs
        // off the real placement event with the structure already complete. Raised one block so the
        // schematic's decorative base plate stays off the template's own floor level.
        BlockPos offset = new BlockPos(0, 1, 0);
        BlockPos corePos = null;
        BlockState coreState = null;
        for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) tag;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            BlockPos at = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)).offset(offset);
            BlockState state = states.get(block.getInt("state"));
            if (state.getBlock() instanceof SmelteryControllerBlock) {
                corePos = at;
                coreState = state;
            } else {
                helper.setBlock(at, state);
            }
        }
        helper.assertTrue(corePos != null, "the ponder schematic must contain the standard core");
        helper.setBlock(corePos, coreState);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        SmelteryStructure structure = core.structure();
        helper.assertTrue(structure != null,
                "expected the ponder schematic's smeltery to form: " + core.lastResult().getString());
        // The scene narrates the minimum structure; pin that this is what the schematic holds.
        helper.assertValueEqual(structure.width(), 1, "interior width");
        helper.assertValueEqual(structure.depth(), 1, "interior depth");
        helper.assertValueEqual(structure.height(), 2, "interior height");
        helper.succeed();
    }

    private static CompoundTag readSchematic() {
        try (InputStream in = Forgeweave.class.getResourceAsStream("/assets/forgeweave/ponder/smeltery.nbt")) {
            if (in == null) {
                throw new IllegalStateException("assets/forgeweave/ponder/smeltery.nbt is missing from the jar");
            }
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
