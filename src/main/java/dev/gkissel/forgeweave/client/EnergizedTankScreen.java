package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import dev.gkissel.forgeweave.block.EnergizedTankBlockEntity;
import dev.gkissel.forgeweave.menu.EnergizedTankMenu;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;

/**
 * The energized tank's GUI (docs/SCOPE.md M8, D-M8-11; issue #1018). The tank has no upstream
 * counterpart, so the layout is original -- but every piece drawn in it is one Forgeweave screens
 * already use, rather than a new look:
 *
 * <ul>
 *   <li>The panel is {@code blank.png}, upstream's plain station background with the player
 *       inventory baked in, which {@link ChestScreen} draws on too. Its whole upper half is bare,
 *       which is the space everything here sits in.
 *   <li>The fuel sample is a {@value #GAUGE_SIZE}x{@value #GAUGE_SIZE} fluid column with the
 *       smeltery sheet's own scale overlay over it, drawn by {@link SmelteryScreen#renderFluid} and
 *       hovered with {@link SmelteryScreen#addAmount}'s tooltip cascade -- the smeltery's gauge,
 *       moved.
 *   <li>The heat readout is {@link TemperatureText}, so this screen cannot disagree with the
 *       smeltery's or JEI's (#933).
 * </ul>
 *
 * <p>Down the right-hand column, in order: the heat the sample gives, what a melt cycle costs at
 * that heat, the Forge Energy buffer as a horizontal bar, the buffer's own numbers under it, and the
 * overdrive button across the bottom. A tank switched off in the config replaces the heat and cost
 * lines -- both of which would read zero -- with a line saying so, and its button is greyed out.
 *
 * <p>The bar is the one thing with no existing sprite behind it: a filled rectangle, because a
 * charge level is not a fluid and none of the ported sheets has a horizontal bar on it. Its numbers
 * are printed underneath rather than left to a hover, so the screen answers "can it afford the next
 * cycle" without being touched.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EnergizedTankScreen extends AbstractContainerScreen<EnergizedTankMenu> {
    /** Upstream {@code GuiTinkerStation.BLANK_BACK}, shared with {@link ChestScreen} (NOTICE.md). */
    private static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/blank.png");
    /** For its 52x52 scale overlay alone; see {@link SmelteryScreen}'s own {@code SCALA} constants. */
    private static final ResourceLocation SMELTERY_SHEET =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/smeltery.png");
    private static final int SHEET = 256;

    private static final int GAUGE_X = 8;
    private static final int GAUGE_Y = 16;
    private static final int GAUGE_SIZE = 52;
    private static final int SCALA_U = 176;
    private static final int SCALA_V = 76;

    /** The right-hand column: everything but the gauge, stacked between the title and the inventory. */
    private static final int COLUMN_X = 66;
    private static final int COLUMN_WIDTH = 102;
    private static final int HEAT_Y = 16;
    private static final int COST_Y = 26;
    private static final int BAR_Y = 36;
    private static final int BAR_HEIGHT = 8;
    private static final int ENERGY_Y = 46;
    private static final int BUTTON_Y = 56;
    private static final int BUTTON_HEIGHT = 15;

    private static final int LINE_HEIGHT = 10;

    /** Vanilla's own label grey, which is what every other label on this panel is drawn in. */
    private static final int TEXT_COLOR = 0x404040;
    /** The bar's recess and its charge, dark-on-panel and Forge Energy's own orange. */
    private static final int BAR_TRACK_COLOR = 0xFF373737;
    private static final int BAR_FILL_COLOR = 0xFFE8620E;

    private static final String KEY_PREFIX = "gui.forgeweave.energized_tank.";

    private Button overdrive;

    public EnergizedTankScreen(EnergizedTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = EnergizedTankMenu.PANEL_WIDTH;
        imageHeight = EnergizedTankMenu.PANEL_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        overdrive = addRenderableWidget(Button.builder(overdriveLabel(), button -> pressOverdrive())
                .bounds(leftPos + COLUMN_X, topPos + BUTTON_Y, COLUMN_WIDTH, BUTTON_HEIGHT)
                .build());
        overdrive.active = menu.active();
    }

    /** The button's label and its enabled state both follow the tank, which a cable can change under us. */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (overdrive != null) {
            overdrive.setMessage(overdriveLabel());
            overdrive.active = menu.active();
        }
    }

    private Component overdriveLabel() {
        return translate(menu.overdrive(level()) ? "overdrive.on" : "overdrive.off");
    }

    /** The one mutation: the server flips the saved flag -- see {@link EnergizedTankMenu#clickMenuButton}. */
    private void pressOverdrive() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, EnergizedTankMenu.OVERDRIVE_BUTTON);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(PANEL, leftPos, topPos, 0, 0, imageWidth, imageHeight, SHEET, SHEET);
        renderSample(graphics);
        renderEnergyBar(graphics);
    }

    /** The sample column, then the scale overlay over it, exactly as {@link SmelteryScreen} layers them. */
    private void renderSample(GuiGraphics graphics) {
        FluidStack sample = menu.sample(level());
        if (!sample.isEmpty()) {
            int height = Math.min(GAUGE_SIZE,
                    GAUGE_SIZE * sample.getAmount() / EnergizedTankBlockEntity.SAMPLE_CAPACITY);
            SmelteryScreen.renderFluid(graphics, sample, leftPos + GAUGE_X,
                    topPos + GAUGE_Y + GAUGE_SIZE - height, GAUGE_SIZE, height);
        }
        graphics.blit(SMELTERY_SHEET, leftPos + GAUGE_X, topPos + GAUGE_Y, SCALA_U, SCALA_V,
                GAUGE_SIZE, GAUGE_SIZE, SHEET, SHEET);
    }

    private void renderEnergyBar(GuiGraphics graphics) {
        int x = leftPos + COLUMN_X;
        int y = topPos + BAR_Y;
        graphics.fill(x, y, x + COLUMN_WIDTH, y + BAR_HEIGHT, BAR_TRACK_COLOR);
        int capacity = menu.energyCapacity(level());
        if (capacity <= 0) {
            return;
        }
        int filled = (COLUMN_WIDTH - 2) * Math.min(menu.energy(level()), capacity) / capacity;
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + BAR_HEIGHT - 1, BAR_FILL_COLOR);
        }
    }

    /** Vanilla's two labels, plus the right-hand column's readouts. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.active()) {
            line(graphics, HEAT_Y, translate("heat", TemperatureText.format(menu.temperature(level()))));
            line(graphics, COST_Y, translate("cost", menu.costPerMeltTick(level())));
        } else {
            // Wrapped, since the notice is longer than the column is wide; the two lines it takes are
            // exactly the two the heat and cost readouts would have used.
            List<net.minecraft.util.FormattedCharSequence> wrapped =
                    font.split(translate("disabled"), COLUMN_WIDTH);
            for (int i = 0; i < wrapped.size(); i++) {
                graphics.drawString(font, wrapped.get(i), COLUMN_X, HEAT_Y + i * LINE_HEIGHT, TEXT_COLOR, false);
            }
        }
        line(graphics, ENERGY_Y, translate("energy", menu.energy(level()), menu.energyCapacity(level())));
    }

    private void line(GuiGraphics graphics, int y, Component text) {
        graphics.drawString(font, text, COLUMN_X, y, TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(GAUGE_X, GAUGE_Y, GAUGE_SIZE, GAUGE_SIZE, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, sampleTooltip(), mouseX, mouseY);
        }
    }

    /** The smeltery's own fluid tooltip, or a line saying the tank is holding nothing yet. */
    private List<Component> sampleTooltip() {
        FluidStack sample = menu.sample(level());
        List<Component> tooltip = new ArrayList<>();
        if (sample.isEmpty()) {
            tooltip.add(translate("no_sample").withStyle(ChatFormatting.WHITE));
            return tooltip;
        }
        tooltip.add(sample.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        SmelteryScreen.addAmount(tooltip, sample.getAmount(), Screen.hasShiftDown(), false);
        return tooltip;
    }

    private Level level() {
        return minecraft == null ? null : minecraft.level;
    }

    private static net.minecraft.network.chat.MutableComponent translate(String key, Object... args) {
        return Component.translatable(KEY_PREFIX + key, args);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.ENERGIZED_TANK.get(), EnergizedTankScreen::new);
    }
}
