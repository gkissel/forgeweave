package dev.gkissel.forgeweave.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * The interior of a formed smeltery: the two opposite corners of the open space enclosed by the
 * floor and walls, both inclusive (docs/SCOPE.md M2 issue #95).
 *
 * <p>Upstream 1.12's {@code MultiblockDetection.MultiblockStructure} also carries the full list of
 * structure block positions, because upstream assigns every one of them a "servant" tile entity
 * pointing back at the controller (NOTICE.md). Forgeweave's seared blocks are plain blocks with no
 * block entity (issue #93), so the position list has no consumer and only the interior bounds are
 * kept -- which is all the melting, alloying and fluid-rendering work (issues #96-#98, #101) needs:
 * the item drop area, the tank capacity, and the render box are all functions of the interior.
 */
public record SmelteryStructure(BlockPos interiorMin, BlockPos interiorMax) {
    public static final Codec<SmelteryStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("interior_min").forGetter(SmelteryStructure::interiorMin),
            BlockPos.CODEC.fieldOf("interior_max").forGetter(SmelteryStructure::interiorMax)
    ).apply(instance, SmelteryStructure::new));

    /** Interior width along X, in blocks (1 to {@link SmelteryScan#MAX_SIZE}). */
    public int width() {
        return interiorMax.getX() - interiorMin.getX() + 1;
    }

    /** Interior depth along Z, in blocks (1 to {@link SmelteryScan#MAX_SIZE}). */
    public int depth() {
        return interiorMax.getZ() - interiorMin.getZ() + 1;
    }

    /** Interior height, in blocks; a smeltery has no ceiling, so this is however far the walls reach. */
    public int height() {
        return interiorMax.getY() - interiorMin.getY() + 1;
    }

    /** Interior block count -- what the smeltery's fluid capacity scales with (upstream's {@code getSizeInventory}). */
    public int interiorVolume() {
        return width() * height() * depth();
    }

    public boolean containsInterior(BlockPos pos) {
        return pos.getX() >= interiorMin.getX() && pos.getX() <= interiorMax.getX()
                && pos.getY() >= interiorMin.getY() && pos.getY() <= interiorMax.getY()
                && pos.getZ() >= interiorMin.getZ() && pos.getZ() <= interiorMax.getZ();
    }
}
