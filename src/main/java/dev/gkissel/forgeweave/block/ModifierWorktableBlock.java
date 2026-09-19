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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Modifier Worktable (issue #1057): the station that works on the modifiers a tool already
 * carries rather than adding new ones. 1.12 has no counterpart, so the parity target is upstream
 * 1.20's {@code ModifierWorktableBlockEntity} and the block art derives from its own
 * (NOTICE.md).
 *
 * <p>Same four-legged table silhouette every other Forgeweave station has, but it is a stone table
 * rather than a wood one, so it carries no {@link WoodTexturedBlockEntity} retexture: upstream 1.20
 * retextures only this table's legs, off its {@code workstation_rock} tag, and Forgeweave's retexture
 * machinery is wood-shaped throughout ({@code RetexturedShapedRecipe}, {@code
 * WoodTexturedBlockEntity}, both keyed to plank blocks). Ships fixed until there is a reason to grow
 * a stone axis.
 */
public class ModifierWorktableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ModifierWorktableBlock> CODEC = simpleCodec(ModifierWorktableBlock::new);

    /** The shared station table shape: a 4px top plate on four corner legs. */
    private static final VoxelShape TABLE_SHAPE = Shapes.or(
            Block.box(0.0D, 12.0D, 0.0D, 16.0D, 16.0D, 16.0D), // top
            Block.box(0.0D, 0.0D, 0.0D, 4.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 0.0D, 16.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 12.0D, 16.0D, 12.0D, 16.0D), // leg
            Block.box(0.0D, 0.0D, 12.0D, 4.0D, 12.0D, 16.0D)).optimize(); // leg

    public ModifierWorktableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends ModifierWorktableBlock> codec() {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TABLE_SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ModifierWorktableBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ModifierWorktableBlockEntity worktable) {
            worktable.open(player); // carries the station-group tab row (issue #78)
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
                && level.getBlockEntity(pos) instanceof ModifierWorktableBlockEntity worktable) {
            Containers.dropContents(level, pos, worktable.container());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
