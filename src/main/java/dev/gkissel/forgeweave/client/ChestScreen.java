package dev.gkissel.forgeweave.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ChestMenu;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;

/**
 * The Pattern Chest/Part Chest GUI (docs/SCOPE.md M1 issue #66), rebuilt as upstream's scaling chest
 * by parity audit T45 (issue #476).
 *
 * <p>Upstream {@code GuiPatternChest} is {@code textures/gui/blank.png} -- a plain station panel
 * whose whole upper half is bare background -- with a {@code GuiScalingChest} module drawn into it:
 * a {@value #MODULE_WIDTH}x{@value #MODULE_HEIGHT} box at ({@value #MODULE_X}, {@value #MODULE_Y})
 * holding a {@link ChestMenu#COLUMNS}x{@link ChestMenu#ROWS} window of {@code generic.png} slot
 * tiles and, down its right-hand edge, that sheet's own slider. Only as many tiles are drawn as the
 * chest currently has slots ({@link ChestMenu#capacity}, self-expanding -- see {@code
 * ChestBlockEntity}); the rest of the partial row is the "no slot here" tile and the rows past it
 * are left as bare panel, which is what makes the chest look like it grows.
 *
 * <p>This replaces the vanilla double-chest {@code generic_54.png} background and the {@code
 * &lt;}/{@code &gt;} page arrows issue #305 shipped in the title row. The slot tiles, the slider and
 * its drag/wheel handling are {@link SideInventoryPanel}'s, which is the same upstream widget pair
 * ({@code GuiDynInventory} and {@code GuiSideInventory} both draw {@code GuiGeneric}'s pieces) and
 * already ported.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ChestScreen extends StationScreen<ChestMenu> {
    /** Upstream {@code GuiTinkerStation.BLANK_BACK}; derived unmodified (NOTICE.md). */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/blank.png");
    static final int PANEL_WIDTH = 176;
    static final int PANEL_HEIGHT = 166;
    /** Upstream's GUI sheets are 256x256; the panel is only their top-left corner. */
    static final int SHEET = 256;

    /** Upstream {@code GuiDynInventory}'s own {@code xOffset}/{@code yOffset}/{@code xSize}/{@code ySize}. */
    private static final int MODULE_X = 7;
    private static final int MODULE_Y = 17;
    private static final int MODULE_WIDTH = 162;
    private static final int MODULE_HEIGHT = 54;
    private static final int SLOT = 18;

    static final int SLIDER_X = MODULE_X + MODULE_WIDTH - SideInventoryPanel.SLIDER_WIDTH;
    static final int SLIDER_Y = MODULE_Y;
    static final int SLIDER_HEIGHT = MODULE_HEIGHT;

    /** Which row of the chest the window starts at; client-side only (see {@link ChestMenu#scrollTo}). */
    private int scrollRow;
    private boolean draggingSlider;

    public ChestScreen(ChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, SHEET, SHEET);

        scrollRow = Math.clamp(scrollRow, 0, maxScrollRow());
        menu.scrollTo(scrollRow);
        renderSlotTiles(graphics);
        SideInventoryPanel.renderSlider(graphics, sliderTrack(), scrollRow, maxScrollRow());
    }

    /**
     * Upstream {@code GuiDynInventory#drawGuiContainerBackgroundLayer}: full rows of the slot tile,
     * then the partial row's tiles followed by the "no slot here" tile for the rest of it, and
     * nothing at all below that.
     */
    private void renderSlotTiles(GuiGraphics graphics) {
        int first = scrollRow * ChestMenu.COLUMNS;
        int visible = Math.clamp(menu.capacity() - first, 0, ChestMenu.VISIBLE_SLOTS);
        int fullRows = visible / ChestMenu.COLUMNS;
        for (int row = 0; row < fullRows; row++) {
            for (int col = 0; col < ChestMenu.COLUMNS; col++) {
                tile(graphics, col, row, true);
            }
        }
        if (visible % ChestMenu.COLUMNS > 0) {
            for (int col = 0; col < ChestMenu.COLUMNS; col++) {
                tile(graphics, col, fullRows, col < visible % ChestMenu.COLUMNS);
            }
        }
    }

    private void tile(GuiGraphics graphics, int col, int row, boolean filled) {
        SideInventoryPanel.renderSlotTile(graphics, leftPos + MODULE_X + col * SLOT,
                topPos + MODULE_Y + row * SLOT, filled);
    }

    private Rect2i sliderTrack() {
        return new Rect2i(leftPos + SLIDER_X, topPos + SLIDER_Y, SideInventoryPanel.SLIDER_WIDTH, SLIDER_HEIGHT);
    }

    private int maxScrollRow() {
        return ChestMenu.maxScrollRow(menu.capacity());
    }

    @Override
    protected boolean sliderClicked(double mouseX, double mouseY) {
        if (maxScrollRow() <= 0 || !sliderTrack().contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        draggingSlider = true;
        scrollRow = scrollRowAt(mouseY);
        return true;
    }

    @Override
    protected boolean sliderDragged(double mouseX, double mouseY) {
        if (!draggingSlider) {
            return false;
        }
        scrollRow = scrollRowAt(mouseY);
        return true;
    }

    @Override
    protected void sliderReleased() {
        draggingSlider = false;
    }

    private int scrollRowAt(double mouseY) {
        return SideInventoryPanel.scrollRowAt(topPos + SLIDER_Y, SLIDER_HEIGHT, maxScrollRow(), mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScrollRow() > 0 && isHovering(MODULE_X, MODULE_Y, MODULE_WIDTH, MODULE_HEIGHT, mouseX, mouseY)) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.PATTERN_CHEST.get(), ChestScreen::new);
        event.register(ForgeweaveMenus.PART_CHEST.get(), ChestScreen::new);
    }
}
