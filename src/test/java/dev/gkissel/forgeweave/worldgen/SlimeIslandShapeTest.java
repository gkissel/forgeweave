package dev.gkissel.forgeweave.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.block.FoliageType;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SlimeVineBlock;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

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
     * Issue #647: {@code generateIslandInChunk} hands {@code generateIsland}'s trailing
     * {@code tryPlacingVine} loop the <em>grass</em> colour's stage-one vine --
     * {@code slimeVineBlue1} on blue and green islands, {@code slimeVinePurple1} on purple -- which
     * is a different vine from the canopy's mid-stage one of the tree's foliage. The magma island
     * and a hand-planted sapling both pass {@code null} and hang no island vines at all.
     */
    @Test
    void everyIslandPaletteHangsItsGrassColoursFirstStageVineOffTheIsland() {
        for (int roll = 0; roll < 10; roll++) {
            SlimeIslandShape.Palette palette = SlimeIslandShape.paletteFor(roll);
            ForgeweaveBlocks.SlimePlants grassPlants =
                    roll <= 1 ? ForgeweaveBlocks.PURPLE_SLIME_PLANTS : ForgeweaveBlocks.BLUE_SLIME_PLANTS;
            assertNotNull(palette.islandVine(), "roll " + roll);
            assertSame(grassPlants.vine().get(), palette.islandVine().getBlock(), "roll " + roll);
        }
        assertNull(SlimeIslandShape.magmaPalette().islandVine(), "the magma island hangs no island vines");
        for (FoliageType foliage : FoliageType.values()) {
            assertNull(SlimeIslandShape.saplingPalette(foliage).islandVine(), foliage.name());
        }
    }

    /**
     * Issue #647: upstream's thirty {@code tryPlacingVine} attempts per island. Every island vine it
     * hangs is the grass colour's stage chain, carries at least one face, and is held up the way
     * upstream leaves it -- stuck to something solid or hanging from the vine above it. The growth
     * loop keeps creeping past the island's bottom plane exactly as upstream's does, so across a few
     * islands at least one strand has to dangle below it.
     */
    @Test
    void islandsHangFirstStageVinesOffTheirExterior() {
        int islandsWithIslandVines = 0;
        int islandsWithATailBelowTheBottom = 0;
        for (long loopSeed = 1; loopSeed <= 20; loopSeed++) {
            long seed = loopSeed;
            Island island = Island.at(seed);
            FoliageType grassColour = ((SlimeVineBlock) island.palette.islandVine().getBlock()).foliage();
            List<int[]> vines = new ArrayList<>();
            island.forEachDrawn((x, y, z, state) -> {
                if (state.getBlock() instanceof SlimeVineBlock vine && vine.foliage() == grassColour) {
                    vines.add(new int[] {x, y, z});
                }
            });
            if (!vines.isEmpty()) {
                islandsWithIslandVines++;
            }
            boolean tailBelow = false;
            for (int[] at : vines) {
                BlockState state = island.canvas.get(at[0], at[1], at[2]);
                assertTrue(hasAnyFace(state),
                        "a faceless island vine at " + at[0] + "," + at[1] + "," + at[2] + " (seed " + seed + ")");
                boolean hangs = island.canvas.get(at[0], at[1] + 1, at[2]).getBlock() instanceof SlimeVineBlock;
                // A lit face may point at another slime vine rather than something solid: a later
                // attempt's candidate can overwrite the very block an earlier vine clung to, and
                // upstream's flag-2 placements never re-check neighbours during generation -- the
                // same faithfully ported quirk that lets one canopy strand another's vines.
                boolean stuck = (state.getValue(VineBlock.NORTH) && supports(island, at[0], at[1], at[2] - 1))
                        || (state.getValue(VineBlock.EAST) && supports(island, at[0] + 1, at[1], at[2]))
                        || (state.getValue(VineBlock.SOUTH) && supports(island, at[0], at[1], at[2] + 1))
                        || (state.getValue(VineBlock.WEST) && supports(island, at[0] - 1, at[1], at[2]));
                assertTrue(hangs || stuck, "an island vine at " + at[0] + "," + at[1] + "," + at[2]
                        + " hangs off nothing (seed " + seed + ")");
                tailBelow |= at[1] < 0;
            }
            if (tailBelow) {
                islandsWithATailBelowTheBottom++;
            }
        }
        assertTrue(islandsWithIslandVines >= 15,
                "only " + islandsWithIslandVines + "/20 islands hung any island vine at all");
        assertTrue(islandsWithATailBelowTheBottom >= 1,
                "no island's vine strand ever crept below the island's bottom plane");
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
            // Only the canopy's vines: since #647 the island also hangs the *grass* colour's vines
            // off its exterior, and the two colours never coincide on one island.
            FoliageType canopyFoliage = foliageOfLeaves(island.palette.leaves().getBlock());
            List<int[]> vines = new ArrayList<>();
            island.forEachDrawn((x, y, z, state) -> {
                if (state.getBlock() instanceof SlimeVineBlock vine && vine.foliage() == canopyFoliage) {
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

    /** What an island vine's lit face may point at: something solid, or the vine that replaced it. */
    private static boolean supports(Island island, int x, int y, int z) {
        BlockState state = island.canvas.get(x, y, z);
        return state.canOcclude() || state.getBlock() instanceof SlimeVineBlock;
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

    /**
     * Issue #450 (parity audit T19): upstream {@code MagmaSlimeIslandGenerator}'s constructor. The
     * Nether island is the same shape drawn from a different set of blocks -- magma slimy dirt and
     * grass, an orange canopy on a magma congealed slime trunk, orange plants -- and, uniquely, its
     * underside erodes into lava rather than air ({@code air = Blocks.LAVA}).
     */
    @Test
    void theMagmaPaletteIsUpstreamsNetherIsland() {
        SlimeIslandShape.Palette palette = SlimeIslandShape.magmaPalette();
        assertSame(ForgeweaveBlocks.MAGMA_SLIME_SOIL.dirt().get(), palette.dirt().getBlock());
        assertSame(ForgeweaveBlocks.MAGMA_SLIME_SOIL.grass().get(), palette.grass().getBlock());
        assertSame(ForgeweaveBlocks.MAGMA_CONGEALED_SLIME.get(), palette.log().getBlock());
        assertSame(ForgeweaveBlocks.ORANGE_SLIME_PLANTS.leaves().get(), palette.leaves().getBlock());
        assertSame(ForgeweaveBlocks.ORANGE_SLIME_PLANTS.tallGrass().get(), palette.tallGrass().getBlock());
        assertSame(ForgeweaveBlocks.ORANGE_SLIME_PLANTS.fern().get(), palette.fern().getBlock());
        assertSame(Blocks.LAVA, palette.eroded().getBlock());
    }

    /**
     * The visible half of {@code air = Blocks.LAVA}: what the first erosion pass carves out of a
     * magma island's underside is backfilled with lava, where an overworld island is left as sky.
     * Both are drawn by the same code, so this is the one thing that separates them in the buffer.
     */
    @Test
    void aMagmaIslandsUndersideErodesIntoLava() {
        int islandsWithLava = 0;
        for (long seed = 1; seed <= 20; seed++) {
            RandomSource random = RandomSource.create(seed);
            SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(random);
            SlimeIslandShape.Palette palette = SlimeIslandShape.magmaPalette();
            SlimeIslandShape.Canvas canvas = SlimeIslandShape.Canvas.forIsland(size);
            SlimeIslandShape.generate(random, canvas, size, palette);

            List<SlimeIslandShape.Canvas.Drawn> drawn = new ArrayList<>();
            canvas.forEachDrawn(drawn::add);
            if (drawn.stream().anyMatch(entry -> entry.state().getBlock() == Blocks.LAVA)) {
                islandsWithLava++;
            }
            // Only the lower erosion pass backfills; the rim pass above the island top clears to
            // air on every island, upstream's own hard-coded Blocks.AIR there. Since #625 the
            // magma island's lake pours lava too, in the four layers sunk under its surface.
            for (SlimeIslandShape.Canvas.Drawn entry : drawn) {
                if (entry.state().getBlock() == Blocks.LAVA) {
                    int y = entry.pos().getY();
                    assertTrue(y <= 8 || (y >= size.yRange() - 4 && y <= size.yRange() - 1),
                            "lava at y " + y + " is neither eroded underside nor lake (seed " + seed + ")");
                }
            }
        }
        assertTrue(islandsWithLava >= 15, "only " + islandsWithLava + "/20 magma islands eroded into lava");
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
                // #647: only a hanging island vine may reach below the island's bottom plane.
                int floor = state.getBlock() instanceof SlimeVineBlock ? -SlimeIslandShape.Size.vineHang() : 0;
                assertTrue(y >= floor && y <= island.size.canvasTop(), "y " + y + " escaped (seed " + seed + ")");
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

    /**
     * Issue #625 (parity audit T18): upstream {@code SlimeIslandGenerator}'s constructor builds three
     * {@code SlimeLakeGenerator}s and {@code generateIslandInChunk} picks between them alongside the
     * dirt -- the purple island gets a purple-slime lake bottomed in purple congealed slime, the
     * green and blue islands both get a blue-slime lake bottomed in their own dirt colour, and both
     * of those scatter green and blue congealed slime around the rim.
     */
    @Test
    void theLakePaletteFollowsTheIslandPalette() {
        for (int roll = 0; roll <= 1; roll++) {
            SlimeIslandShape.Lake lake = SlimeIslandShape.paletteFor(roll).lake();
            assertNotNull(lake, "roll " + roll);
            assertSame(ForgeweaveFluids.PURPLE_SLIME.block().get(), lake.liquid().getBlock(), "roll " + roll);
            assertSame(ForgeweaveBlocks.PURPLE_CONGEALED_SLIME.get(), lake.bottom().getBlock(), "roll " + roll);
            assertEquals(List.of(ForgeweaveBlocks.PURPLE_CONGEALED_SLIME.get()),
                    lake.edges().stream().map(BlockState::getBlock).toList(), "roll " + roll);
        }
        for (int roll = 2; roll < 10; roll++) {
            SlimeIslandShape.Lake lake = SlimeIslandShape.paletteFor(roll).lake();
            assertNotNull(lake, "roll " + roll);
            assertSame(ForgeweaveFluids.BLUE_SLIME.block().get(), lake.liquid().getBlock(), "roll " + roll);
            assertSame(roll < 6 ? ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get() : ForgeweaveBlocks.BLUE_CONGEALED_SLIME.get(),
                    lake.bottom().getBlock(), "roll " + roll);
            assertEquals(List.of(ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get(), ForgeweaveBlocks.BLUE_CONGEALED_SLIME.get()),
                    lake.edges().stream().map(BlockState::getBlock).toList(), "roll " + roll);
        }
    }

    /**
     * Upstream {@code SlimeLakeGenerator#generateLake} sinks its 16x16x8 box four blocks below the
     * island's top-centre column: the lower four layers fill with slime, the upper four are cleared
     * to air, so no liquid ever sits above the island surface, and a cell whose neighbour below is
     * air is skipped, so a lake never hangs off the underside.
     */
    @Test
    void everyIslandSinksASlimeLakeIntoItsSurface() {
        int islandsWithLakes = 0;
        for (long loopSeed = 1; loopSeed <= 20; loopSeed++) {
            long seed = loopSeed;
            Island island = Island.at(seed);
            BlockState liquid = island.palette.lake().liquid();
            int surface = island.size.yRange();

            List<int[]> pool = new ArrayList<>();
            island.forEachDrawn((x, y, z, state) -> {
                if (state == liquid) {
                    pool.add(new int[] {x, y, z});
                }
            });
            if (!pool.isEmpty()) {
                islandsWithLakes++;
            }
            for (int[] cell : pool) {
                assertTrue(cell[1] >= surface - 4 && cell[1] <= surface - 1,
                        "slime at y " + cell[1] + " left upstream's four sunken layers under the "
                                + surface + "-high surface (seed " + seed + ")");
                assertFalse(island.canvas.isAir(cell[0], cell[1] - 1, cell[2]),
                        "slime at " + cell[0] + "," + cell[1] + "," + cell[2] + " floats (seed " + seed + ")");
            }
        }
        assertTrue(islandsWithLakes >= 15, "only " + islandsWithLakes + "/20 islands grew a lake");
    }

    /**
     * The edge pass lays the lake's own bottom block and congealed slimes, and it only ever writes
     * inside upstream's 16x16x8 box -- so nothing congealed appears away from the lake (the trees'
     * trunks are congealed slime too, which is why this checks the box rather than the block).
     */
    @Test
    void theLakeRimIsLaidInCongealedSlime() {
        int islandsWithRim = 0;
        for (long loopSeed = 1; loopSeed <= 20; loopSeed++) {
            long seed = loopSeed;
            Island island = Island.at(seed);
            SlimeIslandShape.Lake lake = island.palette.lake();
            int surface = island.size.yRange();
            // Upstream's box corner: centre - 8 on both horizontal axes, centre - 4 vertically.
            int minX = island.size.xRange() / 2 - 8;
            int minZ = island.size.zRange() / 2 - 8;

            List<int[]> rim = new ArrayList<>();
            island.forEachDrawn((x, y, z, state) -> {
                // Only the four sunken layers: a tree trunk is congealed slime too, and it rises
                // from the surface upwards, so anything at or above it would be ambiguous.
                boolean congealed = state == lake.bottom() || lake.edges().contains(state);
                if (congealed && y >= surface - 4 && y <= surface - 1) {
                    rim.add(new int[] {x, y, z});
                }
            });
            if (!rim.isEmpty()) {
                islandsWithRim++;
            }
            for (int[] cell : rim) {
                assertTrue(cell[0] >= minX && cell[0] < minX + 16 && cell[2] >= minZ && cell[2] < minZ + 16,
                        "congealed slime at " + cell[0] + "," + cell[1] + "," + cell[2]
                                + " is outside the lake box (seed " + seed + ")");
            }
        }
        assertTrue(islandsWithRim >= 15, "only " + islandsWithRim + "/20 islands laid a lake rim");
    }

    /**
     * Issue #625: upstream's {@code lakeGenMagma} pours lava, not slime -- the Nether island's lake
     * is the same generator handed {@code Blocks.LAVA} and magma congealed slime.
     */
    @Test
    void theMagmaIslandsLakeIsLava() {
        SlimeIslandShape.Lake lake = SlimeIslandShape.magmaPalette().lake();
        assertNotNull(lake);
        assertSame(Blocks.LAVA, lake.liquid().getBlock());
        assertSame(ForgeweaveBlocks.MAGMA_CONGEALED_SLIME.get(), lake.bottom().getBlock());
        assertEquals(List.of(ForgeweaveBlocks.MAGMA_CONGEALED_SLIME.get()),
                lake.edges().stream().map(BlockState::getBlock).distinct().toList());
    }

    /** A hand-planted sapling grows a tree, not an island, so its palette carries no lake at all. */
    @Test
    void aSaplingPaletteHasNoLake() {
        for (FoliageType foliage : FoliageType.values()) {
            assertNull(SlimeIslandShape.saplingPalette(foliage).lake(), foliage.name());
        }
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
