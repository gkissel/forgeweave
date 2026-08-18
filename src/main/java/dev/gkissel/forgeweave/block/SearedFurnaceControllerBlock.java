package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The seared furnace controller (issue #442), upstream 1.12's {@code BlockSearedFurnaceController}
 * over {@code BlockMultiblockController} (NOTICE.md): a horizontally-facing block whose {@code
 * FACING} points out of the structure, an {@code active} property driving the lit front texture
 * and a light level of 15 while active, and its inventory dropped when broken (Mantle's {@code
 * BlockInventory}).
 *
 * <p>Same event-driven, never-ticking shape as {@link SmelteryControllerBlock}: scans on placement,
 * neighbour change and use; a scheduled block tick carries the heating while there is any.
 */
public class SearedFurnaceControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty ACTIVE = SmelteryControllerBlock.ACTIVE;
    private static final MapCodec<SearedFurnaceControllerBlock> CODEC = simpleCodec(SearedFurnaceControllerBlock::new);

    public SearedFurnaceControllerBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(ACTIVE) ? 15 : 0));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends SearedFurnaceControllerBlock> codec() {
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
        return new SearedFurnaceBlockEntity(pos, state);
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

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SearedFurnaceBlockEntity furnace) {
            Containers.dropContents(level, pos, furnace.container());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rescans, then opens the furnace GUI or -- when nothing formed -- says why in chat, as the smeltery cores do. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SearedFurnaceBlockEntity furnace) {
            furnace.updateStructure();
            if (furnace.isFormed()) {
                furnace.open(player);
            } else {
                player.displayClientMessage(furnace.lastResult(), false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof SearedFurnaceBlockEntity furnace)) {
            return;
        }
        boolean heating = furnace.heatTick();
        furnace.sweepInterior();
        if (heating) {
            level.scheduleTick(pos, this, SearedFurnaceBlockEntity.HEAT_INTERVAL_TICKS);
        } else if (furnace.isFormed()) {
            level.scheduleTick(pos, this, SearedFurnaceBlockEntity.SWEEP_INTERVAL_TICKS);
        }
    }

    private static void updateStructure(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SearedFurnaceBlockEntity furnace) {
            furnace.updateStructure();
        }
    }
}
