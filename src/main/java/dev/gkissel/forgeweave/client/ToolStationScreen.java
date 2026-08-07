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
 * #43), cropped to its 176x166 base region. Unlike the Part Builder, upstream draws that station's
 * input slots at runtime-computed positions (its slot count/layout depends on the tool being
 * built) rather than baking them into the panel at fixed coordinates, so there's no single
 * "upstream slot layout" to copy for our fixed 4-slot (head/binding/handle/output) design; instead
 * this composites upstream's own reusable slot-background/slot-border sprite pieces (same atlas, at
 * (176,0) and (194,0) in {@code toolstation.png}) onto our four fixed {@link ToolStationMenu} slot
 * coordinates -- see NOTICE.md. {@link AbstractContainerScreen}'s default title/inventory-label
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
