package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.gkissel.forgeweave.Forgeweave;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.RenameStationItemPayload;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;
import dev.gkissel.forgeweave.menu.ToolStationTabs.Tab;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * The Tool Station's GUI, rebuilt to upstream 1.12's layout (issue #47). The background is now
 * upstream's {@code toolstation.png} copied whole rather than the flattened 176x166 crop issue #43
 * shipped, because every piece of chrome that crop removed is back in use: the rename field's
 * highlight strip, the translucent item cover over the big tool preview, and the slot
 * background/border sprites the dynamic slot positions are drawn with (NOTICE.md). Layout constants
 * below are upstream's own -- the panel is 176x174 (which is why {@link ToolStationMenu} puts the
 * player inventory 8px lower than a stock GUI), the output slot sits where {@code
 * ContainerToolStation} puts it, and the sidebar/info-panel geometry is {@code GuiToolStation}'s.
 *
 * <p>Four things make up the parity work, all reading state the menu owns:
 *
 * <ul>
 *   <li><b>Sidebar.</b> One button per {@link ToolStationTabs.Tab} -- repair first, then a tool each
 *       -- drawn from upstream's button sprites in {@code icons.png}, laid out with upstream's own
 *       {@code GuiSideButtons} grid rule (5 columns, 4px gaps, to the left of the panel). Clicking
 *       one takes the vanilla stonecutter path: local {@code clickMenuButton} for instant feedback,
 *       then the real server-bound button-click packet.
 *   <li><b>Slots.</b> Positions come from the menu, which rebuilds them from the synced tab; this
 *       class only draws the sprite under each one and, for empty slots, the ghost of the part that
 *       belongs there (the part's own item texture at 40% alpha, upstream's outline-render idea
 *       without upstream's runtime texture-stitching machinery).
 *   <li><b>Info panels.</b> Two stacked {@link InfoPanel}s to the right, exactly as upstream stacks
 *       its {@code toolInfo}/{@code traitInfo} pair (on the left there, right here per issue #47's
 *       brief). Contents follow upstream's three-way split in {@code GuiToolStation#updateDisplay}:
 *       an assembled tool shows its stats and materials plus its traits, an unbuilt tool tab shows
 *       the tool's description plus the components it needs, and the repair tab shows its own blurb.
 *   <li><b>Rename field.</b> Upstream's text field at its own coordinates. It sends
 *       {@link RenameStationItemPayload} on every edit and the server applies the name to the output
 *       stack, so nothing here decides what the crafted item is called.
 * </ul>
 *
 * <p>{@link AbstractContainerScreen#render} does <em>not</em> call {@link #renderTooltip} on its
 * own -- every vanilla container screen overrides {@code render} to add it (issue #43 regression
 * fix, kept here); the {@code renderTooltip} override adds the sidebar buttons, which aren't slots.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ToolStationScreen extends AbstractContainerScreen<ToolStationMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/tool_station.png");
    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/station_icons.png");
    private static final int SHEET = 256;

    private static final int BASE_WIDTH = 176;
    private static final int BASE_HEIGHT = 174;

    // Regions of toolstation.png, from GuiToolStation's own GuiElement constants.
    private static final int COVER_U = 176;
    private static final int COVER_V = 18;
    private static final int COVER_W = 80;
    private static final int COVER_H = 64;
    private static final int SLOT_BACKGROUND_U = 176;
    private static final int SLOT_BORDER_U = 194;
    private static final int SLOT_SPRITE_V = 0;
    private static final int SLOT_SPRITE_SIZE = 18;
    private static final int TEXT_FIELD_U = 0;
    private static final int TEXT_FIELD_V = 210;
    private static final int TEXT_FIELD_W = 102;
    private static final int TEXT_FIELD_H = 12;
    private static final int BEAM_V = 180; // the wood-style beam; the metal one sits 7px below it
    private static final int BEAM_H = 7;
    private static final int BEAM_END_W = 2;
    private static final int BEAM_CENTER_U = 2;
    private static final int BEAM_CENTER_W = 129;
    private static final int BEAM_RIGHT_U = 131;

    // Regions of icons.png. Buttons are Icons#ICON_Button and friends shifted to the wood style.
    private static final int BUTTON_V = 180;
    private static final int BUTTON_IDLE_U = 180;
    private static final int BUTTON_HOVER_U = 216;
    private static final int BUTTON_PRESSED_U = 144;
    private static final int ANVIL_U = 54;
    private static final int ANVIL_V = 0;
    /** Upstream's repair-slot icons: a tool, then two "repair material" glyphs. */
    private static final int[] REPAIR_ICON_U = {0, 54, 36};
    private static final int[] REPAIR_ICON_V = {234, 234, 216};

    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_SPACING = 4;
    private static final int BUTTON_COLUMNS = 5;
    private static final int BUTTONS_Y = 9;

    private static final int PANEL_GAP = 2;
    private static final int PANEL_TOP = 11;
    private static final int PANEL_SPACING = 4;

    private static final int NAME_FIELD_X = 70;
    private static final int NAME_FIELD_Y = 7;
    private static final int NAME_FIELD_W = 92;
    private static final int NAME_FIELD_H = 12;
    private static final int NAME_MAX_LENGTH = 40;

    private static final float GHOST_ALPHA = 0.4F;
    private static final float COVER_ALPHA = 0.82F;
    private static final float SLOT_BACKGROUND_ALPHA = 0.28F;
    private static final float PREVIEW_SCALE = 3.7F;

    @Nullable
    private EditBox nameField;
    private String lastSentName = "";

    @Nullable
    private Component toolCaption;
    private List<Component> toolLines = List.of();
    @Nullable
    private Component traitCaption;
    private List<Component> traitLines = List.of();
    private int toolScroll;
    private int traitScroll;

    public ToolStationScreen(ToolStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_WIDTH;
        imageHeight = BASE_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(font, leftPos + NAME_FIELD_X, topPos + NAME_FIELD_Y, NAME_FIELD_W, NAME_FIELD_H,
                Component.translatable("gui.forgeweave.tool_station.name"));
        nameField.setBordered(false);
        nameField.setMaxLength(NAME_MAX_LENGTH);
        nameField.setTextColor(0xFFFFFF);
        // lastSentName survives a resize (which re-runs init) so the typed name isn't lost.
        String current = lastSentName.isEmpty() ? menu.getToolName() : lastSentName;
        nameField.setValue(current);
        lastSentName = current;
        nameField.setResponder(this::onNameChanged);
        addRenderableWidget(nameField);
        updateInfo();
    }

    private void onNameChanged(String name) {
        if (name.equals(lastSentName)) {
            return;
        }
        lastSentName = name;
        // Server-authoritative: the menu applies (and validates) the name onto the output stack, and
        // the renamed stack comes back down the ordinary slot sync. Upstream sends per keystroke too.
        PacketDistributor.sendToServer(new RenameStationItemPayload(menu.containerId, name));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateInfo();
    }

    // ------------------------------------------------------------------ info panel contents

    /** Upstream {@code GuiToolStation#updateDisplay}'s three-way split, kept in the same order. */
    private void updateInfo() {
        Tab tab = menu.tab();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        ItemStack head = menu.getSlot(ToolStationMenu.HEAD_SLOT).getItem();
        ItemStack subject = !output.isEmpty() ? output : head.getItem() instanceof ToolItem ? head : ItemStack.EMPTY;

        if (!subject.isEmpty()) {
            describeTool(subject);
        } else {
            toolCaption = tab.title();
            toolLines = List.of(Component.translatable(tab.descriptionKey()));
            if (tab.isRepair()) {
                traitCaption = null;
                traitLines = List.of();
            } else {
                traitCaption = Component.translatable("gui.forgeweave.tool_station.components");
                traitLines = componentLines(tab);
            }
        }
        toolScroll = Math.min(toolScroll, InfoPanel.maxScroll(font, InfoPanel.WIDTH, InfoPanel.HEIGHT, true, toolLines));
        traitScroll = Math.min(traitScroll,
                InfoPanel.maxScroll(font, InfoPanel.WIDTH, InfoPanel.HEIGHT, traitCaption != null, traitLines));
    }

    private void describeTool(ItemStack tool) {
        toolCaption = tool.getHoverName();

        List<Component> lines = new ArrayList<>(StationText.toolStats(tool));
        ToolMaterials materials = tool.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials != null) {
            lines.add(null);
            lines.add(Component.translatable("gui.forgeweave.tool_station.materials").withStyle(ChatFormatting.UNDERLINE));
            lines.addAll(StationText.toolMaterials(registries(), materials));
        }
        if (ToolItem.isBroken(tool)) {
            lines.add(null);
            lines.add(Component.translatable("tooltip.forgeweave.broken").withStyle(ChatFormatting.DARK_RED));
        }
        toolLines = lines;

        traitCaption = Component.translatable("gui.forgeweave.tool_station.traits");
        List<Component> traits = StationText.traits(StationText.traitIdsOf(tool));
        traitLines = traits.isEmpty()
                ? List.of(Component.translatable("gui.forgeweave.tool_station.no_traits").withStyle(ChatFormatting.GRAY))
                : traits;
    }

    /** " * Part Name" per required component, red while its slot doesn't hold that part yet. */
    private List<Component> componentLines(Tab tab) {
        List<Component> lines = new ArrayList<>(ToolStationMenu.INPUT_SLOTS);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            Item part = requiredPart(tab, i);
            boolean satisfied = menu.getSlot(i).getItem().is(part);
            Component name = Component.literal(" * ").append(new ItemStack(part).getHoverName());
            lines.add(satisfied ? name : name.copy().withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /** The part a build tab's slot {@code index} wants: its own head part, then binding, then handle. */
    private static Item requiredPart(Tab tab, int index) {
        return switch (index) {
            case ToolStationMenu.HEAD_SLOT -> tab.headPart().get();
            case ToolStationMenu.BINDING_SLOT -> ForgeweaveItems.PART_TOOL_BINDING.get();
            default -> ForgeweaveItems.PART_TOOL_HANDLE.get();
        };
    }

    @Nullable
    private HolderLookup.Provider registries() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, SHEET, SHEET);
        renderToolPreview(graphics);
        renderSlots(graphics);
        renderSidebar(graphics, mouseX, mouseY);
        renderInfoPanels(graphics);
        if (nameField != null && nameField.isFocused()) {
            graphics.blit(TEXTURE, leftPos + NAME_FIELD_X - 2, topPos + NAME_FIELD_Y - 1,
                    TEXT_FIELD_U, TEXT_FIELD_V, TEXT_FIELD_W, TEXT_FIELD_H, SHEET, SHEET);
        }
    }

    /**
     * Upstream's oversized preview of what the selected tab builds (an anvil glyph for repair),
     * dimmed by the panel's own translucent cover so the slots on top of it stay legible.
     */
    private void renderToolPreview(GuiGraphics graphics) {
        Tab tab = menu.tab();
        if (!tab.isRepair()) {
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + 9.0F, topPos + 20.0F, 0.0F);
            graphics.pose().scale(PREVIEW_SCALE, PREVIEW_SCALE, 1.0F);
            graphics.renderItem(new ItemStack(tab.tool().get()), 0, 0);
            graphics.pose().popPose();
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 250.0F); // above the item render, which draws with depth
        if (tab.isRepair()) {
            graphics.blit(ICONS, leftPos + 38, topPos + 41, ANVIL_U, ANVIL_V, 18, 18, SHEET, SHEET);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, COVER_ALPHA);
        graphics.blit(TEXTURE, leftPos + 7, topPos + 18, COVER_U, COVER_V, COVER_W, COVER_H, SHEET, SHEET);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    /** Slot plate, border and -- while empty -- the ghost of whatever belongs in that slot. */
    private void renderSlots(GuiGraphics graphics) {
        Tab tab = menu.tab();
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            var slot = menu.getSlot(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;

            graphics.setColor(1.0F, 1.0F, 1.0F, SLOT_BACKGROUND_ALPHA);
            graphics.blit(TEXTURE, x, y, SLOT_BACKGROUND_U, SLOT_SPRITE_V,
                    SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE, SHEET, SHEET);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(TEXTURE, x, y, SLOT_BORDER_U, SLOT_SPRITE_V,
                    SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE, SHEET, SHEET);

            if (slot.hasItem()) {
                continue;
            }
            if (tab.isRepair()) {
                graphics.blit(ICONS, x, y, REPAIR_ICON_U[i], REPAIR_ICON_V[i],
                        SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE, SHEET, SHEET);
            } else {
                graphics.setColor(1.0F, 1.0F, 1.0F, GHOST_ALPHA);
                graphics.blit(partTexture(requiredPart(tab, i)), leftPos + slot.x, topPos + slot.y, 0, 0, 16, 16, 16, 16);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    /** A part item's own inventory sprite, blitted straight from its file (GUI draws bypass the atlas). */
    private static ResourceLocation partTexture(Item part) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(part);
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/derived/item/" + id.getPath() + ".png");
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int selected = menu.getSelectedTab();
        for (int i = 0; i < ToolStationTabs.TABS.size(); i++) {
            int x = leftPos + buttonX(i);
            int y = topPos + buttonY(i);
            boolean hovered = isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
            int u = i == selected ? BUTTON_PRESSED_U : hovered ? BUTTON_HOVER_U : BUTTON_IDLE_U;
            graphics.blit(ICONS, x, y, u, BUTTON_V, BUTTON_SIZE, BUTTON_SIZE, SHEET, SHEET);

            Tab tab = ToolStationTabs.get(i);
            if (tab.isRepair()) {
                graphics.blit(ICONS, x, y, ANVIL_U, ANVIL_V, BUTTON_SIZE, BUTTON_SIZE, SHEET, SHEET);
            } else {
                graphics.renderItem(new ItemStack(tab.tool().get()), x + 1, y + 1);
            }
        }
        beam(graphics, leftPos + buttonX(0) - BEAM_END_W, topPos, sidebarWidth());
    }

    private void renderInfoPanels(GuiGraphics graphics) {
        int x = leftPos + BASE_WIDTH + PANEL_GAP;
        int top = topPos + PANEL_TOP;
        beam(graphics, x - BEAM_END_W, topPos, InfoPanel.WIDTH);
        InfoPanel.render(graphics, font, x, top, InfoPanel.WIDTH, InfoPanel.HEIGHT, toolCaption, toolLines, toolScroll);
        InfoPanel.render(graphics, font, x, top + InfoPanel.HEIGHT + PANEL_SPACING,
                InfoPanel.WIDTH, InfoPanel.HEIGHT, traitCaption, traitLines, traitScroll);
    }

    /** Upstream's horizontal beam that visually ties a side module to the main panel. */
    private void beam(GuiGraphics graphics, int x, int y, int width) {
        graphics.blit(TEXTURE, x, y, 0, BEAM_V, BEAM_END_W, BEAM_H, SHEET, SHEET);
        graphics.blit(TEXTURE, x + BEAM_END_W, y, width, BEAM_H, BEAM_CENTER_U, BEAM_V, BEAM_CENTER_W, BEAM_H, SHEET, SHEET);
        graphics.blit(TEXTURE, x + BEAM_END_W + width, y, BEAM_RIGHT_U, BEAM_V, BEAM_END_W, BEAM_H, SHEET, SHEET);
    }

    // ------------------------------------------------------------------ sidebar geometry & input

    private static int sidebarWidth() {
        int columns = Math.min(ToolStationTabs.TABS.size(), BUTTON_COLUMNS);
        return columns * BUTTON_SIZE + (columns - 1) * BUTTON_SPACING;
    }

    private static int buttonX(int index) {
        return -sidebarWidth() - PANEL_GAP + (index % BUTTON_COLUMNS) * (BUTTON_SIZE + BUTTON_SPACING);
    }

    private static int buttonY(int index) {
        return BUTTONS_Y + (index / BUTTON_COLUMNS) * (BUTTON_SIZE + BUTTON_SPACING);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        for (int i = 0; i < ToolStationTabs.TABS.size(); i++) {
            if (isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                graphics.renderTooltip(font, ToolStationTabs.get(i).title(), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < ToolStationTabs.TABS.size(); i++) {
            if (isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)
                    && menu.clickMenuButton(minecraft.player, i)) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                toolScroll = 0;
                traitScroll = 0;
                updateInfo();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelX = BASE_WIDTH + PANEL_GAP;
        if (isHovering(panelX, PANEL_TOP, InfoPanel.WIDTH, InfoPanel.HEIGHT, mouseX, mouseY)) {
            toolScroll = clampScroll(toolScroll - (int) Math.signum(scrollY), true, toolLines);
            return true;
        }
        if (isHovering(panelX, PANEL_TOP + InfoPanel.HEIGHT + PANEL_SPACING, InfoPanel.WIDTH, InfoPanel.HEIGHT, mouseX, mouseY)) {
            traitScroll = clampScroll(traitScroll - (int) Math.signum(scrollY), traitCaption != null, traitLines);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int clampScroll(int value, boolean hasCaption, List<Component> lines) {
        return Math.clamp(value, 0, InfoPanel.maxScroll(font, InfoPanel.WIDTH, InfoPanel.HEIGHT, hasCaption, lines));
    }

    /**
     * Vanilla's anvil screen pattern: while the rename field has focus it eats the keystrokes, so the
     * inventory key doesn't close the screen mid-word, but Escape still does.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            minecraft.player.closeContainer();
            return true;
        }
        return nameField.keyPressed(keyCode, scanCode, modifiers) || nameField.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.TOOL_STATION.get(), ToolStationScreen::new);
    }
}
