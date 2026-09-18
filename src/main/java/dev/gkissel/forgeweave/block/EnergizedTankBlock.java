package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The energized tank (docs/SCOPE.md M8, D-M8-11; issue #972). See
 * {@link EnergizedTankBlockEntity} for what it does; this class is the two ways a player touches it.
 *
 * <ul>
 *   <li><b>With a fluid container</b> -- a bucket, a tank, anything {@link FluidUtil} understands --
 *       fills or empties the fuel sample, the same interaction {@link SearedTankBlock} has. A fluid
 *       the smeltery cannot burn is refused, so the container comes back full.
 *   <li><b>With an empty hand</b> -- presses the overdrive button, which multiplies both the energy
 *       cost per melt tick and the melt progress per melt tick by their config factors. The button
 *       is literally on the block rather than behind a screen: there is no inventory here to open a
 *       container menu over, and a block whose whole interface is one switch does not need one.
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

    private static final String KEY_OVERDRIVE_ON = "tooltip.forgeweave.energized_tank.overdrive_on";
    private static final String KEY_OVERDRIVE_OFF = "tooltip.forgeweave.energized_tank.overdrive_off";
    private static final String KEY_DISABLED = "tooltip.forgeweave.energized_tank.disabled";

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

    /** The overdrive button. Empty hand only; a held fluid container goes to {@link #useItemOn} first. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EnergizedTankBlockEntity tank)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.ENERGIZED_TANK)) {
            // Still a press, still saved -- the toggle makes the block inert, not read-only.
            player.displayClientMessage(Component.translatable(KEY_DISABLED), true);
        }
        boolean overdrive = tank.toggleOverdrive();
        player.displayClientMessage(Component.translatable(overdrive ? KEY_OVERDRIVE_ON : KEY_OVERDRIVE_OFF), true);
        // Vanilla's own button click, since that is what this is.
        level.playSound(null, pos, overdrive ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.BLOCKS, 0.3F, 0.6F);
        return InteractionResult.CONSUME;
    }
}
