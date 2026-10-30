package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

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
    public SearedDrainBlock(Properties properties) {
        super(properties);
        registerDefaultState(SearedTier.standard(defaultBlockState().setValue(FACING, Direction.NORTH)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SearedTier.TIER);
    }

    /** The tier wave's own tick -- see {@link SearedTier#flip}. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SearedTier.flip(state, level, pos);
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        ResourceHandler<FluidResource> smeltery = level.getBlockEntity(pos) instanceof SearedDrainBlockEntity drain
                ? drain.fluidHandler()
                : null;
        if (smeltery == null) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        // The held container, one item of the stack at a time, with whatever it turns into stowed in the
        // player's inventory -- and left alone in creative -- as the removed tryEmptyContainerAndStow did.
        ResourceHandler<FluidResource> held = ItemAccess.forPlayerInteraction(player, hand).oneByOne()
                .getCapability(Capabilities.Fluid.ITEM);
        if (held == null) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide()) {
            ResourceStack<FluidResource> emptied =
                    ResourceHandlerUtil.moveFirst(held, smeltery, fluid -> true, FluidType.BUCKET_VOLUME, null);
            if (emptied != null) {
                SoundEvent sound = emptied.resource().getFluidType()
                        .getSound(emptied.resource().toStack(emptied.amount()), SoundActions.BUCKET_EMPTY);
                if (sound != null) {
                    level.playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), sound, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
