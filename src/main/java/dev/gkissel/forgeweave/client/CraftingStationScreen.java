package dev.gkissel.forgeweave.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
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
 * coordinates in {@link CraftingStationMenu} match upstream's {@code ContainerCraftingStation}
 * (which in turn matches vanilla's {@code CraftingMenu}) exactly so this blit lines up pixel-for-pixel.
 *
 * <p>Issue #68 fix 1: that blit used to pass the <em>panel</em> size (176x166) as the source sheet
 * size. Vanilla's file is a 256x256 sheet with the panel in its top-left corner, so the whole sheet
 * was being squeezed into the panel's footprint -- a shrunken, washed-out crafting table with every
 * slot off its drawn position. {@link #SHEET} is the sheet's real size.
 *
 * <p>When an adjacent block exposes an item handler ({@code CraftingStationBlockEntity#findSideInventory}),
 * its slots render in a bordered panel off the <em>left</em> edge via {@link SideInventoryPanel},
 * where upstream's {@code GuiCraftingStation} puts it (shared with {@link PartBuilderScreen}/{@link
 * ToolStationScreen}'s own side panels, issue #40's follow-up; {@link CraftingStationMenu} owns the
 * coordinates).
 * <p>Item tooltips come from {@link StationScreen}, which owns the {@code render()} override that
 * calls {@code renderTooltip}. This class used to claim {@link AbstractContainerScreen} did that on
 * its own -- it does not, and issue #75 is the playtest report of the resulting screen with no hover
 * text anywhere.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CraftingStationScreen extends StationScreen<CraftingStationMenu> implements StationExtraAreas {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 166;
    /** Vanilla container backgrounds are 256x256 sheets; the panel is only their top-left corner. */
    private static final int SHEET = 256;

    private final SideInventoryPanel sidePanel =
            new SideInventoryPanel(CraftingStationMenu.SIDE_PANEL_X, CraftingStationMenu.SIDE_PANEL_Y);

    public CraftingStationScreen(CraftingStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
    }

    @Override
    protected void renderPanel(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, SHEET, SHEET);
        sidePanel.render(guiGraphics, menu, leftPos, topPos, imageHeight, menu.sideSlots);
    }

    @Override
    protected boolean sliderClicked(double mouseX, double mouseY) {
        return sidePanel.sliderClicked(mouseX, mouseY);
    }

    @Override
    protected boolean sliderDragged(double mouseX, double mouseY) {
        return sidePanel.sliderDragged(mouseY);
    }

    @Override
    protected void sliderReleased() {
        sidePanel.sliderReleased();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return sidePanel.mouseScrolled(mouseX, mouseY, scrollY, imageHeight, menu.sideSlots)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public List<Rect2i> extraGuiAreas() {
        List<Rect2i> areas = super.extraGuiAreas(); // the station-group tab row (issue #78)
        if (!menu.sideSlots.isEmpty()) {
            areas.add(sidePanel.bounds());
        }
        return areas;
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.CRAFTING_STATION.get(), CraftingStationScreen::new);
    }
}
