package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.worldgen.SlimeIslandFeature;
import dev.gkissel.forgeweave.worldgen.SlimeIslandPlacement;

/**
 * Issue #449 (parity audit T18), the live half of the slime island port -- the parts a running
 * server has to answer. The island's shape is pure code and covered by {@code SlimeIslandShapeTest}
 * instead; what is here is that the feature really writes an island into a level, that the placed
 * feature routes through the config gates, and that the three new block behaviours (grass spread,
 * plant placement, the congealed slime's sunken collision box) do what upstream's do.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SlimeIslandGameTests {

    /**
     * The hand-written placed-feature JSON has to actually resolve against the registered modifier
     * type -- the same "does this JSON still parse" guard {@code NetherOreGameTests} keeps for the
     * ores. Without it a renamed modifier would only be noticed in a live world.
     */
    @GameTest(template = "empty")
    public static void theSlimeIslandPlacedFeatureRoutesThroughTheConfigGates(GameTestHelper helper) {
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "slime_island"));
        PlacedFeature feature = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(key);
        helper.assertTrue(feature != null, "expected a placed feature registered as " + key.location());
        helper.assertTrue(feature.placement().stream().anyMatch(SlimeIslandPlacement.class::isInstance),
                "slime_island must take its generation gates from the config-aware placement modifier");
        helper.succeed();
    }

    /**
     * Upstream's four gates ({@code SlimeIslandGenerator#generate}). Synchronous on purpose: this
     * mutates global config values, and GameTests in one batch tick concurrently, so set/assert/
     * restore has to complete inside a single test method.
     */
    @GameTest(template = "empty")
    public static void slimeIslandGenerationFollowsTheConfig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        helper.assertTrue(SlimeIslandPlacement.enabledIn(level, false), "islands generate by default");
        helper.assertFalse(SlimeIslandPlacement.enabledIn(level, true),
                "islands stay out of superflat worlds by default");

        ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(true);
        try {
            helper.assertTrue(SlimeIslandPlacement.enabledIn(level, true),
                    "generateIslandsInSuperflat lets them into a superflat world");
        } finally {
            ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(false);
        }

        ForgeweaveConfig.GEN_SLIME_ISLANDS.set(false);
        try {
            helper.assertFalse(SlimeIslandPlacement.enabledIn(level, false),
                    "generateSlimeIslands off stops them entirely");
        } finally {
            ForgeweaveConfig.GEN_SLIME_ISLANDS.set(true);
        }

        List<? extends String> blacklist = List.of(level.dimension().location().toString());
        ForgeweaveConfig.SLIME_ISLAND_BLACKLIST.set(blacklist);
        try {
            helper.assertFalse(SlimeIslandPlacement.enabledIn(level, false),
                    "a blacklisted dimension gets no islands");
        } finally {
            ForgeweaveConfig.SLIME_ISLAND_BLACKLIST.set(List.of("minecraft:the_nether", "minecraft:the_end"));
        }

        helper.succeed();
    }

    /**
     * The half {@code SlimeIslandShapeTest} cannot reach: the feature's own chunk-relative placement
     * and the blit out of its buffer into a real level. Runs high above the test structure -- an
     * island is a sky feature and needs the room -- and takes its own blocks back out again, so
     * nothing is left behind for the next test in the batch.
     */
    @GameTest(template = "empty")
    public static void theSlimeIslandFeatureBuildsAnIslandInTheSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);
        BlockPos chunkOrigin = new BlockPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(anchor.getX())),
                0, SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(anchor.getZ())));

        boolean placed = SlimeIslandFeature.SLIME_ISLAND.get().place(new FeaturePlaceContext<>(
                Optional.empty(), level, level.getChunkSource().getGenerator(), RandomSource.create(1234L),
                chunkOrigin, NoneFeatureConfiguration.INSTANCE));
        helper.assertTrue(placed, "the slime island feature refused to place in a flat test world");

        List<BlockPos> islandBlocks = new ArrayList<>();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, chunkOrigin.getX(), chunkOrigin.getZ());
        boolean sawGrass = false;
        boolean sawTrunk = false;
        for (int x = chunkOrigin.getX() - 16; x <= chunkOrigin.getX() + 32; x++) {
            for (int z = chunkOrigin.getZ() - 16; z <= chunkOrigin.getZ() + 32; z++) {
                for (int y = surface + 30; y <= Math.min(surface + 140, level.getMaxBuildHeight() - 1); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (!isSlimeIslandBlock(block)) {
                        continue;
                    }
                    islandBlocks.add(pos);
                    sawGrass |= ForgeweaveBlocks.slimeSoils().stream().anyMatch(soil -> soil.grass().get() == block);
                    sawTrunk |= block == ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get();
                }
            }
        }

        try {
            helper.assertTrue(islandBlocks.size() > 500,
                    "the feature wrote only " + islandBlocks.size() + " blocks -- that is not an island");
            helper.assertTrue(sawGrass, "the island has no grass surface");
            helper.assertTrue(sawTrunk, "the island grew no congealed slime tree trunk");
        } finally {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : islandBlocks) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }

        helper.succeed();
    }

    private static boolean isSlimeIslandBlock(Block block) {
        if (block == ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get() || ForgeweaveBlocks.isSlimeSoil(block)) {
            return true;
        }
        return ForgeweaveBlocks.slimePlants().stream().anyMatch(plants ->
                plants.leaves().get() == block || plants.tallGrass().get() == block || plants.fern().get() == block);
    }

    /**
     * Upstream {@code BlockSlimeGrass#updateTick}: slime grass in light spreads onto neighbouring
     * slime dirt. Forgeweave's reduction is that the dirt's own colour decides which grass it
     * becomes (see {@code SlimeGrassBlock}), which is what this pins.
     */
    @GameTest(template = "empty")
    public static void slimeGrassSpreadsOntoNeighbouringSlimeDirt(GameTestHelper helper) {
        BlockPos grassPos = new BlockPos(1, 1, 1);
        BlockPos dirtPos = new BlockPos(2, 1, 1);
        helper.setBlock(grassPos, ForgeweaveBlocks.GREEN_SLIME_SOIL.grass().get());
        helper.setBlock(dirtPos, ForgeweaveBlocks.PURPLE_SLIME_SOIL.dirt().get());

        ServerLevel level = helper.getLevel();
        BlockPos absoluteGrass = helper.absolutePos(grassPos);
        BlockState grass = level.getBlockState(absoluteGrass);
        for (int i = 0; i < 400; i++) {
            grass.randomTick(level, absoluteGrass, level.random);
            if (level.getBlockState(helper.absolutePos(dirtPos)).getBlock()
                    == ForgeweaveBlocks.PURPLE_SLIME_SOIL.grass().get()) {
                helper.succeed();
                return;
            }
        }
        helper.fail("slime grass never spread onto the slime dirt beside it", dirtPos);
    }

    /** Upstream {@code BlockTallSlimeGrass#canPlaceBlockAt}: slime grass or slime dirt, nothing else. */
    @GameTest(template = "empty")
    public static void slimePlantsOnlyStandOnSlimeSoil(GameTestHelper helper) {
        BlockState plant = ForgeweaveBlocks.BLUE_SLIME_PLANTS.tallGrass().get().defaultBlockState();
        ServerLevel level = helper.getLevel();

        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.GREEN_SLIME_SOIL.grass().get());
        helper.assertTrue(plant.canSurvive(level, helper.absolutePos(new BlockPos(1, 2, 1))),
                "a slime plant must stand on slime grass");

        helper.setBlock(new BlockPos(3, 1, 1), ForgeweaveBlocks.BLUE_SLIME_SOIL.dirt().get());
        helper.assertTrue(plant.canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 1))),
                "a slime plant must stand on slime dirt");

        helper.setBlock(new BlockPos(5, 1, 1), Blocks.DIRT);
        helper.assertFalse(plant.canSurvive(level, helper.absolutePos(new BlockPos(5, 2, 1))),
                "a slime plant must not stand on vanilla dirt");

        helper.succeed();
    }

    /** Upstream {@code BlockSlimeCongealed}: a full-looking cube you sink six sixteenths into. */
    @GameTest(template = "empty")
    public static void congealedSlimeIsSunkenUnderfoot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get());
        BlockPos absolute = helper.absolutePos(pos);
        double top = helper.getLevel().getBlockState(absolute)
                .getCollisionShape(helper.getLevel(), absolute).max(net.minecraft.core.Direction.Axis.Y);
        helper.assertValueEqual((int) Math.round(top * 16), 10, "congealed slime collision height in sixteenths");
        helper.succeed();
    }
}
