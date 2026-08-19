package dev.gkissel.forgeweave.block;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Slime grass: upstream 1.12's {@code BlockSlimeGrass} (NOTICE.md), the surface layer the slime
 * island generator lays over its dirt shell (issue #449, parity audit T18).
 *
 * <p>Upstream models this as one block with a {@code type} (which dirt is underneath) and a
 * {@code foliage} (which colour the grass is) property, 15 metadata states in all. Modern Minecraft
 * has no block metadata, so each combination would need its own block and block item; Forgeweave
 * registers one grass block per slime dirt colour instead, each pinned to the foliage the island
 * generator actually pairs that dirt with -- green and blue dirt take {@link FoliageType#BLUE},
 * purple dirt takes {@link FoliageType#PURPLE} ({@code SlimeIslandGenerator#generateIslandInChunk}).
 * The recorded consequence is that {@link #randomTick spread} cannot carry a foliage colour onto a
 * differently-coloured dirt the way upstream's can; see the PR body for #449.
 *
 * <p>The two behaviours that make it grass rather than a coloured cube are ported whole: the
 * light-gated spread onto neighbouring slime dirt ({@code updateTick}) and the bone-meal burst of
 * tall grass and ferns ({@code grow}). Its drop is its own dirt, which is loot-table-side
 * ({@code ForgeweaveBlockLootSubProvider}), matching upstream's {@code getItemDropped}.
 */
public class SlimeGrassBlock extends Block implements BonemealableBlock {
    private final FoliageType foliage;

    public SlimeGrassBlock(Properties properties, FoliageType foliage) {
        super(properties);
        this.foliage = foliage;
    }

    /** The colour this grass, and anything bone-mealed out of it, is tinted with. */
    public FoliageType foliage() {
        return foliage;
    }

    /**
     * Upstream {@code BlockSlimeGrass#updateTick}: with light 9 or better overhead, four attempts to
     * turn a nearby slime dirt block into its grass, each needing light 4 and a near-transparent
     * block above the target.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) < 9) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
            if (!level.isLoaded(target)) {
                return;
            }
            BlockPos above = target.above();
            if (level.getMaxLocalRawBrightness(above) >= 4 && level.getBlockState(above).getLightBlock(level, above) <= 2) {
                Optional<Block> grass = ForgeweaveBlocks.slimeGrassForDirt(level.getBlockState(target).getBlock());
                grass.ifPresent(block -> level.setBlockAndUpdate(target, block.defaultBlockState()));
            }
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    /**
     * Upstream {@code BlockSlimeGrass#grow}, the 1.12 grass-block bone-meal walk: 128 attempts that
     * drift outwards over slime grass of this same block, each planting a fern one time in eight and
     * tall grass otherwise, both in this block's foliage colour.
     */
    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos start = pos.above();
        for (int i = 0; i < 128; i++) {
            BlockPos cursor = start;
            boolean drifted = true;
            for (int j = 0; j < i / 16; j++) {
                BlockPos next = cursor.offset(random.nextInt(3) - 1,
                        (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                if (level.getBlockState(next.below()).getBlock() != this
                        || level.getBlockState(next).isCollisionShapeFullBlock(level, next)) {
                    drifted = false; // upstream abandons the whole attempt the first time a step lands badly
                    break;
                }
                cursor = next;
            }
            if (!drifted || !level.getBlockState(cursor).isAir()) {
                continue;
            }
            Block plant = random.nextInt(8) == 0
                    ? ForgeweaveBlocks.slimeFern(foliage)
                    : ForgeweaveBlocks.slimeTallGrass(foliage);
            BlockState plantState = plant.defaultBlockState();
            if (plantState.canSurvive(level, cursor)) {
                level.setBlock(cursor, plantState, Block.UPDATE_ALL);
            }
        }
    }
}
