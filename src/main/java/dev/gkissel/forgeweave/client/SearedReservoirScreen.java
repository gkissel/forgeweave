package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.SearedReservoirMenu;

/**
 * The seared reservoir's GUI (parity audit T44, issue #475), ported from upstream 1.12's
 * {@code GuiTinkerTank} (NOTICE.md).
 *
 * <p>Every coordinate is upstream's, so the derived background lines up pixel for pixel: a
 * {@value #BASE_WIDTH}x{@value #BASE_HEIGHT} panel, one {@value #TANK_WIDTH}x{@value #TANK_HEIGHT}
 * fluid column at {@code (8, 16)} with the scale overlay from sheet region {@code (122, 0)} drawn
 * over it, and the container name at {@code (8, 6)}. There are no slots and no player inventory --
 * upstream's {@code ContainerTinkerTank} has neither -- so this is the fluid column and nothing
 * else.
 *
 * <p>The column's maths, tooltip cascade and click-to-drain round trip are {@link SmelteryScreen}'s,
 * shared rather than re-derived: upstream shares them too, through {@code GuiUtil} and
 * {@code SmelteryTankRenderer}, and reuses its own {@code gui.smeltery.*} tooltip keys for this
 * screen for the same reason.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SearedReservoirScreen extends AbstractContainerScreen<SearedReservoirMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/seared_reservoir.png");
    private static final int SHEET = 256;

    /** Upstream {@code GuiTinkerTank}'s {@code xSize}/{@code ySize}. */
    private static final int BASE_WIDTH = 122;
    private static final int BASE_HEIGHT = 130;

    /** Upstream's {@code scala} element: {@code new GuiElement(122, 0, 106, 106)} drawn at {@code (8, 16)}. */
    private static final int TANK_X = 8;
    private static final int TANK_Y = 16;
    private static final int TANK_WIDTH = 106;
    private static final int TANK_HEIGHT = 106;
    private static final int SCALA_U = 122;
    private static final int SCALA_V = 0;

    private static final String KEY_PREFIX = "gui.forgeweave.smeltery.";

    public SearedReservoirScreen(SearedReservoirMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, SHEET, SHEET);
        renderTank(graphics);
        // The scale overlay goes on top of the fluids, exactly as upstream draws it in its
        // foreground layer after the background layer has painted the liquids.
        graphics.blit(TEXTURE, leftPos + TANK_X, topPos + TANK_Y, SCALA_U, SCALA_V, TANK_WIDTH, TANK_HEIGHT, SHEET, SHEET);
    }

    /** Upstream's {@code drawContainerName} at {@code (8, 6)}; there is no inventory label to draw. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderTank(GuiGraphics graphics) {
        List<FluidStack> fluids = menu.fluids(level());
        int[] heights = SmelteryScreen.fluidHeights(fluids, capacity(fluids), TANK_HEIGHT);
        int bottom = topPos + TANK_Y + TANK_HEIGHT;
        for (int i = 0; i < heights.length; i++) {
            bottom -= heights[i];
            SmelteryScreen.renderFluid(graphics, fluids.get(i), leftPos + TANK_X, bottom, TANK_WIDTH, heights[i]);
        }
    }

    /** Upstream's {@code max(getFluidAmount(), getCapacity())}: one denominator for drawing, hovering and clicking. */
    private int capacity(List<FluidStack> fluids) {
        int amount = 0;
        for (FluidStack fluid : fluids) {
            amount += fluid.getAmount();
        }
        return Math.max(amount, menu.capacity(level()));
    }

    /** The index of the fluid band under the cursor, or {@code -1}. */
    private int hoveredFluid(double mouseX, double mouseY) {
        if (!isHovering(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
            return -1;
        }
        List<FluidStack> fluids = menu.fluids(level());
        return SmelteryScreen.fluidAt(fluids, capacity(fluids), TANK_HEIGHT, (int) mouseY - topPos - TANK_Y);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, tankTooltip(mouseX, mouseY), mouseX, mouseY);
        }
    }

    /** Upstream {@code GuiUtil#getTankTooltip}: the hovered fluid, or the capacity/free/used cascade. */
    private List<Component> tankTooltip(int mouseX, int mouseY) {
        List<FluidStack> fluids = menu.fluids(level());
        int hovered = hoveredFluid(mouseX, mouseY);
        List<Component> tooltip = new ArrayList<>();
        if (hovered >= 0) {
            FluidStack fluid = fluids.get(hovered);
            tooltip.add(fluid.getHoverName().copy().withStyle(ChatFormatting.WHITE));
            SmelteryScreen.addAmount(tooltip, fluid.getAmount(), Screen.hasShiftDown(),
                    SmelteryScreen.gemValued(level(), fluid));
            return tooltip;
        }

        int used = 0;
        for (FluidStack fluid : fluids) {
            used += fluid.getAmount();
        }
        int capacity = menu.capacity(level());
        tooltip.add(translate("capacity").withStyle(ChatFormatting.WHITE));
        SmelteryScreen.addAmount(tooltip, capacity, Screen.hasShiftDown(), false);
        tooltip.add(translate("capacity_available"));
        SmelteryScreen.addAmount(tooltip, capacity - used, Screen.hasShiftDown(), false);
        tooltip.add(translate("capacity_used"));
        SmelteryScreen.addAmount(tooltip, used, Screen.hasShiftDown(), false);
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.forgeweave.hold_shift").withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }

    /** Clicking a band makes it the drain fluid; index 0 already is, so that click is not sent. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int fluid = button == 0 ? hoveredFluid(mouseX, mouseY) : -1;
        if (fluid > 0 && minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, fluid);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Level level() {
        return minecraft == null ? null : minecraft.level;
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.SEARED_RESERVOIR.get(), SearedReservoirScreen::new);
    }

    private static net.minecraft.network.chat.MutableComponent translate(String key) {
        return Component.translatable(KEY_PREFIX + key);
    }
}
