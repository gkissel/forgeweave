package dev.gkissel.forgeweave.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * One magma slime island (issue #450, parity audit T19): {@link SlimeIslandPiece} drawn from
 * upstream's Nether palette instead of a rolled overworld one, exactly as upstream's
 * {@code MagmaSlimeIslandGenerator} is {@code SlimeIslandGenerator} with its palette fields
 * replaced.
 *
 * <p>It is its own registered piece type rather than a flag on the overworld piece because the piece
 * type id is what a saved chunk stores: a flag would be a new field on a format every existing
 * island already writes, for a distinction the id makes for free.
 */
public class MagmaSlimeIslandPiece extends SlimeIslandPiece {
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Forgeweave.MODID);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> TYPE =
            STRUCTURE_PIECES.register("magma_slime_island",
                    () -> (StructurePieceType.ContextlessType) MagmaSlimeIslandPiece::new);

    public MagmaSlimeIslandPiece(BoundingBox box) {
        super(TYPE.get(), box);
    }

    public MagmaSlimeIslandPiece(CompoundTag tag) {
        super(TYPE.get(), tag);
    }

    @Override
    protected SlimeIslandShape.Palette palette(RandomSource random) {
        return SlimeIslandShape.magmaPalette();
    }
}
