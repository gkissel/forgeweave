package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.menu.StationGroup;
import dev.gkissel.forgeweave.menu.StationMenu;

/**
 * Base class for every Forgeweave container screen, owning the two things all of them share.
 *
 * <h2>Tooltips (issue #75 defect 2)</h2>
 *
 * <p>On 1.21.1 {@code AbstractContainerScreen#render} did not draw item tooltips and every screen
 * had to add the call itself; issues #43 and #75 were screens that forgot. Since 26.1 vanilla's
 * {@link AbstractContainerScreen#extractRenderState} calls {@link #extractTooltip} on its own, so
 * no override is needed any more. Subclasses that need extra hover text (tool tabs, pattern buttons
 * -- things that aren't slots) override {@link #extractTooltip} and call {@code super}.
 *
 * <h2>Station-group tabs (issue #78)</h2>
 *
 * <p>Upstream 1.12 draws a row of tabs along the top edge of every connected station's GUI, one per
 * block in the group, and clicking one opens that block ({@code GuiTinkerStation} +
 * {@code GuiTinkerTabs}). It belongs here for the same reason the tooltip override does: all six
 * Forgeweave stations get it, and the row has to be drawn <em>underneath</em> the station's own
 * background so the selected tab merges into the panel -- which is why {@link #extractBackground} is final
 * and subclasses implement {@link #renderPanel} instead. The alternative was trusting five screens
 * to remember two calls in the right order.
 *
 * <p>The art is vanilla's own creative-inventory tab sprites. That is not a substitution: upstream's
 * {@code GuiTinkerTabs} passes its {@code GuiElement}s to Mantle's {@code GuiWidgetTabs}, which binds
 * {@code textures/gui/container/creative_inventory/tabs.png} -- so 1.12's station tabs already
 * <em>were</em> vanilla's creative tabs, sampled at the unselected (0, 2, 28, 28) and selected
 * (*, 32, 28, 32) regions of that sheet. Modern Minecraft split the same sheet into the 26x32
 * per-column sprites used below, so no derived asset and no NOTICE.md row are involved.
 *
 * <p>Clicking a tab sends nothing but the tab index, as a plain vanilla container-button click that
 * {@link StationMenu#clickMenuButton} validates against the server's own copy of the group; the
 * client never opens a menu itself.
 */
