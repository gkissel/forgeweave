package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

/**
 * T73/issue #504: pins the pure math behind {@code SmelteryControllerBlock#animateTick} and {@code
 * CastingBlock#animateTick} to upstream 1.12's {@code BlockMultiblockController#spawnFireParticles}
 * and {@code TileCasting#update} (NOTICE.md) -- the actual particle spawn calls need a client
 * level to observe, but the facing switch and the cooldown gate are ordinary code any GameTest
 * would only re-derive by eye.
 */
class AmbientParticleTest {
    private static final double FRONT = 0.52;
    private static final double SIDE = -0.1;

    @Test
    void westPutsTheParticleFrontOfTheBlockOnTheXAxis() {
        assertEquals(-FRONT, SmelteryControllerBlock.offsetAlong(Direction.WEST, FRONT, SIDE));
        assertEquals(SIDE, SmelteryControllerBlock.offsetAcross(Direction.WEST, FRONT, SIDE));
    }

    @Test
    void eastPutsTheParticleFrontOfTheBlockOnTheXAxis() {
        assertEquals(FRONT, SmelteryControllerBlock.offsetAlong(Direction.EAST, FRONT, SIDE));
        assertEquals(SIDE, SmelteryControllerBlock.offsetAcross(Direction.EAST, FRONT, SIDE));
    }

    @Test
    void northPutsTheParticleFrontOfTheBlockOnTheZAxis() {
        assertEquals(SIDE, SmelteryControllerBlock.offsetAlong(Direction.NORTH, FRONT, SIDE));
        assertEquals(-FRONT, SmelteryControllerBlock.offsetAcross(Direction.NORTH, FRONT, SIDE));
    }

    @Test
    void southPutsTheParticleFrontOfTheBlockOnTheZAxis() {
        assertEquals(SIDE, SmelteryControllerBlock.offsetAlong(Direction.SOUTH, FRONT, SIDE));
        assertEquals(FRONT, SmelteryControllerBlock.offsetAcross(Direction.SOUTH, FRONT, SIDE));
    }

    @Test
    void anIdleCastingTankNeverReadsAsCooling() {
        // Capacity 0 (nothing being poured) means fluidAmount == capacity is trivially true at 0/0;
        // isCooling must not treat that as mid-cooldown.
        assertFalse(CastingBlockEntity.isCooling(0, 0));
    }

    @Test
    void aMidPourTankIsNotYetCooling() {
        assertFalse(CastingBlockEntity.isCooling(500, 1000));
    }

    @Test
    void aFullTankIsCooling() {
        assertTrue(CastingBlockEntity.isCooling(1000, 1000));
    }
}
