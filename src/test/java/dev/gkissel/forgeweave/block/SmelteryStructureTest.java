package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.Test;

/**
 * The serialization half of docs/SCOPE.md M2 issue #95's verification -- the codec a smeltery core
 * persists its formed bounds with, and the derived sizes the melting work (#96) reads off it. The
 * in-world half (forming, rejecting, invalidating) lives in {@code SmelteryGameTests}.
 */
class SmelteryStructureTest {

    private static final SmelteryStructure NINE_BY_NINE =
            new SmelteryStructure(new BlockPos(-4, 60, 7), new BlockPos(4, 62, 15));

    @Test
    void roundTripsThroughNbt() {
        Tag encoded = SmelteryStructure.CODEC.encodeStart(NbtOps.INSTANCE, NINE_BY_NINE).getOrThrow();
        SmelteryStructure decoded = SmelteryStructure.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(NINE_BY_NINE, decoded);
    }

    @Test
    void derivesItsInteriorSizes() {
        assertEquals(9, NINE_BY_NINE.width());
        assertEquals(9, NINE_BY_NINE.depth());
        assertEquals(3, NINE_BY_NINE.height());
        assertEquals(9 * 9 * 3, NINE_BY_NINE.interiorVolume());
    }

    @Test
    void aOneByOneInteriorIsOneBlock() {
        SmelteryStructure minimum = new SmelteryStructure(new BlockPos(0, 0, 0), new BlockPos(0, 0, 0));

        assertEquals(1, minimum.interiorVolume());
        assertEquals(minimum, SmelteryStructure.CODEC
                .parse(NbtOps.INSTANCE, SmelteryStructure.CODEC.encodeStart(NbtOps.INSTANCE, minimum).getOrThrow())
                .getOrThrow());
    }

    @Test
    void containsOnlyItsOwnInterior() {
        assertTrue(NINE_BY_NINE.containsInterior(new BlockPos(0, 61, 11)));
        assertTrue(NINE_BY_NINE.containsInterior(NINE_BY_NINE.interiorMin()));
        assertTrue(NINE_BY_NINE.containsInterior(NINE_BY_NINE.interiorMax()));
        assertFalse(NINE_BY_NINE.containsInterior(new BlockPos(5, 61, 11)), "one block past the +X wall");
        assertFalse(NINE_BY_NINE.containsInterior(new BlockPos(0, 59, 11)), "the floor below the interior");
        assertFalse(NINE_BY_NINE.containsInterior(new BlockPos(0, 63, 11)), "one block above the walls");
    }
}
