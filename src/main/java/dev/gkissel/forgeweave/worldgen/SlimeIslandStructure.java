package dev.gkissel.forgeweave.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The slime island: upstream 1.12's {@code SlimeIslandGenerator} (NOTICE.md), as a structure
 * (issues #449 and #629, parity audit T18).
 *
 * <p>#449 first ported it the shape 1.12 has, an {@code IWorldGenerator} that Forge calls per chunk,
 * which modern Minecraft spells as a configured/placed feature. That works but leaves islands
 * invisible to {@code /locate structure}, because {@code /locate} walks the structure registry and a
 * feature is not in it. Upstream's own 1.20 branch had already moved islands onto the structure
 * system for exactly that reason ({@code world/worldgen/islands/IslandStructure}, its structure sets
 * and its biome tags), so #629 follows it: this class is the structure, {@link SlimeIslandPiece} is
 * its one piece, and the piece still draws 1.12's island through {@link SlimeIslandShape}. Upstream
 * 1.20 swapped the procedural island for hand-built NBT templates; that is a content change, not an
 * adaptation, so the 1.12 geometry stays.
 *
 * <p>Where the two upstream generations disagree about <em>how many</em> islands there are, 1.12
 * wins (the maintainer parity default). 1.12 rolls {@code random.nextInt(slimeIslandRate)} per chunk
 * and dropped every island config option when it moved to structures; Forgeweave keeps all five
 * options #449 shipped. The structure set's {@code random_spread} grid supplies the candidate
 * chunks -- one per {@value #GRID_SPACING}x{@value #GRID_SPACING} chunks, so {@value #GRID_DENSITY}
 * of every chunk -- and {@link #findGenerationPoint} thins them by
 * {@code GRID_DENSITY / slimeIslandRate} so the product is exactly one island per
 * {@code slimeIslandRate} chunks. At upstream's default rate of 730 that is exact; a rate below
 * {@value #GRID_DENSITY} is capped at the grid, since a grid cannot be made denser than its own
 * spacing at runtime (see the PR body for #629).
 *
 * <p>Both of the paths that ask "is there an island here" -- world generation and {@code /locate}'s
 * {@code StructureCheck} -- route through {@link #findGenerationPoint}, which is why every config
 * gate lives here rather than in a custom structure placement: a placement's own
 * {@code isPlacementChunk} is consulted by generation only, so gating there would have
 * {@code /locate} pointing at islands that never get built.
 */
public class SlimeIslandStructure extends Structure {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Forgeweave.MODID);

    public static final MapCodec<SlimeIslandStructure> CODEC = simpleCodec(SlimeIslandStructure::new);

    public static final DeferredHolder<StructureType<?>, StructureType<SlimeIslandStructure>> TYPE =
            STRUCTURE_TYPES.register("slime_island", () -> () -> CODEC);

    /** The datapack structure this class backs, {@code data/forgeweave/worldgen/structure/slime_island.json}. */
    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "slime_island"));

    /** The {@code spacing} of the structure set's {@code random_spread} placement; keep the two in step. */
    public static final int GRID_SPACING = 9;

    /** One candidate chunk per {@link #GRID_SPACING} squared chunks -- the density the rate thins. */
    public static final int GRID_DENSITY = GRID_SPACING * GRID_SPACING;

    public SlimeIslandStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return TYPE.get();
    }

    /**
     * Whether an island may start in this dimension at all -- upstream's first four
     * {@code SlimeIslandGenerator#generate} gates, unchanged from #449.
     *
     * <p>Two of them change shape in the port, both because their 1.12 concepts no longer exist.
     * Upstream's {@code isSurfaceWorld()} becomes {@code DimensionType#natural()}, modern Minecraft's
     * own "behaves like the overworld" flag. Upstream's blacklist is a list of numeric dimension ids
     * ({@code -1, 1} -- the Nether and the End); dimensions are named by {@link ResourceLocation}
     * now, so the option holds ids like {@code minecraft:the_nether} and keeps the same two defaults.
     */
    public static boolean enabledIn(ServerLevel level, boolean superflat) {
        if (!ForgeweaveConfig.GEN_SLIME_ISLANDS.get()) {
            return false;
        }
        if (superflat && !ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.get()) {
            return false;
        }
        if (ForgeweaveConfig.SLIME_ISLANDS_ONLY_IN_SURFACE_WORLDS.get() && !level.dimensionType().natural()) {
            return false;
        }
        String dimension = level.dimension().location().toString();
        return !ForgeweaveConfig.SLIME_ISLAND_BLACKLIST.get().contains(dimension);
    }

    /**
     * The level a generation context belongs to. Vanilla's {@code GenerationContext} carries the
     * chunk generator but not the dimension, and two of upstream's gates are dimension questions, so
     * the level is recovered by the one thing that is unique per dimension and present in both: each
     * {@code LevelStem} builds its own chunk generator instance, so identity on it names the level.
     * The loop is over the server's handful of dimensions and runs only for the
     * {@value #GRID_DENSITY}th chunk the grid offers.
     */
    private static ServerLevel levelOf(ChunkGenerator generator) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() == generator) {
                return level;
            }
        }
        return null;
    }

    /** Upstream's five gates, in its own order: the four dimension ones, then the rarity roll. */
    private static boolean allowed(GenerationContext context) {
        ChunkGenerator generator = context.chunkGenerator();
        ServerLevel level = levelOf(generator);
        if (level == null) {
            // No running server owns this generator -- a world-creation preview, or a test harness.
            // Answer with the one gate that needs no dimension rather than vetoing generation.
            return ForgeweaveConfig.GEN_SLIME_ISLANDS.get();
        }
        if (!enabledIn(level, generator instanceof FlatLevelSource)) {
            return false;
        }
        // Upstream: one chunk in slimeIslandRate, and nothing at all when the rate is zero or less.
        int rate = ForgeweaveConfig.SLIME_ISLAND_RATE.get();
        if (rate <= 0) {
            return false;
        }
        return rate <= GRID_DENSITY || context.random().nextFloat() < (float) GRID_DENSITY / rate;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!allowed(context)) {
            return Optional.empty();
        }

        WorldgenRandom random = context.random();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heights = context.heightAccessor();

        // Upstream generateIslandInChunk: a spot in the middle half of the chunk, 61 to 110 blocks
        // above the surface below it.
        int x = context.chunkPos().getMinBlockX() + 4 + random.nextInt(8);
        int z = context.chunkPos().getMinBlockZ() + 4 + random.nextInt(8);
        int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights,
                context.randomState());
        int islandTop = surface + 50 + random.nextInt(50) + 11;

        SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(random);

        // Upstream generates against 1.12's fixed 0..255 column and never clamps; a 1.21 dimension
        // can be shorter than the surface height plus an island plus its canopies, so the island is
        // pulled down to fit rather than silently losing its treetops to the build ceiling.
        int ceiling = heights.getMaxBuildHeight() - 1;
        islandTop = Math.min(islandTop, ceiling - size.canvasTop() + size.yRange());
        int bottom = islandTop - size.yRange();
        if (bottom <= heights.getMinBuildHeight()) {
            return Optional.empty();
        }

        int pad = SlimeIslandShape.Size.canvasPad();
        int minX = x - size.xRange() / 2 - pad;
        int minZ = z - size.zRange() / 2 - pad;
        BoundingBox box = new BoundingBox(minX, bottom, minZ,
                minX + size.canvasSizeX() - 1, bottom + size.canvasSizeY() - 1, minZ + size.canvasSizeZ() - 1);

        // The stub's own position is the surface, not the island: vanilla biome-checks the stub, and
        // sampling the biome a hundred blocks up in open sky is not the biome the island belongs to.
        // Upstream 1.20 does the same, anchoring on onTopOfChunkCenter and lifting inside its piece.
        return Optional.of(new GenerationStub(new BlockPos(x, surface, z),
                builder -> builder.addPiece(new SlimeIslandPiece(box))));
    }
}
