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
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
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
 * The magma slime island: upstream 1.12's {@code MagmaSlimeIslandGenerator} (NOTICE.md), as a
 * structure (issue #450, parity audit T19).
 *
 * <p>Upstream's magma generator is {@code SlimeIslandGenerator} with four things swapped: its own
 * palette (see {@link SlimeIslandShape#magmaPalette()}), its own rate, a Nether-only dimension gate,
 * and a fixed altitude. Everything else -- the per-chunk spot pick, the island geometry, the
 * structure/piece split, the grid-thinned rate -- it inherits, and so does this: the class is
 * {@link SlimeIslandStructure} with those four swaps, and the island itself is drawn by the same
 * {@link SlimeIslandShape} through {@link MagmaSlimeIslandPiece}.
 *
 * <p>The altitude is the interesting swap. An overworld island is dropped in the sky 61-110 blocks
 * above the terrain; a Nether island is floated <em>on the lava sea</em>. Upstream probes
 * {@code y = 31} and its four horizontal neighbours for lava and, if all five are lava, puts the
 * island's top surface at {@code y = 32}. Modern Minecraft still generates the Nether with a lava
 * sea level of 32 (its noise settings' {@code sea_level}, so the topmost lava block is 31), which is
 * why both numbers survive the port unchanged. What changes is only where the five probes read
 * from: a structure is placed before any chunk exists, so they come off
 * {@link ChunkGenerator#getBaseColumn} -- the generator's own answer for "what block will be here"
 * -- instead of {@code World#getBlockState}.
 *
 * <p>Upstream's magma generator asks three gates rather than its parent's four: {@code
 * genSlimeIslands}, the superflat exemption, and {@code world.provider instanceof WorldProviderHell}.
 * It deliberately skips both the dimension blacklist and the surface-world rule, whose defaults
 * would each rule the Nether out. Only the third changes shape: the Nether is a biome tag now, so
 * {@code #forgeweave:has_structure/magma_slime_island} is what keeps magma islands out of everywhere
 * else -- and, being a tag, a datapack can move them.
 */
public class MagmaSlimeIslandStructure extends Structure {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Forgeweave.MODID);

    public static final MapCodec<MagmaSlimeIslandStructure> CODEC = simpleCodec(MagmaSlimeIslandStructure::new);

    public static final DeferredHolder<StructureType<?>, StructureType<MagmaSlimeIslandStructure>> TYPE =
            STRUCTURE_TYPES.register("magma_slime_island", () -> () -> CODEC);

    /** The datapack structure this class backs, {@code data/forgeweave/worldgen/structure/magma_slime_island.json}. */
    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "magma_slime_island"));

    /** Upstream: {@code int y = 31; // lava lake surface is at 32}. */
    public static final int LAVA_PROBE_Y = 31;

    public MagmaSlimeIslandStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return TYPE.get();
    }

    /**
     * Whether a magma island may start in this level at all -- upstream's first two
     * {@code MagmaSlimeIslandGenerator#generate} gates. The third, its Nether-only check, is the
     * structure's biome tag.
     */
    public static boolean enabledIn(ServerLevel level, boolean superflat) {
        if (!ForgeweaveConfig.GEN_SLIME_ISLANDS.get()) {
            return false;
        }
        return !superflat || ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.get();
    }

    /** See {@code SlimeIslandStructure#levelOf} -- a generation context does not carry its dimension. */
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

    /** Upstream's gates in its own order: the two dimension ones, then {@code magmaIslandRate}. */
    private static boolean allowed(GenerationContext context) {
        ChunkGenerator generator = context.chunkGenerator();
        ServerLevel level = levelOf(generator);
        if (level != null && !enabledIn(level, generator instanceof FlatLevelSource)) {
            return false;
        }
        if (level == null && !ForgeweaveConfig.GEN_SLIME_ISLANDS.get()) {
            return false;
        }
        int rate = ForgeweaveConfig.MAGMA_ISLAND_RATE.get();
        if (rate <= 0) {
            return false;
        }
        return rate <= SlimeIslandStructure.GRID_DENSITY
                || context.random().nextFloat() < (float) SlimeIslandStructure.GRID_DENSITY / rate;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!allowed(context)) {
            return Optional.empty();
        }

        WorldgenRandom random = context.random();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heights = context.heightAccessor();
        RandomState randomState = context.randomState();

        int x = context.chunkPos().getMinBlockX() + 4 + random.nextInt(8);
        int z = context.chunkPos().getMinBlockZ() + 4 + random.nextInt(8);
        if (!isLava(generator, heights, randomState, x, z)
                || !isLava(generator, heights, randomState, x, z - 1)
                || !isLava(generator, heights, randomState, x + 1, z)
                || !isLava(generator, heights, randomState, x, z + 1)
                || !isLava(generator, heights, randomState, x - 1, z)) {
            return Optional.empty();
        }

        SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(random);

        int islandTop = LAVA_PROBE_Y + 1;
        int bottom = islandTop - size.yRange();
        if (bottom <= heights.getMinBuildHeight()) {
            return Optional.empty();
        }

        int pad = SlimeIslandShape.Size.canvasPad();
        int minX = x - size.xRange() / 2 - pad;
        int minZ = z - size.zRange() / 2 - pad;
        BoundingBox box = new BoundingBox(minX, bottom, minZ,
                minX + size.canvasSizeX() - 1, bottom + size.canvasSizeY() - 1, minZ + size.canvasSizeZ() - 1);

        return Optional.of(new GenerationStub(new BlockPos(x, islandTop, z),
                builder -> builder.addPiece(new MagmaSlimeIslandPiece(box))));
    }

    /** Upstream {@code MagmaSlimeIslandGenerator#isLava}, asked of the generator instead of the world. */
    private static boolean isLava(ChunkGenerator generator, LevelHeightAccessor heights, RandomState randomState,
            int x, int z) {
        NoiseColumn column = generator.getBaseColumn(x, z, heights, randomState);
        return column.getBlock(LAVA_PROBE_Y).is(Blocks.LAVA);
    }
}
