package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
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
 * The seared duct (docs/SCOPE.md M3.4 issue #277): a drain that only lets one fluid through, ported
 * from the 1.20 clone's {@code SearedDuctBlock} (NOTICE.md). Which fluid is chosen by putting a
 * filled fluid container into the one-slot GUI this block opens; see {@link SearedDuctBlockEntity}.
 *
 * <p>Faces outward like the drain and the core do.
 */
public class SearedDuctBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SearedDuctBlock> CODEC = simpleCodec(SearedDuctBlock::new);

    public SearedDuctBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends SearedDuctBlock> codec() {
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
        return new SearedDuctBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SearedDuctBlockEntity duct) {
            player.openMenu(duct);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The filter container is a real item a player put in; breaking the duct gives it back. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SearedDuctBlockEntity duct) {
            Containers.dropContents(level, pos, new SimpleContainer(duct.filterSlot().getStackInSlot(0)));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
