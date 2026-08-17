package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
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
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.SmelteryMenu;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

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
 * heat bars the melting grid needs -- are all still live. The panel itself is L-shaped: the
 * top-right of the 176x166 region is transparent in upstream's art too, and nothing is drawn there.
 * That corner is simply unused space -- the tank and fuel gauge need only the left 94px, and the
 * melting slots hang off the panel's <em>left</em> edge as their own module ({@link #renderMeltGrid})
 * -- which is why upstream's own 1.20 rewrite dropped the notch and squared the art off.
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
    private static final int BASE_HEIGHT = SmelteryMenu.PANEL_HEIGHT;

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
     * {@code (0, 166)}, the "no slot here" filler at {@code (22, 166)}, and the four 3x16 heat bars
     * at {@code (176, 150)} (progressing), {@code (179, 150)} (no fuel/no progress) and
     * {@code (182, 150)} (upstream's {@code uberHeatBar}, its "tank full, can't finish" state --
     * #290's stalled variant). Upstream's fourth bar at {@code (185, 150)}, "no melting recipe", has
     * no Forgeweave use: a melt slot only ever holds something it already accepted as meltable.
     */
    private static final int CELL_U = 0;
    private static final int CELL_EMPTY_U = 22;
    private static final int CELL_V = 166;
    private static final int BAR_ACTIVE_U = 176;
    private static final int BAR_IDLE_U = 179;
    private static final int BAR_STALLED_U = 182;
    private static final int BAR_V = 150;
    private static final int BAR_WIDTH = 3;
    private static final int BAR_HEIGHT = 16;

    /**
     * Upstream {@code Material.VALUE_Block/VALUE_Ingot/VALUE_Nugget/VALUE_Gem}, the unit sizes its
     * fluid tooltips break an amount down into.
     *
     * <p>Upstream derives these per fluid by scanning every registered casting recipe for one that
     * casts it with no cast (a block), with an ingot cast, and so on ({@code
     * GuiUtil#calcFluidGuiEntries}). Casting has since landed (#100), so that scan is possible here
     * -- and still not worth doing in full: every recipe it would find uses exactly these numbers, so
     * a full scan is a per-tooltip registry walk that can only ever return the constants below.
     *
     * <p>What the scan does still decide is <em>which</em> of them apply, and #361 made that a real
     * question: molten emerald casts gems and nothing else, so running the metal cascade over it
     * would report a gem and a half as "1 Blocks" and change. {@link #gemValued} asks the registry
     * that one question -- does this fluid cast gems -- and the cascade picks a unit family from the
     * answer. Split the families further (a fluid that casts both gems and ingots) only when one
     * ships; today no fluid does.
     *
     * <p>Deliberately not {@code FaucetBlockEntity.TRANSACTION_AMOUNT}, which is also 144: that is a
     * faucet's pour rate, a different quantity that happens to coincide.
     */
    private static final int VALUE_BLOCK = 1296;
    private static final int VALUE_GEM = 666;
    private static final int VALUE_INGOT = 144;
    private static final int VALUE_NUGGET = 16;
    private static final int VALUE_KILOBUCKET = 1_000_000;
    private static final int VALUE_BUCKET = 1_000;

    private static final String KEY_PREFIX = "gui.forgeweave.smeltery.";

    /** The slider's track after the last {@link #renderMeltGrid}; empty while the rows do not overflow. */
    private Rect2i sliderTrack = new Rect2i(0, 0, 0, 0);
    private boolean draggingSlider;

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
     * <p><b>Sized to the smeltery, and hung off the panel's left edge</b> (issues #146 and #408),
     * which is where upstream puts it: {@code GuiSmelterySideInventory} is a {@code connected}
     * module built {@code rightSide = false}, so the frame's right edge laps a pixel column over the
     * panel and the two read as one window. Nothing is ever drawn in the transparent notch at the
     * panel art's top-right -- that region is unused in upstream's own art too, which is why
     * upstream's 1.20 rewrite dropped it. Putting the grid <em>in</em> the notch (Forgeweave's
     * earlier deviation) is what produced #146's "corrupted fragment": a frame sized to a two-slot
     * smeltery floating over the rest of a fixed-size hole. Out here the frame simply is the grid's
     * size, so there is no hole to half-fill.
     *
     * <p>Rows are {@code ceil(slots / 3)} capped by upstream's {@code calcCappedYSize} at
     * {@link SmelteryMenu#MELT_MAX_ROWS}, with the last row's spare cells drawn in upstream's own
     * "no slot here" tile and a slider past the cap.
     */
    private void renderMeltGrid(GuiGraphics graphics) {
        int slots = menu.meltSlotCount();
        int rows = SmelteryMenu.visibleMeltRows(slots);
        int gridX = SmelteryMenu.gridX(slots);
        int gridLeft = leftPos + gridX + SmelteryMenu.GRID_BORDER;
        int gridTop = topPos + SmelteryMenu.GRID_Y + SmelteryMenu.GRID_BORDER;
        // Upstream wraps its side inventories in generic.png's nine-sliced frame, with the edge
        // facing the parent drawn from the overlap pieces instead (its `connected` flag).
        SideInventoryPanel.renderBorder(graphics, leftPos + gridX, topPos + SmelteryMenu.GRID_Y,
                SmelteryMenu.gridWidth(slots), SmelteryMenu.gridHeight(slots), true);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < SmelteryMenu.MELT_COLUMNS; col++) {
                int index = (menu.scrollRow() + row) * SmelteryMenu.MELT_COLUMNS + col;
                graphics.blit(TEXTURE,
                        gridLeft + col * SmelteryMenu.CELL_WIDTH, gridTop + row * SmelteryMenu.SLOT_SIZE,
                        index < slots ? CELL_U : CELL_EMPTY_U, CELL_V,
                        SmelteryMenu.CELL_WIDTH, SmelteryMenu.SLOT_SIZE, SHEET, SHEET);
            }
        }
        renderHeatBars(graphics, slots, rows, gridLeft, gridTop);
        renderSlider(graphics, slots, gridLeft, gridTop, rows);
    }

    /**
     * Upstream's slider, drawn from {@code generic.png} next to the cells whenever the rows overflow
     * ({@code GuiSideInventory#updatePosition} enables it on exactly that condition) -- the same
     * widget, from the same sheet, that {@link SideInventoryPanel} gives the station side panels.
     */
    private void renderSlider(GuiGraphics graphics, int slots, int gridLeft, int gridTop, int rows) {
        int maxScrollRow = SmelteryMenu.meltScrollRows(slots);
        if (maxScrollRow <= 0) {
            sliderTrack = new Rect2i(0, 0, 0, 0);
            return;
        }
        sliderTrack = new Rect2i(gridLeft + SmelteryMenu.MELT_COLUMNS * SmelteryMenu.CELL_WIDTH, gridTop,
                SideInventoryPanel.SLIDER_WIDTH, rows * SmelteryMenu.SLOT_SIZE);
        SideInventoryPanel.renderSlider(graphics, sliderTrack, menu.scrollRow(), maxScrollRow);
    }

    /**
     * One bar per occupied slot, filling upward as the item heats -- upstream's
     * {@code GuiElementScalable(176..185, 150, 3, 16)} variants, chosen by why a slot is or is not
     * progressing so the bar doubles as the explanation. #290: a slot stuck on a full tank draws full
     * and in {@link #BAR_STALLED_U} rather than whatever fraction {@link SmelteryMenu#meltProgress}
     * happens to report, since that fraction pinned itself at 1.0 the instant the melt finished and
     * says nothing about the stall.
     *
     * <p>The bar is only half the explanation -- three of upstream's four variants are two shades of
     * grey apart, and a player who has never seen the sheet cannot tell "no fuel" from "not hot
     * enough" by looking. {@link #heatBarTooltip} is the other half (#377): hovering a bar says which
     * of them it is in words.
     */
    private void renderHeatBars(GuiGraphics graphics, int slots, int rows, int gridLeft, int gridTop) {
        List<ItemStack> items = menu.meltingItems(level());
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < SmelteryMenu.MELT_COLUMNS; col++) {
                int index = (menu.scrollRow() + row) * SmelteryMenu.MELT_COLUMNS + col;
                if (index >= slots || index >= items.size() || items.get(index).isEmpty()) {
                    continue;
                }
                boolean stalled = menu.meltStalled(level(), index);
                float progress = stalled ? 1f : menu.meltProgress(level(), index);
                int height = 1 + Math.round(Math.clamp(progress, 0f, 1f) * (BAR_HEIGHT - 1));
                int x = gridLeft + col * SmelteryMenu.CELL_WIDTH + 1;
                int y = gridTop + row * SmelteryMenu.SLOT_SIZE + 1;
                int barU = stalled ? BAR_STALLED_U : progress > 0f ? BAR_ACTIVE_U : BAR_IDLE_U;
                // Bars fill from the bottom, so the drawn slice is the bottom `height` px of both
                // the sprite and the bar's own box.
                graphics.blit(TEXTURE, x, y + BAR_HEIGHT - height,
                        barU, BAR_V + BAR_HEIGHT - height,
                        BAR_WIDTH, height, SHEET, SHEET);
            }
        }
    }

    /** Upstream {@code GuiSideInventory}'s wheel scroll, the same one {@code SideInventoryPanel} gives the station side panels. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int slots = menu.meltSlotCount();
        if (SmelteryMenu.meltScrollRows(slots) > 0
                && isHovering(SmelteryMenu.gridX(slots), SmelteryMenu.GRID_Y,
                        SmelteryMenu.gridWidth(slots), SmelteryMenu.gridHeight(slots), mouseX, mouseY)) {
            menu.setScrollRow(menu.scrollRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Grabs the slider if the press landed on it -- {@link SideInventoryPanel}'s own drag handling,
     * gated on the press so a slider grab survives the cursor leaving the track and an item drag
     * across the column does not hijack it.
     */
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
                SmelteryMenu.meltScrollRows(menu.meltSlotCount()), mouseY));
    }

    /**
     * Upstream {@code GuiSmeltery#updateScreen}: a smeltery that is rebuilt bigger or smaller while
     * its screen is open has a different number of melting slots than the menu was built with, and
     * every index in it -- slots, heat bars, the grid's own size -- is off by the difference. Nothing
     * on the screen can be salvaged, so it closes, exactly as upstream does.
     *
     * <p>Gated on the structure still being formed, because an unformed core reports no melting
     * slots at all: without the gate, a client whose block-entity sync has not caught up yet would
     * read that as a resize and close a screen the server is perfectly happy with. An actually
     * unformed smeltery closes anyway, from the server side ({@link SmelteryMenu#stillValid}).
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        SmelteryControllerBlockEntity core = menu.core(level());
        if (core != null && core.isFormed() && core.meltingItems().size() != menu.meltSlotCount()
                && minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
    }

    /**
     * Upstream's {@code GuiMultiModule} draws no title or inventory label, and the L-shaped panel has
     * no room for either -- the "Inventory" label's usual spot is transparent background here.
     *
     * <p>The one thing that does get drawn is the content-family toggles ticket's "smeltery
     * disabled" line, over the melt grid where the slots a disabled smeltery will never process
     * are: nothing melts, nothing alloys and nothing casts while it is up, and a player staring at
     * a fully built, fully fuelled structure otherwise has no way to find that out.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component notice = menu.disabledNotice();
        if (notice == null) {
            return;
        }
        int slots = menu.meltSlotCount();
        int x = SmelteryMenu.gridX(slots) + SmelteryMenu.GRID_BORDER;
        graphics.drawWordWrap(font, notice, x, SmelteryMenu.GRID_Y + SmelteryMenu.GRID_BORDER,
                SmelteryMenu.gridWidth(slots) - SmelteryMenu.GRID_BORDER * 2, 0xFFFF5555);
    }

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
     * The top edge of the fluid band at {@code index}, in the same column-relative pixel space
     * {@link #fluidAt} reads from -- {@link #fluidAt} walks the stack from the bottom to answer
     * "which band is here"; this walks it top-down to answer "where does this band start", the other
     * half {@link #hoveredTankFluid} needs to size a band's hit rectangle for JEI (issue #308).
     */
    static int tankBandTop(List<FluidStack> fluids, int capacity, int height, int index) {
        int[] heights = fluidHeights(fluids, capacity, height);
        int top = height;
        for (int i = 0; i <= index; i++) {
            top -= heights[i];
        }
        return top;
    }

    /**
     * A fluid band under the cursor and the absolute screen rectangle it occupies. Public (unlike the
     * rest of this class's package-private helpers) because {@link #hoveredTankFluid} is {@code
     * jei.SmelteryTankGuiHandler}'s only way to see the tank -- JEI is a separate package so it can
     * stay {@code compileOnly} (see {@code SubtypeKeys}'s javadoc for the same split).
     */
    public record TankHover(FluidStack fluid, Rect2i area) {}

    /**
     * The fluid stack under the cursor in the tank, paired with the screen rectangle it occupies --
     * upstream 1.12's {@code TinkerGuiTankHandler#getIngredientUnderMouse} (issue #308), which lets
     * JEI's R/U recipe lookup work on whichever fluid layer is hovered. Reuses {@link #hoveredFluid}'s
     * index and {@link #fluidHeights}' band sizes, the same maths {@link #tankTooltip} and {@link
     * #mouseClicked} already agree on, so JEI can never point at a different band than the tooltip
     * shows or the click would select.
     */
    public Optional<TankHover> hoveredTankFluid(double mouseX, double mouseY) {
        int index = hoveredFluid(mouseX, mouseY);
        if (index < 0) {
            return Optional.empty();
        }
        List<FluidStack> fluids = menu.fluids(level());
        int capacity = capacity(fluids);
        int top = tankBandTop(fluids, capacity, TANK_HEIGHT, index);
        int height = fluidHeights(fluids, capacity, TANK_HEIGHT)[index];
        Rect2i area = new Rect2i(leftPos + TANK_X, topPos + TANK_Y + top, TANK_WIDTH, height);
        return Optional.of(new TankHover(fluids.get(index), area));
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
        } else {
            Component stall = heatBarTooltip(mouseX, mouseY);
            if (stall != null) {
                graphics.renderTooltip(font, stall, mouseX, mouseY);
            }
        }
    }

    /**
     * What the heat bar under the cursor has to say, or {@code null} -- upstream
     * {@code GuiSmelterySideInventory}'s {@code tooltipText}, which is set only by the branches that
     * picked a bar variant <em>because</em> the slot is not progressing. A slot melting normally has
     * no tooltip there, and neither does an empty one.
     */
    @Nullable
    private Component heatBarTooltip(int mouseX, int mouseY) {
        Level level = level();
        if (level == null) {
            return null;
        }
        int slots = menu.meltSlotCount();
        int index = heatBarAt(menu.scrollRow(), SmelteryMenu.visibleMeltRows(slots),
                mouseX - leftPos - SmelteryMenu.gridX(slots) - SmelteryMenu.GRID_BORDER,
                mouseY - topPos - SmelteryMenu.GRID_Y - SmelteryMenu.GRID_BORDER);
        List<ItemStack> items = menu.meltingItems(level);
        if (index < 0 || index >= slots || index >= items.size() || items.get(index).isEmpty()) {
            return null;
        }
        int heatNeeded = MeltingRecipe.find(level.registryAccess(), items.get(index))
                .map(recipe -> recipe.heatRequired() / MeltingRecipe.TIME_FACTOR)
                .orElse(0);
        String reason = stallReason(menu.meltStalled(level, index), menu.smelteryTemperature(level), heatNeeded);
        return reason == null ? null : translate(reason);
    }

    /**
     * Why a melt slot is not progressing, as the {@link #KEY_PREFIX} suffix its bar's hover tooltip
     * shows, or {@code null} while it is melting normally.
     *
     * <p>The three reachable branches of upstream {@code GuiSmelterySideInventory}. Its fourth,
     * {@code no_recipe}, is not reachable here for the reason {@link #BAR_STALLED_U}'s javadoc gives:
     * a melt slot only ever holds something it already accepted as meltable.
     *
     * <p>The stall check goes first, where upstream tests its {@code no_fuel} branch before its
     * overheat one. A slot that is both finished-and-stuck and out of fuel is already <em>drawn</em>
     * in the stalled variant by {@link #renderHeatBars}, and a full-and-stalled bar whose tooltip
     * says "no fuel" contradicts the picture it is attached to. The melt is done either way; the tank
     * is the thing to fix.
     *
     * @param stalled     {@link SmelteryMenu#meltStalled}, upstream's {@code progress > 2f} overheat
     *                    state -- the melt is done and the tank has nowhere to put it (#290)
     * @param temperature the smeltery's working heat, {@code 0} when nothing burnable is in reach --
     *                    upstream's {@code getFuel() == 0}
     * @param heatNeeded  the recipe's own {@code heatRequired() / TIME_FACTOR}, which is the exact
     *                    quantity {@code SmelteryControllerBlockEntity#meltTick} compares the
     *                    smeltery's heat against before advancing a slot, so this line and the server
     *                    can never disagree about whether a slot is too cold
     */
    @Nullable
    static String stallReason(boolean stalled, int temperature, int heatNeeded) {
        if (stalled) {
            return "progress.no_space";
        }
        if (temperature <= 0) {
            return "progress.no_fuel";
        }
        return temperature - MeltingRecipe.AMBIENT_TEMPERATURE < heatNeeded ? "progress.no_heat" : null;
    }

    /**
     * The melt slot whose heat bar covers {@code (x, y)}, measured from the grid's first cell, or
     * {@code -1} -- the inverse of the cell walk {@link #renderHeatBars} draws with, so the hit box
     * is the drawn bar and not the whole cell (the other 19px of which is the slot itself, whose own
     * tooltip vanilla already renders).
     */
    static int heatBarAt(int scrollRow, int rows, int x, int y) {
        if (x < 0 || y < 0) {
            return -1;
        }
        int col = x / SmelteryMenu.CELL_WIDTH;
        int row = y / SmelteryMenu.SLOT_SIZE;
        if (col >= SmelteryMenu.MELT_COLUMNS || row >= rows) {
            return -1;
        }
        // The bar is drawn 1px in from the cell's top-left corner; see renderHeatBars.
        int barX = x - col * SmelteryMenu.CELL_WIDTH - 1;
        int barY = y - row * SmelteryMenu.SLOT_SIZE - 1;
        if (barX < 0 || barX >= BAR_WIDTH || barY < 0 || barY >= BAR_HEIGHT) {
            return -1;
        }
        return (scrollRow + row) * SmelteryMenu.MELT_COLUMNS + col;
    }

    /**
     * Upstream {@code GuiUtil#getTankTooltip}: over a fluid, its name and how much of it there is;
     * over the empty headroom, the tank's capacity, free space and used space.
     *
     * <p><b>Recorded deviation, maintainer re-confirmed 2026-08-14 (#377).</b> The
     * Capacity/Free/Used lines run the full {@link #addAmount} cascade, where upstream runs only
     * {@code amountToIngotString} (ingots, then buckets) for those three and keeps the block/nugget
     * breakdown for the hovered-fluid branch. Showing blocks and nuggets there too is strictly more
     * information about the same number, and the three lines are the screen's answer to "how much
     * more fits", which a player reads in blocks far more often than in ingots. Kept as-is.
     *
     * <p>Those three are also always the metal cascade: they are properties of the <em>tank</em>,
     * which can hold any mix of fluids at once, so there is no one fluid whose unit family they could
     * follow. Only the hovered-fluid branch, which does have a fluid, asks {@link #gemValued}.
     */
    private List<Component> tankTooltip(int mouseX, int mouseY) {
        List<FluidStack> fluids = menu.fluids(level());
        int hovered = hoveredFluid(mouseX, mouseY);
        List<Component> tooltip = new ArrayList<>();
        if (hovered >= 0) {
            FluidStack fluid = fluids.get(hovered);
            tooltip.add(fluid.getHoverName().copy().withStyle(ChatFormatting.WHITE));
            addAmount(tooltip, fluid.getAmount(), Screen.hasShiftDown(), gemValued(level(), fluid));
            return tooltip;
        }

        int used = 0;
        for (FluidStack fluid : fluids) {
            used += fluid.getAmount();
        }
        int capacity = menu.capacity(level());
        tooltip.add(translate("capacity").withStyle(ChatFormatting.WHITE));
        addAmount(tooltip, capacity, Screen.hasShiftDown(), false);
        tooltip.add(translate("capacity_available"));
        addAmount(tooltip, capacity - used, Screen.hasShiftDown(), false);
        tooltip.add(translate("capacity_used"));
        addAmount(tooltip, used, Screen.hasShiftDown(), false);
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.forgeweave.hold_shift").withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }

    /**
     * Upstream {@code GuiHeatingStructureFuelTank#drawFuelTooltip}, all three of its branches (#377):
     * nothing in reach, a fluid that burns (name, amount, the heat it burns at), or a fluid that does
     * not (its name and the reason it will never burn, in red).
     *
     * <p>The heat line follows the <em>loaded fuel</em>, not an in-progress burn: upstream shows it
     * whenever the tank holds something {@code TinkerRegistry.isSmelteryFuel} accepts, and a smeltery
     * sitting idle over a full lava tank is exactly when a player most wants to be told it is hot
     * enough. {@link SmelteryMenu#smelteryTemperature} is where the burn's temperature and the loaded
     * fuel's own are reconciled.
     */
    private List<Component> fuelTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(translate("fuel").withStyle(ChatFormatting.WHITE));
        FluidStack fuel = menu.fuel(level());
        if (fuel.isEmpty()) {
            tooltip.add(translate("fuel.empty"));
            return tooltip;
        }
        if (menu.loadedFuel(level()).isEmpty()) {
            tooltip.add(Component.translatable(KEY_PREFIX + "fuel.invalid", fuel.getHoverName())
                    .withStyle(ChatFormatting.DARK_RED));
            return tooltip;
        }
        tooltip.add(fuel.getHoverName().copy());
        addAmount(tooltip, fuel.getAmount(), Screen.hasShiftDown(), gemValued(level(), fuel));
        tooltip.add(Component.translatable(KEY_PREFIX + "fuel.heat",
                        TemperatureText.format(menu.smelteryTemperature(level())))
                .withStyle(ChatFormatting.GRAY));
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
     * @param gemValued   whether this is a gem fluid rather than a metal one ({@link #gemValued}),
     *                    likewise passed in so the cascade needs no registry to be exercisable
     */
    static void addAmount(List<Component> tooltip, int amount, boolean bucketsOnly, boolean gemValued) {
        if (!bucketsOnly) {
            if (gemValued) {
                amount = addUnit(tooltip, amount, VALUE_GEM, "liquid.gem");
            } else {
                amount = addUnit(tooltip, amount, VALUE_BLOCK, "liquid.block");
                amount = addUnit(tooltip, amount, VALUE_INGOT, "liquid.ingot");
                amount = addUnit(tooltip, amount, VALUE_NUGGET, "liquid.nugget");
            }
        }
        amount = addUnit(tooltip, amount, VALUE_KILOBUCKET, "liquid.kilobucket");
        amount = addUnit(tooltip, amount, VALUE_BUCKET, "liquid.bucket");
        addUnit(tooltip, amount, 1, "liquid.millibucket");
    }

    /**
     * Whether {@code fluid} is measured in gems rather than in blocks and ingots -- upstream's gem
     * branch in {@code GuiUtil#calcFluidGuiEntries}, asked as the one registry question the constant
     * cascade above cannot answer for itself.
     *
     * <p>A fluid qualifies by having a table casting recipe that pours it into the gem cast, which is
     * exactly upstream's own test ({@code recipe.cast.matches(castGem)}). {@link CastingRecipe} is a
     * synced datapack registry, so this is answerable client-side without a packet of its own; the
     * walk is a registry scan per hovered tooltip, which is what upstream does too (behind a per-fluid
     * cache it can afford because its recipe lists never reload).
     */
    static boolean gemValued(@Nullable Level level, FluidStack fluid) {
        if (level == null || fluid.isEmpty()) {
            return false;
        }
        return CastingRecipe.find(level.registryAccess().registryOrThrow(CastingRecipe.REGISTRY),
                CastingRecipe.Station.TABLE, new ItemStack(ForgeweaveItems.CAST_GEM.get()), fluid.getFluid()) != null;
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

    /**
     * The melt grid hangs off the panel's left edge, outside {@code imageWidth}, so JEI has to be
     * told about it or it draws its item list over the slots (issue #68 fix 4's mechanism, the same
     * way the station side panels report theirs).
     */
    @Override
    public List<Rect2i> extraGuiAreas() {
        List<Rect2i> areas = super.extraGuiAreas();
        int slots = menu.meltSlotCount();
        areas.add(new Rect2i(leftPos + SmelteryMenu.gridX(slots), topPos + SmelteryMenu.GRID_Y,
                SmelteryMenu.gridWidth(slots), SmelteryMenu.gridHeight(slots)));
        return areas;
    }

    private Level level() {
        return minecraft == null ? null : minecraft.level;
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.SMELTERY.get(), SmelteryScreen::new);
    }
}
