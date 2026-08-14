package dev.gkissel.forgeweave.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.client.book.BookOpener;

/**
 * The workshop guide book (issue #273) -- Forgeweave's "Materials and You". Right-clicking opens
 * the client-side {@code BookScreen}; the item itself carries no state, exactly upstream 1.12's
 * {@code common/item/ItemTinkerBook} (NOTICE.md): a plain stack-size-1 item whose only behaviour is
 * opening the book GUI on the client.
 */
public class GuideBookItem extends Item {

    public GuideBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            BookOpener.open();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
