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
 * The Part Builder: a horizontal-facing block whose GUI turns a part pattern plus material items
 * into the matching part (docs/SCOPE.md M1 issue #9; CONTEXT.md invariant: "part crafting always
 * goes through a Station"). Holds a persistent 3-slot inventory in its block entity, matching
 * upstream 1.12's saved-inventory `TilePartBuilder` rather than a transient crafting-table-style
 * container: contents survive re-opening the GUI, and spill into the world (not preserved onto the
 * mined block) when the block is broken, same as upstream's `BlockToolTable#keepInventory()`
 * returning {@code false} for the part builder variant. No NOTICE.md row for this fact -- it's a
 * design decision read from upstream's source, not copied code or assets.
 */
public class PartBuilderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<PartBuilderBlock> CODEC = simpleCodec(PartBuilderBlock::new);

    public PartBuilderBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends PartBuilderBlock> codec() {
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
        return new PartBuilderBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PartBuilderBlockEntity partBuilder) {
            player.openMenu(partBuilder);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof PartBuilderBlockEntity partBuilder) {
                Containers.dropContents(level, pos, partBuilder.container());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
