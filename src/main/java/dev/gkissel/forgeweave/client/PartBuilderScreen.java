package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * The Part Builder's GUI. The background is upstream 1.12's real {@code partbuilder.png} (issue
 * #43, replacing the flat placeholder panel from issue #9), cropped to its 176x166 panel region
 * (NOTICE.md); {@link PartBuilderMenu}'s slot coordinates were moved to match its baked-in slot art.
 *
 * <p>Issue #47 adds upstream's right-hand information panel and the two smaller readouts that go
 * with it, all from {@code GuiPartBuilder}:
 *
 * <ul>
 *   <li>the {@link InfoPanel} describing the material currently loaded -- what it makes the part's
 *       stats, what trait it grants and what that trait does -- or, with only a pattern in, what
 *       that part costs;
 *   <li>upstream's centred "Material Value" line under the slots, showing what the material stack
 *       is worth against the selected part's cost and turning red when it isn't enough
 *       ({@code GuiPartBuilder#drawGuiContainerForegroundLayer});
 *   <li>ghost icons in the empty pattern/material/change slots, upstream's {@code drawIconEmpty}
 *       drawn from the same {@code icons.png} sprites.
 * </ul>
 *
 * <p>Every number shown comes from {@link PartBuilderRecipes} (the one place that prices parts) or
 * from the material registry; nothing is restated here.
 *
 * <p>{@link AbstractContainerScreen#render} does <em>not</em> call {@link #renderTooltip} on its
 * own (unlike the label/slot rendering, that call is left to subclasses), so this screen once
 * showed no item tooltips at all (issue #43). That override now lives in {@link StationScreen},
 * shared by every station screen, because copying it per screen let the same defect reappear in
 * three later ones (issue #75).
 *
 * <p>When a neighboring block exposes an item handler ({@code PartBuilderBlockEntity#findSideInventory},
 * issue #40's follow-up), its slots render in a panel off the station's <em>left</em> edge via
 * {@link SideInventoryPanel} -- where upstream's {@code GuiPartBuilder} puts its pattern chest,
 * the right-hand side being taken by the info panel. Shared with {@link CraftingStationScreen}/
 * {@link ToolStationScreen}'s own side panels; {@link dev.gkissel.forgeweave.menu.PartBuilderMenu}
 * owns the coordinates.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PartBuilderScreen extends StationScreen<PartBuilderMenu> implements StationExtraAreas {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/part_builder.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/station_icons.png");
    private static final int SHEET = 256;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 166;

    /** Upstream {@code Icons}: the pattern, ingot, block, (no icon on output) and shard glyphs, in slot order. */
    private static final int[] SLOT_ICON_U = {0, 54, 36, -1, 18};
    private static final int[] SLOT_ICON_V = {216, 234, 216, -1, 216};
    private static final int ICON_SIZE = 18;

    /**
     * Upstream's {@code GuiPartBuilder} never touches its info panel's {@code xOffset}/{@code
     * yOffset} -- only {@code GuiToolStation} does, to clear its beam and panel decorations. Issue
     * #79: this station had picked up the Tool Station's 2px gap, which is not upstream's.
     */
    private static final int PANEL_GAP = 0;

    /** Upstream {@code GuiPartBuilder.Column_Count} and {@code GuiSideButtons}' own grid metrics (issue #78). */
    private static final int BUTTON_COLUMNS = 4;
    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_SPACING = 4;
    /** The wood-style button row of {@code icons.png}, same constants {@link StencilTableScreen} uses. */
    private static final int BUTTON_V = 234;
    private static final int BUTTON_IDLE_U = 180;
    private static final int BUTTON_HOVER_U = 216;
    private static final int PANEL_TOP = 0;
    /** Upstream gives this station's single panel the full GUI height ({@code info.ySize = this.ySize}). */
    private static final int PANEL_HEIGHT = BASE_HEIGHT;
    /** Upstream leaves {@code GuiPartBuilder}'s panel on the default dark frame; only the Tool Station calls {@code wood()}. */
    private static final InfoPanel.Style PANEL_STYLE = InfoPanel.Style.DEFAULT;
    private static final int MATERIAL_VALUE_Y = 63;

    @Nullable
    private Component caption;
    private List<Component> lines = List.of();
    private int scroll;
    private boolean draggingScroll;
    private final SideInventoryPanel sidePanel =
            new SideInventoryPanel(PartBuilderMenu.SIDE_PANEL_X, PartBuilderMenu.SIDE_PANEL_Y);

    public PartBuilderScreen(PartBuilderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        updateInfo();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateInfo();
    }

    private void updateInfo() {
        ItemStack pattern = menu.getSlot(PartBuilderMenu.PATTERN_SLOT).getItem();
        List<Component> body = new ArrayList<>();

        Optional<PartItem> patternPart = PartBuilderRecipes.patternPart(pattern);
        patternPart.ifPresent(part -> {
            body.add(new ItemStack(part).getHoverName().copy().withStyle(ChatFormatting.UNDERLINE));
            PartBuilderRecipes.patternCost(pattern)
                    .ifPresent(cost -> body.add(Component.translatable("gui.forgeweave.part_builder.cost", cost)));
            body.add(null);
        });

        Optional<Material> loaded = materialInSlots();
        if (loaded.isPresent()) {
            caption = MaterialDisplay.name(registries(), materialId().orElseThrow());
            body.addAll(StationText.materialStats(loaded.get()));
            body.add(null);
            // Scoped to the part the loaded pattern makes, as upstream's GuiPartBuilder is (it renders
            // the part's own tooltip trait info); with no pattern in, the material's general traits.
            PartItem.Kind kind = patternPart.map(PartItem::kind).orElse(PartItem.Kind.NONE);
            body.addAll(StationText.traits(loaded.get().color(), loaded.get().traits().forPart(kind)));
        } else {
            caption = title;
            if (body.isEmpty()) {
                body.add(Component.translatable("gui.forgeweave.part_builder.info"));
            }
        }

        lines = body;
        scroll = Math.min(scroll, InfoPanel.maxScroll(font, InfoPanel.WIDTH, PANEL_HEIGHT, caption != null, lines));
    }

    /**
     * The combined material match across both material slots (issue #306), upstream's own
     * {@code GuiPartBuilder#getMaterial(input1, input2)}.
     */
    private Optional<PartBuilderRecipes.CombinedMaterialMatch> matchMaterial() {
        HolderLookup.Provider registries = registries();
        if (registries == null) {
            return Optional.empty();
        }
        ItemStack material1 = menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem();
        ItemStack material2 = menu.getSlot(PartBuilderMenu.MATERIAL_SLOT_2).getItem();
        return PartBuilderRecipes.combinedMaterialValue(registries, material1, material2);
    }

    private Optional<ResourceLocation> materialId() {
        return matchMaterial().map(PartBuilderRecipes.CombinedMaterialMatch::id);
    }

    private Optional<Material> materialInSlots() {
        return materialId().flatMap(id -> MaterialDisplay.lookup(registries(), id));
    }

    @Nullable
    private HolderLookup.Provider registries() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        renderSlotIcons(graphics);
        renderMaterialValue(graphics);
        InfoPanel.render(graphics, font, panelX(), panelY(),
                InfoPanel.WIDTH, PANEL_HEIGHT, PANEL_STYLE, caption, lines, scroll);
        if (menu.partCrafter) {
            renderPatternButtons(graphics, mouseX, mouseY);
        } else {
            sidePanel.render(graphics, menu, leftPos, topPos, imageHeight, menu.sideSlots);
        }
    }

    // ------------------------------------------------------------ pattern chest sidebar (issue #78)

    /**
     * Upstream's {@code GuiButtonsPartCrafter}: with a Pattern Chest attached (and the Stencil Table
     * and Crafting Station its {@code partCrafter} check also wants), the chest's slots are replaced
     * by one button per pattern it holds, and clicking one swaps that pattern into the pattern slot.
     * Same {@code GuiSideButtons} grid rule {@link StencilTableScreen} already draws -- {@code
     * GuiPartBuilder.Column_Count} is 4 there too, and {@code GuiPartBuilder} likewise sets no module
     * offsets, so the grid is flush with the panel's left edge at its top.
     *
     * <p>{@link PartBuilderMenu#patternButtons} decides <em>which</em> patterns appear; this only
     * draws them. The button's id is its index into the fixed pattern list, so a click identifies a
     * pattern rather than a screen position.
     */
    private void renderPatternButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Integer> buttons = menu.patternButtons();
        for (int i = 0; i < buttons.size(); i++) {
            int x = leftPos + buttonX(i);
            int y = topPos + buttonY(i);
            int u = isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)
                    ? BUTTON_HOVER_U : BUTTON_IDLE_U;
            graphics.blit(ICONS, x, y, u, BUTTON_V, BUTTON_SIZE, BUTTON_SIZE, SHEET, SHEET);
            graphics.renderItem(patternStack(buttons.get(i)), x + 1, y + 1);
        }
    }

    private static ItemStack patternStack(int patternId) {
        return new ItemStack(StencilTableMenu.PATTERNS.get(patternId).get());
    }

    private int sidebarColumns() {
        return Math.min(Math.max(menu.patternButtons().size(), 1), BUTTON_COLUMNS);
    }

    private int sidebarWidth() {
        int columns = sidebarColumns();
        return columns * BUTTON_SIZE + (columns - 1) * BUTTON_SPACING;
    }

    private int sidebarHeight() {
        int rows = (menu.patternButtons().size() + BUTTON_COLUMNS - 1) / BUTTON_COLUMNS;
        return Math.max(rows, 1) * BUTTON_SIZE + (Math.max(rows, 1) - 1) * BUTTON_SPACING;
    }

    private int buttonX(int index) {
        return -sidebarWidth() + (index % BUTTON_COLUMNS) * (BUTTON_SIZE + BUTTON_SPACING);
    }

    private static int buttonY(int index) {
        return (index / BUTTON_COLUMNS) * (BUTTON_SIZE + BUTTON_SPACING);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Integer> buttons = menu.patternButtons();
        for (int i = 0; i < buttons.size(); i++) {
            if (isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY) && minecraft != null) {
                // Server-authoritative: the swap happens in PartBuilderMenu#clickMenuButton and comes
                // back as ordinary slot syncs, so nothing is applied locally first.
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttons.get(i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        List<Integer> buttons = menu.patternButtons();
        for (int i = 0; i < buttons.size(); i++) {
            if (isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                graphics.renderTooltip(font, patternStack(buttons.get(i)), mouseX, mouseY);
                return;
            }
        }
        // Issue #376: upstream's GuiPartBuilder gives every line of this panel a tooltip
        // (setDisplayForMaterial passes a `tips` list alongside its `stats`), and this station's
        // material panel was the biggest hover gap left after issue #258 wired the Tool Station's.
        // Same machinery: StationText hangs SHOW_TEXT events on the lines, the panel says which
        // style is under the cursor, and vanilla renders whatever it carries -- lines without one
        // no-op inside the call.
        Style hovered = InfoPanel.hoveredStyle(font, panelX(), panelY(), InfoPanel.WIDTH, PANEL_HEIGHT,
                caption != null, lines, scroll, mouseX, mouseY);
        if (hovered != null) {
            graphics.renderComponentHoverEffect(font, hovered, mouseX, mouseY);
        }
    }

    private int panelX() {
        return leftPos + BASE_WIDTH + PANEL_GAP;
    }

    private int panelY() {
        return topPos + PANEL_TOP;
    }

    @Override
    protected boolean sliderClicked(double mouseX, double mouseY) {
        if (sidePanel.sliderClicked(mouseX, mouseY)) {
            return true;
        }
        if (!InfoPanel.overSlider(font, panelX(), panelY(), InfoPanel.WIDTH, PANEL_HEIGHT,
                caption != null, lines, mouseX, mouseY)) {
            return false;
        }
        draggingScroll = true;
        return sliderDragged(mouseX, mouseY);
    }

    @Override
    protected boolean sliderDragged(double mouseX, double mouseY) {
        if (sidePanel.sliderDragged(mouseY)) {
            return true;
        }
        if (!draggingScroll) {
            return false;
        }
        scroll = InfoPanel.sliderScroll(font, panelY(), InfoPanel.WIDTH, PANEL_HEIGHT, caption != null, lines, mouseY);
        return true;
    }

    @Override
    protected void sliderReleased() {
        sidePanel.sliderReleased();
        draggingScroll = false;
    }

    /** Upstream's {@code drawIconEmpty}: a hint glyph in each empty slot, never over a real item. */
    private void renderSlotIcons(GuiGraphics graphics) {
        for (int i = 0; i < SLOT_ICON_U.length; i++) {
            if (SLOT_ICON_U[i] < 0 || menu.getSlot(i).hasItem()) {
                continue;
            }
            graphics.blit(ICONS, leftPos + menu.getSlot(i).x - 1, topPos + menu.getSlot(i).y - 1,
                    SLOT_ICON_U[i], SLOT_ICON_V[i], ICON_SIZE, ICON_SIZE, SHEET, SHEET);
        }
    }

    /**
     * "Material Value: N" for the loaded stack, in red when it falls short of the selected pattern's
     * cost -- the one readout that tells a player why the output slot is empty.
     */
    private void renderMaterialValue(GuiGraphics graphics) {
        Optional<PartBuilderRecipes.CombinedMaterialMatch> matched = matchMaterial();
        if (matched.isEmpty()) {
            return;
        }
        int available = matched.get().totalValue();
        Component text = Component.translatable("gui.forgeweave.part_builder.material_value", available);
        boolean enough = PartBuilderRecipes.patternCost(menu.getSlot(PartBuilderMenu.PATTERN_SLOT).getItem())
                .map(cost -> available >= cost)
                .orElse(true);
        Component line = enough ? text : text.copy().withStyle(ChatFormatting.DARK_RED);
        graphics.drawString(font, line, leftPos + BASE_WIDTH / 2 - font.width(line) / 2,
                topPos + MATERIAL_VALUE_Y, 0x777777, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sidePanel.mouseScrolled(mouseX, mouseY, scrollY, imageHeight, menu.sideSlots)) {
            return true;
        }
        if (isHovering(BASE_WIDTH + PANEL_GAP, PANEL_TOP, InfoPanel.WIDTH, PANEL_HEIGHT, mouseX, mouseY)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0,
                    InfoPanel.maxScroll(font, InfoPanel.WIDTH, PANEL_HEIGHT, caption != null, lines));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Issue #68 fix 4: the info panel and side panel hang outside {@code imageWidth}; JEI has to be told. */
    @Override
    public List<Rect2i> extraGuiAreas() {
        List<Rect2i> areas = super.extraGuiAreas(); // the station-group tab row (issue #78)
        areas.add(new Rect2i(leftPos + BASE_WIDTH + PANEL_GAP, topPos + PANEL_TOP, InfoPanel.WIDTH, PANEL_HEIGHT));
        if (menu.partCrafter) {
            if (!menu.patternButtons().isEmpty()) {
                areas.add(new Rect2i(leftPos + buttonX(0), topPos, sidebarWidth(), sidebarHeight()));
            }
        } else if (!menu.sideSlots.isEmpty()) {
            areas.add(sidePanel.bounds());
        }
        return areas;
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.PART_BUILDER.get(), PartBuilderScreen::new);
    }
}
