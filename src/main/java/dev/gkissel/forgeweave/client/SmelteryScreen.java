package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.SmelteryMenu;

/**
 * The smeltery controller's GUI (docs/SCOPE.md M2 issue #101), ported from upstream 1.12's {@code
 * GuiSmeltery} plus the tank helpers in {@code GuiUtil}/{@code SmelteryTankRenderer} (NOTICE.md).
 *
 * <h2>Layout, and where the numbers come from</h2>
 *
 * <p>Every coordinate below is upstream's own, so the derived background lines up pixel for pixel:
 *
 * <ul>
 *   <li><b>Fluid column</b> at {@code (8, 16)}, {@value #TANK_WIDTH}x{@value #TANK_HEIGHT} --
 *       {@code GuiSmeltery}'s {@code drawGuiTank(tank, 8 + cornerX, 16 + cornerY, scala.w, scala.h)}
 *       and the matching {@code (8, 16, 60, 68)} hit box its tooltip and click handlers use.
 *   <li><b>Scale overlay</b> ("scala") from sheet region {@code (176, 76, 52, 52)}, drawn over the
 *       fluids at the same {@code (8, 16)} -- {@code GuiSmeltery}'s {@code scala} element.
 *   <li><b>Fuel gauge</b> at {@code (71, 16)}, {@value #FUEL_WIDTH}x{@value #FUEL_HEIGHT} --
 *       {@code drawFuel(71, 16, 12, 52)}, with the same {@code 71 <= x < 83, 16 <= y < 68} tooltip
 *       box.
 *   <li><b>Player inventory</b> at {@code (8, 84)} -- {@code ContainerSmeltery}'s
 *       {@code addPlayerInventory(inventoryPlayer, 8, 84)}, laid out by {@link SmelteryMenu}.
 * </ul>
 *
 * <p>The background is upstream's {@code smeltery.png} copied whole rather than cropped to the panel
 * (NOTICE.md), because the sheet's other regions -- the scale overlay here, and the slot tiles and
 * heat bars issue #96's melting grid needs -- are all still live. The panel itself is L-shaped: the
 * top-right of the 176x166 region is transparent in upstream's art too, and is exactly where #96's
 * melting slots go. Until they land that corner renders empty, which is upstream's own background
 * with nothing drawn over it, not a layout mistake.
 *
 * <h2>Authority</h2>
 *
 * <p>Read-only except for one thing: clicking a fluid makes it the drain fluid. That goes out as a
 * vanilla container button click carrying the index and nothing else, and the reordering happens on
 * the server ({@link SmelteryMenu#clickMenuButton}); the column redraws when the block entity's
 * update packet comes back. Upstream sends its own {@code SmelteryFluidClicked} packet for the same
 * round trip -- {@code handleInventoryButtonClick} is the modern equivalent, so no custom payload is
 * needed.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SmelteryScreen extends StationScreen<SmelteryMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/smeltery.png");
    private static final int SHEET = 256;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 166;

    /** Upstream {@code GuiSmeltery}: the tank rectangle, and the {@code scala} element drawn over it. */
    private static final int TANK_X = 8;
    private static final int TANK_Y = 16;
    private static final int TANK_WIDTH = 52;
    private static final int TANK_HEIGHT = 52;
    private static final int SCALA_U = 176;
    private static final int SCALA_V = 76;

    /** Upstream {@code GuiSmeltery#drawFuel(71, 16, 12, 52)}. */
    private static final int FUEL_X = 71;
    private static final int FUEL_Y = 16;
    private static final int FUEL_WIDTH = 12;
    private static final int FUEL_HEIGHT = 52;

    /** Upstream {@code SmelteryTankRenderer.calcLiquidHeights}'s {@code min} for GUI tanks: 3px. */
    private static final int MIN_FLUID_HEIGHT = 3;

    /** Fluid sprites are 16x16 atlas tiles; the column is tiled with them, not stretched. */
    private static final int FLUID_TILE = 16;

    /**
     * Melt-grid art, upstream {@code GuiSmelterySideInventory}: a 22x18 cell with a slot at
     * {@code (0, 166)}, the "no slot here" filler at {@code (22, 166)}, and the 3x16 heat bars at
     * {@code (176, 150)} (progressing) and {@code (179, 150)} (stalled).
     */
    private static final int CELL_U = 0;
    private static final int CELL_EMPTY_U = 22;
    private static final int CELL_V = 166;
    private static final int BAR_ACTIVE_U = 176;
    private static final int BAR_IDLE_U = 179;
    private static final int BAR_V = 150;
    private static final int BAR_WIDTH = 3;
    private static final int BAR_HEIGHT = 16;

    /**
     * Upstream {@code Material.VALUE_Block/VALUE_Ingot/VALUE_Nugget}, the unit sizes its fluid
     * tooltips break an amount down into.
     *
     * <p>Upstream derives these by scanning every registered casting recipe for one that casts the
     * fluid with no cast (a block), with an ingot cast, and so on. Casting has since landed (#100),
     * so that scan is now possible here -- and still not worth it: every recipe it would find uses
     * exactly these three numbers, so the scan is a per-tooltip registry walk that can only ever
     * return the constants below. Derive them if a material ever ships with a non-standard ingot
     * value; until then this is the same answer for less work.
     *
     * <p>Deliberately not {@code FaucetBlockEntity.TRANSACTION_AMOUNT}, which is also 144: that is a
     * faucet's pour rate, a different quantity that happens to coincide.
     */
    private static final int VALUE_BLOCK = 1296;
    private static final int VALUE_INGOT = 144;
    private static final int VALUE_NUGGET = 16;
    private static final int VALUE_KILOBUCKET = 1_000_000;
    private static final int VALUE_BUCKET = 1_000;

    private static final String KEY_PREFIX = "gui.forgeweave.smeltery.";

    public SmelteryScreen(SmelteryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, SHEET, SHEET);
        renderMeltGrid(graphics);
        renderFuel(graphics);
        renderTank(graphics);
        // The scale marks go over the fluids, as upstream draws them last in its background layer.
        graphics.blit(TEXTURE, leftPos + TANK_X, topPos + TANK_Y, SCALA_U, SCALA_V, TANK_WIDTH, TANK_HEIGHT, SHEET, SHEET);
    }

    /* Melt grid (issue #96's inventory, drawn upstream's way) */

    /**
     * The three-column grid of melting slots in the panel's notch, with a heat bar down the left of
     * each cell -- upstream's {@code GuiSmelterySideInventory}.
     *
     * <p>Slot tiles come from the same sheet as everything else: {@code (0, 166, 22, 18)} for a cell
     * that has a slot and {@code (22, 166, 22, 18)} for the "no slot here" filler upstream draws past
     * the end of a partial last row, so the grid stays a rectangle.
     *
     * <p><b>Always the full {@value SmelteryMenu#MELT_VISIBLE_ROWS} rows</b> (issue #146). Upstream
     * sizes its side inventory to its slot count, which it can afford because that inventory is a
     * free-floating module hanging off the <em>side</em> of a rectangular GUI. Forgeweave's grid
     * instead sits in the notch of the L-shaped panel art -- a hole of fixed size -- so a grid sized
     * to the slot count leaves the rest of that hole see-through: a smeltery with a 1x1x2 interior
     * drew a one-row, two-slot frame floating over a transparent gap the size of the notch, which is
     * the "corrupted fragment" the playtest reported (and which any minimum-size smeltery shows the
     * moment its contents finish melting and the items stop papering over it). Drawing the frame at
     * the notch's size and filling the cells past the end with the filler tile is upstream's own
     * partial-row behaviour applied to whole rows, and it makes the drawn grid the same rectangle
     * {@link #mouseScrolled} already hit-tests.
     */
    private void renderMeltGrid(GuiGraphics graphics) {
        int slots = menu.meltSlotCount();
        int gridLeft = leftPos + SmelteryMenu.GRID_X + SmelteryMenu.GRID_BORDER;
        int gridTop = topPos + SmelteryMenu.GRID_Y + SmelteryMenu.GRID_BORDER;
        // Upstream wraps its side inventories in generic.png's nine-sliced frame; the panel art's
        // notch is cut to exactly this width so the two read as one window.
        SideInventoryPanel.renderBorder(graphics, leftPos + SmelteryMenu.GRID_X, topPos + SmelteryMenu.GRID_Y,
                gridWidth(), gridHeight());
        for (int row = 0; row < SmelteryMenu.MELT_VISIBLE_ROWS; row++) {
            for (int col = 0; col < SmelteryMenu.MELT_COLUMNS; col++) {
                int index = (menu.scrollRow() + row) * SmelteryMenu.MELT_COLUMNS + col;
                graphics.blit(TEXTURE,
                        gridLeft + col * SmelteryMenu.CELL_WIDTH, gridTop + row * SmelteryMenu.SLOT_SIZE,
                        index < slots ? CELL_U : CELL_EMPTY_U, CELL_V,
                        SmelteryMenu.CELL_WIDTH, SmelteryMenu.SLOT_SIZE, SHEET, SHEET);
            }
        }
        renderHeatBars(graphics, slots, gridLeft, gridTop);
    }

    /**
     * One bar per occupied slot, filling upward as the item heats -- upstream's four
     * {@code GuiElementScalable(176..185, 150, 3, 16)} variants, chosen by why a slot is or is not
     * progressing so the bar doubles as the explanation.
     */
    private void renderHeatBars(GuiGraphics graphics, int slots, int gridLeft, int gridTop) {
        List<ItemStack> items = menu.meltingItems(level());
        for (int row = 0; row < SmelteryMenu.MELT_VISIBLE_ROWS; row++) {
            for (int col = 0; col < SmelteryMenu.MELT_COLUMNS; col++) {
                int index = (menu.scrollRow() + row) * SmelteryMenu.MELT_COLUMNS + col;
                if (index >= slots || index >= items.size() || items.get(index).isEmpty()) {
                    continue;
                }
                float progress = menu.meltProgress(level(), index);
                int height = 1 + Math.round(Math.clamp(progress, 0f, 1f) * (BAR_HEIGHT - 1));
                int x = gridLeft + col * SmelteryMenu.CELL_WIDTH + 1;
                int y = gridTop + row * SmelteryMenu.SLOT_SIZE + 1;
                // Bars fill from the bottom, so the drawn slice is the bottom `height` px of both
                // the sprite and the bar's own box.
                graphics.blit(TEXTURE, x, y + BAR_HEIGHT - height,
                        progress > 0f ? BAR_ACTIVE_U : BAR_IDLE_U, BAR_V + BAR_HEIGHT - height,
                        BAR_WIDTH, height, SHEET, SHEET);
            }
        }
    }

    /** Upstream {@code GuiSideInventory}'s wheel scroll, the same one {@code SideInventoryPanel} gives the station side panels. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (SmelteryMenu.meltRows(menu.meltSlotCount()) > SmelteryMenu.MELT_VISIBLE_ROWS
                && isHovering(SmelteryMenu.GRID_X, SmelteryMenu.GRID_Y, gridWidth(), gridHeight(), mouseX, mouseY)) {
            menu.setScrollRow(menu.scrollRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * The melt grid's drawn frame -- one rectangle, sized to the panel art's notch rather than to the
     * smeltery, so it is also the box {@link #mouseScrolled} hit-tests and the box
     * {@link #renderMeltGrid} paints. {@code SmelteryMeltGridTest} pins both against the art.
     */
    static int gridWidth() {
        return SmelteryMenu.MELT_COLUMNS * SmelteryMenu.CELL_WIDTH + SmelteryMenu.GRID_BORDER * 2;
    }

    /** @see #gridWidth() */
    static int gridHeight() {
        return SmelteryMenu.MELT_VISIBLE_ROWS * SmelteryMenu.SLOT_SIZE + SmelteryMenu.GRID_BORDER * 2;
    }

    /**
     * Upstream's {@code GuiMultiModule} draws no title or inventory label, and the L-shaped panel has
     * no room for either -- the "Inventory" label's usual spot is transparent background here.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    /* Tank */

    private void renderTank(GuiGraphics graphics) {
        List<FluidStack> fluids = menu.fluids(level());
        int[] heights = fluidHeights(fluids, capacity(fluids), TANK_HEIGHT);
        int bottom = topPos + TANK_Y + TANK_HEIGHT;
        for (int i = 0; i < heights.length; i++) {
            bottom -= heights[i];
            renderFluid(graphics, fluids.get(i), leftPos + TANK_X, bottom, TANK_WIDTH, heights[i]);
        }
    }

    /**
     * Upstream {@code SmelteryTankRenderer#calcLiquidHeights}: every fluid gets at least
     * {@value #MIN_FLUID_HEIGHT}px so a trace amount is still visible and still clickable, a
     * part-full tank keeps that much empty headroom, and any overflow is shaved a pixel at a time
     * off whichever band is currently tallest.
     */
    static int[] fluidHeights(List<FluidStack> fluids, int capacity, int height) {
        int[] heights = new int[fluids.size()];
        if (fluids.isEmpty() || capacity <= 0) {
            return heights;
        }
        int total = 0;
        for (int i = 0; i < fluids.size(); i++) {
            int amount = fluids.get(i).getAmount();
            total += amount;
            heights[i] = Math.max(MIN_FLUID_HEIGHT, (int) Math.ceil((double) amount * height / capacity));
        }
        if (total < capacity) {
            height -= MIN_FLUID_HEIGHT;
        }
        int sum;
        do {
            sum = 0;
            int tallest = -1;
            int tallestIndex = 0;
            for (int i = 0; i < heights.length; i++) {
                sum += heights[i];
                if (heights[i] > tallest) {
                    tallest = heights[i];
                    tallestIndex = i;
                }
            }
            if (heights[tallestIndex] == 0) {
                break; // cannot shrink any further without going negative
            }
            if (sum > height) {
                heights[tallestIndex]--;
            }
        } while (sum > height);
        return heights;
    }

    /**
     * The index of the fluid band under the cursor, or {@code -1}. Bands stack upward from the
     * bottom, so the cursor's distance from the bottom edge is what is walked -- upstream's
     * {@code GuiUtil#getFluidStackIndexAtPosition}.
     */
    static int fluidAt(List<FluidStack> fluids, int capacity, int height, int offsetFromTop) {
        int[] heights = fluidHeights(fluids, capacity, height);
        int fromBottom = height - offsetFromTop - 1;
        for (int i = 0; i < heights.length; i++) {
            if (fromBottom < heights[i]) {
                return i;
            }
            fromBottom -= heights[i];
        }
        return -1;
    }

    /** The index of the fluid band under the cursor in screen coordinates, or {@code -1}. */
    private int hoveredFluid(double mouseX, double mouseY) {
        if (!isHovering(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
            return -1;
        }
        List<FluidStack> fluids = menu.fluids(level());
        return fluidAt(fluids, capacity(fluids), TANK_HEIGHT, (int) mouseY - topPos - TANK_Y);
    }

    /**
     * The denominator the bands are scaled against, upstream's
     * {@code max(getFluidAmount(), getCapacity())} -- one expression used by drawing, hovering and
     * clicking alike, so the three can never disagree about where a band starts.
     */
    private int capacity(List<FluidStack> fluids) {
        int amount = 0;
        for (FluidStack fluid : fluids) {
            amount += fluid.getAmount();
        }
        return Math.max(amount, menu.capacity(level()));
    }

    /* Fuel -- see SmelteryMenu#fuel for where the fill actually comes from. */

    private void renderFuel(GuiGraphics graphics) {
        FluidStack fuel = menu.fuel(level());
        int capacity = menu.fuelCapacity(level());
        if (fuel.isEmpty() || capacity <= 0) {
            return;
        }
        int height = Math.min(FUEL_HEIGHT, FUEL_HEIGHT * fuel.getAmount() / capacity);
        renderFluid(graphics, fuel, leftPos + FUEL_X, topPos + FUEL_Y + FUEL_HEIGHT - height, FUEL_WIDTH, height);
    }

    /* Fluid rendering */

    /**
     * Draws a fluid's still sprite tiled over the given rectangle, in its client tint.
     *
     * <p>Upstream tiles by hand, emitting a quad per tile with interpolated UVs for the partial tile
     * at each edge. Scissoring the rectangle and drawing whole tiles over it produces the identical
     * pixels -- a clipped tile shows exactly the sprite's leading rows and columns, which is what
     * those interpolated UVs select -- in a fraction of the code, and without reaching past
     * {@link GuiGraphics} into the buffer builder.
     */
    private void renderFluid(GuiGraphics graphics, FluidStack fluid, int x, int y, int width, int height) {
        if (fluid.isEmpty() || width <= 0 || height <= 0 || minecraft == null) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extensions.getStillTexture(fluid));
        int tint = extensions.getTintColor(fluid);

        graphics.setColor(
                FastColor.ARGB32.red(tint) / 255.0F,
                FastColor.ARGB32.green(tint) / 255.0F,
                FastColor.ARGB32.blue(tint) / 255.0F,
                FastColor.ARGB32.alpha(tint) / 255.0F);
        graphics.enableScissor(x, y, x + width, y + height);
        for (int tileY = y; tileY < y + height; tileY += FLUID_TILE) {
            for (int tileX = x; tileX < x + width; tileX += FLUID_TILE) {
                graphics.blit(tileX, tileY, 0, FLUID_TILE, FLUID_TILE, sprite);
            }
        }
        graphics.disableScissor();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /* Tooltips */

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, tankTooltip(mouseX, mouseY), mouseX, mouseY);
        } else if (isHovering(FUEL_X, FUEL_Y, FUEL_WIDTH, FUEL_HEIGHT, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font, fuelTooltip(), mouseX, mouseY);
        }
    }

    /**
     * Upstream {@code GuiUtil#getTankTooltip}: over a fluid, its name and how much of it there is;
     * over the empty headroom, the tank's capacity, free space and used space.
     */
    private List<Component> tankTooltip(int mouseX, int mouseY) {
        List<FluidStack> fluids = menu.fluids(level());
        int hovered = hoveredFluid(mouseX, mouseY);
        List<Component> tooltip = new ArrayList<>();
        if (hovered >= 0) {
            FluidStack fluid = fluids.get(hovered);
            tooltip.add(fluid.getHoverName().copy().withStyle(ChatFormatting.WHITE));
            addAmount(tooltip, fluid.getAmount(), Screen.hasShiftDown());
            return tooltip;
        }

        int used = 0;
        for (FluidStack fluid : fluids) {
            used += fluid.getAmount();
        }
        int capacity = menu.capacity(level());
        tooltip.add(translate("capacity").withStyle(ChatFormatting.WHITE));
        addAmount(tooltip, capacity, Screen.hasShiftDown());
        tooltip.add(translate("capacity_available"));
        addAmount(tooltip, capacity - used, Screen.hasShiftDown());
        tooltip.add(translate("capacity_used"));
        addAmount(tooltip, used, Screen.hasShiftDown());
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.forgeweave.hold_shift").withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }

    /**
     * Upstream {@code GuiHeatingStructureFuelTank#drawFuelTooltip}. The empty branch is all that can
     * be reached today; issue #97's fuel state adds the fluid name and temperature lines here.
     */
    private List<Component> fuelTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(translate("fuel").withStyle(ChatFormatting.WHITE));
        FluidStack fuel = menu.fuel(level());
        if (!fuel.isEmpty()) {
            tooltip.add(fuel.getHoverName().copy());
            addAmount(tooltip, fuel.getAmount(), Screen.hasShiftDown());
        }
        // #131's burn state, which unlike the fuel liquid is synced (see SmelteryMenu#fuel).
        int temperature = menu.fuelTemperature(level());
        if (temperature > 0) {
            tooltip.add(Component.translatable(KEY_PREFIX + "fuel.heat", temperature)
                    .withStyle(ChatFormatting.GRAY));
        } else if (fuel.isEmpty()) {
            tooltip.add(translate("fuel.empty"));
        }
        return tooltip;
    }

    /**
     * Upstream {@code GuiUtil#liquidToString}/{@code amountToString}: break the amount into the
     * largest units that divide it, remainder last. Holding shift drops the metal units and shows
     * plain buckets, which is what {@code tooltip.forgeweave.hold_shift} advertises.
     *
     * <p>Amounts are <em>not</em> assumed to be whole ingots. Melting (#96) yields per-item values
     * read off loot tables -- vanilla copper ore melts at 504 mB and nether gold ore at 64 -- so
     * fractional ingots are reachable without alloying, and the cascade has to carry the remainder
     * down to millibuckets rather than round it away. That is what {@code SmelteryTooltipTest}
     * pins.
     *
     * @param bucketsOnly the shift state, passed in rather than read from {@link Screen} so the
     *                    cascade is exercisable without a client
     */
    static void addAmount(List<Component> tooltip, int amount, boolean bucketsOnly) {
        if (!bucketsOnly) {
            amount = addUnit(tooltip, amount, VALUE_BLOCK, "liquid.block");
            amount = addUnit(tooltip, amount, VALUE_INGOT, "liquid.ingot");
            amount = addUnit(tooltip, amount, VALUE_NUGGET, "liquid.nugget");
        }
        amount = addUnit(tooltip, amount, VALUE_KILOBUCKET, "liquid.kilobucket");
        amount = addUnit(tooltip, amount, VALUE_BUCKET, "liquid.bucket");
        addUnit(tooltip, amount, 1, "liquid.millibucket");
    }

    /** @return what is left of {@code amount} after taking out whole {@code unit}s. */
    private static int addUnit(List<Component> tooltip, int amount, int unit, String key) {
        int whole = amount / unit;
        if (whole > 0) {
            tooltip.add(Component.literal(whole + " ").append(translate(key)).withStyle(ChatFormatting.GRAY));
        }
        return amount % unit;
    }

    private static net.minecraft.network.chat.MutableComponent translate(String key) {
        return Component.translatable(KEY_PREFIX + key);
    }

    /* Input */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int fluid = button == 0 ? hoveredFluid(mouseX, mouseY) : -1;
        if (fluid > 0 && minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
            // Server-authoritative, same shape as the station tab row: the index goes out as a plain
            // container button click and the reorder comes back as a block update. Index 0 is
            // already the drain fluid, so clicking it is not sent at all.
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
        event.register(ForgeweaveMenus.SMELTERY.get(), SmelteryScreen::new);
    }
}
