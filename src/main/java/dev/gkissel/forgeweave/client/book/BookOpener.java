package dev.gkissel.forgeweave.client.book;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * The one client entry point {@code GuideBookItem} calls. Kept as its own class so the item class
 * never names a {@code net.minecraft.client} type: the JVM only resolves this class when the
 * {@code level.isClientSide} branch actually executes, which a dedicated server's never does --
 * upstream 1.12's {@code ItemTinkerBook#onItemRightClick} guards its
 * {@code TinkerBook.INSTANCE.openGui} call the same way.
 */
public final class BookOpener {

    private BookOpener() {
    }

    /**
     * Opens the book onto the page bookmarked on the stack, upstream's {@code GuiBook} constructor
     * calling {@code openPage(book.findPageNumber(BookHelper.getSavedPage(item)))} (issue #623);
     * a book with no bookmark opens on the cover, and closing the screen saves the open page back
     * through the hand.
     */
    public static void open(ItemStack stack, InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.setScreen(new BookScreen(BookContent.sections(minecraft.level.registryAccess()),
                    stack.getOrDefault(ForgeweaveDataComponents.BOOK_PAGE.get(), ""), hand));
        }
    }
}
