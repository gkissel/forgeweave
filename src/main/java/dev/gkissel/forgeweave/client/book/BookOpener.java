package dev.gkissel.forgeweave.client.book;

import net.minecraft.client.Minecraft;

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

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.setScreen(new BookScreen(BookContent.sections(minecraft.level.registryAccess())));
        }
    }
}
