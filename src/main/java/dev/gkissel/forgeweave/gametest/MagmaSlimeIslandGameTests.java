package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.worldgen.MagmaSlimeIslandPiece;
import dev.gkissel.forgeweave.worldgen.MagmaSlimeIslandStructure;
import dev.gkissel.forgeweave.worldgen.SlimeIslandShape;
import dev.gkissel.forgeweave.worldgen.SlimeIslandStructure;

/**
 * Issue #450 (parity audit T19), the live half of the magma island port: that the structure and its
 * set are registered and reachable by {@code /locate}, that its piece draws upstream's Nether
 * palette, that upstream's two config gates hold, and that the structure carries upstream's magma
 * cube spawn override. The island's shape and palette are pure code and covered by
 * {@code SlimeIslandShapeTest}.
 *
 * <p>The GameTest world is a vanilla superflat overworld with no Nether generation of its own, so
 * there is nowhere in it the lava probe can pass; {@code findGenerationPoint} is therefore exercised
 * through its gates and its piece rather than end to end.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MagmaSlimeIslandGameTests {

    /**
     * The same three things {@code /locate structure forgeweave:magma_slime_island} needs that
     * {@code SlimeIslandGameTests} pins for the overworld island: the datapack JSON resolves against
     * the registered structure type, a structure set carries it, and that set's placement is a
     * random-spread one on the grid the rarity math assumes.
     */
    @GameTest(template = "empty")
    public static void theMagmaIslandStructureIsLocatable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(MagmaSlimeIslandStructure.KEY);
        helper.assertTrue(structure instanceof MagmaSlimeIslandStructure,
                "expected the magma island structure registered as " + MagmaSlimeIslandStructure.KEY.location());

        StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).stream()
                .filter(candidate -> candidate.structures().stream()
                        .anyMatch(entry -> entry.structure().value() == structure))
                .findFirst()
                .orElse(null);
        helper.assertTrue(set != null,
                "no structure set carries forgeweave:magma_slime_island, so /locate can never reach it");
        helper.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement,
                "/locate only walks random-spread placements, so the island's set has to use one");
        helper.assertValueEqual(((RandomSpreadStructurePlacement) set.placement()).spacing(),
                SlimeIslandStructure.GRID_SPACING,
                "the structure set's spacing and the rarity math in MagmaSlimeIslandStructure");

        helper.succeed();
    }

    /**
     * Upstream {@code WorldEvents#extraSlimeSpawn}, the magma half: inside a magma island the
     * monster spawn list is replaced outright with one entry -- magma cubes, weight 150, packs of
     * four to six. Modern Minecraft has that as a structure's own {@code spawn_overrides}, which is
     * where it lives here; upstream's hand-rolled {@code PotentialSpawns} handler needed a saved
     * per-world list of island bounding boxes to answer the same question, and a structure already
     * is one.
     */
    @GameTest(template = "empty")
    public static void magmaIslandsSpawnMagmaCubesAndNothingElse(GameTestHelper helper) {
        Structure structure = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(MagmaSlimeIslandStructure.KEY);
        helper.assertTrue(structure != null, "the magma island structure is not registered");

        StructureSpawnOverride override = structure.spawnOverrides().get(MobCategory.MONSTER);
        helper.assertTrue(override != null, "a magma island must override its own monster spawns");
        helper.assertTrue(override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE,
                "the override has to be scoped to the island itself, not the whole structure chunk");

        List<MobSpawnSettings.SpawnerData> spawns = override.spawns().unwrap();
        helper.assertValueEqual(spawns.size(), 1, "magma island monster spawn entries");
        MobSpawnSettings.SpawnerData magma = spawns.getFirst();
        helper.assertTrue(magma.type == EntityType.MAGMA_CUBE, "a magma island must only spawn magma cubes");
        helper.assertValueEqual(magma.getWeight().asInt(), 150, "magma cube spawn weight");
        helper.assertValueEqual(magma.minCount, 4, "magma cube minimum pack size");
        helper.assertValueEqual(magma.maxCount, 6, "magma cube maximum pack size");

        helper.succeed();
    }

    /**
     * Upstream {@code MagmaSlimeIslandGenerator#generate} asks two of the four questions its
     * overworld parent asks: the dimension blacklist and the surface-world rule are skipped, and
     * their defaults would each rule the Nether out. Synchronous set/assert/restore, because
     * GameTests in one batch tick concurrently and this mutates global config values.
     */
    @GameTest(template = "empty")
    public static void magmaIslandGenerationFollowsTheConfig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        helper.assertTrue(MagmaSlimeIslandStructure.enabledIn(level, false), "magma islands generate by default");
        helper.assertFalse(MagmaSlimeIslandStructure.enabledIn(level, true),
                "magma islands stay out of superflat worlds by default");

        ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(true);
        try {
            helper.assertTrue(MagmaSlimeIslandStructure.enabledIn(level, true),
                    "generateIslandsInSuperflat lets them into a superflat world");
        } finally {
            ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(false);
        }

        ForgeweaveConfig.GEN_SLIME_ISLANDS.set(false);
        try {
            helper.assertFalse(MagmaSlimeIslandStructure.enabledIn(level, false),
                    "generateSlimeIslands off stops magma islands too");
        } finally {
            ForgeweaveConfig.GEN_SLIME_ISLANDS.set(true);
        }

        // Both of the gates upstream skips: the blacklist names the Nether by default, and the
        // Nether is not a surface world, so honouring either would mean no magma islands at all.
        helper.assertTrue(ForgeweaveConfig.SLIME_ISLAND_BLACKLIST.get().contains("minecraft:the_nether"),
                "the blacklist default still names the Nether");
        helper.assertTrue(MagmaSlimeIslandStructure.enabledIn(level, false),
                "the magma island must ignore the blacklist and the surface-world rule");

        helper.succeed();
    }

    /**
     * The magma piece is the overworld piece with upstream's Nether palette: magma slimy dirt and
     * grass, magma congealed slime trunks, an orange canopy, lava under the eroded rim -- and no
     * overworld colour anywhere. Drawn into a real level from nothing but its own bounding box, then
     * taken back out again so nothing is left for the next test in the batch.
     */
    @GameTest(template = "empty")
    public static void theMagmaIslandPieceBuildsANetherIsland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);

        SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(RandomSource.create(1234L));
        int bottom = Math.min(anchor.getY() + 40, level.getMaxBuildHeight() - 1 - size.canvasSizeY());
        int minX = anchor.getX() + 8;
        int minZ = anchor.getZ() + 8;
        BoundingBox box = new BoundingBox(minX, bottom, minZ,
                minX + size.canvasSizeX() - 1, bottom + size.canvasSizeY() - 1, minZ + size.canvasSizeZ() - 1);

        MagmaSlimeIslandPiece piece = new MagmaSlimeIslandPiece(box);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(1234L), box, new ChunkPos(minX >> 4, minZ >> 4), anchor);

        List<BlockPos> written = new ArrayList<>();
        boolean sawGrass = false;
        boolean sawTrunk = false;
        boolean sawLava = false;
        boolean sawOverworldColour = false;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == Blocks.AIR) {
                        continue;
                    }
                    written.add(pos);
                    sawGrass |= block == ForgeweaveBlocks.MAGMA_SLIME_SOIL.grass().get();
                    sawTrunk |= block == ForgeweaveBlocks.MAGMA_CONGEALED_SLIME.get();
                    sawLava |= block == Blocks.LAVA;
                    sawOverworldColour |= block == ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get()
                            || block == ForgeweaveBlocks.GREEN_SLIME_SOIL.dirt().get()
                            || block == ForgeweaveBlocks.BLUE_SLIME_SOIL.dirt().get()
                            || block == ForgeweaveBlocks.PURPLE_SLIME_SOIL.dirt().get();
                }
            }
        }

        try {
            helper.assertTrue(written.size() > 500,
                    "the piece wrote only " + written.size() + " blocks -- that is not an island");
            helper.assertTrue(sawGrass, "the magma island has no magma slimy grass surface");
            helper.assertTrue(sawTrunk, "the magma island grew no magma congealed slime tree trunk");
            helper.assertTrue(sawLava, "the magma island's underside did not erode into lava");
            helper.assertFalse(sawOverworldColour, "an overworld island block turned up on a magma island");
        } finally {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : written) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }

        helper.succeed();
    }
}
