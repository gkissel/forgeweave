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
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * The Part Builder's GUI. The background is a freshly-authored panel, not a crop of upstream's
 * `partbuilder.png` -- that texture's non-rectangular (rounded-corner) shape and 5-slot layout
 * didn't fit this GUI's simpler 3-slot (pattern/material/output) layout cleanly, so per the issue
 * #9 brief this uses a plain generic background instead (no NOTICE.md row: nothing was copied).
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PartBuilderScreen extends AbstractContainerScreen<PartBuilderMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/gui/part_builder.png");

    public PartBuilderScreen(PartBuilderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.PART_BUILDER.get(), PartBuilderScreen::new);
    }
}
