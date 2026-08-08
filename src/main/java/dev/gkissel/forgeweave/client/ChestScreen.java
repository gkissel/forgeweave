package dev.gkissel.forgeweave.client;

import net.minecraft.client.gui.GuiGraphics;
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
 * The Pattern Chest/Part Chest GUI (docs/SCOPE.md M1 issue #66). Background is vanilla's own
 * double-chest {@code generic_54.png}, not a derived crop of upstream's {@code
 * patternchest.png}/{@code partchest.png} GUI art -- those are drawn for upstream's dynamic scaling
 * chest window ({@code GuiScalingChest}), which this ships as a fixed 6-row grid instead ({@link
 * dev.gkissel.forgeweave.block.ChestBlockEntity}'s capacity note), so there is no matching panel
 * shape to crop. Same "no Forgeweave-original art for this screen" precedent {@code
 * CraftingStationScreen} already uses for vanilla's crafting-table background -- no NOTICE.md row.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ChestScreen extends StationScreen<ChestMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 222;
    /** Vanilla container backgrounds are 256x256 sheets; the panel is only their top-left corner. */
    private static final int SHEET = 256;

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
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.PATTERN_CHEST.get(), ChestScreen::new);
        event.register(ForgeweaveMenus.PART_CHEST.get(), ChestScreen::new);
    }
}
