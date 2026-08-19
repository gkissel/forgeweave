package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

/**
 * Issue #595: where a right-click on a seared channel lands, against upstream 1.12's
 * {@code BlockChannel#onBlockActivated} -- its {@code Util.clickedAABB} arm test over
 * {@code BOUNDS_NORTH}/{@code SOUTH}/{@code WEST}/{@code EAST}, then its {@code side = facing == UP
 * ? DOWN : facing} fallback.
 *
 * <p>This is the half of "wonky to interact with" that is pure geometry, so it is pinned here rather
 * than in the game tests: aiming at a 6x4x6 centre piece is unforgiving enough without the rules
 * quietly drifting.
 */
class SearedChannelInteractionTest {

    private static Direction clicked(Direction face, double x, double y, double z) {
        return SearedChannelBlock.sideClicked(BlockPos.ZERO,
                new BlockHitResult(new Vec3(x, y, z), face, BlockPos.ZERO, false));
    }

    /**
     * Upstream tests the arm boxes before it looks at the hit face, so the whole north arm -- top,
     * outer face, flanks -- is the north toggle no matter where on it you aim.
     */
    @Test
    void anyHitOnAnArmTogglesThatArm() {
        assertEquals(Direction.NORTH, clicked(Direction.UP, 0.5, 0.5, 0.1));
        assertEquals(Direction.NORTH, clicked(Direction.NORTH, 0.5, 0.375, 0));
        assertEquals(Direction.SOUTH, clicked(Direction.UP, 0.5, 0.5, 0.9));
        assertEquals(Direction.WEST, clicked(Direction.UP, 0.1, 0.5, 0.5));
        assertEquals(Direction.EAST, clicked(Direction.UP, 0.9, 0.5, 0.5));
    }

    /** A hit on the centre piece means the downspout, whichever face of it was hit. */
    @Test
    void aHitOnTheCentreMeansTheDownspout() {
        assertEquals(Direction.DOWN, clicked(Direction.UP, 0.5, 0.5, 0.5));
        assertEquals(Direction.DOWN, clicked(Direction.DOWN, 0.5, 0.125, 0.5));
    }

    /**
     * The side faces of the centre piece stay that side -- upstream's {@code side = facing} fallback
     * -- so a bare channel can be pointed anywhere without an arm to aim at first.
     */
    @Test
    void aHitOnTheCentresFaceMeansThatSide() {
        assertEquals(Direction.NORTH, clicked(Direction.NORTH, 0.5, 0.375, 5 / 16d));
        assertEquals(Direction.EAST, clicked(Direction.EAST, 11 / 16d, 0.375, 0.5));
    }

    /**
     * The lower lip of the centre -- the strip between y=2/16 and y=4/16 that only the ray-traced
     * centre covers -- is below every arm box, so it takes the hit face like the rest of the centre
     * rather than snapping to the nearest arm.
     */
    @Test
    void theLowerLipOfTheCentreTakesTheHitFace() {
        assertEquals(Direction.NORTH, clicked(Direction.NORTH, 0.5, 0.15, 5 / 16d));
    }

    /**
     * A diagonal corner of the block belongs to no arm at all -- upstream's boxes are a plus, not a
     * square -- so it falls back to the hit face rather than snapping to whichever arm was tested
     * first.
     */
    @Test
    void aCornerBelongsToNoArm() {
        assertEquals(Direction.WEST, clicked(Direction.WEST, 0.1, 0.375, 0.1));
    }
}
