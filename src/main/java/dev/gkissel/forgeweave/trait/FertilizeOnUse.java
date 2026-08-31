package dev.gkissel.forgeweave.trait;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Right-click fertilizes a {@link BonemealableBlock} at a durability cost -- ADR-0004's M6
 * utility/economy library batch (issue #829). The kama's crop-harvest right-click
 * ({@code dev.gkissel.forgeweave.tool.CropHarvest}) is the interaction precedent this follows: a
 * per-block right-click hook checked from {@code ToolItem#useOn}'s fallthrough ({@link
 * Trait#useOnBlock}), server side only, with vanilla's own growth-particle level event on success.
 *
 * <p>Deliberately does not call {@code BoneMealItem#applyBonemeal}: that method shrinks the
 * {@code ItemStack} it is handed by one, which is exactly right for a stack of bone meal and exactly
 * wrong for a tool -- a durability-holding stack of count 1, shrunk by one, is gone. {@code chance} is
 * this trait's own success roll, checked in place of (not in addition to) {@link
 * BonemealableBlock#isBonemealSuccess}, so a miss costs the tool nothing and simply falls through to
 * vanilla's own right-click behavior for the block.
 *
 * @param durabilityCost how much durability one successful fertilize costs
 * @param chance the chance a right-click on a valid target succeeds
 */
public record FertilizeOnUse(int durabilityCost, float chance) implements Trait {

    /** Upstream vanilla {@code BoneMealItem#useOn}'s own growth-particle level event id and data. */
    private static final int GROWTH_PARTICLES_EVENT = 1505;
    private static final int GROWTH_PARTICLES_DATA = 15;

    @Override
    public InteractionResult useOnBlock(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BonemealableBlock bonemealable)
                || !bonemealable.isValidBonemealTarget(level, pos, state)
                || serverLevel.getRandom().nextFloat() >= chance) {
            return InteractionResult.PASS;
        }
        bonemealable.performBonemeal(serverLevel, serverLevel.getRandom(), pos, state);
        level.levelEvent(GROWTH_PARTICLES_EVENT, pos, GROWTH_PARTICLES_DATA);
        stack.hurtAndBreak(durabilityCost, player, EquipmentSlot.MAINHAND);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
