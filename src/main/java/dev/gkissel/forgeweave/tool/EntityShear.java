package dev.gkissel.forgeweave.tool;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.common.IShearable;

import dev.gkissel.forgeweave.item.ToolItem;

/**
 * Shearing {@link IShearable} entities (docs/SCOPE.md issue #467), upstream 1.12's {@code
 * tools/tools/Kama.java}'s {@code shearEntity} core, reached two ways:
 *
 * <ul>
 *   <li>{@link #shearAt} -- the kama's own single-entity reach, upstream's {@code
 *       Kama#itemInteractionForEntity}. {@code KamaItem} calls this.
 *   <li>{@link #shearAround} -- the scythe's, upstream's {@code Scythe#itemInteractionForEntity}/
 *       {@code #getAoeEntities}: every shearable entity in the same 3x3x3 its crop harvest ({@link
 *       CropHarvest}) and its area attack ({@link dev.gkissel.forgeweave.combat.SweepAttackSeam})
 *       already cover, not just the one right-clicked. {@code ToolItem#interactLivingEntity} calls
 *       this for every tool whose {@link AoeHarvest.Shape} is {@link AoeHarvest.Shape#CUBE_3X3X3},
 *       the same gate {@code ToolItem#useOn} already uses for the crop-harvest half of the scythe.
 * </ul>
 */
public final class EntityShear {

    /**
     * How far from the target's own box the area reaches -- upstream's 3x3x3, i.e. {@code (3 - 1) /
     * 2}, the same radius {@link dev.gkissel.forgeweave.combat.SweepAttackSeam} uses for the scythe's
     * area attack.
     */
    private static final double AREA_RADIUS = 1.0;

    /** Shears {@code target} alone. */
    public static InteractionResult shearAt(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (ToolItem.isBroken(stack) || !(target instanceof IShearable shearable)) {
            return InteractionResult.PASS;
        }
        return shear(stack, player, target, shearable, hand)
                ? InteractionResult.sidedSuccess(target.level().isClientSide())
                : InteractionResult.PASS;
    }

    /** Shears every {@link IShearable} entity within the 3x3x3 around {@code target}. */
    public static InteractionResult shearAround(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (ToolItem.isBroken(stack)) {
            return InteractionResult.PASS;
        }
        Level level = target.level();
        AABB area = target.getBoundingBox().inflate(AREA_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity instanceof IShearable);

        boolean shorn = false;
        for (LivingEntity entity : candidates) {
            shorn |= shear(stack, player, entity, (IShearable) entity, hand);
        }
        if (!shorn) {
            return InteractionResult.PASS;
        }
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                player.getSoundSource(), 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** One entity's shear attempt, upstream's {@code Kama#shearEntity}; shared by both reaches above. */
    private static boolean shear(ItemStack stack, Player player, LivingEntity entity, IShearable shearable,
            InteractionHand hand) {
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        if (!shearable.isShearable(player, stack, level, pos)) {
            return false;
        }
        boolean isClient = level.isClientSide();
        List<ItemStack> drops = shearable.onSheared(player, stack, level, pos);
        if (!isClient) {
            drops.forEach(drop -> shearable.spawnShearedDrop(level, pos, drop));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        entity.gameEvent(GameEvent.SHEAR, player);
        return true;
    }

    private EntityShear() {}
}
