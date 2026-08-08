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
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * The Part Builder's GUI. The background is upstream 1.12's real {@code partbuilder.png} (issue
 * #43, replacing the flat placeholder panel from issue #9), cropped to its 176x166 panel region
 * (NOTICE.md); {@link PartBuilderMenu}'s slot coordinates were moved to match its baked-in slot
 * art. {@link AbstractContainerScreen}'s default title/inventory-label positions already match
 * upstream's, so this class only needs the background blit.
 *
 * <p>{@link AbstractContainerScreen#render} does <em>not</em> call {@link #renderTooltip} on its
 * own (unlike the label/slot rendering, that call is left to subclasses) -- every vanilla container
 * screen overrides {@code render} to add it, and this one previously didn't, so item tooltips never
 * showed (issue #43 regression fix).
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PartBuilderScreen extends AbstractContainerScreen<PartBuilderMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/part_builder.png");

    public PartBuilderScreen(PartBuilderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.PART_BUILDER.get(), PartBuilderScreen::new);
    }
}
