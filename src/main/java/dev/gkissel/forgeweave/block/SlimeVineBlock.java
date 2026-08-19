package dev.gkissel.forgeweave.block;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * One stage of a slime vine: upstream 1.12's {@code BlockSlimeVine} (NOTICE.md), the vines that hang
 * off a slime tree's canopy (issue #488, parity audit T57).
 *
 * <p>Upstream registers six of these -- three stages per foliage colour, each holding a reference to
 * the next -- and a vine creeps downwards one block at a time, advancing to the next (thinner) stage
 * once it is hanging free. That chain is why the stages are separate blocks here too: the stage is
 * the block, not a state property, exactly as upstream has it.
 *
 * <p>Two behaviours vanilla's {@link VineBlock} cannot give us, both ported from upstream:
 *
 * <ul>
 *   <li>vanilla only lets a vine hang from a vine of the <em>same</em> block, so a {@code _mid}
 *       under a full vine would drop off; {@link #hangsFrom} widens that to any slime vine and,
 *       as upstream's {@code neighborChanged} does, to slime leaves -- which is what a canopy vine
 *       actually hangs from, leaves having no sturdy face of their own;
 *   <li>vanilla's random tick is the creeping, sideways-spreading vanilla vine; {@link #randomTick}
 *       replaces it with upstream's {@code updateTick}/{@code grow} -- a one-in-four roll to extend
 *       straight down, advancing a stage when the column is free-floating.
 * </ul>
 */
public class SlimeVineBlock extends VineBlock {
    public static final MapCodec<VineBlock> CODEC =
            simpleCodec(properties -> new SlimeVineBlock(properties, FoliageType.BLUE, null));

    private final FoliageType foliage;

    /** The thinner stage this one grows into, or {@code null} for the end stage, which never extends. */
    @Nullable
    private final Supplier<Block> nextStage;

    public SlimeVineBlock(Properties properties, FoliageType foliage, @Nullable Supplier<Block> nextStage) {
        super(properties);
        this.foliage = foliage;
        this.nextStage = nextStage;
    }

    /** The colour this vine is tinted with. */
    public FoliageType foliage() {
        return foliage;
    }

    @Override
    public MapCodec<VineBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return hasFaces(updatedState(state, level, pos));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        }
        BlockState updated = updatedState(state, level, pos);
        return hasFaces(updated) ? updated : Blocks.AIR.defaultBlockState();
    }

    /**
     * Vanilla's {@code getUpdatedState} with upstream's extra support rule: a face survives if the
     * block it is stuck to still holds it, or if what is directly above holds it for us.
     */
    private BlockState updatedState(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        BlockState updated = state;
        if (state.getValue(UP)) {
            updated = updated.setValue(UP, isAcceptableNeighbour(level, pos.above(), Direction.DOWN));
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BooleanProperty face = getPropertyForFace(direction);
            if (!state.getValue(face)) {
                continue;
            }
            boolean supported = isAcceptableNeighbour(level, pos.relative(direction), direction)
                    || hangsFrom(above, face);
            updated = updated.setValue(face, supported);
        }
        return updated;
    }

    /**
     * Upstream {@code neighborChanged}: slime leaves above hold a vine by any face (a canopy vine's
     * only anchor), and a slime vine above holds the face it carries itself, which is what keeps a
     * multi-stage column attached.
     */
    private static boolean hangsFrom(BlockState above, BooleanProperty face) {
        return above.getBlock() instanceof SlimeVineBlock ? above.getValue(face)
                : ForgeweaveBlocks.isSlimeLeaves(above.getBlock());
    }

    private static boolean hasFaces(BlockState state) {
        for (BooleanProperty face : PROPERTY_BY_DIRECTION.values()) {
            if (state.getValue(face)) {
                return true;
            }
        }
        return false;
    }

    /** Upstream {@code updateTick}: one roll in four to creep another block downwards. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            grow(level, random, pos, state);
        }
    }

    /**
     * Upstream {@code grow}: extend into the air below, carrying the same faces. A column that hangs
     * free -- no face of it stuck to anything -- thins out into the next stage, certainly once it is
     * more than two blocks long and on a coin flip before that.
     */
    private void grow(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (nextStage == null) {
            return;
        }
        BlockPos below = pos.below();
        if (!level.isEmptyBlock(below)) {
            return;
        }
        BlockState extension = state;
        if (freeFloating(level, pos, state)) {
            int length = 0;
            while (level.getBlockState(pos.above(length)).is(this)) {
                length++;
            }
            if (length > 2 || random.nextInt(2) == 0) {
                extension = copyFaces(state, nextStage.get().defaultBlockState());
            }
        }
        level.setBlock(below, extension, Block.UPDATE_ALL);
    }

    /** Upstream {@code freeFloating}: no horizontal face of this vine is stuck to anything. */
    private static boolean freeFloating(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (state.getValue(getPropertyForFace(direction))
                    && isAcceptableNeighbour(level, pos.relative(direction), direction)) {
                return false;
            }
        }
        return true;
    }

    private static BlockState copyFaces(BlockState from, BlockState to) {
        BlockState copied = to;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BooleanProperty face = getPropertyForFace(direction);
            copied = copied.setValue(face, from.getValue(face));
        }
        return copied;
    }
}
