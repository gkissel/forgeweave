package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.block.SlimeSaplingBlock;
import dev.gkissel.forgeweave.block.SlimeVineBlock;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.worldgen.SlimeIslandPiece;
import dev.gkissel.forgeweave.worldgen.SlimeIslandShape;
import dev.gkissel.forgeweave.worldgen.SlimeIslandStructure;

/**
 * Issues #449 and #629 (parity audit T18), the live half of the slime island port -- the parts a
 * running server has to answer. The island's shape is pure code and covered by
 * {@code SlimeIslandShapeTest} instead; what is here is that {@code /locate} can actually find the
 * structure, that its piece really writes an island into a level, that generation routes through the
 * config gates, and that the three new block behaviours (grass spread, plant placement, the congealed
 * slime's sunken collision box) do what upstream's do.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SlimeIslandGameTests {

    /**
     * The point of #629: {@code /locate structure forgeweave:slime_island} has to work. Its lookup
     * ({@code ChunkGenerator#findNearestMapStructure}) needs three things, and this pins all three --
     * the datapack structure JSON resolves against the registered structure type, a structure set
     * carries it, and that set's placement is a random-spread one, the only kind the lookup walks.
     * The last block then runs the lookup's own inner call,
     * {@code StructureManager#checkStructurePresence}, over the placement's candidate chunks and
     * requires at least one island among them.
     *
     * <p>Two accommodations for the GameTest world, neither of them about the island. It is a vanilla
     * superflat, whose preset pins {@code structure_overrides} to strongholds and villages, so no mod
     * structure set ever reaches its generator state and {@code findNearestMapStructure} itself cannot
     * be called here -- hence driving the candidate chunks straight off the registered set. And
     * upstream's {@code generateIslandsInSuperflat} defaults to off, so it is turned on around the
     * search; without it, finding nothing would be the correct answer. Synchronous set/assert/restore,
     * like the config test below.
     */
    @GameTest(template = "empty")
    public static void theSlimeIslandStructureIsLocatable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure structure = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(SlimeIslandStructure.KEY);
        helper.assertTrue(structure instanceof SlimeIslandStructure,
                "expected the slime island structure registered as " + SlimeIslandStructure.KEY.location());

        StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).stream()
                .filter(candidate -> candidate.structures().stream()
                        .anyMatch(entry -> entry.structure().value() == structure))
                .findFirst()
                .orElse(null);
        helper.assertTrue(set != null,
                "no structure set carries forgeweave:slime_island, so /locate can never reach it");
        helper.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement,
                "/locate only walks random-spread placements, so the island's set has to use one");

        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        helper.assertValueEqual(placement.spacing(), SlimeIslandStructure.GRID_SPACING,
                "the structure set's spacing and the rarity math in SlimeIslandStructure");

        ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(true);
        try {
            long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
            int found = 0;
            for (int cellX = -6; cellX <= 6; cellX++) {
                for (int cellZ = -6; cellZ <= 6; cellZ++) {
                    ChunkPos candidate = placement.getPotentialStructureChunk(seed,
                            cellX * placement.spacing(), cellZ * placement.spacing());
                    if (level.structureManager().checkStructurePresence(candidate, structure, placement, false)
                            != StructureCheckResult.START_NOT_PRESENT) {
                        found++;
                    }
                }
            }
            helper.assertTrue(found > 0,
                    "the structure lookup /locate uses found no slime island among 169 candidate chunks");
        } finally {
            ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(false);
        }

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

        helper.assertTrue(SlimeIslandStructure.enabledIn(level, false), "islands generate by default");
        helper.assertFalse(SlimeIslandStructure.enabledIn(level, true),
                "islands stay out of superflat worlds by default");

        ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(true);
        try {
            helper.assertTrue(SlimeIslandStructure.enabledIn(level, true),
                    "generateIslandsInSuperflat lets them into a superflat world");
        } finally {
            ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.set(false);
        }

        ForgeweaveConfig.GEN_SLIME_ISLANDS.set(false);
        try {
            helper.assertFalse(SlimeIslandStructure.enabledIn(level, false),
                    "generateSlimeIslands off stops them entirely");
        } finally {
            ForgeweaveConfig.GEN_SLIME_ISLANDS.set(true);
        }

        List<? extends String> blacklist = List.of(level.dimension().location().toString());
        ForgeweaveConfig.SLIME_ISLAND_BLACKLIST.set(blacklist);
        try {
            helper.assertFalse(SlimeIslandStructure.enabledIn(level, false),
                    "a blacklisted dimension gets no islands");
        } finally {
            ForgeweaveConfig.SLIME_ISLAND_BLACKLIST.set(List.of("minecraft:the_nether", "minecraft:the_end"));
        }

        helper.succeed();
    }

    /**
     * The half {@code SlimeIslandShapeTest} cannot reach: that the structure piece recovers the
     * island it was sized for from nothing but its own bounding box, and blits it into a real level.
     * Runs high above the test structure -- an island is a sky feature and needs the room -- and
     * takes its own blocks back out again, so nothing is left behind for the next test in the batch.
     */
    @GameTest(template = "empty")
    public static void theSlimeIslandPieceBuildsAnIslandInTheSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);

        SlimeIslandShape.Size size = SlimeIslandShape.Size.roll(RandomSource.create(1234L));
        int bottom = Math.min(anchor.getY() + 40, level.getMaxBuildHeight() - 1 - size.canvasSizeY());
        int minX = anchor.getX() + 8;
        int minZ = anchor.getZ() + 8;
        BoundingBox box = new BoundingBox(minX, bottom, minZ,
                minX + size.canvasSizeX() - 1, bottom + size.canvasSizeY() - 1, minZ + size.canvasSizeZ() - 1);

        SlimeIslandPiece piece = new SlimeIslandPiece(box);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(1234L), box, new ChunkPos(minX >> 4, minZ >> 4), anchor);

        List<BlockPos> islandBlocks = new ArrayList<>();
        boolean sawGrass = false;
        boolean sawTrunk = false;
        boolean sawLake = false;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = level.getBlockState(pos).getBlock();
                    if (!isSlimeIslandBlock(block)) {
                        continue;
                    }
                    islandBlocks.add(pos);
                    sawGrass |= ForgeweaveBlocks.slimeSoils().stream().anyMatch(soil -> soil.grass().get() == block);
                    sawTrunk |= block == ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get();
                    sawLake |= block == ForgeweaveFluids.BLUE_SLIME.block().get()
                            || block == ForgeweaveFluids.PURPLE_SLIME.block().get();
                }
            }
        }

        try {
            helper.assertTrue(islandBlocks.size() > 500,
                    "the piece wrote only " + islandBlocks.size() + " blocks -- that is not an island");
            helper.assertTrue(sawGrass, "the island has no grass surface");
            helper.assertTrue(sawTrunk, "the island grew no congealed slime tree trunk");
            // #625: the lake's fluid block really lands in a live level, not just in the buffer.
            helper.assertTrue(sawLake, "the island has no slime lake");
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
        // #625 -- the lake: its two fluids, and the congealed slime its rim is laid in.
        if (block == ForgeweaveFluids.BLUE_SLIME.block().get() || block == ForgeweaveFluids.PURPLE_SLIME.block().get()
                || block == ForgeweaveBlocks.BLUE_CONGEALED_SLIME.get()
                || block == ForgeweaveBlocks.PURPLE_CONGEALED_SLIME.get()) {
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

    // ----------------------------------------------------------------------------------------
    // #488 (parity audit T57): the sapling, the tree it grows and the vines that hang off a canopy.

    /** Upstream {@code BlockSlimeSapling#canPlaceBlockAt}: slime grass or slime dirt, nothing else. */
    @GameTest(template = "empty")
    public static void slimeSaplingsOnlyStandOnSlimeSoil(GameTestHelper helper) {
        BlockState sapling = ForgeweaveBlocks.BLUE_SLIME_PLANTS.sapling().get().defaultBlockState();
        ServerLevel level = helper.getLevel();

        helper.setBlock(new BlockPos(1, 1, 1), ForgeweaveBlocks.GREEN_SLIME_SOIL.grass().get());
        helper.assertTrue(sapling.canSurvive(level, helper.absolutePos(new BlockPos(1, 2, 1))),
                "a slime sapling must stand on slime grass");

        helper.setBlock(new BlockPos(3, 1, 1), ForgeweaveBlocks.BLUE_SLIME_SOIL.dirt().get());
        helper.assertTrue(sapling.canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 1))),
                "a slime sapling must stand on slime dirt");

        helper.setBlock(new BlockPos(5, 1, 1), Blocks.DIRT);
        helper.assertFalse(sapling.canSurvive(level, helper.absolutePos(new BlockPos(5, 2, 1))),
                "a slime sapling must not stand on vanilla dirt");

        helper.succeed();
    }

    /**
     * Upstream {@code BlockSlimeSapling#generateTree}: an armed sapling turns into a congealed-slime
     * trunk under a leaf canopy of its own foliage, and -- taking upstream's {@code vine == null}
     * branch -- hangs no vines. Grown well above the test structure and cleaned up afterwards, the
     * same way the island feature's test does, because a tree does not fit inside one.
     */
    @GameTest(template = "empty")
    public static void anArmedSlimeSaplingGrowsAVinelessSlimeTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos soil = helper.absolutePos(BlockPos.ZERO).above(40);
        BlockPos saplingPos = soil.above();
        SlimeSaplingBlock saplingBlock = ForgeweaveBlocks.BLUE_SLIME_PLANTS.sapling().get();
        BlockState sapling = saplingBlock.defaultBlockState().setValue(SlimeSaplingBlock.STAGE, 1);

        level.setBlock(soil, ForgeweaveBlocks.BLUE_SLIME_SOIL.grass().get().defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(saplingPos, sapling, Block.UPDATE_CLIENTS);
        saplingBlock.advanceTree(level, saplingPos, sapling, level.random);

        List<BlockPos> grown = new ArrayList<>();
        int trunk = 0;
        int leaves = 0;
        int vines = 0;
        for (BlockPos pos : BlockPos.betweenClosed(soil.offset(-6, 0, -6), soil.offset(6, 14, 6))) {
            Block block = level.getBlockState(pos).getBlock();
            if (block == ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get()) {
                trunk++;
            } else if (block == ForgeweaveBlocks.BLUE_SLIME_PLANTS.leaves().get()) {
                leaves++;
            } else if (block instanceof SlimeVineBlock) {
                vines++;
            } else {
                continue;
            }
            grown.add(pos.immutable());
        }

        try {
            helper.assertTrue(trunk >= 5, "the sapling grew a trunk of only " + trunk + " congealed slime");
            helper.assertTrue(leaves > trunk, "the sapling grew a trunk with no canopy (" + leaves + " leaves)");
            helper.assertValueEqual(vines, 0, "vines on a hand-planted slime tree");
            helper.assertFalse(level.getBlockState(saplingPos).is(saplingBlock), "the sapling survived its own tree");
        } finally {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : grown) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
            level.setBlock(soil, air, Block.UPDATE_CLIENTS);
            level.setBlock(saplingPos, air, Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }

    /**
     * The one thing vanilla's {@code VineBlock} cannot do and upstream's {@code neighborChanged} can:
     * hold a vine up by the slime leaves above it -- leaves have no sturdy face, so vanilla would
     * drop it -- and hold one stage up by the <em>different</em> stage above it, which is what a
     * three-stage vine column is made of.
     */
    @GameTest(template = "empty")
    public static void slimeVinesHangFromSlimeLeavesAndFromEachOther(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState vine = ForgeweaveBlocks.BLUE_SLIME_PLANTS.vine().get().defaultBlockState()
                .setValue(VineBlock.NORTH, true);
        BlockState mid = ForgeweaveBlocks.BLUE_SLIME_PLANTS.vineMid().get().defaultBlockState()
                .setValue(VineBlock.NORTH, true);

        BlockPos anchor = new BlockPos(1, 3, 1);
        helper.assertFalse(vine.canSurvive(level, helper.absolutePos(anchor)),
                "a vine with nothing above it and nothing to cling to must fall");

        helper.setBlock(anchor.above(), ForgeweaveBlocks.BLUE_SLIME_PLANTS.leaves().get());
        helper.assertTrue(vine.canSurvive(level, helper.absolutePos(anchor)),
                "a vine must hang from the slime leaves above it");

        helper.setBlock(anchor, vine);
        helper.assertTrue(mid.canSurvive(level, helper.absolutePos(anchor.below())),
                "the mid stage must hang from the full stage above it");

        helper.setBlock(new BlockPos(3, 3, 1), Blocks.STONE);
        helper.assertFalse(mid.canSurvive(level, helper.absolutePos(new BlockPos(3, 2, 1))),
                "a vine must not hang from an ordinary block above it");

        helper.succeed();
    }

    /**
     * Upstream {@code BlockSlimeVine#grow}: a vine creeps one block down at a time and, once the
     * column hangs free, thins into the next stage -- certainly past two blocks, on a coin flip
     * before that. The end stage never extends.
     */
    @GameTest(template = "empty")
    public static void slimeVinesCreepDownwardsAndThinOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos leaves = helper.absolutePos(BlockPos.ZERO).above(40);
        BlockPos top = leaves.below();
        List<BlockPos> placed = new ArrayList<>();

        level.setBlock(leaves, ForgeweaveBlocks.BLUE_SLIME_PLANTS.leaves().get().defaultBlockState(),
                Block.UPDATE_CLIENTS);
        placed.add(leaves);
        BlockState vine = ForgeweaveBlocks.BLUE_SLIME_PLANTS.vine().get().defaultBlockState()
                .setValue(VineBlock.NORTH, true);
        level.setBlock(top, vine, Block.UPDATE_CLIENTS);
        placed.add(top);

        try {
            boolean thinned = false;
            for (int tick = 0; tick < 4000 && !thinned; tick++) {
                for (int depth = 0; depth < 8; depth++) {
                    BlockPos pos = top.below(depth);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof SlimeVineBlock) {
                        placed.add(pos);
                        state.randomTick(level, pos, level.random);
                        if (state.is(ForgeweaveBlocks.BLUE_SLIME_PLANTS.vineMid().get())
                                || state.is(ForgeweaveBlocks.BLUE_SLIME_PLANTS.vineEnd().get())) {
                            thinned = true;
                        }
                    }
                }
            }
            helper.assertTrue(level.getBlockState(top.below()).getBlock() instanceof SlimeVineBlock,
                    "the vine never crept downwards");
            helper.assertTrue(thinned, "a free-hanging vine column never thinned into its next stage");
        } finally {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : placed) {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }
        helper.succeed();
    }
}
