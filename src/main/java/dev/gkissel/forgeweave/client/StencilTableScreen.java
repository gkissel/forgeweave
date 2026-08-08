package dev.gkissel.forgeweave.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * #43); {@link StencilTableMenu}'s input/output slot coordinates match upstream's {@code
 * ContainerStencilTable} so the blit lines up.
 *
 * <p>The five pattern-selection buttons have no baked art to derive (upstream draws them from its
 * own button-icon sprite sheet via {@code GuiButtonsStencilTable}, which isn't part of the cropped
 * panel), so they're drawn procedurally here -- a filled backdrop per button (selected/hovered/
 * idle) with the pattern's item icon on top, the same "compose from GuiGraphics primitives instead
 * of a second pre-baked image" approach {@code CraftingStationScreen} uses for its side-inventory
 * panel. Clicking one follows the vanilla stonecutter/loom path: {@link #mouseClicked} calls {@link
 * StencilTableMenu#clickMenuButton} for immediate client-side feedback, then sends the real
 * server-bound button-click packet via {@code handleInventoryButtonClick} ({@code
 * StonecutterScreen#mouseClicked} does exactly this).
 *
 * <p>{@link AbstractContainerScreen}'s default {@code render()} already calls {@link
 * #renderTooltip}; it's overridden here only to add tooltips for the button row, which isn't a slot.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StencilTableScreen extends AbstractContainerScreen<StencilTableMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/stencil_table.png");
    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 166;

    private static final int BUTTON_X = 8;
    private static final int BUTTON_Y = 52;
    private static final int BUTTON_SIZE = 18;

    private static final int COLOR_IDLE = 0xFF8B8B8B;
    private static final int COLOR_HOVERED = 0xFFACACAC;
    private static final int COLOR_SELECTED = 0xFF6688DD;

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
            int x = leftPos + BUTTON_X + i * BUTTON_SIZE;
            int y = topPos + BUTTON_Y;
            boolean selected = i == menu.getSelectedPattern();
            boolean hovered = isHovering(BUTTON_X + i * BUTTON_SIZE, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
            int color = selected ? COLOR_SELECTED : hovered ? COLOR_HOVERED : COLOR_IDLE;
            guiGraphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, color);
            guiGraphics.renderItem(new ItemStack(patterns.get(i).get()), x + 1, y + 1);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        List<DeferredItem<Item>> patterns = StencilTableMenu.PATTERNS;
        for (int i = 0; i < patterns.size(); i++) {
            if (isHovering(BUTTON_X + i * BUTTON_SIZE, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                guiGraphics.renderTooltip(font, new ItemStack(patterns.get(i).get()), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<DeferredItem<Item>> patterns = StencilTableMenu.PATTERNS;
        for (int i = 0; i < patterns.size(); i++) {
            if (isHovering(BUTTON_X + i * BUTTON_SIZE, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)
                    && menu.clickMenuButton(minecraft.player, i)) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.STENCIL_TABLE.get(), StencilTableScreen::new);
    }
}
