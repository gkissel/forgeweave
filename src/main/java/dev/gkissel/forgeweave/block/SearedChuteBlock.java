package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The seared chute (docs/SCOPE.md M3.4 issue #277): the item counterpart of the drain, ported from
 * the 1.20 clone's {@code seared_chute} -- a {@code RetexturedOrientableSmelteryBlock} over
 * {@code SmelteryInputOutputBlockEntity.ChuteBlockEntity} (NOTICE.md).
 *
 * <p>It has no GUI of its own: it re-exposes the smeltery's melting inventory outside the walls, so a
 * hopper or pipe can feed the smeltery and pull items back out. See {@link SearedChuteBlockEntity}.
 */
public class SearedChuteBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SearedChuteBlock> CODEC = simpleCodec(SearedChuteBlock::new);

    public SearedChuteBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends SearedChuteBlock> codec() {
        return CODEC;
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
        return new SearedChuteBlockEntity(pos, state);
    }
}
