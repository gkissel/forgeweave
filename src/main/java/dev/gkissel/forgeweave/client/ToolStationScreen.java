package dev.gkissel.forgeweave.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

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
 *
 * <p>The plain-gray panel has no baked-in socket art at our slot positions (unlike the Part
 * Builder's), so without anything else drawn the 4 station slots were invisible on an empty panel
 * (issue #43 regression: read as "no slots at all"); {@link #renderBg} now draws a simple sunken
 * outline (flat-filled rectangles, not sampled from upstream art -- no compositing) at each one.
 *
 * <p>{@link AbstractContainerScreen#render} does <em>not</em> call {@link #renderTooltip} on its
 * own (unlike the label/slot rendering, that call is left to subclasses) -- every vanilla container
 * screen overrides {@code render} to add it, and this one previously didn't, so item tooltips never
 * showed (issue #43 regression fix).
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ToolStationScreen extends AbstractContainerScreen<ToolStationMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/tool_station.png");
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;

    public ToolStationScreen(ToolStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        for (int i = 0; i < ToolStationMenu.CONTAINER_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            drawSlotOutline(guiGraphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    /** A plain sunken-square outline, drawn with flat fills -- no upstream pixel art involved. */
    private static void drawSlotOutline(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_FILL);
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + 1, SLOT_SHADOW);
        guiGraphics.fill(x, y, x + 1, y + SLOT_SIZE, SLOT_SHADOW);
        guiGraphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_HIGHLIGHT);
        guiGraphics.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_HIGHLIGHT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.TOOL_STATION.get(), ToolStationScreen::new);
    }
}
