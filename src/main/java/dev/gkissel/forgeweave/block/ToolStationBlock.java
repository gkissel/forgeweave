package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * The Tool Station: assembles head + binding + handle parts into a tool (docs/SCOPE.md M1 issue
 * #10; CONTEXT.md glossary). Same saved-inventory block-entity shape as {@link PartBuilderBlock}
 * -- see that class's javadoc for the upstream 1.12 rationale ({@code TileToolStation} is likewise
 * a persistent-inventory tile, not a transient crafting-table container).
 *
 * <p>Table-shaped and wood-retexturing the same way as {@link PartBuilderBlock} -- see that
 * class's javadoc for the {@code TABLE_SHAPE}/texture-component rationale (issue #43).
 */
public class ToolStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ToolStationBlock> CODEC = simpleCodec(ToolStationBlock::new);

    private static final VoxelShape TABLE_SHAPE = Shapes.or(
            Block.box(0.0D, 12.0D, 0.0D, 16.0D, 16.0D, 16.0D), // top
            Block.box(0.0D, 0.0D, 0.0D, 4.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 0.0D, 16.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 12.0D, 16.0D, 12.0D, 16.0D), // leg
            Block.box(0.0D, 0.0D, 12.0D, 4.0D, 12.0D, 16.0D)).optimize(); // leg

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
        return new ToolStationBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ToolStationBlockEntity toolStation) {
            ResourceLocation textureId = stack.get(ForgeweaveDataComponents.TEXTURE.get());
            if (textureId != null && BuiltInRegistries.BLOCK.containsKey(textureId)) {
                toolStation.setTexture(BuiltInRegistries.BLOCK.get(textureId));
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof ToolStationBlockEntity toolStation) {
            stack.set(ForgeweaveDataComponents.TEXTURE.get(), BuiltInRegistries.BLOCK.getKey(toolStation.getTexture()));
        }
        return stack;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ToolStationBlockEntity toolStation) {
            // Payload (side-inventory slot count, then the station-group tab row) lives on the block
            // entity so the tab row can open this station too -- see StationMenuHost.
            toolStation.open(player);
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
