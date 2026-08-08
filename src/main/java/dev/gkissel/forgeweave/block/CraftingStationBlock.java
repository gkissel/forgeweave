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
 * The Crafting Station: a 3x3 crafting bench whose grid persists across GUI close (docs/SCOPE.md M1
 * issue #40; CONTEXT.md invariant list has no station-specific carve-out, so this behaves exactly
 * like upstream 1.12's {@code TileCraftingStation} -- a saved-inventory tile, not vanilla's
 * transient crafting-table container -- see {@link CraftingStationBlockEntity}) and otherwise
 * crafts with the real global recipe manager, so it accepts any registered {@code CraftingRecipe}
 * (vanilla or modded) exactly like a vanilla crafting table.
 *
 * <p>Table-shaped and wood-retexturing the same way as {@link PartBuilderBlock}/{@link
 * ToolStationBlock} (issue #43's machinery, reused verbatim per issue #40's brief) -- see {@code
 * PartBuilderBlock}'s javadoc for the {@code TABLE_SHAPE}/texture-component rationale. Unlike those
 * two, this block's crafting recipe has no wood-tagged ingredient (see {@code
 * ForgeweaveRecipeProvider}), so a crafted Crafting Station always ends up textured with whatever
 * {@link ForgeweaveDataComponents#TEXTURE} its sole non-pattern ingredient (a vanilla crafting
 * table) resolves to -- the machinery is still fully wired in (creative-tab items and future
 * recipes/dyes could vary it), it just has one practical outcome today.
 */
public class CraftingStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CraftingStationBlock> CODEC = simpleCodec(CraftingStationBlock::new);

    private static final VoxelShape TABLE_SHAPE = Shapes.or(
            Block.box(0.0D, 12.0D, 0.0D, 16.0D, 16.0D, 16.0D), // top
            Block.box(0.0D, 0.0D, 0.0D, 4.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 0.0D, 16.0D, 12.0D, 4.0D), // leg
            Block.box(12.0D, 0.0D, 12.0D, 16.0D, 12.0D, 16.0D), // leg
            Block.box(0.0D, 0.0D, 12.0D, 4.0D, 12.0D, 16.0D)).optimize(); // leg

    public CraftingStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends CraftingStationBlock> codec() {
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
        return new CraftingStationBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof CraftingStationBlockEntity craftingStation) {
            ResourceLocation textureId = stack.get(ForgeweaveDataComponents.TEXTURE.get());
            if (textureId != null && BuiltInRegistries.BLOCK.containsKey(textureId)) {
                craftingStation.setTexture(BuiltInRegistries.BLOCK.get(textureId));
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof CraftingStationBlockEntity craftingStation) {
            stack.set(ForgeweaveDataComponents.TEXTURE.get(), BuiltInRegistries.BLOCK.getKey(craftingStation.getTexture()));
        }
        return stack;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CraftingStationBlockEntity craftingStation) {
            // The client-side menu needs to know how many side-inventory slots to build (issue #40:
            // an adjacent item-handler block's inventory shows in a side panel) before it can even
            // construct matching Slot objects, so that count rides along on the open-menu packet --
            // see ForgeweaveMenus's registration and CraftingStationMenu's buf-reading constructor.
            IItemHandler sideInventory = craftingStation.findSideInventory();
            int sideInventorySlots = sideInventory == null ? 0 : sideInventory.getSlots();
            player.openMenu(craftingStation, buf -> buf.writeVarInt(sideInventorySlots));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos) instanceof CraftingStationBlockEntity craftingStation) {
                Containers.dropContents(level, pos, craftingStation.container());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
