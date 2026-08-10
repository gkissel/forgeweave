package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The seared tank, gauge and window (docs/SCOPE.md M2 issue #95): the fluid-holding wall blocks a
 * smeltery needs at least one of. Ported from upstream 1.12's {@code BlockTank} (NOTICE.md), which
 * is one block with a {@code type} property over the three appearances; Forgeweave registers three
 * blocks sharing this class, the same split issue #93 already made for the seared brick variants.
 *
 * <p>Behaviour is upstream's: right-clicking with a fluid container fills or empties the tank, and
 * the tank drives a comparator over its capacity.
 *
 * <p>ponytail: upstream also renders the fluid level inside the gauge/window and lights the block
 * from a glowing fluid. Both are block-entity rendering, which arrives with the smeltery's own fluid
 * rendering in issue #101 -- and until melting lands (#96) there is no Forgeweave fluid to show.
 */
public class SearedTankBlock extends Block implements EntityBlock {
    public static final MapCodec<SearedTankBlock> CODEC = simpleCodec(SearedTankBlock::new);

    public SearedTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SearedTankBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SearedTankBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank
                && FluidUtil.interactWithFluidHandler(player, hand, tank.tank())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank ? tank.comparatorStrength() : 0;
    }
}
