package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.menu.ModifierWorktableMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.modifier.Worktable;

/**
 * The Modifier Worktable's GUI (issue #1057). The panel derives from upstream 1.20's
 * {@code textures/gui/worktable.png} (NOTICE.md), so every coordinate below is that sheet's own: a
 * 176x184 panel, a four-wide grid of modifier buttons inside it at (28, 15), the scroll handle at
 * (103, 15), and the button sprites in the sheet's right-hand column at u = 176.
 *
 * <p>Two things this screen does not have to do that upstream's does. It never asks a block entity
 * what the buttons are -- {@link ModifierWorktableMenu#options} runs the same
 * {@link Worktable} code on both sides off the synced slot contents. And it draws a modifier as the
 * reagent that applies it ({@link ModifierRecipe}, also synced) rather than as a per-modifier icon,
 * because Forgeweave has no icon sheet for modifiers and the reagent is what a player already reads
 * a modifier by at the Tool Station.
 *
 * <p>The armor stand preview is {@link StandPreview} (PR #1043), which exists as its own class
 * exactly so this screen and the Tool Station share one copy; it shows the result when there is one
 * and the loaded tool otherwise, as upstream's {@code ModifierWorktableScreen#updateDisplay} does.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModifierWorktableScreen extends StationScreen<ModifierWorktableMenu> implements StationExtraAreas {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/modifier_worktable.png");
    private static final int SHEET = 256;

    /** Upstream's panel: 176 wide, {@code imageHeight = 184}. */
    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 184;

    // The button grid, upstream ModifierWorktableScreen's own constants.
    private static final int MODIFIER_LEFT = 28;
    private static final int MODIFIER_TOP = 15;
    private static final int MODIFIER_SIZE = 18;
    private static final int MODIFIER_COLUMNS = 4;
    private static final int MODIFIER_ROWS = 4;
    private static final int MAX_MODIFIER = MODIFIER_COLUMNS * MODIFIER_ROWS;
    private static final int MODIFIER_U = 176;
    private static final int MODIFIER_V_START = 15;

    // The scrollbar, same source.
    private static final int SLIDER_LEFT = 103;
    private static final int SLIDER_TOP = 15;
    private static final int SLIDER_WIDTH = 12;
    private static final int HANDLE_HEIGHT = 15;
    private static final int HANDLE_U = 176;
    private static final int HANDLE_U_DISABLED = 188;
    private static final int BAR_HEIGHT = 72;
    private static final int SCROLLABLE_AREA = BAR_HEIGHT + 2 - HANDLE_HEIGHT;

    /** Info panels, laid out exactly as {@link ToolStationScreen} lays out its own pair. */
    private static final int PANEL_GAP = 2;
    private static final int PANEL_TOP = 11;
    private static final int PANEL_SPACING = 4;

    /** Upstream {@code ModifierWorktableScreen#init}: {@code setupArmorStandPreview(-55, 134, 50)}. */
    private static final int PREVIEW_X = -55;
    private static final int PREVIEW_Y = 134;
    private static final int PREVIEW_SCALE = 50;

    private final StandPreview preview = new StandPreview();
    private final SideInventoryPanel sidePanel =
            new SideInventoryPanel(ModifierWorktableMenu.SIDE_PANEL_X, ModifierWorktableMenu.SIDE_PANEL_Y);

    @Nullable
    private Component toolCaption;
    private List<Component> toolLines = List.of();
    @Nullable
    private Component modifierCaption;
    private List<Component> modifierLines = List.of();
    private int toolScroll;
    private int modifierScroll;
    private int draggingPanel = -1;

    private float sliderProgress;
    private boolean clickedOnScrollBar;
    private int indexOffset;

    public ModifierWorktableScreen(ModifierWorktableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        preview.close();
        if (menu.sideSlots.isEmpty()) {
            // Same conflict the Tool Station settles the same way (#1043): a chest beside the station
            // stands where the preview goes, and the chest wins.
            preview.open(minecraft == null ? null : minecraft.level,
                    leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_SCALE);
        }
        updateInfo();
    }

    @Override
    public void removed() {
        super.removed();
        preview.close(); // no entity outlives the screen
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateInfo();
    }

    // ------------------------------------------------------------------ info panels

    private HolderLookup.Provider registries() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    /**
     * Fills the two panels from whatever the table would hand back, falling back to the loaded tool.
     * The captions follow {@link dev.gkissel.forgeweave.menu.StationMenu.Rejection}'s error/warning
     * split, which is the one upstream {@code error(...)}/{@code warning(...)} pair every Forgeweave
     * station already shows.
     */
    private void updateInfo() {
        HolderLookup.Provider registries = registries();
        ItemStack tool = menu.tool();
        Worktable.Result outcome = registries == null
                ? Worktable.Result.rejected(null)
                : Worktable.resolve(registries, tool, menu.inputs(), menu.getSelected());
        ItemStack shown = outcome.output();
        // Upstream shows the loaded tool until there is a result to show instead.
        preview.setItem(shown.isEmpty() ? tool : shown);

        if (outcome.rejection() != null) {
            toolCaption = Component.translatable("gui.forgeweave.warning");
            toolLines = List.of(outcome.rejection());
            modifierCaption = null;
            modifierLines = List.of();
            return;
        }
        if (shown.isEmpty()) {
            toolCaption = Component.translatable("block.forgeweave.modifier_worktable");
            toolLines = List.of(Component.translatable("gui.forgeweave.worktable.info"));
            modifierCaption = null;
            modifierLines = List.of();
            return;
        }
        toolCaption = shown.getHoverName().copy();
        toolLines = shown.has(ForgeweaveDataComponents.ARMOR_STATS.get())
                ? StationText.armorStats(shown)
                : StationText.toolStats(shown);
        List<Component> modifiers = StationText.toolModifiers(shown);
        modifierCaption = modifiers.isEmpty() ? null : Component.translatable("gui.forgeweave.tool_station.modifiers");
        modifierLines = modifiers;
        toolScroll = clamp(toolScroll, toolCaption != null, toolLines);
        modifierScroll = clamp(modifierScroll, modifierCaption != null, modifierLines);
    }

    private int clamp(int value, boolean hasCaption, List<Component> lines) {
        return Math.clamp(value, 0, InfoPanel.maxScroll(font, InfoPanel.WIDTH, InfoPanel.HEIGHT, hasCaption, lines));
    }

    private int panelX() {
        return leftPos + BASE_WIDTH + PANEL_GAP;
    }

    private int panelY() {
        return topPos + PANEL_TOP;
    }

    private int modifierPanelY() {
        return topPos + PANEL_TOP + InfoPanel.HEIGHT + PANEL_SPACING;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, SHEET, SHEET);
        graphics.blit(TEXTURE, leftPos + SLIDER_LEFT, topPos + SLIDER_TOP + (int) (SCROLLABLE_AREA * sliderProgress),
                canScroll() ? HANDLE_U : HANDLE_U_DISABLED, 0, SLIDER_WIDTH, HANDLE_HEIGHT, SHEET, SHEET);
        renderModifierButtons(graphics, mouseX, mouseY);
        InfoPanel.render(graphics, font, panelX(), panelY(), InfoPanel.WIDTH, InfoPanel.HEIGHT,
                InfoPanel.Style.METAL, toolCaption, toolLines, toolScroll);
        InfoPanel.render(graphics, font, panelX(), modifierPanelY(), InfoPanel.WIDTH, InfoPanel.HEIGHT,
                InfoPanel.Style.METAL, modifierCaption, modifierLines, modifierScroll);
        sidePanel.render(graphics, menu, leftPos, topPos, imageHeight, menu.sideSlots, menu.sideInventoryLiveSlots());
        preview.render(graphics);
    }

    private void renderModifierButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ModifierEntry> options = menu.options();
        int max = Math.min(indexOffset + MAX_MODIFIER, options.size());
        for (int i = indexOffset; i < max; i++) {
            int x = leftPos + buttonX(i);
            int y = topPos + buttonY(i);
            int v = MODIFIER_V_START;
            if (i == menu.getSelected()) {
                v += MODIFIER_SIZE;
            } else if (mouseX >= x && mouseY >= y && mouseX < x + MODIFIER_SIZE && mouseY < y + MODIFIER_SIZE) {
                v += 2 * MODIFIER_SIZE;
            }
            graphics.blit(TEXTURE, x, y, MODIFIER_U, v, MODIFIER_SIZE, MODIFIER_SIZE, SHEET, SHEET);
            ItemStack icon = iconFor(options.get(i));
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + 1, y + 1);
            }
        }
    }

    /**
     * What a modifier is drawn as: the first reagent of the application recipe that adds it, which is
     * the item a player already associates with it (redstone for haste, quartz for sharpness). Empty
     * for a modifier no loaded recipe adds -- a pack-defined one whose reagent was removed, say --
     * which leaves the button blank rather than guessing.
     */
    private ItemStack iconFor(ModifierEntry entry) {
        HolderLookup.Provider registries = registries();
        if (registries == null) {
            return ItemStack.EMPTY;
        }
        return registries.lookup(ModifierRecipe.REGISTRY)
                .flatMap(lookup -> lookup.listElements()
                        .map(holder -> holder.value())
                        .filter(recipe -> recipe.modifier().equals(entry.id()))
                        .findFirst())
                .map(recipe -> {
                    ItemStack[] items = recipe.reagent().getItems();
                    return items.length == 0 ? ItemStack.EMPTY : items[0];
                })
                .orElse(ItemStack.EMPTY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        int index = buttonAt(mouseX, mouseY);
        if (index >= 0) {
            List<ModifierEntry> options = menu.options();
            if (index < options.size()) {
                ModifierEntry entry = options.get(index);
                graphics.renderTooltip(font, List.of(
                        ModifierApplication.displayName(entry.id(),
                                ForgeweaveModifiers.displayLevel(entry.id(), entry.level())).getVisualOrderText(),
                        ModifierApplication.description(entry.id()).getVisualOrderText()), mouseX, mouseY);
            }
        }
    }

    // ------------------------------------------------------------------ button grid geometry & input

    private static int buttonX(int index) {
        return MODIFIER_LEFT + (index % MODIFIER_COLUMNS) * MODIFIER_SIZE;
    }

    private int buttonY(int index) {
        return MODIFIER_TOP + ((index - indexOffset) / MODIFIER_COLUMNS) * MODIFIER_SIZE;
    }

    /** The button under the cursor, or {@code -1}; indexes into {@link ModifierWorktableMenu#options}. */
    private int buttonAt(double mouseX, double mouseY) {
        int count = menu.options().size();
        int max = Math.min(indexOffset + MAX_MODIFIER, count);
        for (int i = indexOffset; i < max; i++) {
            int x = leftPos + buttonX(i);
            int y = topPos + buttonY(i);
            if (mouseX >= x && mouseY >= y && mouseX < x + MODIFIER_SIZE && mouseY < y + MODIFIER_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private boolean canScroll() {
        return menu.options().size() > MAX_MODIFIER;
    }

    private int hiddenRows() {
        return Math.max(1, (menu.options().size() + MODIFIER_COLUMNS - 1) / MODIFIER_COLUMNS - MODIFIER_ROWS);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        clickedOnScrollBar = false;
        int index = buttonAt(mouseX, mouseY);
        if (index >= 0 && minecraft != null && menu.clickMenuButton(minecraft.player, index)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            updateInfo();
            return true;
        }
        int sliderX = leftPos + SLIDER_LEFT;
        int sliderY = topPos + SLIDER_TOP;
        if (canScroll() && mouseX >= sliderX && mouseX < sliderX + SLIDER_WIDTH
                && mouseY >= sliderY && mouseY < sliderY + BAR_HEIGHT) {
            clickedOnScrollBar = true;
            return true;
        }
        if (preview.mouseClicked(mouseX, mouseY)) {
            return true; // grabbed the stand; see StandPreview#mouseClicked for why it is swallowed
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (clickedOnScrollBar && canScroll()) {
            int barStart = topPos + SLIDER_TOP;
            sliderProgress = Math.clamp((float) (mouseY - barStart - HANDLE_HEIGHT / 2.0) / SCROLLABLE_AREA, 0.0F, 1.0F);
            indexOffset = Math.round(sliderProgress * hiddenRows()) * MODIFIER_COLUMNS;
            return true;
        }
        preview.mouseDragged(mouseX);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        clickedOnScrollBar = false;
        preview.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sidePanel.mouseScrolled(mouseX, mouseY, scrollY, imageHeight, menu.sideInventoryLiveSlots())) {
            return true;
        }
        if (canScroll()) {
            int hidden = hiddenRows();
            sliderProgress = Math.clamp((float) (sliderProgress - scrollY / hidden), 0.0F, 1.0F);
            indexOffset = Math.round(sliderProgress * hidden) * MODIFIER_COLUMNS;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean sliderClicked(double mouseX, double mouseY) {
        if (sidePanel.sliderClicked(mouseX, mouseY)) {
            return true;
        }
        if (InfoPanel.overSlider(font, panelX(), panelY(), InfoPanel.WIDTH, InfoPanel.HEIGHT,
                toolCaption != null, toolLines, mouseX, mouseY)) {
            draggingPanel = 0;
        } else if (InfoPanel.overSlider(font, panelX(), modifierPanelY(), InfoPanel.WIDTH, InfoPanel.HEIGHT,
                modifierCaption != null, modifierLines, mouseX, mouseY)) {
            draggingPanel = 1;
        } else {
            return false;
        }
        return sliderDragged(mouseX, mouseY);
    }

    @Override
    protected boolean sliderDragged(double mouseX, double mouseY) {
        if (sidePanel.sliderDragged(mouseY)) {
            return true;
        }
        if (draggingPanel == 0) {
            toolScroll = InfoPanel.sliderScroll(font, panelY(), InfoPanel.WIDTH, InfoPanel.HEIGHT,
                    toolCaption != null, toolLines, mouseY);
        } else if (draggingPanel == 1) {
            modifierScroll = InfoPanel.sliderScroll(font, modifierPanelY(), InfoPanel.WIDTH, InfoPanel.HEIGHT,
                    modifierCaption != null, modifierLines, mouseY);
        } else {
            return false;
        }
        return true;
    }

    @Override
    protected void sliderReleased() {
        sidePanel.sliderReleased();
        draggingPanel = -1;
    }

    /** The info panels, the side inventory and the stand all hang outside {@code imageWidth} (#68 fix 4). */
    @Override
    public List<Rect2i> extraGuiAreas() {
        List<Rect2i> areas = new ArrayList<>(super.extraGuiAreas()); // the station-group tab row (#78)
        areas.add(new Rect2i(panelX(), panelY(), InfoPanel.WIDTH,
                InfoPanel.HEIGHT * 2 + PANEL_SPACING));
        if (!menu.sideSlots.isEmpty()) {
            areas.add(sidePanel.bounds());
        } else {
            areas.add(StandPreview.box(leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_SCALE));
        }
        return areas;
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.MODIFIER_WORKTABLE.get(), ModifierWorktableScreen::new);
    }
}
