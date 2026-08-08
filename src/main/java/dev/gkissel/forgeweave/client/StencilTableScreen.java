package dev.gkissel.forgeweave.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * The Stencil Table's GUI (docs/SCOPE.md M1 issue #44). Background is derived from upstream 1.12's
 * {@code stenciltable.png}, cropped to its 176x166 panel region (NOTICE.md) -- the same "crop the
 * real upstream GUI art" approach {@code PartBuilderScreen}/{@code ToolStationScreen} use (issue
 * #43); {@link StencilTableMenu}'s input (48, 35) and output (106, 35) slot coordinates are
 * upstream's {@code ContainerStencilTable}'s own, so the blit lines up.
 *
 * <p>Issue #68 fix 5: the pattern-selection buttons used to be flat coloured rectangles drawn
 * <em>on top of</em> the panel art at (8, 52), which is what made the whole screen read as a generic
 * panel. They are now upstream's own arrangement -- {@code GuiButtonsStencilTable} is a {@code
 * GuiSideButtons} grid {@link #BUTTON_COLUMNS} wide, sitting to the <em>left</em> of the panel and
 * drawn from the wood-style button sprites in {@code icons.png} with the pattern's item on top. The
 * geometry constants below are the same ones {@link ToolStationScreen}'s tab sidebar already uses
 * (both are upstream's {@code GuiSideButtons}: 18px buttons, 4px spacing, immediately left of the
 * parent panel), so the two stations' sidebars line up with each other.
 *
 * <p>Clicking one follows the vanilla stonecutter/loom path: {@link #mouseClicked} calls {@link
 * StencilTableMenu#clickMenuButton} for immediate client-side feedback, then sends the real
 * server-bound button-click packet via {@code handleInventoryButtonClick} ({@code
 * StonecutterScreen#mouseClicked} does exactly this).
 *
 * <p>{@link AbstractContainerScreen}'s default {@code render()} already calls {@link
 * #renderTooltip}; it's overridden here only to add tooltips for the buttons, which aren't slots.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StencilTableScreen extends AbstractContainerScreen<StencilTableMenu> implements StationExtraAreas {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/stencil_table.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/station_icons.png");
    private static final int SHEET = 256;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 166;

    /** Upstream {@code GuiStencilTable.Column_Count}. */
    private static final int BUTTON_COLUMNS = 4;
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_SPACING = 4;
    private static final int BUTTONS_Y = 9;
    private static final int PANEL_GAP = 2;

    // Regions of icons.png, the wood-style button row (Icons#ICON_Button and friends, shifted +18y).
    private static final int BUTTON_V = 180;
    private static final int BUTTON_IDLE_U = 180;
    private static final int BUTTON_HOVER_U = 216;
    private static final int BUTTON_PRESSED_U = 144;

    public StencilTableScreen(StencilTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        renderPatternButtons(guiGraphics, mouseX, mouseY);
    }

    private void renderPatternButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<DeferredItem<Item>> patterns = StencilTableMenu.PATTERNS;
        for (int i = 0; i < patterns.size(); i++) {
            int x = leftPos + buttonX(i);
            int y = topPos + buttonY(i);
            boolean hovered = isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
            int u = i == menu.getSelectedPattern() ? BUTTON_PRESSED_U : hovered ? BUTTON_HOVER_U : BUTTON_IDLE_U;
            guiGraphics.blit(ICONS, x, y, u, BUTTON_V, BUTTON_SIZE, BUTTON_SIZE, SHEET, SHEET);
            guiGraphics.renderItem(new ItemStack(patterns.get(i).get()), x + 1, y + 1);
        }
    }

    private static int sidebarWidth() {
        int columns = Math.min(StencilTableMenu.PATTERNS.size(), BUTTON_COLUMNS);
        return columns * BUTTON_SIZE + (columns - 1) * BUTTON_SPACING;
    }

    private static int sidebarHeight() {
        int rows = (StencilTableMenu.PATTERNS.size() + BUTTON_COLUMNS - 1) / BUTTON_COLUMNS;
        return rows * BUTTON_SIZE + (rows - 1) * BUTTON_SPACING;
    }

    private static int buttonX(int index) {
        return -sidebarWidth() - PANEL_GAP + (index % BUTTON_COLUMNS) * (BUTTON_SIZE + BUTTON_SPACING);
    }

    private static int buttonY(int index) {
        return BUTTONS_Y + (index / BUTTON_COLUMNS) * (BUTTON_SIZE + BUTTON_SPACING);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        List<DeferredItem<Item>> patterns = StencilTableMenu.PATTERNS;
        for (int i = 0; i < patterns.size(); i++) {
            if (isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, new ItemStack(patterns.get(i).get()), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<DeferredItem<Item>> patterns = StencilTableMenu.PATTERNS;
        for (int i = 0; i < patterns.size(); i++) {
            if (isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)
                    && menu.clickMenuButton(minecraft.player, i)) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Issue #68 fix 4: the button sidebar hangs left of {@code imageWidth}; JEI has to be told. */
    @Override
    public List<Rect2i> extraGuiAreas() {
        return List.of(new Rect2i(leftPos + buttonX(0), topPos + BUTTONS_Y, sidebarWidth(), sidebarHeight()));
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.STENCIL_TABLE.get(), StencilTableScreen::new);
    }
}
