package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.client.book.BookOpener;

/**
 * The workshop guide book (issue #273) -- Forgeweave's "Materials and You". Right-clicking opens
 * the client-side {@code BookScreen}, exactly upstream 1.12's {@code common/item/ItemTinkerBook}
 * (NOTICE.md): a plain stack-size-1 item whose only behaviour is opening the book GUI on the
 * client. Its one piece of state is the bookmark (issue #623): closing the screen saves the open
 * page on the stack ({@code ForgeweaveDataComponents#BOOK_PAGE} via {@code SavedBookPagePayload},
 * upstream's {@code mantle.book.page} NBT), and opening hands the stack to {@code BookOpener} so
 * the book reopens there.
 */
public class GuideBookItem extends Item {

    public GuideBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            BookOpener.open(player.getItemInHand(hand), hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /**
     * The grey flavour line (issue #379), upstream's {@code ItemTinkerBook#addInformation} printing
     * {@code item.tconstruct.book.tooltip} in {@code TextFormatting.GRAY}. Upstream's copy wraps over
     * an embedded newline and names its author; Forgeweave's is one line in its own vocabulary.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.forgeweave.guide_book").withStyle(ChatFormatting.GRAY));
    }
}
