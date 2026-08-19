package dev.gkissel.forgeweave.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The slime island: upstream 1.12's {@code SlimeIslandGenerator} (NOTICE.md), ported as a world
 * generation feature (issue #449, parity audit T18).
 *
 * <p>Upstream is an {@code IWorldGenerator} that Forge calls once per chunk after generation, so it
 * has to solve two problems modern Minecraft solves for it. The first is deciding whether a chunk
 * gets an island and where -- that is a placement question, and lives in {@link
 * SlimeIslandPlacement} so the four upstream config options keep the seam they need. The second is
 * that a 1.12 world generator can be handed a chunk whose neighbours are not loaded yet, which is
 * why upstream keeps a per-world {@code SlimeIslandData} of chunks it still owes an island and
 * replays them later. Modern chunk generation stages features against a guaranteed-writable 3x3
 * chunk area, so that queue has no counterpart here and no saved data of its own is written -- the
 * same conclusion upstream's own 1.20 generation reached when it moved islands onto structures.
 *
 * <p>The island's shape, palette, plants and trees are all {@link SlimeIslandShape}; this class only
 * picks the spot upstream's {@code generateIslandInChunk} picks and blits the result.
 */
public class SlimeIslandFeature extends Feature<NoneFeatureConfiguration> {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Forgeweave.MODID);

    public static final DeferredHolder<Feature<?>, SlimeIslandFeature> SLIME_ISLAND =
            FEATURES.register("slime_island", SlimeIslandFeature::new);

    public SlimeIslandFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        var random = context.random();
        BlockPos chunkOrigin = context.origin();

        // Upstream generateIslandInChunk: a spot in the middle half of the chunk, 61 to 110 blocks
        // above the surface below it.
        int x = chunkOrigin.getX() + 4 + random.nextInt(8);
        int z = chunkOrigin.getZ() + 4 + random.nextInt(8);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int islandTop = surface + 50 + random.nextInt(50) + 11;

        SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(random);
        SlimeIslandShape.Palette palette = SlimeIslandShape.roll(random);

        // Upstream generates against 1.12's fixed 0..255 column and never clamps; a 1.21 dimension
        // can be shorter than the surface height plus an island plus its canopies, so the island is
        // pulled down to fit rather than silently losing its treetops to the build ceiling.
        int ceiling = level.getMaxBuildHeight() - 1;
        islandTop = Math.min(islandTop, ceiling - size.canvasTop() + size.yRange());
        int bottom = islandTop - size.yRange();
        if (bottom <= level.getMinBuildHeight()) {
            return false;
        }

        SlimeIslandShape.Canvas canvas = SlimeIslandShape.Canvas.forIsland(size);
        SlimeIslandShape.generate(random, canvas, size, palette);

        BlockPos start = new BlockPos(x - size.xRange() / 2, bottom, z - size.zRange() / 2);
        canvas.forEachDrawn(drawn -> level.setBlock(start.offset(drawn.pos()), drawn.state(), Block.UPDATE_CLIENTS));
        return true;
    }
}
