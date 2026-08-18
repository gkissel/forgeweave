package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.SearedFurnaceBlockEntity;
import dev.gkissel.forgeweave.block.SearedFurnaceBlockEntity.Progress;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.SearedFurnaceMenu;
import dev.gkissel.forgeweave.menu.SmelteryMenu;

/**
 * The seared furnace controller's GUI (issue #442), ported from upstream 1.12's {@code
 * GuiSearedFurnace}/{@code GuiSearedFurnaceSideInventory} over {@code GuiHeatingStructureFuelTank}
 * (NOTICE.md), on upstream's own {@code seared_furnace.png}:
 *
 * <ul>
 *   <li><b>Flame</b> at {@code (26, 41)}, the 28x28 sprite at sheet {@code (176, 76)}, filling
 *       upward with {@code getFuelPercentage()} -- {@code fuel / fuelQuality}, how much of the last
 *       consumed fuel unit is left.
 *   <li><b>Fuel gauge</b> at {@code (71, 16)}, 12x52, with the same tooltip box and the same three
 *       tooltip branches as the smeltery's.
 *   <li><b>Slot grid</b>: {@link SmelteryMenu}'s three-column module off the panel's left edge, on
 *       this sheet's own slot tiles at {@code (0, 166)}/{@code (22, 166)}, with a 3x16 bar per
 *       occupied slot from the five variants at {@code (176..188, 150)} chosen by
 *       {@link Progress}, and upstream's {@code gui.searedfurnace.progress.*} tooltip over the bar.
 * </ul>
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SearedFurnaceScreen extends StationScreen<SearedFurnaceMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/seared_furnace.png");
    private static final int SHEET = 256;
    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = SmelteryMenu.PANEL_HEIGHT;

    /** Upstream {@code GuiSearedFurnace}: {@code flame = (176, 76, 28, 28)} drawn at {@code (26, 41)}. */
    private static final int FLAME_X = 26;
    private static final int FLAME_Y = 41;
    private static final int FLAME_U = 176;
    private static final int FLAME_V = 76;
    private static final int FLAME_SIZE = 28;

    /** Upstream {@code drawFuel(71, 16, 12, 52)}. */
    private static final int FUEL_X = 71;
    private static final int FUEL_Y = 16;
    private static final int FUEL_WIDTH = 12;
    private static final int FUEL_HEIGHT = 52;

    private static final int CELL_U = 0;
    private static final int CELL_EMPTY_U = 22;
    private static final int CELL_V = 166;
    /** Upstream {@code GuiSearedFurnaceSideInventory}'s five bars: progress, unprogress, uberHeat, noMelt, complete. */
    private static final int BAR_PROGRESS_U = 176;
    private static final int BAR_UNPROGRESS_U = 179;
    private static final int BAR_NO_SPACE_U = 182;
    private static final int BAR_NO_RECIPE_U = 185;
    private static final int BAR_COMPLETE_U = 188;
    private static final int BAR_V = 150;
    private static final int BAR_WIDTH = 3;
    private static final int BAR_HEIGHT = 16;

    private static final String KEY_PREFIX = "gui.forgeweave.seared_furnace.";
    private static final String SMELTERY_PREFIX = "gui.forgeweave.smeltery.";

    private Rect2i sliderTrack = new Rect2i(0, 0, 0, 0);
    private boolean draggingSlider;

    public SearedFurnaceScreen(SearedFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, SHEET, SHEET);
        renderGrid(graphics);
        float fuel = menu.fuelPercentage(level());
        if (fuel > 0) {
            int height = 1 + Math.round(fuel * (FLAME_SIZE - 1));
            graphics.blit(TEXTURE, leftPos + FLAME_X, topPos + FLAME_Y + FLAME_SIZE - height,
                    FLAME_U, FLAME_V + FLAME_SIZE - height, FLAME_SIZE, height, SHEET, SHEET);
        }
        renderFuel(graphics);
    }

    private void renderGrid(GuiGraphics graphics) {
        int slots = menu.slotCount();
        int rows = SmelteryMenu.visibleMeltRows(slots);
        int gridX = SmelteryMenu.gridX(slots);
        int gridLeft = leftPos + gridX + SmelteryMenu.GRID_BORDER;
        int gridTop = topPos + SmelteryMenu.GRID_Y + SmelteryMenu.GRID_BORDER;
        SideInventoryPanel.renderBorder(graphics, leftPos + gridX, topPos + SmelteryMenu.GRID_Y,
                SmelteryMenu.gridWidth(slots), SmelteryMenu.gridHeight(slots), true);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < SmelteryMenu.MELT_COLUMNS; col++) {
                int index = (menu.scrollRow() + row) * SmelteryMenu.MELT_COLUMNS + col;
                graphics.blit(TEXTURE,
                        gridLeft + col * SmelteryMenu.CELL_WIDTH, gridTop + row * SmelteryMenu.SLOT_SIZE,
                        index < slots ? CELL_U : CELL_EMPTY_U, CELL_V,
                        SmelteryMenu.CELL_WIDTH, SmelteryMenu.SLOT_SIZE, SHEET, SHEET);
                Progress state = index < slots ? menu.progressState(level(), index) : Progress.NONE;
                if (state == Progress.NONE) {
                    continue;
                }
                float progress = menu.progress(level(), index);
                int height = 1 + Math.round(Math.clamp(progress, 0f, 1f) * (BAR_HEIGHT - 1));
                int x = gridLeft + col * SmelteryMenu.CELL_WIDTH + 1;
                int y = gridTop + row * SmelteryMenu.SLOT_SIZE + 1;
                graphics.blit(TEXTURE, x, y + BAR_HEIGHT - height, barU(state), BAR_V + BAR_HEIGHT - height,
                        BAR_WIDTH, height, SHEET, SHEET);
            }
        }
        int maxScrollRow = SmelteryMenu.meltScrollRows(slots);
        if (maxScrollRow <= 0) {
            sliderTrack = new Rect2i(0, 0, 0, 0);
            return;
        }
        sliderTrack = new Rect2i(gridLeft + SmelteryMenu.MELT_COLUMNS * SmelteryMenu.CELL_WIDTH, gridTop,
                SideInventoryPanel.SLIDER_WIDTH, rows * SmelteryMenu.SLOT_SIZE);
        SideInventoryPanel.renderSlider(graphics, sliderTrack, menu.scrollRow(), maxScrollRow);
    }

    /** Which of upstream's five bar variants a slot state draws. */
    static int barU(Progress state) {
        return switch (state) {
            case NO_RECIPE -> BAR_NO_RECIPE_U;
            case COMPLETE -> BAR_COMPLETE_U;
            case NO_SPACE -> BAR_NO_SPACE_U;
            case NO_FUEL, NO_HEAT -> BAR_UNPROGRESS_U;
            default -> BAR_PROGRESS_U;
        };
    }

    /** Upstream's {@code tooltipText} per state, as the {@link #KEY_PREFIX} suffix; {@code null} while cooking normally. */
    @Nullable
    static String barTooltip(Progress state) {
        return switch (state) {
            case NO_RECIPE -> "progress.no_recipe";
            case COMPLETE -> "progress.complete";
            case NO_SPACE -> "progress.no_space";
            case NO_FUEL -> "progress.no_fuel";
            case NO_HEAT -> "progress.no_heat";
            default -> null;
        };
    }

    private void renderFuel(GuiGraphics graphics) {
        FluidStack fuel = menu.fuel(level());
        if (fuel.isEmpty()) {
            return;
        }
        int height = Math.min(FUEL_HEIGHT, FUEL_HEIGHT * fuel.getAmount() / SearedTankBlockEntity.CAPACITY);
        SmelteryScreen.renderFluid(graphics, fuel, leftPos + FUEL_X, topPos + FUEL_Y + FUEL_HEIGHT - height, FUEL_WIDTH, height);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(FUEL_X, FUEL_Y, FUEL_WIDTH, FUEL_HEIGHT, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, fuelTooltip(), mouseX, mouseY);
            return;
        }
        int slots = menu.slotCount();
        int index = SmelteryScreen.heatBarAt(menu.scrollRow(), SmelteryMenu.visibleMeltRows(slots),
                mouseX - leftPos - SmelteryMenu.gridX(slots) - SmelteryMenu.GRID_BORDER,
                mouseY - topPos - SmelteryMenu.GRID_Y - SmelteryMenu.GRID_BORDER);
        if (index < 0 || index >= slots) {
            return;
        }
        String key = barTooltip(menu.progressState(level(), index));
        if (key != null) {
            graphics.renderTooltip(font, Component.translatable(KEY_PREFIX + key), mouseX, mouseY);
        }
    }

    /** Upstream {@code GuiHeatingStructureFuelTank#drawFuelTooltip}, the same three branches as {@code SmelteryScreen}'s. */
    private List<Component> fuelTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(SMELTERY_PREFIX + "fuel").withStyle(ChatFormatting.WHITE));
        FluidStack fuel = menu.fuel(level());
        if (fuel.isEmpty()) {
            tooltip.add(Component.translatable(SMELTERY_PREFIX + "fuel.empty"));
            return tooltip;
        }
        if (menu.loadedFuel(level()).isEmpty()) {
            tooltip.add(Component.translatable(SMELTERY_PREFIX + "fuel.invalid", fuel.getHoverName()).withStyle(ChatFormatting.DARK_RED));
            return tooltip;
        }
        tooltip.add(fuel.getHoverName().copy());
        SmelteryScreen.addAmount(tooltip, fuel.getAmount(), Screen.hasShiftDown(), SmelteryScreen.gemValued(level(), fuel));
        tooltip.add(Component.translatable(SMELTERY_PREFIX + "fuel.heat", TemperatureText.format(menu.temperature(level())))
                .withStyle(ChatFormatting.GRAY));
        return tooltip;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int slots = menu.slotCount();
        if (SmelteryMenu.meltScrollRows(slots) > 0
                && isHovering(SmelteryMenu.gridX(slots), SmelteryMenu.GRID_Y,
                        SmelteryMenu.gridWidth(slots), SmelteryMenu.gridHeight(slots), mouseX, mouseY)) {
            menu.setScrollRow(menu.scrollRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean sliderClicked(double mouseX, double mouseY) {
        if (!sliderTrack.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        draggingSlider = true;
        scrollTo(mouseY);
        return true;
    }

    @Override
    protected boolean sliderDragged(double mouseX, double mouseY) {
        if (!draggingSlider) {
            return false;
        }
        scrollTo(mouseY);
        return true;
    }

    @Override
    protected void sliderReleased() {
        draggingSlider = false;
    }

    private void scrollTo(double mouseY) {
        menu.setScrollRow(SideInventoryPanel.scrollRowAt(sliderTrack.getY(), sliderTrack.getHeight(),
                SmelteryMenu.meltScrollRows(menu.slotCount()), mouseY));
    }

    /** Upstream {@code GuiSearedFurnace#updateScreen}: a structure rebuilt to a different size closes the screen. */
    @Override
    protected void containerTick() {
        super.containerTick();
        SearedFurnaceBlockEntity furnace = menu.furnace(level());
        if (furnace != null && furnace.isFormed() && furnace.items().size() != menu.slotCount()
                && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
    }

    /** Upstream's {@code GuiMultiModule} draws no title or inventory label. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public List<Rect2i> extraGuiAreas() {
        List<Rect2i> areas = super.extraGuiAreas();
        int slots = menu.slotCount();
        areas.add(new Rect2i(leftPos + SmelteryMenu.gridX(slots), topPos + SmelteryMenu.GRID_Y,
                SmelteryMenu.gridWidth(slots), SmelteryMenu.gridHeight(slots)));
        return areas;
    }

    @Nullable
    private Level level() {
        return minecraft == null ? null : minecraft.level;
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.SEARED_FURNACE.get(), SearedFurnaceScreen::new);
    }
}
