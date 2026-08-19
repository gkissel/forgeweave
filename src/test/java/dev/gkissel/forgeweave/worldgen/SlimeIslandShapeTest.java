package dev.gkissel.forgeweave.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.block.FoliageType;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SlimeVineBlock;

/**
 * Issue #449 (parity audit T18): pins the ported slime island generator to upstream 1.12's
 * {@code SlimeIslandGenerator#generateIsland}. Everything here is the pure half of the port -- the
 * shape, the palette pick, the erosion, the surfacing and where plants and trees end up -- which is
 * exactly why {@link SlimeIslandShape} draws into a buffer instead of straight into a level: the
 * alternative would be re-deriving the silhouette by eye in a live world.
 */
class SlimeIslandShapeTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Upstream {@code generateIslandInChunk}: {@code rnr <= 1} is the purple island, {@code rnr < 6}
     * the green-dirt one, and everything above that the blue-dirt one -- and the trees always take
     * the <em>other</em> foliage colour from the grass.
     */
    @Test
    void thePaletteRollMatchesUpstreamsThreeIslandFlavours() {
        for (int roll = 0; roll <= 1; roll++) {
            SlimeIslandShape.Palette palette = SlimeIslandShape.paletteFor(roll);
            assertSame(ForgeweaveBlocks.PURPLE_SLIME_SOIL.dirt().get(), palette.dirt().getBlock(), "roll " + roll);
            assertSame(ForgeweaveBlocks.PURPLE_SLIME_SOIL.grass().get(), palette.grass().getBlock(), "roll " + roll);
            assertSame(ForgeweaveBlocks.BLUE_SLIME_PLANTS.leaves().get(), palette.leaves().getBlock(), "roll " + roll);
        }
        for (int roll = 2; roll < 6; roll++) {
            SlimeIslandShape.Palette palette = SlimeIslandShape.paletteFor(roll);
            assertSame(ForgeweaveBlocks.GREEN_SLIME_SOIL.dirt().get(), palette.dirt().getBlock(), "roll " + roll);
            assertSame(ForgeweaveBlocks.BLUE_SLIME_SOIL.grass().get(), palette.grass().getBlock(), "roll " + roll);
            assertSame(ForgeweaveBlocks.PURPLE_SLIME_PLANTS.leaves().get(), palette.leaves().getBlock(), "roll " + roll);
        }
        for (int roll = 6; roll < 10; roll++) {
            SlimeIslandShape.Palette palette = SlimeIslandShape.paletteFor(roll);
            assertSame(ForgeweaveBlocks.BLUE_SLIME_SOIL.dirt().get(), palette.dirt().getBlock(), "roll " + roll);
            assertSame(ForgeweaveBlocks.BLUE_SLIME_SOIL.grass().get(), palette.grass().getBlock(), "roll " + roll);
            assertSame(ForgeweaveBlocks.PURPLE_SLIME_PLANTS.leaves().get(), palette.leaves().getBlock(), "roll " + roll);
        }
    }

    /** Every trunk is green congealed slime, upstream's one {@code slimeGreen} log for both tree generators. */
    @Test
    void everyIslandGrowsGreenCongealedSlimeTrunks() {
        for (int roll = 0; roll < 10; roll++) {
            assertSame(ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get(),
                    SlimeIslandShape.paletteFor(roll).log().getBlock(), "roll " + roll);
        }
    }

    /** Upstream's ranges: 20-32 across and deep, 11-13 tall. */
    @Test
    void islandSizesStayInUpstreamsRanges() {
        for (long seed = 0; seed < 200; seed++) {
            SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(RandomSource.create(seed));
            assertTrue(size.xRange() >= 20 && size.xRange() <= 32, "xRange " + size.xRange());
            assertTrue(size.zRange() >= 20 && size.zRange() <= 32, "zRange " + size.zRange());
            assertTrue(size.yRange() >= 11 && size.yRange() <= 13, "yRange " + size.yRange());
        }
    }

    @Test
    void anIslandIsAGrassToppedPlugOfItsOwnDirt() {
        for (long loopSeed = 1; loopSeed <= 20; loopSeed++) {
            long seed = loopSeed;
            Island island = Island.at(seed);

            assertTrue(island.count(island.palette.dirt()) > 500,
                    "seed " + seed + " produced a nearly empty island");

            // Upstream's surfacing pass turns the topmost dirt of a column into grass, so no grass is
            // ever buried under more soil -- only under what grows on it (a plant, or a tree trunk
            // and its canopy, which upstream raises from the surface block itself).
            island.forEachDrawn((x, y, z, state) -> {
                if (state == island.palette.grass()) {
                    BlockState above = island.canvas.get(x, y + 1, z);
                    assertNotEquals(island.palette.dirt(), above,
                            "grass at " + x + "," + y + "," + z + " is buried under dirt (seed " + seed + ")");
                    assertNotEquals(island.palette.grass(), above,
                            "grass at " + x + "," + y + "," + z + " is buried under grass (seed " + seed + ")");
                }
            });

            // The solid plug never reaches above the island's own top.
            for (int y = island.size.yRange() + 1; y <= island.size.canvasTop(); y++) {
                for (int x = 0; x <= island.size.xRange(); x++) {
                    for (int z = 0; z <= island.size.zRange(); z++) {
                        assertNotEquals(island.palette.dirt(), island.canvas.get(x, y, z),
                                "dirt above the island top at " + x + "," + y + "," + z + " (seed " + seed + ")");
                    }
                }
            }
        }
    }

    /** Upstream draws the plug inside an ellipse, so the footprint corners stay empty sky. */
    @Test
    void theIslandFootprintIsElliptical() {
        Island island = Island.at(7);
        int midHeight = island.size.yRange() / 2;
        for (int y = 0; y <= midHeight; y++) {
            assertTrue(island.canvas.isAir(0, y, 0), "corner 0,0 is solid");
            assertTrue(island.canvas.isAir(island.size.xRange(), y, 0), "corner xMax,0 is solid");
            assertTrue(island.canvas.isAir(0, y, island.size.zRange()), "corner 0,zMax is solid");
            assertTrue(island.canvas.isAir(island.size.xRange(), y, island.size.zRange()), "corner xMax,zMax is solid");
        }
    }

    /**
     * Upstream {@code SlimeTreeGenerator}: three trunks of congealed slime, each with a leaf canopy
     * that reaches above it. The vine-less branch fills the canopy corners with leaves instead.
     */
    @Test
    void islandsGrowTreesWithCanopies() {
        int islandsWithTrees = 0;
        for (long seed = 1; seed <= 20; seed++) {
            Island island = Island.at(seed);
            int trunks = island.count(island.palette.log());
            int leaves = island.count(island.palette.leaves());
            if (trunks > 0) {
                islandsWithTrees++;
                assertTrue(leaves > trunks, "seed " + seed + " grew a trunk with no canopy");
            }
        }
        assertTrue(islandsWithTrees >= 15, "only " + islandsWithTrees + "/20 islands grew any tree at all");
    }

    /**
     * Upstream builds each tree generator with the vine that matches the leaves it hangs
     * ({@code treeGenBlue} takes {@code slimeVineBlue2}), and it is always the <em>middle</em> stage
     * (issue #488, parity audit T57).
     */
    @Test
    void everyIslandPaletteHangsItsOwnFoliagesMiddleVine() {
        for (int roll = 0; roll < 10; roll++) {
            SlimeIslandShape.Palette palette = SlimeIslandShape.paletteFor(roll);
            FoliageType foliage = foliageOfLeaves(palette.leaves().getBlock());
            assertSame(ForgeweaveBlocks.slimePlants(foliage).vineMid().get(), palette.vine().getBlock(),
                    "roll " + roll);
        }
    }

    /**
     * Upstream's {@code vine != null} branch of {@code placeCanopy}: the canopy corners are hollowed
     * out and vines hang from the skirt instead of leaves filling it. Every vine it hangs carries at
     * least one face -- {@code getRandomizedVine} lights one to three of them -- and is held up by
     * leaves or by another vine, which is what keeps it in the world once neighbours update.
     */
    @Test
    void islandTreesHangVinesUnderTheirCanopies() {
        int islandsWithVines = 0;
        for (long seed = 1; seed <= 20; seed++) {
            Island island = Island.at(seed);
            List<int[]> vines = new ArrayList<>();
            island.forEachDrawn((x, y, z, state) -> {
                if (state.getBlock() instanceof SlimeVineBlock) {
                    vines.add(new int[] {x, y, z});
                }
            });
            if (island.count(island.palette.log()) == 0) {
                continue; // no tree, no vines
            }
            if (!vines.isEmpty()) {
                islandsWithVines++;
            }
            for (int[] vine : vines) {
                BlockState state = island.canvas.get(vine[0], vine[1], vine[2]);
                assertSame(island.palette.vine().getBlock(), state.getBlock(),
                        "a vine of the wrong colour at " + vine[0] + "," + vine[1] + "," + vine[2]);
                assertTrue(hasAnyFace(state),
                        "a faceless vine at " + vine[0] + "," + vine[1] + "," + vine[2] + " (seed " + seed + ")");
            }
        }
        assertTrue(islandsWithVines >= 15, "only " + islandsWithVines + "/20 islands hung any vine at all");
    }

    /**
     * Every vine one tree hangs is held up by leaves or by the vine above it -- upstream's own
     * canopy geometry, and what {@code SlimeVineBlock}'s widened support rule then keeps alive once
     * the world starts sending neighbour updates. Asserted on a single tree rather than a whole
     * island, because upstream lets a later tree's canopy carve air out of an earlier one's and
     * strand its vines, which is a faithfully ported quirk rather than a bug to assert away.
     */
    @Test
    void everyVineOfOneTreeHangsFromLeavesOrAnotherVine() {
        SlimeIslandShape.Palette palette = SlimeIslandShape.paletteFor(0);
        for (long seed = 1; seed <= 40; seed++) {
            SlimeIslandShape.Canvas canvas = SlimeIslandShape.Canvas.forTree();
            SlimeIslandShape.plantTree(RandomSource.create(seed), canvas, palette, 0, 0, 0);
            List<SlimeIslandShape.Canvas.Drawn> drawn = new ArrayList<>();
            canvas.forEachDrawn(drawn::add);
            long vines = drawn.stream().filter(entry -> entry.state().getBlock() instanceof SlimeVineBlock).count();
            assertTrue(vines > 0, "seed " + seed + " grew a tree with no vines");
            for (SlimeIslandShape.Canvas.Drawn entry : drawn) {
                if (!(entry.state().getBlock() instanceof SlimeVineBlock)) {
                    continue;
                }
                BlockPos pos = entry.pos();
                BlockState above = canvas.get(pos.getX(), pos.getY() + 1, pos.getZ());
                assertTrue(above.getBlock() == palette.leaves().getBlock()
                                || above.getBlock() instanceof SlimeVineBlock,
                        "a vine at " + pos + " hangs off nothing (seed " + seed + ")");
            }
        }
    }

    /**
     * A hand-planted sapling takes upstream's other branch: {@code BlockSlimeSapling#generateTree}
     * passes a {@code null} vine, so the canopy corners are leaves and no vine is placed at all.
     */
    @Test
    void aPlantedSaplingGrowsAVinelessCanopy() {
        for (long seed = 1; seed <= 20; seed++) {
            SlimeIslandShape.Palette palette = SlimeIslandShape.saplingPalette(FoliageType.PURPLE);
            assertNull(palette.vine(), "a sapling's palette must carry no vine");
            SlimeIslandShape.Canvas canvas = SlimeIslandShape.Canvas.forTree();
            SlimeIslandShape.plantTree(RandomSource.create(seed), canvas, palette, 0, 0, 0);

            List<SlimeIslandShape.Canvas.Drawn> drawn = new ArrayList<>();
            canvas.forEachDrawn(drawn::add);
            assertTrue(drawn.stream().anyMatch(entry -> entry.state() == palette.log()),
                    "seed " + seed + " grew no trunk");
            assertTrue(drawn.stream().anyMatch(entry -> entry.state() == palette.leaves()),
                    "seed " + seed + " grew no canopy");
            assertTrue(drawn.stream().noneMatch(entry -> entry.state().getBlock() instanceof SlimeVineBlock),
                    "seed " + seed + " hung a vine off a hand-planted tree");
            // Nothing may escape the canvas the sapling sizes for it.
            assertEquals(drawn.size(), drawn.stream().distinct().count());
        }
    }

    private static boolean hasAnyFace(BlockState state) {
        return state.getValue(VineBlock.NORTH) || state.getValue(VineBlock.EAST)
                || state.getValue(VineBlock.SOUTH) || state.getValue(VineBlock.WEST)
                || state.getValue(VineBlock.UP);
    }

    private static FoliageType foliageOfLeaves(net.minecraft.world.level.block.Block leaves) {
        return ForgeweaveBlocks.slimePlants().stream()
                .filter(plants -> plants.leaves().get() == leaves)
                .map(ForgeweaveBlocks.SlimePlants::foliage)
                .findFirst()
                .orElseThrow();
    }

    /** Upstream's plants stand one block above the surface, on slime soil and nothing else. */
    @Test
    void plantsOnlyStandOnSlimeSoil() {
        int islandsWithPlants = 0;
        for (long seed = 1; seed <= 20; seed++) {
            Island island = Island.at(seed);
            List<int[]> plants = new ArrayList<>();
            island.forEachDrawn((x, y, z, state) -> {
                if (island.isPlantState(state)) {
                    plants.add(new int[] {x, y, z});
                }
            });
            if (!plants.isEmpty()) {
                islandsWithPlants++;
            }
            for (int[] plant : plants) {
                assertTrue(ForgeweaveBlocks.isSlimeSoil(island.canvas.get(plant[0], plant[1] - 1, plant[2]).getBlock()),
                        "a plant at " + plant[0] + "," + plant[1] + "," + plant[2] + " is floating (seed " + seed + ")");
                assertEquals(island.size.yRange() + 1, plant[1],
                        "a plant left the island surface (seed " + seed + ")");
            }
        }
        assertTrue(islandsWithPlants >= 15, "only " + islandsWithPlants + "/20 islands grew any plant at all");
    }

    /** Two islands from the same seed have to be identical -- chunk generation is replayed on load. */
    @Test
    void islandsAreDeterministicForASeed() {
        assertEquals(Island.at(42).signature(), Island.at(42).signature());
        assertNotEquals(Island.at(42).signature(), Island.at(43).signature());
    }

    /** Nothing an island draws may escape the canvas the feature sizes for it. */
    @Test
    void nothingIsDrawnOutsideTheCanvas() {
        for (long loopSeed = 1; loopSeed <= 20; loopSeed++) {
            long seed = loopSeed;
            Island island = Island.at(seed);
            int pad = SlimeIslandShape.Size.canvasPad();
            island.forEachDrawn((x, y, z, state) -> {
                assertTrue(x >= -pad && x <= island.size.xRange() + pad, "x " + x + " escaped (seed " + seed + ")");
                assertTrue(z >= -pad && z <= island.size.zRange() + pad, "z " + z + " escaped (seed " + seed + ")");
                assertTrue(y >= 0 && y <= island.size.canvasTop(), "y " + y + " escaped (seed " + seed + ")");
            });
            assertFalse(island.drawn().isEmpty());
        }
    }

    /**
     * Issue #629: the island is a structure now, and a structure piece is reloaded from nothing but
     * the bounding box vanilla already writes for it. That only works while the canvas the piece
     * spans determines the size that produced it, so the round trip is pinned over the whole rolled
     * range rather than trusted.
     */
    @Test
    void aCanvasSpanRecoversTheSizeThatAskedForIt() {
        for (int xRange = 20; xRange <= 32; xRange++) {
            for (int zRange = 20; zRange <= 32; zRange++) {
                for (int yRange = 11; yRange <= 13; yRange++) {
                    SlimeIslandShape.Size size = new SlimeIslandShape.Size(xRange, zRange, yRange);
                    assertEquals(size, SlimeIslandShape.Size.fromCanvasSpan(
                            size.canvasSizeX(), size.canvasSizeY(), size.canvasSizeZ()));
                }
            }
        }
    }

    /** The canvas the shape allocates has to be the span the piece's bounding box will claim. */
    @Test
    void theCanvasSpanIsTheSpanTheCanvasActuallyCovers() {
        SlimeIslandShape.Size size = new SlimeIslandShape.Size(24, 27, 12);
        int pad = SlimeIslandShape.Size.canvasPad();
        assertEquals(size.xRange() + 1 + 2 * pad, size.canvasSizeX());
        assertEquals(size.zRange() + 1 + 2 * pad, size.canvasSizeZ());
        assertEquals(size.canvasTop() + 1, size.canvasSizeY());
    }

    /** One generated island, kept together with the size and palette it was drawn from. */
    private record Island(SlimeIslandShape.Canvas canvas, SlimeIslandShape.Size size, SlimeIslandShape.Palette palette) {
        static Island at(long seed) {
            RandomSource random = RandomSource.create(seed);
            SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(random);
            SlimeIslandShape.Palette palette = SlimeIslandShape.roll(random);
            SlimeIslandShape.Canvas canvas = SlimeIslandShape.Canvas.forIsland(size);
            SlimeIslandShape.generate(random, canvas, size, palette);
            return new Island(canvas, size, palette);
        }

        List<SlimeIslandShape.Canvas.Drawn> drawn() {
            List<SlimeIslandShape.Canvas.Drawn> drawn = new ArrayList<>();
            canvas.forEachDrawn(drawn::add);
            return drawn;
        }

        void forEachDrawn(Visitor visitor) {
            canvas.forEachDrawn(entry ->
                    visitor.accept(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(), entry.state()));
        }

        int count(BlockState state) {
            return (int) drawn().stream().filter(entry -> entry.state() == state).count();
        }

        boolean isPlantState(BlockState state) {
            return state == palette.tallGrass() || state == palette.fern();
        }

        boolean isPlant(int x, int y, int z) {
            return isPlantState(canvas.get(x, y, z));
        }

        String signature() {
            StringBuilder builder = new StringBuilder();
            for (SlimeIslandShape.Canvas.Drawn entry : drawn()) {
                builder.append(entry.pos()).append('=').append(entry.state()).append(';');
            }
            return builder.toString();
        }
    }

    @FunctionalInterface
    private interface Visitor {
        void accept(int x, int y, int z, BlockState state);
    }
}
