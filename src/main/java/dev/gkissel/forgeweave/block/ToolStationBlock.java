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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Tool Station: assembles head + binding + handle parts into a tool (docs/SCOPE.md M1 issue
 * #10; CONTEXT.md glossary). Same saved-inventory block-entity shape as {@link PartBuilderBlock}
 * -- see that class's javadoc for the upstream 1.12 rationale ({@code TileToolStation} is likewise
 * a persistent-inventory tile, not a transient crafting-table container).
 */
public class ToolStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ToolStationBlock> CODEC = simpleCodec(ToolStationBlock::new);

    public ToolStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends ToolStationBlock> codec() {
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

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ToolStationBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ToolStationBlockEntity toolStation) {
            player.openMenu(toolStation);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof ToolStationBlockEntity toolStation) {
                Containers.dropContents(level, pos, toolStation.container());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
