package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.client.book.StructureElement;
import dev.gkissel.forgeweave.client.book.StructureInfo;

/**
 * Issue #651, the structure page: the smeltery section's rotating 3D schematic, upstream's
 * {@code sections/smeltery.json} page {@code {"type": "structure", "data": "smeltery/multiblock.json"}}
 * whose data resolves to {@code book/en_us/structure/smeltery.json} (Tinkers' 1.12, pinned commit
 * c01173c, MIT, NOTICE.md) rendered through Mantle's {@code ContentStructure}/{@code
 * ElementStructure}/{@code StructureInfo}/{@code StructureBlockAccess} (Mantle {@code 1.12} @
 * {@code 340a386}, NOTICE.md).
 *
 * <p>These tests pin the ported shell against upstream's block spans -- translated to Forgeweave's
 * blocks (1.12 {@code tconstruct:seared} meta 3 is the seared bricks block; {@code smeltery_io}
 * meta 0 unpacks to a south-facing drain per {@code BlockSmelteryIO#getMetaFromState}'s
 * {@code facing.getHorizontalIndex() << 2} layout) -- and {@code StructureInfo}'s animation
 * counters, whose index arithmetic ({@code y*(length*width) + x*width + z}) drives both the
 * build-up animation and {@code StructureBlockAccess}'s visibility limiter.
 */
class BookSmelteryStructureTest {

    private static StructureInfo smeltery() {
        return StructureInfo.load("structure/smeltery.json");
    }

    /** Upstream {@code structure/smeltery.json}: a 5x3x5 shell with open corners, 33 blocks. */
    @Test
    void theShippedFileIsUpstreamsFiveByThreeByFiveShell() {
        StructureInfo info = smeltery();

        assertEquals(5, info.length(), "size[0], the x extent");
        assertEquals(3, info.height(), "size[1], the y extent");
        assertEquals(5, info.width(), "size[2], the z extent");

        int filled = 0;
        for (int y = 0; y < info.height(); y++) {
            for (int x = 0; x < info.length(); x++) {
                for (int z = 0; z < info.width(); z++) {
                    if (info.stateAt(x, y, z) != null) {
                        filled++;
                    }
                }
            }
        }
        assertEquals(33, filled, "9 floor + 12 side walls + 6 front + 3 top back + tank/core/drain");

        // The 3x3 seared-bricks base, upstream's first span.
        BlockState floor = info.stateAt(2, 0, 2);
        assertNotNull(floor);
        assertEquals(ForgeweaveBlocks.SEARED_BRICKS.get(), floor.getBlock(),
                "tconstruct:seared meta 3 is the seared bricks variant");
        // The corner columns stay open, exactly as upstream's spans leave them.
        assertNull(info.stateAt(0, 0, 0));
        assertNull(info.stateAt(4, 1, 0));
        assertNull(info.stateAt(0, 2, 4));
        // The structure floats on a hollow interior.
        assertNull(info.stateAt(2, 1, 2), "the smeltery interior is empty");
    }

    /** The back row: tank, controller (facing south, active) and drain, upstream's states. */
    @Test
    void theControllerRowFacesTheReader() {
        StructureInfo info = smeltery();

        BlockState tank = info.stateAt(1, 1, 4);
        assertNotNull(tank);
        assertEquals(ForgeweaveBlocks.SEARED_TANK.get(), tank.getBlock());

        BlockState core = info.stateAt(2, 1, 4);
        assertNotNull(core);
        assertEquals(ForgeweaveBlocks.STANDARD_CORE.get(), core.getBlock());
        assertEquals(Direction.SOUTH, core.getValue(BlockStateProperties.HORIZONTAL_FACING),
                "upstream's state map sets facing: south");
        assertTrue(core.getValue(SmelteryControllerBlock.ACTIVE),
                "upstream's state map sets active: true, so the book shows the lit face");

        BlockState drain = info.stateAt(3, 1, 4);
        assertNotNull(drain);
        assertEquals(ForgeweaveBlocks.SEARED_DRAIN.get(), drain.getBlock());
        assertEquals(Direction.SOUTH, drain.getValue(BlockStateProperties.HORIZONTAL_FACING),
                "smeltery_io meta 0 is a south-facing drain");
    }

    /**
     * {@code StructureInfo}'s animation counters, upstream semantics: fresh and after
     * {@code reset()} everything is visible, {@code setShowLayer(n)} clamps the limiter to the
     * first n+1 layers, and {@code step()} builds the structure back one block at a time in index
     * order, wrapping to the first block after a reset.
     */
    @Test
    void theAnimationCountersMatchUpstream() {
        StructureInfo info = smeltery();

        // maxBlockIndex = 5*3*5: every index passes the limiter, the whole structure shows.
        assertNotNull(info.visibleStateAt(3, 2, 4), "a fresh structure shows its last blocks");

        info.setShowLayer(0);
        assertNotNull(info.visibleStateAt(1, 0, 1), "layer 0 keeps the floor visible");
        assertNull(info.visibleStateAt(1, 1, 4), "layer 0 hides everything above the floor");

        info.reset();
        assertNotNull(info.visibleStateAt(3, 2, 4), "reset shows the whole structure again");

        // At the everything-visible state canStep is false -- that pause is what
        // ElementStructure's fullStructureSteps counter waits out before restarting.
        assertFalse(info.canStep(), "a complete structure pauses before the build-up restarts");
        info.step();
        assertNotNull(info.visibleStateAt(1, 0, 1), "the first floor brick is placed first");
        assertNull(info.visibleStateAt(2, 0, 1), "its neighbour is not placed yet");

        // Stepping to exhaustion visits every one of the 33 blocks exactly once.
        int steps = 1;
        while (info.canStep()) {
            info.step();
            steps++;
        }
        assertEquals(33, steps, "one animation step per placed block");
    }

    /** Upstream {@code StructureInfo#convert}: an unknown block id becomes air, not a crash. */
    @Test
    void anUnknownBlockIdFallsBackToAir() {
        StructureInfo info = StructureInfo.parse("""
                {
                  "size": [1, 1, 1],
                  "structure": [
                    { "pos": [0, 0, 0], "endPos": [0, 0, 0], "block": "forgeweave:no_such_block" }
                  ]
                }""");

        assertNull(info.stateAt(0, 0, 0), "an unresolvable block renders as nothing");
        assertFalse(info.canStep(), "an all-air structure has nothing to animate");
    }

    /**
     * {@code ElementStructure}'s constructor math: {@code scale = 100/maxDim * min(w/PAGE_WIDTH,
     * h/PAGE_HEIGHT)}, and the view opens at upstream's 25/-45 degree tilt.
     */
    @Test
    void theElementScalesAndTiltsLikeUpstream() {
        StructureElement element = new StructureElement(smeltery(), 182, 164);

        assertEquals(100f / 5f * (164f / 176f), element.scale(), 1e-4,
                "PAGE_WIDTH-wide, PAGE_TEXT_H-tall element: the height ratio limits the scale");
        assertEquals(25f, element.rotX(), "upstream's initial pitch");
        assertEquals(-45f, element.rotY(), "upstream's initial yaw");
        assertFalse(element.animating(), "the build-up animation starts toggled off");
    }
}
