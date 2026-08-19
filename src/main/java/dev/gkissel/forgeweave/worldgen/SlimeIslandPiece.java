package dev.gkissel.forgeweave.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * One slime island (issue #629): the single piece {@link SlimeIslandStructure} starts, drawing
 * upstream 1.12's island through {@link SlimeIslandShape} and blitting the result into the level.
 *
 * <p>A structure piece is written into the world's chunk NBT and rebuilt from it on load, and this
 * one deliberately writes <b>nothing of its own</b> -- {@link #addAdditionalSaveData} is empty, so
 * the saved form is exactly the {@code id}/{@code BB}/{@code O}/{@code GD} every vanilla piece has.
 * That is possible because the island is a pure function of two things the bounding box already
 * carries: {@link SlimeIslandShape.Size#fromCanvasSpan} recovers the rolled size from the box's own
 * span, and the box corner plus the world seed give the palette and erosion their randomness. The
 * alternative -- storing the generating seed -- would be a new serialized format on every existing
 * island for no gain.
 *
 * <p>Consequently the randomness here is <em>not</em> the {@code RandomSource} vanilla hands
 * {@link #postProcess}: that one is re-seeded per chunk, and an island spans several, so using it
 * would draw a different island into each chunk the island crosses.
 */
public class SlimeIslandPiece extends StructurePiece {
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Forgeweave.MODID);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> TYPE =
            STRUCTURE_PIECES.register("slime_island",
                    () -> (StructurePieceType.ContextlessType) SlimeIslandPiece::new);

    public SlimeIslandPiece(BoundingBox box) {
        this(TYPE.get(), box);
    }

    public SlimeIslandPiece(CompoundTag tag) {
        this(TYPE.get(), tag);
    }

    /** For {@link MagmaSlimeIslandPiece}, which is this piece drawn from a different palette. */
    protected SlimeIslandPiece(StructurePieceType type, BoundingBox box) {
        super(type, 0, box);
    }

    /** For {@link MagmaSlimeIslandPiece}'s load-from-NBT constructor. */
    protected SlimeIslandPiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
    }

    /**
     * The blocks this island is built from. Upstream rolls one of its three overworld palettes here
     * ({@code SlimeIslandGenerator#generateIslandInChunk}); its Nether subclass has exactly one and
     * rolls nothing, which is what {@link MagmaSlimeIslandPiece} overrides this to say.
     */
    protected SlimeIslandShape.Palette palette(RandomSource random) {
        return SlimeIslandShape.roll(random);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        // Nothing: the bounding box vanilla writes is the whole of this piece's state.
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource perChunkRandom, BoundingBox writeBox, ChunkPos chunkPos, BlockPos origin) {
        BoundingBox box = this.boundingBox;
        SlimeIslandShape.Size size =
                SlimeIslandShape.Size.fromCanvasSpan(box.getXSpan(), box.getYSpan(), box.getZSpan());
        if (size.xRange() <= 0 || size.zRange() <= 0 || size.yRange() <= 0) {
            return;
        }

        RandomSource random = islandRandom(level.getSeed(), box);
        SlimeIslandShape.Palette palette = palette(random);
        SlimeIslandShape.Canvas canvas = SlimeIslandShape.Canvas.forIsland(size);
        SlimeIslandShape.generate(random, canvas, size, palette);

        // The canvas origin sits one canopy pad inside the bounding box; see Canvas#forIsland.
        int pad = SlimeIslandShape.Size.canvasPad();
        BlockPos start = new BlockPos(box.minX() + pad, box.minY(), box.minZ() + pad);
        canvas.forEachDrawn(drawn -> {
            BlockPos at = start.offset(drawn.pos());
            if (writeBox.isInside(at)) {
                level.setBlock(at, drawn.state(), Block.UPDATE_CLIENTS);
            }
        });
    }

    /**
     * The island's own randomness: fixed by the world seed and the island's corner, so every chunk
     * the island crosses draws the same island and a reloaded world draws the one already there.
     *
     * <p>Public so a test can reproduce the exact island a given box will draw: the GameTest server
     * scatters its plot grid to a random position every run, so a test that only knows its own box
     * cannot otherwise say which of upstream's rolls it is about to get (issue #643).
     */
    public static RandomSource islandRandom(long seed, BoundingBox box) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, box.minX(), box.minZ());
        return random;
    }
}
