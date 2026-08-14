package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66), sharing one class parameterized by
 * {@link ChestKind} (see that enum's javadoc). Issue #66 shipped both as plain facing-aware cubes;
 * issue #342 replaced that with upstream 1.12's real cabinet geometry ({@code
 * models/block/patternchest.json}, transcribed into {@code models/block/chest.json}, NOTICE.md), so
 * {@link #CHEST_SHAPE} below is upstream's own {@code BlockToolTable.BOUNDS_Chest} multi-box hitbox
 * converted to a {@link VoxelShape}: the top slab, the inset body, and the four legs. Its boxes are
 * symmetric under y-rotation, so unlike the model it needs no per-{@code FACING} variant (upstream's
 * drawer fronts stand outside the block's own 1x1 footprint and are not part of the hitbox there
 * either). {@code ChestScreen} carries the GUI-simplification note.
 */
public class ChestBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape CHEST_SHAPE = Shapes.or(
            Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D), // top slab
            Block.box(1.0D, 3.0D, 1.0D, 15.0D, 16.0D, 15.0D), // body
            Block.box(0.5D, 0.0D, 0.5D, 2.5D, 12.0D, 2.5D), // leg
            Block.box(13.5D, 0.0D, 0.5D, 15.5D, 12.0D, 2.5D), // leg
            Block.box(13.5D, 0.0D, 13.5D, 15.5D, 12.0D, 15.5D), // leg
            Block.box(0.5D, 0.0D, 13.5D, 2.5D, 12.0D, 15.5D)).optimize(); // leg

    private final ChestKind kind;
    private final MapCodec<ChestBlock> codec;

    public ChestBlock(Properties properties, ChestKind kind) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new ChestBlock(p, kind));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends ChestBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CHEST_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChestBlockEntity(pos, state, kind);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.open(player); // carries the station-group tab row (issue #78)
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                Containers.dropContents(level, pos, chest.container());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
