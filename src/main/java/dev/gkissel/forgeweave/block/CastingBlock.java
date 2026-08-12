package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.gkissel.forgeweave.casting.CastingRecipe;

/**
 * The Casting Table and Casting Basin blocks (docs/SCOPE.md M2 issue #100), ported from upstream
 * 1.12's {@code BlockCasting} (NOTICE.md) -- one class per station, where upstream uses one block
 * with a {@code type} property, the same split issue #93 made for the seared brick variants.
 *
 * <p>Right-clicking puts an item in or takes one out ({@link CastingBlockEntity#interact}); a faucet
 * pours into it through the fluid-handler capability. There is no GUI: casting is an in-world
 * interaction upstream and stays one here.
 *
 * <p>The cooling delay runs on {@link #tick}, a vanilla scheduled block tick booked by the fill that
 * tops the block up -- see {@link CastingBlockEntity} for why there is no block-entity ticker.
 *
 * <p>The held item and the fluid pooling over it are drawn by {@code CastingBlockEntityRenderer}
 * (#182), off the slots and tank this block entity already syncs; upstream instead routes the items
 * through an extended-blockstate table model and only the fluid through a renderer.
 */
public class CastingBlock extends Block implements EntityBlock {
    // Upstream's BOUNDS_Table / BOUNDS_Basin, in sixteenths: a slab on four short legs, the basin's
    // slab thicker and its legs stubbier.
    private static final VoxelShape TABLE_SHAPE = Shapes.or(
            Block.box(0, 10, 0, 16, 16, 16),
            Block.box(0, 0, 0, 4, 10, 4),
            Block.box(12, 0, 0, 16, 10, 4),
            Block.box(12, 0, 12, 16, 10, 16),
            Block.box(0, 0, 12, 4, 10, 16));

    private static final VoxelShape BASIN_SHAPE = Shapes.or(
            Block.box(0, 4, 0, 16, 16, 16),
            Block.box(0, 0, 0, 5, 4, 5),
            Block.box(11, 0, 0, 16, 4, 5),
            Block.box(11, 0, 11, 16, 4, 16),
            Block.box(0, 0, 11, 5, 4, 16));

    private final CastingRecipe.Station station;
    private final MapCodec<CastingBlock> codec;

    public CastingBlock(Properties properties, CastingRecipe.Station station) {
        super(properties);
        this.station = station;
        this.codec = simpleCodec(p -> new CastingBlock(p, station));
    }

    @Override
    protected MapCodec<? extends CastingBlock> codec() {
        return codec;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingBlockEntity(pos, state, station);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return station == CastingRecipe.Station.TABLE ? TABLE_SHAPE : BASIN_SHAPE;
    }

    /**
     * Upstream's {@code IFaucetDepth#getFlowDepth}: how far below its own top a faucet draws its
     * stream sinking into this block. A table's pool is a sheet on the surface, a basin's is deep.
     * Read by {@code FaucetBlockEntityRenderer} (#182); upstream declares it through a one-method
     * interface, which for two constants in one class buys nothing here.
     */
    public float flowDepth() {
        return station == CastingRecipe.Station.TABLE ? 0.125f : 0.725f;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CastingBlockEntity casting) {
            casting.interact(player, hand);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CastingBlockEntity casting) {
            casting.interact(player, InteractionHand.MAIN_HAND);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The scheduled tick a full pour books: the cooling time is up, so the recipe finishes. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CastingBlockEntity casting) {
            casting.finishCasting();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CastingBlockEntity casting) {
            casting.dropContents();
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CastingBlockEntity casting ? casting.comparatorStrength() : 0;
    }
}
