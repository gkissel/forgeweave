package dev.gkissel.forgeweave.block;

import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The faucet block (docs/SCOPE.md M2 issue #100), ported from upstream 1.12's {@code BlockFaucet}
 * (NOTICE.md).
 *
 * <p>{@link #FACING} points at the <em>input</em> -- the drain or tank the faucet draws from -- and
 * can be any side but down, because down is always the output. Right-clicking starts or stops a
 * pour; a rising redstone edge starts one too, on upstream's two-tick delay.
 */
public class FaucetBlock extends Block implements EntityBlock {
    public static final MapCodec<FaucetBlock> CODEC = simpleCodec(FaucetBlock::new);

    /** Upstream's {@code BlockFaucet.FACING}: the side the fluid comes from, never {@code DOWN}. */
    public static final DirectionProperty FACING = DirectionProperty.create("facing",
            direction -> direction != Direction.DOWN);

    // Upstream's BOUNDS map, in sixteenths.
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP, Block.box(4, 10, 4, 12, 16, 12),
            Direction.NORTH, Block.box(4, 4, 0, 12, 10, 6),
            Direction.SOUTH, Block.box(4, 4, 10, 12, 10, 16),
            Direction.EAST, Block.box(10, 4, 4, 16, 10, 12),
            Direction.WEST, Block.box(0, 4, 4, 6, 10, 12));

    public FaucetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends FaucetBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Upstream's {@code getStateForPlacement}: face the block clicked, falling back to the player's facing. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attached = context.getClickedFace().getOpposite();
        if (attached == Direction.DOWN) {
            attached = context.getHorizontalDirection().getOpposite();
        }
        return defaultBlockState().setValue(FACING, attached);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FaucetBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet) {
            faucet.activate();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Upstream {@code neighborChanged}: a rising redstone edge schedules an {@code activate} two ticks out. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet) {
            faucet.handleRedstone(level.hasNeighborSignal(pos));
        }
    }

    /**
     * The faucet's only tick source: either the redstone pulse above or the next step of a pour in
     * progress. A faucet that is already pouring treats the tick as a pour step, so a redstone pulse
     * mid-pour cannot double up.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet) {
            if (faucet.isPouring()) {
                faucet.pourStep();
            } else {
                faucet.activate();
            }
        }
    }
}
