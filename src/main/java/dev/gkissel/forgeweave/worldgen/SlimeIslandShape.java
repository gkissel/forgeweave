package dev.gkissel.forgeweave.worldgen;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import dev.gkissel.forgeweave.block.FoliageType;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks.SlimeSoil;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * The shape of a slime island: upstream 1.12's {@code SlimeIslandGenerator#generateIsland} together
 * with {@code SlimeTreeGenerator} and {@code SlimePlantGenerator} (NOTICE.md), ported whole
 * (issue #449, parity audit T18).
 *
 * <p>Upstream draws straight into the world through {@code World#setBlockState}. This draws into a
 * {@link Canvas} -- an air-filled box of block states, addressed relative to the island's own corner
 * -- which {@link SlimeIslandPiece} then blits into the world in one pass. Two reasons: the
 * erosion passes read back what earlier passes wrote, which a buffer answers without touching chunk
 * storage, and the whole algorithm becomes a pure function that a plain unit test can drive without
 * a running server (see {@code SlimeIslandShapeTest}).
 *
 * <p>The canopy vines arrived with issue #488 (parity audit T57) and the slime lake with #625. Its
 * two documented null branches are still reachable and still used: a hand-planted sapling grows
 * through {@code vine == null} (canopy corners filled with leaves instead of vines, upstream's own
 * {@code BlockSlimeSapling} path) and carries no lake, since a sapling grows a tree rather than an
 * island.
 *
 * <p>One pass of upstream's {@code generateIsland} is still missing: its trailing
 * {@code tryPlacingVine} loop, thirty attempts per island at hanging a stage-one vine off a column
 * of the island's exterior. It is tracked separately because a faithful port of it needs vanilla
 * 1.12's own {@code BlockVine#canPlaceBlockOnSide}, which neither reference clone carries.
 */
public final class SlimeIslandShape {
    /** Upstream {@code SlimeIslandGenerator.RANDOMNESS}: 2% chance of a stray hole in the eroded surface. */
    private static final int RANDOMNESS = 1;

    /** Upstream {@code SlimeTreeGenerator(5, 4, ...)}: trunk of 5 to 8 blocks. */
    private static final int MIN_TREE_HEIGHT = 5;
    private static final int TREE_HEIGHT_RANGE = 4;

    /** Upstream places three trees and makes 128 attempts at plants per island. */
    private static final int TREES_PER_ISLAND = 3;
    private static final int PLANT_ATTEMPTS = 128;

    private SlimeIslandShape() {}

    /**
     * The blocks one island is built from. Upstream builds six of these in
     * {@code SlimeIslandGenerator}'s constructor and picks between them per island; {@link #roll}
     * makes the same pick.
     *
     * <p>{@code eroded} is upstream's {@code air} field -- what the first erosion pass leaves behind
     * where it removes dirt. Air on every overworld island; {@code MagmaSlimeIslandGenerator} sets
     * it to lava, because a Nether island sits sunk in the lava sea (issue #450, parity audit T19).
     * The second erosion pass ignores it and always clears to air, upstream's own hard-coded
     * {@code Blocks.AIR} there.
     */
    public record Palette(BlockState dirt, BlockState grass, BlockState log, BlockState leaves,
                          BlockState tallGrass, BlockState fern, @Nullable BlockState vine,
                          BlockState eroded, @Nullable Lake lake) {}

    /**
     * One {@code SlimeLakeGenerator}'s three constructor arguments (issue #625, parity audit T18):
     * the {@code liquid} it pools, the {@code lakeBottomBlock} it lays one time in ten under that
     * liquid, and the {@code slimeBlocks} varargs it picks from for the rest of the rim.
     *
     * <p>{@code edges} keeps upstream's <em>multiset</em>, not a set: {@code lakeGenMagma} passes
     * magma congealed slime five times and blood congealed once, so a plain uniform pick over the
     * array is a one-in-six blood rim. Whichever list is handed in is indexed exactly the way
     * upstream indexes its array.
     */
    public record Lake(BlockState liquid, BlockState bottom, List<BlockState> edges) {
        public Lake {
            edges = List.copyOf(edges);
        }
    }

    /**
     * The palette a hand-planted sapling grows with (issue #488): upstream's {@code BlockSlimeSapling
     * #generateTree} builds a {@code SlimeTreeGenerator} with a congealed-slime trunk (green, or magma
     * for the orange foliage), its own foliage's leaves and a {@code null} vine, so a planted tree
     * takes the leafy-corner branch of
     * the canopy rather than the island generator's hanging vines. Only the trunk, leaves and vine
     * fields are read when growing a tree; the soil and plant fields are the sapling's own ground.
     */
    public static Palette saplingPalette(FoliageType foliage) {
        var plants = ForgeweaveBlocks.slimePlants(foliage);
        // Upstream BlockSlimeSapling#generateTree picks the trunk off the foliage colour: green
        // congealed slime for every colour except ORANGE, whose tree is the magma island's own
        // (issue #450, parity audit T19).
        BlockState trunk = foliage == FoliageType.ORANGE
                ? ForgeweaveBlocks.MAGMA_CONGEALED_SLIME.get().defaultBlockState()
                : ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get().defaultBlockState();
        return new Palette(
                ForgeweaveBlocks.GREEN_SLIME_SOIL.dirt().get().defaultBlockState(),
                ForgeweaveBlocks.GREEN_SLIME_SOIL.grass().get().defaultBlockState(),
                trunk,
                plants.leaves().get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                plants.tallGrass().get().defaultBlockState(),
                plants.fern().get().defaultBlockState(),
                null,
                Blocks.AIR.defaultBlockState(),
                null);
    }

    /**
     * Grows one sapling-planted tree into the level at {@code pos}, upstream's
     * {@code BlockSlimeSapling#generateTree}: the sapling itself is cleared, the trunk rises from its
     * own block, and anything the canopy would drop on a block that is neither air nor leaves is
     * skipped ({@code SlimeTreeGenerator#setBlockAndMetadata}). Drawn into the same buffer the island
     * feature uses, so both paths share one tested generator.
     */
    public static void growSaplingTree(ServerLevel level, RandomSource random, BlockPos pos, FoliageType foliage) {
        Palette palette = saplingPalette(foliage);
        Canvas canvas = Canvas.forTree();
        plantTree(random, canvas, palette, 0, 0, 0);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_INVISIBLE);
        canvas.forEachDrawn(drawn -> {
            BlockPos target = pos.offset(drawn.pos());
            BlockState existing = level.getBlockState(target);
            if (existing.isAir() || existing.canBeReplaced() || existing.getBlock() == palette.leaves().getBlock()) {
                level.setBlock(target, drawn.state(), Block.UPDATE_ALL);
            }
        });
    }

    /**
     * Upstream {@code generateIslandInChunk}'s palette roll: one island in five is a purple island
     * (purple dirt and grass, blue trees and plants), two in five are green-dirt islands and two in
     * five blue-dirt ones -- both with blue grass and purple trees and plants. The trunk is green
     * congealed slime on every island, upstream's {@code slimeGreen} for both tree generators.
     */
    public static Palette roll(RandomSource random) {
        return paletteFor(random.nextInt(10));
    }

    /** The palette upstream's {@code rnr} value picks; split out so a unit test can walk all ten. */
    public static Palette paletteFor(int roll) {
        SlimeSoil soil = ForgeweaveBlocks.BLUE_SLIME_SOIL;
        SlimeSoil grassSoil = ForgeweaveBlocks.BLUE_SLIME_SOIL;
        boolean purpleIsland = false;

        if (roll <= 1) {
            soil = ForgeweaveBlocks.PURPLE_SLIME_SOIL;
            grassSoil = ForgeweaveBlocks.PURPLE_SLIME_SOIL;
            purpleIsland = true;
        } else if (roll < 6) {
            soil = ForgeweaveBlocks.GREEN_SLIME_SOIL;
        }

        // Upstream pairs the foliage the *other* way round from the grass: purple islands grow blue
        // trees and plants, blue and green ones grow purple.
        var plants = purpleIsland ? ForgeweaveBlocks.BLUE_SLIME_PLANTS : ForgeweaveBlocks.PURPLE_SLIME_PLANTS;
        return new Palette(
                soil.dirt().get().defaultBlockState(),
                grassSoil.grass().get().defaultBlockState(),
                ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get().defaultBlockState(),
                plants.leaves().get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                plants.tallGrass().get().defaultBlockState(),
                plants.fern().get().defaultBlockState(),
                plants.vineMid().get().defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                purpleIsland ? purpleLake() : blueSlimeLake(soil));
    }

    /**
     * Upstream's {@code lakeGenPurple}: {@code new SlimeLakeGenerator(purpleSlime, slimePurple,
     * slimePurple)} -- a purple-slime pool bottomed and rimmed in purple congealed slime alone.
     */
    private static Lake purpleLake() {
        BlockState purple = ForgeweaveBlocks.PURPLE_CONGEALED_SLIME.get().defaultBlockState();
        return new Lake(ForgeweaveFluids.PURPLE_SLIME.block().get().defaultBlockState(), purple, List.of(purple));
    }

    /**
     * Upstream's {@code lakeGenGreen} and {@code lakeGenBlue}, which differ only in their bottom
     * block: both pool blue slime and both rim with {@code (slimeGreen, slimeBlue)}, but a green
     * island's floor is green congealed slime and a blue island's is blue. Upstream hands
     * {@code lakeGenPurple} the purple fluid and everything else the blue one.
     */
    private static Lake blueSlimeLake(SlimeSoil soil) {
        BlockState green = ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get().defaultBlockState();
        BlockState blue = ForgeweaveBlocks.BLUE_CONGEALED_SLIME.get().defaultBlockState();
        BlockState bottom = soil == ForgeweaveBlocks.GREEN_SLIME_SOIL ? green : blue;
        return new Lake(ForgeweaveFluids.BLUE_SLIME.block().get().defaultBlockState(), bottom, List.of(green, blue));
    }

    /**
     * The Nether island's palette: upstream {@code MagmaSlimeIslandGenerator}'s constructor, which
     * builds exactly one (issue #450, parity audit T19). Magma slimy dirt and grass, an orange
     * canopy over a magma congealed slime trunk, orange plants, and lava where the underside erodes.
     */
    public static Palette magmaPalette() {
        var plants = ForgeweaveBlocks.ORANGE_SLIME_PLANTS;
        return new Palette(
                ForgeweaveBlocks.MAGMA_SLIME_SOIL.dirt().get().defaultBlockState(),
                ForgeweaveBlocks.MAGMA_SLIME_SOIL.grass().get().defaultBlockState(),
                ForgeweaveBlocks.MAGMA_CONGEALED_SLIME.get().defaultBlockState(),
                plants.leaves().get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                plants.tallGrass().get().defaultBlockState(),
                plants.fern().get().defaultBlockState(),
                null,
                Blocks.LAVA.defaultBlockState(),
                magmaLake());
    }

    /**
     * Upstream's {@code lakeGenMagma}: {@code new SlimeLakeGenerator(Blocks.LAVA, slimeMagma,
     * slimeMagma x5, slimeBlood)} -- a lava pool bottomed in magma congealed slime, rimmed five
     * parts magma to one part <em>blood</em> congealed slime.
     *
     * <p>Blood congealed slime is the one colour still missing (parity audit T57, issue #635), so
     * magma stands in its slot rather than the array shrinking to five: {@code nextInt(6)} keeps its
     * upstream draw, and only the block that one roll in six lands on differs. Recorded deviation.
     */
    private static Lake magmaLake() {
        BlockState magma = ForgeweaveBlocks.MAGMA_CONGEALED_SLIME.get().defaultBlockState();
        return new Lake(Blocks.LAVA.defaultBlockState(), magma,
                List.of(magma, magma, magma, magma, magma, magma));
    }

    /** The island's overall extent, rolled before anything is drawn so the canvas can be sized for it. */
    public record Size(int xRange, int zRange, int yRange) {
        /** Upstream: 20 + rnd(13) across, 20 + rnd(13) deep, 11 + rnd(3) tall. */
        public static Size roll(RandomSource random) {
            return new Size(20 + random.nextInt(13), 20 + random.nextInt(13), 11 + random.nextInt(3));
        }

        /**
         * How far above the island's own top the canvas has to reach: the tallest trunk plus its
         * canopy, which upstream draws from the trunk top downwards.
         */
        public int canvasTop() {
            return yRange + MIN_TREE_HEIGHT + TREE_HEIGHT_RANGE + 2;
        }

        /** How far outside the island footprint a canopy can reach, in blocks. */
        public static int canvasPad() {
            return 4;
        }

        /** The full width of {@link Canvas#forIsland}'s canvas for this island, canopy pad included. */
        public int canvasSizeX() {
            return xRange + 1 + 2 * canvasPad();
        }

        /** The full height of {@link Canvas#forIsland}'s canvas for this island. */
        public int canvasSizeY() {
            return canvasTop() + 1;
        }

        /** The full depth of {@link Canvas#forIsland}'s canvas for this island, canopy pad included. */
        public int canvasSizeZ() {
            return zRange + 1 + 2 * canvasPad();
        }

        /**
         * The inverse of the three {@code canvasSize} accessors: the island a canvas of this span was
         * sized for. {@link SlimeIslandPiece} reloads itself with this, so a saved island needs no
         * NBT of its own beyond the bounding box vanilla already writes for every structure piece.
         */
        public static Size fromCanvasSpan(int sizeX, int sizeY, int sizeZ) {
            int pad = canvasPad();
            return new Size(sizeX - 1 - 2 * pad, sizeZ - 1 - 2 * pad,
                    sizeY - 1 - MIN_TREE_HEIGHT - TREE_HEIGHT_RANGE - 2);
        }
    }

    /**
     * An air-filled box of block states, addressed in island-relative coordinates (the origin is
     * upstream's {@code start} corner). Reads outside the box answer air and writes outside it are
     * dropped, which is what the surrounding sky would do anyway -- a slime island generates in open
     * air, so nothing it reads back belongs to the world.
     */
    public static final class Canvas {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final BlockState[] states;

        public Canvas(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.states = new BlockState[sizeX * sizeY * sizeZ];
        }

        /** A canvas big enough for an island of {@code size}, including its canopies. */
        public static Canvas forIsland(Size size) {
            int pad = Size.canvasPad();
            return new Canvas(-pad, 0, -pad, size.canvasSizeX(), size.canvasSizeY(), size.canvasSizeZ());
        }

        /** A canvas big enough for one tree grown from a sapling at its origin. */
        public static Canvas forTree() {
            int pad = Size.canvasPad();
            return new Canvas(-pad, 0, -pad, 2 * pad + 1, MIN_TREE_HEIGHT + TREE_HEIGHT_RANGE + 1, 2 * pad + 1);
        }

        private int index(int x, int y, int z) {
            int ix = x - minX;
            int iy = y - minY;
            int iz = z - minZ;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= sizeX || iy >= sizeY || iz >= sizeZ) {
                return -1;
            }
            return (ix * sizeY + iy) * sizeZ + iz;
        }

        /** The state at an island-relative position; air outside the canvas or where nothing was drawn. */
        public BlockState get(int x, int y, int z) {
            int index = index(x, y, z);
            BlockState state = index < 0 ? null : states[index];
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        public boolean isAir(int x, int y, int z) {
            return get(x, y, z).isAir();
        }

        public void set(int x, int y, int z, BlockState state) {
            int index = index(x, y, z);
            if (index >= 0) {
                states[index] = state;
            }
        }

        /** Every position that was actually drawn on, in island-relative coordinates. */
        public void forEachDrawn(Consumer<Drawn> consumer) {
            for (int ix = 0; ix < sizeX; ix++) {
                for (int iy = 0; iy < sizeY; iy++) {
                    for (int iz = 0; iz < sizeZ; iz++) {
                        BlockState state = states[(ix * sizeY + iy) * sizeZ + iz];
                        if (state != null && !state.isAir()) {
                            consumer.accept(new Drawn(new BlockPos(ix + minX, iy + minY, iz + minZ), state));
                        }
                    }
                }
            }
        }

        /** One drawn block: its island-relative position and the state to place there. */
        public record Drawn(BlockPos pos, BlockState state) {}
    }

    /**
     * Draws one island into {@code canvas}: upstream's solid elliptical plug, its two erosion passes,
     * its grass surfacing, then its plants and its three trees, in that order.
     */
    public static void generate(RandomSource random, Canvas canvas, Size size, Palette palette) {
        int xRange = size.xRange();
        int zRange = size.zRange();
        int yRange = size.yRange();
        int height = yRange;

        for (int x = 0; x <= xRange; x++) {
            for (int z = 0; z <= zRange; z++) {
                for (int y = 0; y <= yRange; y++) {
                    if (inEllipse(x, z, xRange, zRange)) {
                        canvas.set(x, y, z, palette.dirt());
                    }
                }
            }
        }

        erodeUnderside(random, canvas, size, palette);
        erodeRim(canvas, size, palette);
        surfaceWithGrass(canvas, size, palette, height);
        // Upstream's order, and it is load-bearing: the lake is dug before the plants and trees are
        // scattered, so it never has to carve around them -- and both of those draw from the same
        // random, so moving it would reshuffle every island.
        if (palette.lake() != null) {
            generateLake(random, canvas, palette.lake(), size.xRange() / 2, height, size.zRange() / 2);
        }
        plantPlants(random, canvas, size, palette, height);

        for (int i = 0; i < TREES_PER_ISLAND; i++) {
            growTree(random, canvas, palette, random.nextInt(xRange), height, random.nextInt(zRange));
        }
    }

    /**
     * {@code java.awt.geom.Ellipse2D.Double(0, 0, xRange, zRange).contains(x, z)}, inlined -- an
     * island is drawn on a headless server, and this is the one line of {@code java.desktop} upstream
     * leans on.
     */
    private static boolean inEllipse(int x, int z, int xRange, int zRange) {
        if (xRange <= 0 || zRange <= 0) {
            return false;
        }
        double normX = (double) x / xRange - 0.5D;
        double normZ = (double) z / zRange - 0.5D;
        return normX * normX + normZ * normZ < 0.25D;
    }

    /**
     * Upstream's first erosion pass: the bottom nine layers lose any dirt that is not fully covered
     * from above, worked inwards from both corners at once, plus a 2% chance of a stray hole.
     *
     * <p>The four "covered from above" probes are upstream's exactly, including that the fourth
     * samples {@code (-1, +1, +1)} rather than the {@code (0, +1, +1)} the other three imply -- a
     * verbatim port, asymmetry included, because the eroded silhouette is the one players know.
     */
    private static void erodeUnderside(RandomSource random, Canvas canvas, Size size, Palette palette) {
        int erodeHeight = 8;
        for (int x = 0; x <= size.xRange(); x++) {
            for (int z = 0; z <= size.zRange(); z++) {
                for (int y = 0; y <= erodeHeight; y++) {
                    erodeUndersideAt(random, canvas, palette, x, erodeHeight - y, z);
                    erodeUndersideAt(random, canvas, palette, size.xRange() - x, erodeHeight - y, size.zRange() - z);
                }
            }
        }
    }

    private static void erodeUndersideAt(RandomSource random, Canvas canvas, Palette palette, int x, int y, int z) {
        if (canvas.get(x, y, z) != palette.dirt()) {
            return;
        }
        if (canvas.get(x - 1, y + 1, z) != palette.dirt()
                || canvas.get(x + 1, y + 1, z) != palette.dirt()
                || canvas.get(x, y + 1, z - 1) != palette.dirt()
                || canvas.get(x - 1, y + 1, z + 1) != palette.dirt()
                || random.nextInt(100) <= RANDOMNESS) {
            canvas.set(x, y, z, palette.eroded());
        }
    }

    /**
     * Upstream's second erosion pass: the top three layers lose anything whose four horizontal
     * neighbours one block down are not all dirt, rounding the island's rim off.
     */
    private static void erodeRim(Canvas canvas, Size size, Palette palette) {
        int erodeHeight = 2;
        int height = size.yRange();
        for (int x = 0; x <= size.xRange(); x++) {
            for (int z = 0; z <= size.zRange(); z++) {
                for (int y = 0; y <= erodeHeight; y++) {
                    erodeRimAt(canvas, palette, x, y + height, z);
                    erodeRimAt(canvas, palette, size.xRange() - x, y + height, size.zRange() - z);
                }
            }
        }
    }

    private static void erodeRimAt(Canvas canvas, Palette palette, int x, int y, int z) {
        int below = y - 1;
        if (canvas.get(x, below, z - 1) != palette.dirt()
                || canvas.get(x + 1, below, z) != palette.dirt()
                || canvas.get(x, below, z + 1) != palette.dirt()
                || canvas.get(x - 1, below, z) != palette.dirt()) {
            canvas.set(x, y, z, Blocks.AIR.defaultBlockState());
        }
    }

    /** Upstream's surfacing pass: the topmost dirt in each column with air above it becomes grass. */
    private static void surfaceWithGrass(Canvas canvas, Size size, Palette palette, int height) {
        for (int x = 0; x <= size.xRange(); x++) {
            for (int z = 0; z <= size.zRange(); z++) {
                for (int y = 0; y <= height; y++) {
                    int cursor = height - y;
                    if (canvas.get(x, cursor, z) == palette.dirt() && canvas.isAir(x, cursor + 1, z)) {
                        canvas.set(x, cursor, z, palette.grass());
                        break;
                    }
                }
            }
        }
    }

    /** Upstream {@code SlimeLakeGenerator}'s grid: 16 across, 16 deep, 8 tall. */
    private static final int LAKE_WIDTH = 16;
    private static final int LAKE_HEIGHT = 8;

    /**
     * Upstream {@code SlimeLakeGenerator#generateLake} (issue #625, parity audit T18), whole. It is
     * handed the island's top-centre column, walks down it while that column is air, then drops its
     * 16x16x8 box eight blocks back on each horizontal axis and four blocks down -- so the lower
     * four layers cut into the island's surface and the upper four sit in the sky above it.
     *
     * <p>Four passes, upstream's: four to seven overlapping ellipsoid blobs marked into a boolean
     * grid; an abort scan that walks away from the whole lake if any cell bordering the blob at or
     * above the waterline is already liquid; the fill, which pools liquid below the waterline, clears
     * air above it, and skips any cell whose neighbour below is air so a lake cannot hang; and the
     * rim pass, which re-walks the same border and lays the bottom block or a congealed slime.
     *
     * <p>Where upstream reads the level, this reads the {@link Canvas}. An island generates 50+
     * blocks up in open sky, so everything around it is air either way -- which is also why the abort
     * scan never actually fires here, exactly as it never fires upstream on an island (it is written
     * for {@code SlimeLakeGenerator}'s other life as a standalone {@code IWorldGenerator}). It is
     * ported anyway: it is what keeps the pass a faithful port rather than a re-derivation.
     */
    private static void generateLake(RandomSource random, Canvas canvas, Lake lake, int centreX, int centreY, int centreZ) {
        // Upstream: `while(pos.getY() > 5 && world.isAirBlock(pos)) pos = pos.down();`. The island's
        // centre column is always its grass surface by this point (the plug fills the full ellipse and
        // neither erosion pass reaches the middle of the top layer), so this never actually descends;
        // upstream's absolute y > 5 floor becomes the canvas floor, which is the island's own bottom.
        int surfaceY = centreY;
        while (surfaceY > 0 && canvas.isAir(centreX, surfaceY, centreZ)) {
            surfaceY--;
        }

        int minX = centreX - 8;
        int minY = surfaceY - 4;
        int minZ = centreZ - 8;

        boolean[] blob = rollLakeBlob(random);

        // Upstream's abort scan: any border cell at or above the waterline that is already liquid
        // means this lake would breach an existing one, so nothing at all is drawn.
        for (int x = 0; x < LAKE_WIDTH; x++) {
            for (int z = 0; z < LAKE_WIDTH; z++) {
                for (int y = 4; y < LAKE_HEIGHT; y++) {
                    if (bordersBlob(blob, x, y, z) && canvas.get(minX + x, minY + y, minZ + z).liquid()) {
                        return;
                    }
                }
            }
        }

        // The fill. Below the waterline the blob pools liquid, above it the blob is cleared to sky;
        // either way upstream skips a cell standing on air, which is what keeps the pool inside the
        // island instead of raining off its underside.
        for (int x = 0; x < LAKE_WIDTH; x++) {
            for (int z = 0; z < LAKE_WIDTH; z++) {
                for (int y = 0; y < LAKE_HEIGHT; y++) {
                    if (blob[lakeIndex(x, y, z)] && !canvas.isAir(minX + x, minY + y - 1, minZ + z)) {
                        canvas.set(minX + x, minY + y, minZ + z,
                                y >= 4 ? Blocks.AIR.defaultBlockState() : lake.liquid());
                    }
                }
            }
        }

        // The rim. Upstream re-derives the same border and, on every solid block it finds there,
        // lays its bottom block one time in ten where the block above is liquid and a congealed
        // slime everywhere else. Above the waterline it skips half the border on a coin flip, which
        // is what stops the pool's lip from reading as a drawn-on ring.
        for (int x = 0; x < LAKE_WIDTH; x++) {
            for (int z = 0; z < LAKE_WIDTH; z++) {
                for (int y = 0; y < LAKE_HEIGHT; y++) {
                    if (!bordersBlob(blob, x, y, z)) {
                        continue;
                    }
                    int atX = minX + x;
                    int atY = minY + y;
                    int atZ = minZ + z;
                    // Upstream's Material#isSolid: at this point in generateIsland the canvas holds
                    // nothing but the island's own soil, the lake's liquid and (on a magma island)
                    // the lava its underside eroded into -- plants and trees come after -- so "not
                    // air and not liquid" is exactly upstream's set here.
                    BlockState state = canvas.get(atX, atY, atZ);
                    boolean solid = !state.isAir() && !state.liquid();
                    // Upstream's `(yy < 4 || random.nextInt(2) != 0) && isSolid()`: the coin flip is
                    // only rolled above the waterline, so the short circuit is what keeps the random
                    // stream -- and therefore every island -- identical.
                    if ((y >= 4 && random.nextInt(2) == 0) || !solid) {
                        continue;
                    }
                    if (canvas.get(atX, atY + 1, atZ).liquid()) {
                        if (random.nextInt(10) == 0) {
                            canvas.set(atX, atY, atZ, lake.bottom());
                        }
                    } else if (!lake.edges().isEmpty()) {
                        canvas.set(atX, atY, atZ, lake.edges().get(random.nextInt(lake.edges().size())));
                    }
                }
            }
        }
    }

    /**
     * Upstream's blob pass: four to seven ellipsoids, each rolled its own three radii and centre, all
     * unioned into one boolean grid. The 1-to-15 and 1-to-7 loop bounds are upstream's too -- the
     * outermost shell of the grid is never marked, which is what leaves a border for the rim to sit
     * on.
     */
    private static boolean[] rollLakeBlob(RandomSource random) {
        boolean[] blob = new boolean[LAKE_WIDTH * LAKE_WIDTH * LAKE_HEIGHT];
        int spots = random.nextInt(4) + 4;
        for (int i = 0; i < spots; i++) {
            double xr = random.nextDouble() * 6 + 3;
            double yr = random.nextDouble() * 4 + 2;
            double zr = random.nextDouble() * 6 + 3;

            double xp = random.nextDouble() * (16 - xr - 2) + 1 + xr / 2;
            double yp = random.nextDouble() * (8 - yr - 4) + 2 + yr / 2;
            double zp = random.nextDouble() * (16 - zr - 2) + 1 + zr / 2;

            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    for (int y = 1; y < 7; y++) {
                        double xd = (x - xp) / (xr / 2);
                        double yd = (y - yp) / (yr / 2);
                        double zd = (z - zp) / (zr / 2);
                        if (xd * xd + yd * yd + zd * zd < 1) {
                            blob[lakeIndex(x, y, z)] = true;
                        }
                    }
                }
            }
        }
        return blob;
    }

    /** Upstream's border test: a cell outside the blob with at least one of its six neighbours inside it. */
    private static boolean bordersBlob(boolean[] blob, int x, int y, int z) {
        if (blob[lakeIndex(x, y, z)]) {
            return false;
        }
        return (x < 15 && blob[lakeIndex(x + 1, y, z)])
                || (x > 0 && blob[lakeIndex(x - 1, y, z)])
                || (z < 15 && blob[lakeIndex(x, y, z + 1)])
                || (z > 0 && blob[lakeIndex(x, y, z - 1)])
                || (y < 7 && blob[lakeIndex(x, y + 1, z)])
                || (y > 0 && blob[lakeIndex(x, y - 1, z)]);
    }

    /** Upstream's own {@code (xx * 16 + zz) * 8 + yy} packing, kept so the grid indexes identically. */
    private static int lakeIndex(int x, int y, int z) {
        return (x * LAKE_WIDTH + z) * LAKE_HEIGHT + y;
    }

    /**
     * Upstream {@code SlimePlantGenerator#generatePlants}, called with {@code from} one block above
     * the island top and {@code to} three blocks <em>below</em> it. That inverted y span is
     * upstream's, and it is load-bearing: the generator's descend loop is written {@code j < yd} with
     * a negative {@code yd}, so it never runs and every plant lands on the island surface itself.
     * Ported as written -- the alternative would move plants somewhere upstream never puts them.
     */
    private static void plantPlants(RandomSource random, Canvas canvas, Size size, Palette palette, int height) {
        boolean fern = true; // upstream cycles the shape property before placing, so the first is a fern
        for (int i = 0; i < PLANT_ATTEMPTS; i++) {
            int x = random.nextInt(size.xRange());
            int z = random.nextInt(size.zRange());
            int y = height + 1;
            BlockState plant = fern ? palette.fern() : palette.tallGrass();
            fern = !fern;
            if (canvas.isAir(x, y, z) && ForgeweaveBlocks.isSlimeSoil(canvas.get(x, y - 1, z).getBlock())) {
                canvas.set(x, y, z, plant);
            }
        }
    }

    /**
     * Upstream {@code SlimeTreeGenerator#generateTree} with {@code seekHeight}: find the island
     * surface under the given column, then raise a congealed-slime trunk and hang a canopy on it.
     */
    private static void growTree(RandomSource random, Canvas canvas, Palette palette, int x, int startY, int z) {
        int trunkHeight = random.nextInt(TREE_HEIGHT_RANGE) + MIN_TREE_HEIGHT;
        int ground = findGround(canvas, x, startY, z);
        if (ground < 0) {
            return;
        }
        // Upstream's soil check: the block under the trunk has to be one that sustains a slime plant.
        if (!ForgeweaveBlocks.isSlimeSoil(canvas.get(x, ground - 1, z).getBlock())) {
            return;
        }
        placeTrunk(canvas, palette, x, ground, z, trunkHeight);
        placeCanopy(random, canvas, palette, x, ground + trunkHeight, z);
    }

    /**
     * One tree whose ground is already known -- the sapling path, where the planted block itself is
     * the trunk's first block and there is no island surface to seek down to.
     */
    public static void plantTree(RandomSource random, Canvas canvas, Palette palette, int x, int y, int z) {
        int trunkHeight = random.nextInt(TREE_HEIGHT_RANGE) + MIN_TREE_HEIGHT;
        placeTrunk(canvas, palette, x, y, z, trunkHeight);
        placeCanopy(random, canvas, palette, x, y + trunkHeight, z);
    }

    /** Upstream {@code findGround}: walk down to the first slime soil whose top face is clear. */
    private static int findGround(Canvas canvas, int x, int startY, int z) {
        for (int y = startY; y > 0; y--) {
            if (ForgeweaveBlocks.isSlimeSoil(canvas.get(x, y, z).getBlock())
                    && !canvas.get(x, y + 1, z).canOcclude()) {
                return y + 1;
            }
        }
        return -1;
    }

    private static void placeTrunk(Canvas canvas, Palette palette, int x, int y, int z, int trunkHeight) {
        for (int i = 0; i < trunkHeight; i++) {
            BlockState existing = canvas.get(x, y + i, z);
            if (existing.isAir() || existing.getBlock() == palette.leaves().getBlock()) {
                canvas.set(x, y + i, z, palette.log());
            }
        }
    }

    /**
     * Upstream {@code placeCanopy}: four diamond layers of leaves down from the trunk top, then the
     * two shaping passes. Which shaping it does depends on whether the palette carries a vine: with
     * one, the four canopy corners are hollowed out and vines are hung from the skirt and the
     * corners; without one -- upstream's own {@code vine == null} branch, which is what a
     * hand-planted sapling takes -- those corners are filled with leaves instead.
     */
    private static void placeCanopy(RandomSource random, Canvas canvas, Palette palette, int x, int top, int z) {
        for (int i = 0; i < 4; i++) {
            placeDiamondLayer(canvas, palette, x, top - i, z, i + 1);
        }

        BlockState vine = palette.vine();
        BlockState air = Blocks.AIR.defaultBlockState();

        int arms = top - 3;
        setLeafy(canvas, palette, x + 4, arms, z, air);
        setLeafy(canvas, palette, x - 4, arms, z, air);
        setLeafy(canvas, palette, x, arms, z + 4, air);
        setLeafy(canvas, palette, x, arms, z - 4, air);
        if (vine != null) {
            setLeafy(canvas, palette, x + 1, arms, z + 1, air);
            setLeafy(canvas, palette, x + 1, arms, z - 1, air);
            setLeafy(canvas, palette, x - 1, arms, z + 1, air);
            setLeafy(canvas, palette, x - 1, arms, z - 1, air);
        }

        int skirt = arms - 1;
        setLeafy(canvas, palette, x + 3, skirt, z, palette.leaves());
        setLeafy(canvas, palette, x - 3, skirt, z, palette.leaves());
        setLeafy(canvas, palette, x, skirt, z - 3, palette.leaves());
        setLeafy(canvas, palette, x, skirt, z + 3, palette.leaves());
        if (vine == null) {
            setLeafy(canvas, palette, x + 1, skirt, z + 1, palette.leaves());
            setLeafy(canvas, palette, x + 1, skirt, z - 1, palette.leaves());
            setLeafy(canvas, palette, x - 1, skirt, z + 1, palette.leaves());
            setLeafy(canvas, palette, x - 1, skirt, z - 1, palette.leaves());
            return;
        }

        int hang = skirt - 1;
        setLeafy(canvas, palette, x + 3, hang, z, randomizedVine(random, vine));
        setLeafy(canvas, palette, x - 3, hang, z, randomizedVine(random, vine));
        setLeafy(canvas, palette, x, hang, z - 3, randomizedVine(random, vine));
        setLeafy(canvas, palette, x, hang, z + 3, randomizedVine(random, vine));
        // Upstream hangs each corner as a two-block pair with the same faces, so the lower one is
        // held up by the upper one rather than by anything solid.
        hangCorner(random, canvas, palette, x + 2, hang, z + 2, vine);
        hangCorner(random, canvas, palette, x + 2, hang, z - 2, vine);
        hangCorner(random, canvas, palette, x - 2, hang, z + 2, vine);
        hangCorner(random, canvas, palette, x - 2, hang, z - 2, vine);
    }

    private static void hangCorner(RandomSource random, Canvas canvas, Palette palette,
                                   int x, int hang, int z, BlockState vine) {
        BlockState corner = randomizedVine(random, vine);
        setLeafy(canvas, palette, x, hang + 1, z, corner);
        setLeafy(canvas, palette, x, hang, z, corner);
    }

    /**
     * Upstream {@code getRandomizedVine}: clear every face, then turn one to three of them back on
     * at random -- the same roll order, so the same seed hangs the same vines.
     */
    private static BlockState randomizedVine(RandomSource random, BlockState vine) {
        BooleanProperty[] faces = {VineBlock.NORTH, VineBlock.EAST, VineBlock.SOUTH, VineBlock.WEST};
        BlockState state = vine;
        for (BooleanProperty face : faces) {
            state = state.setValue(face, false);
        }
        for (int i = random.nextInt(3) + 1; i > 0; i--) {
            state = state.setValue(faces[random.nextInt(faces.length)], true);
        }
        return state;
    }

    private static void placeDiamondLayer(Canvas canvas, Palette palette, int x, int y, int z, int range) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= range) {
                    setLeafy(canvas, palette, x + dx, y, z + dz, palette.leaves());
                }
            }
        }
    }

    /** Upstream {@code setBlockAndMetadata}: overwrite only air or leaves -- never the trunk. */
    private static void setLeafy(Canvas canvas, Palette palette, int x, int y, int z, BlockState state) {
        BlockState existing = canvas.get(x, y, z);
        if (existing.isAir() || existing.getBlock() == palette.leaves().getBlock()) {
            canvas.set(x, y, z, state);
        }
    }
}
