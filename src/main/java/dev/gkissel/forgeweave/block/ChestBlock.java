package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66), sharing one class parameterized by
 * {@link ChestKind} (see that enum's javadoc). Modeled as a plain facing-aware cube rather than
 * upstream's table-with-drawers geometry ({@code models/block/patternchest.json}, NOTICE.md) --
 * that shape is legs + a drawer front/back/handles on top of the same 1x1 footprint every other
 * Forgeweave station uses, and issue #66's own brief blesses this simplification explicitly ("a
 * plain cube with its textures is acceptable for M1"). {@code ChestScreen}/{@code
 * ForgeweaveBlockStateProvider} carry the corresponding texture-derivation and GUI-simplification
 * notes.
 */
public class ChestBlock extends HorizontalDirectionalBlock implements EntityBlock {
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
