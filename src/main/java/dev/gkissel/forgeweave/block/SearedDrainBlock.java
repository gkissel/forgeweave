package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * The seared drain (docs/SCOPE.md M2 issue #95), ported from upstream 1.12's {@code BlockSmelteryIO}
 * / {@code TileDrain} (NOTICE.md): a wall block that exposes the smeltery's own tank to whatever is
 * next to it, so faucets and pipes can pull molten metal out of the structure (the faucet itself is
 * issue #100). Faces outward like the core does.
 *
 * <p>Right-clicking it with a filled container tips that container into the smeltery, and
 * right-clicking with any fluid container at all is swallowed so a bucket cannot place its fluid
 * against the wall instead -- both straight out of upstream's {@code BlockSmelteryIO#onBlockActivated}
 * (issue #604).
 */
public class SearedDrainBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SearedDrainBlock> CODEC = simpleCodec(SearedDrainBlock::new);

    public SearedDrainBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends SearedDrainBlock> codec() {
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
        return new SearedDrainBlockEntity(pos, state);
    }

    /**
     * Upstream's {@code BlockSmelteryIO#onBlockActivated}, one for one: a held container is
     * <em>emptied into</em> the smeltery, and nothing is ever drawn out of it by hand -- the melt is
     * poured through a faucet, and the only thing a bucket can be filled from directly is a seared
     * tank ({@link SearedTankBlock}, which uses the two-way {@code interactWithFluidHandler}).
     *
     * <p>The trailing check is upstream's too: holding any fluid container over a drain swallows the
     * click even when nothing moved, so a water bucket places no water block against the wall.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        IFluidHandler smeltery = level.getBlockEntity(pos) instanceof SearedDrainBlockEntity drain
                ? drain.fluidHandler()
                : null;
        if (smeltery == null) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (FluidUtil.getFluidHandler(stack).isEmpty()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide) {
            FluidActionResult emptied = FluidUtil.tryEmptyContainerAndStow(stack, smeltery,
                    new PlayerMainInvWrapper(player.getInventory()), FluidType.BUCKET_VOLUME, player, true);
            if (emptied.isSuccess()) {
                player.setItemInHand(hand, emptied.getResult());
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
