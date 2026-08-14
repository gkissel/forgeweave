package dev.gkissel.forgeweave.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.menu.ChestMenu;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;

/**
 * The Pattern Chest/Part Chest GUI (docs/SCOPE.md M1 issue #66). Background is vanilla's own
 * double-chest {@code generic_54.png}, not a derived crop of upstream's {@code
 * patternchest.png}/{@code partchest.png} GUI art -- those are drawn for upstream's dynamic scaling
 * chest window ({@code GuiScalingChest}), which this ships as a fixed 6-row grid instead ({@link
 * dev.gkissel.forgeweave.block.ChestBlockEntity}'s capacity note), so there is no matching panel
 * shape to crop. Same "no Forgeweave-original art for this screen" precedent {@code
 * CraftingStationScreen} already uses for vanilla's crafting-table background -- no NOTICE.md row.
 *
 * <h2>Page controls (issue #305)</h2>
 *
 * <p>Two clickable arrows and a "page X/Y" label sit in the title row's unused right-hand space,
 * following the same manual-hitbox-plus-{@code handleInventoryButtonClick} idiom {@link
 * StationScreen}'s own tab row uses -- there being no other button-widget convention in this
 * codebase's screens to reuse instead (see that class's javadoc).
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ChestScreen extends StationScreen<ChestMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 222;
    /** Vanilla container backgrounds are 256x256 sheets; the panel is only their top-left corner. */
    private static final int SHEET = 256;

    private static final int PAGE_ROW_Y = 6;
    private static final int PAGE_ARROW_SIZE = 9;
    private static final int PAGE_PREVIOUS_X = 132;
    private static final int PAGE_LABEL_X = 143;
    private static final int PAGE_NEXT_X = 167;
    private static final int COLOR_ENABLED = 0xFFFFFF;
    private static final int COLOR_DISABLED = 0x707070;
    private static final int COLOR_LABEL = 0x404040;

    public ChestScreen(ChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderPanel(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Same defect issue #68 fix 1 found in CraftingStationScreen: passing the panel size as the
        // source sheet size squeezes the whole 256x256 file into the panel's footprint.
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, SHEET, SHEET);
        renderPageControls(guiGraphics);
    }

    private void renderPageControls(GuiGraphics guiGraphics) {
        boolean hasPrevious = menu.currentPage() > 0;
        boolean hasNext = (menu.currentPage() + 1) * ChestBlockEntity.PAGE_SIZE < menu.capacity();
        guiGraphics.drawString(font, "<", leftPos + PAGE_PREVIOUS_X, topPos + PAGE_ROW_Y,
                hasPrevious ? COLOR_ENABLED : COLOR_DISABLED, false);
        guiGraphics.drawString(font,
                Component.translatable("gui.forgeweave.chest.page", menu.currentPage() + 1, totalPages()),
                leftPos + PAGE_LABEL_X, topPos + PAGE_ROW_Y, COLOR_LABEL, false);
        guiGraphics.drawString(font, ">", leftPos + PAGE_NEXT_X, topPos + PAGE_ROW_Y,
                hasNext ? COLOR_ENABLED : COLOR_DISABLED, false);
    }

    /** {@code capacity} is always a whole number of pages except the short final one -- round up. */
    private int totalPages() {
        return (menu.capacity() + ChestBlockEntity.PAGE_SIZE - 1) / ChestBlockEntity.PAGE_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(PAGE_PREVIOUS_X, PAGE_ROW_Y, PAGE_ARROW_SIZE, PAGE_ARROW_SIZE, mouseX, mouseY)
                && menu.currentPage() > 0) {
            sendPageClick(ChestMenu.BUTTON_PREVIOUS_PAGE);
            return true;
        }
        if (isHovering(PAGE_NEXT_X, PAGE_ROW_Y, PAGE_ARROW_SIZE, PAGE_ARROW_SIZE, mouseX, mouseY)
                && (menu.currentPage() + 1) * ChestBlockEntity.PAGE_SIZE < menu.capacity()) {
            sendPageClick(ChestMenu.BUTTON_NEXT_PAGE);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Server-authoritative, same as {@link StationScreen}'s tab clicks: only the button id is sent. */
    private void sendPageClick(int buttonId) {
        if (minecraft == null) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.PATTERN_CHEST.get(), ChestScreen::new);
        event.register(ForgeweaveMenus.PART_CHEST.get(), ChestScreen::new);
    }
}
