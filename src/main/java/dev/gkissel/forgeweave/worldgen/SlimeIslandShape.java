package dev.gkissel.forgeweave.worldgen;

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
 * <p>The slime vines arrived with issue #488 (parity audit T57); the slime <em>lake</em> is still
 * deliberately absent, because it needs the blue and purple slime fluids that same ticket's
 * follow-up carries. Upstream itself supports leaving it out -- {@code generateIsland} null-checks
 * its lake generator. {@code SlimeTreeGenerator}'s other documented null branch, {@code vine == null}
 * (canopy corners filled with leaves instead of vines), is still reachable and is what a
 * hand-planted sapling takes, exactly as upstream's {@code BlockSlimeSapling} does.
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
     */
    public record Palette(BlockState dirt, BlockState grass, BlockState log, BlockState leaves,
                          BlockState tallGrass, BlockState fern, @Nullable BlockState vine) {}

    /**
     * The palette a hand-planted sapling grows with (issue #488): upstream's {@code BlockSlimeSapling
     * #generateTree} builds a {@code SlimeTreeGenerator} with a green congealed-slime trunk, its own
     * foliage's leaves and a {@code null} vine, so a planted tree takes the leafy-corner branch of
     * the canopy rather than the island generator's hanging vines. Only the trunk, leaves and vine
     * fields are read when growing a tree; the soil and plant fields are the sapling's own ground.
     */
    public static Palette saplingPalette(FoliageType foliage) {
        var plants = ForgeweaveBlocks.slimePlants(foliage);
        return new Palette(
                ForgeweaveBlocks.GREEN_SLIME_SOIL.dirt().get().defaultBlockState(),
                ForgeweaveBlocks.GREEN_SLIME_SOIL.grass().get().defaultBlockState(),
                ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get().defaultBlockState(),
                plants.leaves().get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                plants.tallGrass().get().defaultBlockState(),
                plants.fern().get().defaultBlockState(),
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
                plants.vineMid().get().defaultBlockState());
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
            canvas.set(x, y, z, Blocks.AIR.defaultBlockState());
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
