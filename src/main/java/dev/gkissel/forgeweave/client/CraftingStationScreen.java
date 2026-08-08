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
 * its slots render in a panel to the right via {@link SideInventoryPanel} (shared with {@link
 * PartBuilderScreen}/{@link ToolStationScreen}'s own side panels, issue #40's follow-up), composited
 * at render time from repeated blits of the same vanilla texture's own crafting-grid slot tile rather
 * than a pre-baked image, since the panel's slot count varies per placement. {@link
 * AbstractContainerScreen}'s default {@code render()} already calls {@code renderTooltip} for hovered
 * slots, so slot tooltips (including side-panel ones) work without any extra override here.
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
        int slotCount = menu.sideInventorySlotCount;
        imageWidth = slotCount == 0 ? BASE_WIDTH : CraftingStationMenu.SIDE_PANEL_X + SideInventoryPanel.panelWidth(slotCount) + 4;
        imageHeight = Math.max(BASE_HEIGHT, CraftingStationMenu.SIDE_PANEL_Y + SideInventoryPanel.panelHeight(slotCount) + 7);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        SideInventoryPanel.render(guiGraphics, TEXTURE, BASE_WIDTH, BASE_HEIGHT, SLOT_TILE_U, SLOT_TILE_V,
                leftPos, topPos, CraftingStationMenu.SIDE_PANEL_X, CraftingStationMenu.SIDE_PANEL_Y, menu.sideInventorySlotCount);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.CRAFTING_STATION.get(), CraftingStationScreen::new);
    }
}
