package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The mattock (docs/SCOPE.md M3 issue #156): upstream {@code tools/tools/Mattock.java}'s axe+shovel
 * dual head. Mining effectiveness on both block families is plain {@link ToolItem} machinery -- it
 * simply names both vanilla {@code mineable/*} tags -- so the only thing this subclass adds is the
 * hoe-style tilling upstream's {@code onItemUse} ports from {@code Items.DIAMOND_HOE.onItemUse}
 * (with the tool's own damage temporarily restored so vanilla's hoe-item durability call doesn't
 * touch it, then Forgeweave's own {@code ToolHelper.damageTool}-equivalent applies once).
 *
 * <p>Modern NeoForge exposes the same "can this block be tool-modified" question upstream's own
 * {@code TILLABLES} map answered as {@link BlockState#getToolModifiedState}, driven by {@link
 * ItemAbilities#HOE_TILL} -- {@code HoeItem#useOn} (NOTICE.md) is the vanilla caller this mirrors, so
 * tilling stays consistent with every other hoe (including mod-added tillable blocks) instead of
 * re-deriving upstream's block-to-block table.
 */
public class MattockItem extends ToolItem {

    public MattockItem(Properties properties) {
        super(properties, ToolConstants.MATTOCK,
                List.of(BlockTags.MINEABLE_WITH_AXE, BlockTags.MINEABLE_WITH_SHOVEL),
                false, ForgeweaveInnates.HEFT);
    }

    /** Tills soil like a hoe (see the class javadoc); a Broken mattock refuses like every other action. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (isBroken(context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockState tilled = level.getBlockState(context.getClickedPos())
                .getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
        if (tilled == null) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        level.playSound(player, context.getClickedPos(), SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!level.isClientSide) {
            level.setBlock(context.getClickedPos(), tilled, 11);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(itemAbility)
                || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(itemAbility)
                || ItemAbilities.DEFAULT_HOE_ACTIONS.contains(itemAbility);
    }
}
