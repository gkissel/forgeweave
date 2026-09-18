package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The energized tank (docs/SCOPE.md M8, D-M8-11; issue #972). See
 * {@link EnergizedTankBlockEntity} for what it does; this class is the two ways a player touches it.
 *
 * <ul>
 *   <li><b>With a fluid container</b> -- a bucket, a tank, anything {@link FluidUtil} understands --
 *       fills or empties the fuel sample, the same interaction {@link SearedTankBlock} has. A fluid
 *       the smeltery cannot burn is refused, so the container comes back full. No screen opens, so a
 *       player topping the sample up never has one in the way.
 *   <li><b>With anything else, an empty hand included</b> -- opens the tank's screen, which is where
 *       the sample gauge, the energy buffer, the heat and cost readouts and the overdrive button
 *       live. Maintainer decision of 2026-09-18 (issue #1018): the tank is a GUI block, and the
 *       overdrive button is a button on that screen rather than a press on the block face.
 * </ul>
 *
 * <p>{@link #OVERDRIVE} is the block state the model reads to show which way the button sits. The
 * authority is the block entity's own saved field, which is what the save-compat fixture pins; this
 * property is kept in step by {@link EnergizedTankBlockEntity#toggleOverdrive()} and is only ever a
 * view of it, the same relationship {@link SmelteryControllerBlock#HOT} has with the fuel.
 *
 * <p>Original Forgeweave design: the 1.12 generation has no energized tank, so neither this block
 * nor its art derives from either clone.
 */
public class EnergizedTankBlock extends Block implements EntityBlock {
    public static final MapCodec<EnergizedTankBlock> CODEC = simpleCodec(EnergizedTankBlock::new);

    public static final BooleanProperty OVERDRIVE = BooleanProperty.create("overdrive");

    public EnergizedTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OVERDRIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OVERDRIVE);
    }

    @Override
    protected MapCodec<? extends EnergizedTankBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergizedTankBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof EnergizedTankBlockEntity tank
                && FluidUtil.interactWithFluidHandler(player, hand, tank.sample())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * Opens the tank's screen. A held fluid container goes to {@link #useItemOn} first and never gets
     * here; anything else, an empty hand included, opens the screen -- a dormant tank too, which
     * still has a sample and a buffer worth reading.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EnergizedTankBlockEntity tank)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide) {
            tank.open(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
