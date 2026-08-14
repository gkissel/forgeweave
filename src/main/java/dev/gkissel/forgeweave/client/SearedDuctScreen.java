package dev.gkissel.forgeweave.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.SearedDuctMenu;

/**
 * The seared duct's filter GUI (docs/SCOPE.md M3.4 issue #277). Background is the 1.20 clone's own
 * {@code textures/gui/duct.png} (NOTICE.md), which is where the slot and inventory positions
 * {@link SearedDuctMenu} uses come from.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SearedDuctScreen extends AbstractContainerScreen<SearedDuctMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/seared_duct.png");
    /** The panel is the top-left corner of a 256x256 sheet, as every vanilla container background is. */
    private static final int SHEET = 256;

    public SearedDuctScreen(SearedDuctMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SearedDuctMenu.PANEL_WIDTH;
        imageHeight = SearedDuctMenu.PANEL_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, SHEET, SHEET);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.SEARED_DUCT.get(), SearedDuctScreen::new);
    }
}
