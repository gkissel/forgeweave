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

import net.neoforged.neoforge.items.IItemHandler;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * The Part Builder: a horizontal-facing block whose GUI turns a part pattern plus material items
 * into the matching part (docs/SCOPE.md M1 issue #9; CONTEXT.md invariant: "part crafting always
 * goes through a Station"). Holds a persistent 3-slot inventory in its block entity, matching
 * upstream 1.12's saved-inventory `TilePartBuilder` rather than a transient crafting-table-style
 * container: contents survive re-opening the GUI, and spill into the world (not preserved onto the
 * mined block) when the block is broken, same as upstream's `BlockToolTable#keepInventory()`
 * returning {@code false} for the part builder variant. No NOTICE.md row for this fact -- it's a
 * design decision read from upstream's source, not copied code or assets.
 *
 * <p>Table-shaped (tabletop + 4 legs, hollow underside) and retains the wood block it was crafted
 * from (issue #43): the {@code TABLE_SHAPE} collision box mirrors the 1.20.1 reference clone's
 * {@code shared.block.TableBlock} ("top + 4 legs" boxes via {@code Shapes.or().optimize()}, adjusted
 * to this block's shorter legs -- NOTICE.md), and {@link #setPlacedBy}/{@link #getCloneItemStack}
 * move the {@link ForgeweaveDataComponents#TEXTURE} component between the crafted item and the
 * placed block entity the same way Mantle's real (out-of-clone) {@code RetexturedBlock} does.
 */
public class PartBuilderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<PartBuilderBlock> CODEC = simpleCodec(PartBuilderBlock::new);

    private static final VoxelShape TABLE_SHAPE = Shapes.or(
            Block.box(0.0D, 12.0D, 0.0D, 16.0D, 16.0D, 16.0D), // top
            Block.box(0.0D, 0.0D, 0.0D, 4.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 0.0D, 16.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 12.0D, 16.0D, 12.0D, 16.0D), // leg
            Block.box(0.0D, 0.0D, 12.0D, 4.0D, 12.0D, 16.0D)).optimize(); // leg

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
        return new PartBuilderBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof PartBuilderBlockEntity partBuilder) {
            ResourceLocation textureId = stack.get(ForgeweaveDataComponents.TEXTURE.get());
            if (textureId != null && BuiltInRegistries.BLOCK.containsKey(textureId)) {
                partBuilder.setTexture(BuiltInRegistries.BLOCK.get(textureId));
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof PartBuilderBlockEntity partBuilder) {
            stack.set(ForgeweaveDataComponents.TEXTURE.get(), BuiltInRegistries.BLOCK.getKey(partBuilder.getTexture()));
        }
        return stack;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PartBuilderBlockEntity partBuilder) {
            // The client-side menu needs the side-inventory slot count before it can construct
            // matching Slot objects (issue #40's follow-up, same pattern as CraftingStationBlock).
            IItemHandler sideInventory = partBuilder.findSideInventory();
            int sideInventorySlots = sideInventory == null ? 0 : sideInventory.getSlots();
            player.openMenu(partBuilder, buf -> buf.writeVarInt(sideInventorySlots));
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