public abstract class StationScreen<T extends StationMenu> extends AbstractContainerScreen<T>
        implements StationExtraAreas {

    /** Vanilla's creative-inventory top-tab sprites, one per column; see the class javadoc. */
    private static final String TAB_SPRITE = "container/creative_inventory/tab_top_";
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    /**
     * Vanilla's own column pitch. Six tabs span 139px of the 176px panel; issue #1057's seventh
     * station kind takes the full row 16px past the panel's right edge, which is cosmetic only --
     * nothing but the tab sprites themselves lives out there, and {@link #extraGuiAreas} reports the
     * whole row to JEI either way.
     */
    private static final int TAB_PITCH = 27;
    /** Upstream {@code GuiTinkerTabs#updatePosition}: {@code tabs.setPosition(guiLeft + 4, ...)}. */
    private static final int TAB_X = 4;
    /**
     * Tabs are drawn 28px above the panel with a 32px sprite, so their last 4px slide under it --
     * vanilla's own arrangement, and what makes the selected tab (drawn <em>after</em> the panel)
     * read as connected to it while the others read as behind it.
     */
    private static final int TAB_TOP = -28;
    private static final int ICON_X = 5;
    private static final int ICON_Y = 9;

    /** How far above the panel the tab row reaches -- the strip vanilla's centring does not know about. */
    private static final int TAB_STRIP = -TAB_TOP;

    protected StationScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** For stations whose panel is not vanilla's default 176x166; 26.1 made the size final. */
    protected StationScreen(T menu, Inventory playerInventory, Component title, int imageWidth, int imageHeight) {
        super(menu, playerInventory, title, imageWidth, imageHeight);
    }

    /**
     * Re-centres the screen on the panel <em>plus</em> its tab row (issue #86).
     *
     * <p>{@link AbstractContainerScreen#init} centres on {@code imageHeight} alone, which is the
     * panel and nothing else. The tab row is 28px of chrome hanging above that box, so what actually
     * gets centred is 28px shorter than what is drawn, and the whole thing sits 14px too high. On
     * the short stations there is enough headroom for that to go unnoticed; on the 222px-tall
     * Pattern/Part Chest there is not, and the tab row rendered off the top of the window -- at
     * vanilla's own minimum scaled height of 240 the panel alone leaves 9px of headroom for a 28px
     * strip, so the tabs were three-quarters gone and their item icons entirely so.
     *
     * <p>Only when there is a tab row: without one there is no extra chrome, and vanilla's centring
     * is already right.
     *
     * <p>Subclasses that place widgets from {@code topPos} ({@code ToolStationScreen}'s rename field)
     * call {@code super.init()} first, so they see the corrected value.
     */
    @Override
    protected void init() {
        super.init();
        if (!menu.stationGroup().isEmpty()) {
            topPos = centreWithTabStrip(height, imageHeight);
        }
    }

    /**
     * {@code topPos} for a screen whose drawn height is {@code imageHeight + TAB_STRIP}, keeping the
     * panel itself below the strip. Split out from {@link #init} so the arithmetic is testable
     * without a client -- see {@code StationScreenTabLayoutTest}.
     *
     * <p>When the two together genuinely exceed the window -- the chest at a 240px scaled height
     * needs 250 -- this spreads the overflow across both edges instead of dumping all of it on the
     * tab row. Nothing interactive is lost at that point: the clipped pixels are the tab sprites'
     * top rows and the panel's bottom border, not the icons, slots or hotbar.
     */
    static int centreWithTabStrip(int screenHeight, int imageHeight) {
        return TAB_STRIP + (screenHeight - (imageHeight + TAB_STRIP)) / 2;
    }

    /** Draws this station's own panel and chrome; the tab row is layered around it by {@link #extractBackground}. */
    protected abstract void renderPanel(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY);

    @Override
    public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        renderTabs(graphics, false);
        renderPanel(graphics, partialTick, mouseX, mouseY);
        renderTabs(graphics, true);
    }

    private void renderTabs(GuiGraphicsExtractor graphics, boolean selectedPass) {
        StationGroup group = menu.stationGroup();
        List<BlockPos> members = group.members();
        for (int i = 0; i < members.size(); i++) {
            boolean selected = i == group.selected();
            if (selected != selectedPass) {
                continue;
            }
            int x = leftPos + tabX(i);
            int y = topPos + TAB_TOP;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(
                    TAB_SPRITE + (selected ? "selected_" : "unselected_") + (i + 1)), x, y, TAB_WIDTH, TAB_HEIGHT);
            graphics.item(tabIcon(members.get(i)), x + ICON_X, y + ICON_Y);
        }
    }

    /** The block's own pick-block stack, so a retextured station's tab shows the wood it was built from. */
    private ItemStack tabIcon(BlockPos pos) {
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return ItemStack.EMPTY;
        }
        BlockState state = level.getBlockState(pos);
        return state.getCloneItemStack(level, pos, false);
    }

    private static int tabX(int index) {
        return TAB_X + index * TAB_PITCH;
    }

    /** The tab under the cursor, or {@code -1}. Only the 28px above the panel counts as the tab. */
    private int hoveredTab(double mouseX, double mouseY) {
        for (int i = 0; i < menu.stationGroup().members().size(); i++) {
            if (isHovering(tabX(i), TAB_TOP, TAB_WIDTH, -TAB_TOP, mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int tab = hoveredTab(mouseX, mouseY);
        if (tab >= 0) {
            graphics.setTooltipForNextFrame(font, tabIcon(menu.stationGroup().members().get(tab)), mouseX, mouseY);
        }
    }

    /**
     * Grabs a panel scrollbar under the cursor, if this station has one there (issue #376). Three
     * overrides in one place rather than in each of the four screens with a side inventory and the
     * two with info panels: the drag has to survive the cursor leaving the track, so it needs a
     * press, a move and a release, and wiring six screens for that is how the issue #75 tooltip
     * defect happened.
     *
     * @return true when the slider took the event
     */
    protected boolean sliderClicked(double mouseX, double mouseY) {
        return false;
    }

    protected boolean sliderDragged(double mouseX, double mouseY) {
        return false;
    }

    protected void sliderReleased() {}

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return sliderDragged(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        sliderReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (sliderClicked(mouseX, mouseY)) {
            return true;
        }
        int tab = hoveredTab(mouseX, mouseY);
        if (tab >= 0 && minecraft != null) {
            if (tab != menu.stationGroup().selected()) {
                // Server-authoritative: the button click is all that is sent, and the server opens
                // the target station's own MenuProvider. Upstream plays the same vanilla UI click.
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StationGroup.TAB_BUTTON_BASE + tab);
            }
            // Consumed either way: falling through would count as a click *outside* the GUI, which
            // is how a container screen decides to throw the stack on the cursor onto the floor.
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * The tab row hangs above {@code imageHeight}, so JEI has to be told about it (issue #68 fix 4's
     * mechanism). Subclasses with panels of their own override this and add to {@code super}'s list.
     */
    @Override
    public List<Rect2i> extraGuiAreas() {
        List<Rect2i> areas = new ArrayList<>(3);
        int tabs = menu.stationGroup().members().size();
        if (tabs > 0) {
            areas.add(new Rect2i(leftPos + tabX(0), topPos + TAB_TOP, tabs * TAB_PITCH, -TAB_TOP));
        }
        return areas;
    }
}
