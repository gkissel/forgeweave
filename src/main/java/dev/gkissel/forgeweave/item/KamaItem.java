package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.IShearable;

import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.tool.CropHarvest;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The kama (docs/SCOPE.md M3 issue #156): upstream {@code tools/tools/Kama.java}'s harvest sickle.
 * Its two utility innates are parity ports:
 *
 * <ul>
 *   <li><b>Shears</b> ({@code itemInteractionForEntity} -> {@code shearEntity}, NOTICE.md): mirrors
 *       vanilla {@code ShearsItem#interactLivingEntity} against the same {@link IShearable}
 *       capability (sheep, mooshroom, snow golem, and any modded entity implementing it) rather than
 *       hardcoding a species list -- "shearable things per vanilla shears behavior where sensible".
 *   <li><b>Right-click crop harvest+replant</b> ({@code onItemRightClick} -> {@code harvestCrop}/
 *       {@code doHarvestCrop}, NOTICE.md): {@link CropHarvest}.
 * </ul>
 *
 * <p>The kama names no {@code mineable/*} tag: it isn't a digging tool upstream either (only {@code
 * Category.HARVEST}/{@code WEAPON}), so {@link ToolItem}'s {@code tool} component carries just the
 * tier-denial rule and no effective-block rule -- it never digs at bonus speed.
 */
public class KamaItem extends ToolItem {

    public KamaItem(Properties properties) {
        super(properties, ToolConstants.KAMA, List.of(), true, ForgeweaveInnates.REAP);
    }

    /** Shears {@link IShearable} entities (sheep, mooshroom, snow golem, ...); see the class javadoc. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (isBroken(stack) || !(target instanceof IShearable shearable)) {
            return InteractionResult.PASS;
        }
        var pos = target.blockPosition();
        boolean isClient = target.level().isClientSide();
        if (!shearable.isShearable(player, stack, target.level(), pos)) {
            return InteractionResult.PASS;
        }
        var drops = shearable.onSheared(player, stack, target.level(), pos);
        if (!isClient) {
            drops.forEach(drop -> shearable.spawnShearedDrop(target.level(), pos, drop));
        }
        target.gameEvent(net.minecraft.world.level.gameevent.GameEvent.SHEAR, player);
        if (!isClient) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResult.sidedSuccess(isClient);
    }

    /**
     * Harvests and replants the mature crop under the cursor. The rules are {@link CropHarvest}'s --
     * shared with the scythe (issue #157), whose 3x3x3 harvest is the same port over more blocks.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return isBroken(context.getItemInHand()) ? InteractionResult.PASS : CropHarvest.harvestAt(context);
    }
}
