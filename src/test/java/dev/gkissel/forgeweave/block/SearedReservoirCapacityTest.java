package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

/**
 * Parity audit T44 (issue #475): the seared reservoir's capacity math, upstream 1.12's
 * {@code TileTinkerTank#updateStructureInfo}.
 *
 * <p>The whole point of the {@code + 2} on each axis is upstream's own comment -- "we add 2 to the
 * coordinates so we include the walls/floor/ceiling in the size calculation, otherwise a 3x3x3 tank
 * is way too little capacity". Getting that wrong is silent: the reservoir still forms and still
 * holds fluid, just a fraction of what it should, which is exactly the kind of thing a unit test
 * catches and a playtest does not.
 */
class SearedReservoirCapacityTest {

    /** A 1x1x1 interior is a 3x3x3 cuboid: 27 blocks, four buckets each. */
    @Test
    void theSmallestReservoirCountsItsWholeShell() {
        SmelteryStructure oneByOne = new SmelteryStructure(new BlockPos(0, 64, 0), new BlockPos(0, 64, 0));

        assertEquals(1, oneByOne.interiorVolume());
        assertEquals(27 * 4000, SearedReservoirBlockEntity.capacityFor(oneByOne));
    }

    /** A 9x9 interior three tall is an 11x5x11 cuboid: 605 blocks, 2420 buckets. */
    @Test
    void aLargeReservoirScalesOnEveryAxis() {
        SmelteryStructure nineByNine = new SmelteryStructure(new BlockPos(-4, 60, 7), new BlockPos(4, 62, 15));

        assertEquals(11 * 5 * 11 * 4000, SearedReservoirBlockEntity.capacityFor(nineByNine));
    }

    /** An unformed reservoir holds nothing, so anything left in it is trimmed away on the next scan. */
    @Test
    void anUnformedReservoirHasNoCapacity() {
        assertEquals(0, SearedReservoirBlockEntity.capacityFor(null));
    }
}
