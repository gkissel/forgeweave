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
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * The Tool Station's GUI. Background is upstream 1.12's real {@code toolstation.png} panel (issue
 * #43), derived as-is: a straight 176x166 crop of the base panel region with no compositing on top
 * (a first cut tried pasting upstream's reusable slot-background/slot-border sprite pieces onto our
 * slot coordinates, using the wrong alpha, which rendered as solid black boxes -- regression fixed
 * by dropping that entirely). Unlike the Part Builder, upstream draws that station's input slots at
 * runtime-computed positions (its slot count/layout depends on the tool being built), so there's no
 * baked-in slot art anywhere in the panel to align to; our four {@link ToolStationMenu} slots just
 * sit in the panel's open area, avoiding upstream's baked-in item-preview icon/arrow decoration
 * (around x 90-150) and its name-textfield/button-tab chrome (flattened to plain panel gray at
 * export time -- see NOTICE.md -- since Forgeweave's Tool Station has neither renaming nor
 * tool-selection buttons). {@link AbstractContainerScreen}'s default title/inventory-label
 * positions already match upstream's.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ToolStationScreen extends AbstractContainerScreen<ToolStationMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/tool_station.png");

    public ToolStationScreen(ToolStationMenu menu, Inventory playerInventory, Component title) {
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
        event.register(ForgeweaveMenus.TOOL_STATION.get(), ToolStationScreen::new);
    }
}
