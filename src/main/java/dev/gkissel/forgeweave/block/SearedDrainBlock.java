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
 * The seared drain (docs/SCOPE.md M2 issue #95), ported from upstream 1.12's {@code BlockSmelteryIO}
 * / {@code TileDrain} (NOTICE.md): a wall block that exposes the smeltery's own tank to whatever is
 * next to it, so faucets and pipes can pull molten metal out of the structure (the faucet itself is
 * issue #100). Faces outward like the core does.
 */
public class SearedDrainBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SearedDrainBlock> CODEC = simpleCodec(SearedDrainBlock::new);

    public SearedDrainBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends SearedDrainBlock> codec() {
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
        return new SearedDrainBlockEntity(pos, state);
    }
}
