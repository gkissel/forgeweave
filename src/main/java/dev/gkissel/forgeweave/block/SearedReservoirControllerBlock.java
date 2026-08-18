package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The seared reservoir controller (parity audit T44, issue #475), upstream 1.12's
 * {@code BlockTinkerTankController} over {@code BlockMultiblockController} (NOTICE.md): a
 * horizontally-facing block whose {@code FACING} points out of the structure, and an {@code active}
 * property swapping the front texture once the structure forms.
 *
 * <p>Unlike the smeltery core and the seared furnace controller this block emits no light and no
 * particles while active -- upstream's {@code BlockTinkerTankController} sets neither, because a
 * reservoir holds no fire. It also never ticks: there is nothing to burn or melt, so a scan on
 * placement, on a neighbour change and on use is the whole life cycle.
 */
public class SearedReservoirControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty ACTIVE = SmelteryControllerBlock.ACTIVE;
    private static final MapCodec<SearedReservoirControllerBlock> CODEC = simpleCodec(SearedReservoirControllerBlock::new);

    public SearedReservoirControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends SearedReservoirControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SearedReservoirBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            updateStructure(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
        updateStructure(level, pos);
    }

    /** Rescans, then opens the reservoir GUI or -- when nothing formed -- says why in chat, as the cores do. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SearedReservoirBlockEntity reservoir) {
            reservoir.updateStructure();
            if (reservoir.isFormed()) {
                reservoir.open(player);
            } else {
                player.displayClientMessage(reservoir.lastResult(), false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void updateStructure(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SearedReservoirBlockEntity reservoir) {
            reservoir.updateStructure();
        }
    }
}
