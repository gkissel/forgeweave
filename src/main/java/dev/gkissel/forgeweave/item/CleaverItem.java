package dev.gkissel.forgeweave.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The cleaver (docs/SCOPE.md M3 issue #158; parity audit T67/#498): its own item class purely so
 * right-click has somewhere to go that isn't the generic {@code ToolUseAction} pipeline. Upstream
 * {@code tools/melee/item/Cleaver.java#onItemRightClick} swallows the click outright -- always
 * {@code SUCCESS} with the stack unchanged -- rather than falling through to vanilla's default
 * {@code PASS}. {@link ToolItem#use} can't carry that: the cleaver's registration deliberately passes
 * a {@code null} innate ({@code ForgeweaveInnates#innateId}'s "no Innate seam at all" carve-out for
 * beheading, so the level isn't rolled twice), so there is no
 * {@link dev.gkissel.forgeweave.combat.ToolUseAction} to hang a swallow-only behavior off. Same shape
 * as {@link WarmaceItem}: a per-tool subclass for the one tool whose vanilla-parity behavior the
 * shared Innate wiring cannot express.
 *
 * <p>Why it matters: vanilla only tries the off-hand item's own right-click when the main-hand result
 * does not {@code consumesAction()} (i.e. was {@code PASS}). Every other Forgeweave weapon either has
 * a real use action or falls through to that default {@code PASS}; without this override a cleaver in
 * the main hand would let whatever is in the off-hand -- food, a block, a shield -- also act on the
 * same click, which upstream never allows.
 */
public class CleaverItem extends MeleeWeaponItem {

    public CleaverItem(Properties properties, ToolConstants.Entry constants, TagKey<Block> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        super(properties, constants, mineableBlocks, weapon, innate);
    }

    /** Upstream {@code Cleaver#onItemRightClick}: always {@code SUCCESS}, stack untouched. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
