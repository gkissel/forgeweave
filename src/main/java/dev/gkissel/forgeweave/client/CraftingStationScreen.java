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
import dev.gkissel.forgeweave.menu.CraftingStationMenu;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;

/**
 * The Crafting Station's GUI (docs/SCOPE.md M1 issue #40). Background is vanilla's own {@code
 * textures/gui/container/crafting_table.png}, referenced directly rather than copied into a derived
 * texture: upstream 1.12's {@code GuiCraftingStation} does exactly this too ({@code new
 * ResourceLocation("textures/gui/container/crafting_table.png")}, no TinkersConstruct-original art of
 * its own), so there is nothing to derive -- no NOTICE.md row, see that class for reference. Slot
 * coordinates in {@link CraftingStationMenu} match vanilla's {@code CraftingMenu} exactly so this
 * blit lines up pixel-for-pixel.
 *
 * <p>When an adjacent block exposes an item handler ({@code CraftingStationBlockEntity#findSideInventory}),
 * its slots render in a panel to the right, composited at render time from repeated blits of the same
 * vanilla texture's own crafting-grid slot tile (matching the "reuse upstream's own reusable
 * slot-background sprite pieces" approach {@code ToolStationScreen} already uses) rather than a
 * pre-baked image, since the panel's slot count varies per placement. {@link AbstractContainerScreen}'s
 * default {@code render()} already calls {@code renderTooltip} for hovered slots, so slot tooltips
 * (including side-panel ones) work without any extra override here.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CraftingStationScreen extends AbstractContainerScreen<CraftingStationMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 166;

    /** The base texture's own crafting-grid slot tile (background + border), reused for the side panel. */
    private static final int SLOT_TILE_U = 30;
    private static final int SLOT_TILE_V = 17;

    public CraftingStationScreen(CraftingStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        int sideColumns = Math.min(menu.sideInventorySlotCount, CraftingStationMenu.SIDE_INVENTORY_COLUMNS);
        int sideRows = menu.sideInventorySlotCount == 0 ? 0
                : (menu.sideInventorySlotCount + CraftingStationMenu.SIDE_INVENTORY_COLUMNS - 1) / CraftingStationMenu.SIDE_INVENTORY_COLUMNS;
        int panelWidth = sideColumns * CraftingStationMenu.SLOT_SIZE;
        imageWidth = menu.sideInventorySlotCount == 0 ? BASE_WIDTH : CraftingStationMenu.SIDE_PANEL_X + panelWidth + 4;
        imageHeight = Math.max(BASE_HEIGHT, CraftingStationMenu.SIDE_PANEL_Y + sideRows * CraftingStationMenu.SLOT_SIZE + 7);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        renderSideInventoryBackground(guiGraphics);
    }

    private void renderSideInventoryBackground(GuiGraphics guiGraphics) {
        int slotCount = menu.sideInventorySlotCount;
        if (slotCount == 0) {
            return;
        }
        int columns = CraftingStationMenu.SIDE_INVENTORY_COLUMNS;
        int size = CraftingStationMenu.SLOT_SIZE;
        int panelWidth = Math.min(slotCount, columns) * size;
        int rows = (slotCount + columns - 1) / columns;
        int panelHeight = rows * size;

        // A backdrop so the side panel reads as a distinct region rather than floating slot tiles.
        int backdropLeft = leftPos + CraftingStationMenu.SIDE_PANEL_X - 3;
        int backdropTop = topPos + CraftingStationMenu.SIDE_PANEL_Y - 3;
        guiGraphics.fill(backdropLeft, backdropTop, backdropLeft + panelWidth + 6, backdropTop + panelHeight + 6, 0x88000000);

        for (int i = 0; i < slotCount; i++) {
            int x = leftPos + CraftingStationMenu.SIDE_PANEL_X + (i % columns) * size;
            int y = topPos + CraftingStationMenu.SIDE_PANEL_Y + (i / columns) * size;
            guiGraphics.blit(TEXTURE, x, y, SLOT_TILE_U, SLOT_TILE_V, size, size, BASE_WIDTH, BASE_HEIGHT);
        }
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.CRAFTING_STATION.get(), CraftingStationScreen::new);
    }
}
