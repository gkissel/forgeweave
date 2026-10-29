package dev.gkissel.forgeweave.client;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
@EventBusSubscriber(modid = Forgeweave.MODID, value = Dist.CLIENT)
public class SearedDuctScreen extends AbstractContainerScreen<SearedDuctMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/seared_duct.png");
    /** The panel is the top-left corner of a 256x256 sheet, as every vanilla container background is. */
    private static final int SHEET = 256;

    public SearedDuctScreen(SearedDuctMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SearedDuctMenu.PANEL_WIDTH, SearedDuctMenu.PANEL_HEIGHT);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, SHEET, SHEET);
    }


    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.SEARED_DUCT.get(), SearedDuctScreen::new);
    }
}
