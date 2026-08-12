package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
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
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolConstants;
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
 * own (issue #43); that override now lives in {@link StationScreen}, shared by every station
 * screen, because copying it per screen let the same defect reappear in three later ones (issue
 * #75). The {@code renderTooltip} override below adds the sidebar buttons, which aren't slots.
 *
 * <p>When a neighboring block exposes an item handler ({@code ToolStationBlockEntity#findSideInventory},
 * issue #40's follow-up), its slots render via {@link SideInventoryPanel} on the station's
 * <em>left</em> edge, below the tool-tab column -- the same edge {@link CraftingStationScreen} and
 * {@link PartBuilderScreen} use, stacked under the tabs rather than beside them because that edge is
 * shared here. Issue #79 had put it right of the info panels instead; issue #88 is the playtest
 * report of what that cost, and {@link ToolStationMenu#SIDE_PANEL_X} carries the measurements.
 *
 * <p>This is also the Tool Forge's GUI (issue #152). Upstream's {@code GuiToolForge} is a
 * three-line {@code GuiToolStation} subclass whose whole content is a {@code metal()} call plus a
 * different buildable-tool set; here both blocks open the same {@link ToolStationMenu} type, so
 * there is one screen registration and the metal styling is picked per-instance from
 * {@link ToolStationMenu#isForge()} -- see {@link #panelStyle()}, {@link #beamV()} and
 * {@link #buttonV()}. Which tools each block can build is the menu's business, not the screen's.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ToolStationScreen extends StationScreen<ToolStationMenu> implements StationExtraAreas {
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
    /** Upstream {@code GuiToolStation#metal()}: {@code beamL/R/C.shift(0, beam.h)} (issue #152). */
    private static final int BEAM_METAL_V = BEAM_V + 7;
    private static final int BEAM_H = 7;
    private static final int BEAM_END_W = 2;
    private static final int BEAM_CENTER_U = 2;
    private static final int BEAM_CENTER_W = 129;
    private static final int BEAM_RIGHT_U = 131;

    // Regions of icons.png. Buttons are Icons#ICON_Button and friends shifted to the wood style:
    // upstream's ICON_Button is (180, 216) and the wood style is that row shifted +18y, so v = 234.
    // Issue #75: this said 180 -- 54px too high, landing on a decorative plank tile with no button
    // bevel -- which is why the tab buttons rendered as flat wooden squares. Same constant was wrong
    // in StencilTableScreen; both are fixed together since both come from upstream's GuiSideButtons.
    private static final int BUTTON_V = 234;
    /**
     * The Tool Forge's button row (issue #152). Upstream's three side-button styles are three rows of
     * {@code icons.png} 18px apart, and {@code GuiButtonsToolStation#metal()} picks the one between
     * the default and the wood row -- v 198 against {@code ICON_Button}'s own 216.
     */
    private static final int BUTTON_METAL_V = 198;
    private static final int BUTTON_IDLE_U = 180;
    private static final int BUTTON_HOVER_U = 216;
    private static final int BUTTON_PRESSED_U = 144;
    private static final int ANVIL_U = 54;
    private static final int ANVIL_V = 0;
    /**
     * Upstream's repair-slot glyphs, in its own order: {@code Icons.ICON_Pickaxe}, {@code ICON_Dust},
     * {@code ICON_Lapis}, {@code ICON_Ingot}, {@code ICON_Gem} ({@code GuiToolStation
     * #drawRepairSlotIcon} pairs them with {@code GuiButtonRepair}'s positions, one per active slot).
     * Issue #154 added the 4th and 5th along with the two extra reagent slots embossing needs.
     */
    private static final int[] REPAIR_ICON_U = {0, 18, 36, 54, 72};
    private static final int REPAIR_ICON_V = 234;

    /**
     * The layers of a tool's item model, and the fixed colours upstream tints them with when it
     * renders a tool purely as a picture: {@code ClientProxy.RenderMaterials}' {@code MaterialGUI}
     * entries, which is what {@code TinkersItem#buildItemForRenderingInGui} feeds
     * {@code GuiToolStation}'s preview and {@code GuiButtonItem}'s icons. The preview deliberately
     * ignores what is actually in the slots, exactly as upstream's does.
     *
     * <p>Keyed on the part's role rather than on its slot index, for the same reason
     * {@link ToolArt#layers} is: a four-part tool's slot 2 is a second head, and a three-part one's
     * is the binding, so an index would tint the battleaxe's front head binding-blue (issue #159).
     */
    private static final Map<ToolConstants.Role, Integer> TOOL_LAYER_COLORS = new EnumMap<>(Map.of(
            ToolConstants.Role.HANDLE, 0x684E1E,
            ToolConstants.Role.HEAD, 0xC1C1C1,
            ToolConstants.Role.EXTRA, 0x2376DD));

    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_SPACING = 4;
    private static final int BUTTON_COLUMNS = 5;
    private static final int BUTTONS_Y = 9;

    /**
     * Upstream {@code GuiToolStation#initGui}: {@code buttons.xOffset = -2}, {@code toolInfo.xOffset
     * = 2}, {@code toolInfo.yOffset = beamC.h + panelDecorationL.h} (7 + 4), {@code traitInfo.yOffset
     * = toolInfo.yOffset + toolInfo.ySize + 4}. This is the one station upstream gives module offsets
     * to -- see {@link PartBuilderScreen}/{@link StencilTableScreen}, which get none (issue #79).
     */
    private static final int PANEL_GAP = 2;
    private static final int PANEL_TOP = 11;
    private static final int PANEL_SPACING = 4;
    /**
     * Upstream's {@code wood()} call in {@code GuiToolStation}'s constructor, and the {@code metal()}
     * call {@code GuiToolForge}'s constructor replaces it with (issue #152). That one-line override is
     * the entire difference between the two screens upstream, which is why the Tool Forge has no
     * screen class here either: both blocks open the same {@link ToolStationMenu} type, so there is
     * only one screen to register and it reads {@link ToolStationMenu#isForge()} for its style.
     */
    private InfoPanel.Style panelStyle() {
        return menu.isForge() ? InfoPanel.Style.METAL : InfoPanel.Style.WOOD;
    }

    private int beamV() {
        return menu.isForge() ? BEAM_METAL_V : BEAM_V;
    }

    private int buttonV() {
        return menu.isForge() ? BUTTON_METAL_V : BUTTON_V;
    }

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
    private final SideInventoryPanel sidePanel =
            new SideInventoryPanel(ToolStationMenu.SIDE_PANEL_X, ToolStationMenu.SIDE_PANEL_Y);

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
        // Why the loaded reagents can't be applied (issue #105). Upstream shows its
        // TinkerGuiException text in this same panel; the menu resolves the answer from the synced
        // modifier-recipe registry, so no packet is involved.
        Component rejection = menu.rejection();
        if (rejection != null) {
            List<Component> withError = new ArrayList<>(toolLines);
            withError.add(null);
            withError.add(rejection.copy().withStyle(ChatFormatting.RED));
            toolLines = withError;
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
        lines.add(null);
        lines.add(Component.translatable("gui.forgeweave.tool_station.modifiers").withStyle(ChatFormatting.UNDERLINE));
        lines.addAll(StationText.toolModifiers(tool));
        if (ToolItem.isBroken(tool)) {
            lines.add(null);
            lines.add(Component.translatable("tooltip.forgeweave.broken").withStyle(ChatFormatting.DARK_RED));
        }
        toolLines = lines;

        traitCaption = Component.translatable("gui.forgeweave.tool_station.traits");
        // Each trait in its granting material's colour, as upstream's info panel does (issue #64).
        List<Component> traits = materials == null
                ? List.of()
                : StationText.toolTraits(registries(), materials, StationText.traitIdsOf(tool));
        traitLines = traits.isEmpty()
                ? List.of(Component.translatable("gui.forgeweave.tool_station.no_traits").withStyle(ChatFormatting.GRAY))
                : traits;
    }

    /** " * Part Name" per required component, red while its slot doesn't hold that part yet. */
    private List<Component> componentLines(Tab tab) {
        List<Component> lines = new ArrayList<>(tab.slots().size());
        for (int i = 0; i < tab.slots().size(); i++) {
            Item part = tab.part(i);
            boolean satisfied = menu.getSlot(i).getItem().is(part);
            Component name = Component.literal(" * ").append(new ItemStack(part).getHoverName());
            lines.add(satisfied ? name : name.copy().withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    @Nullable
    private HolderLookup.Provider registries() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, SHEET, SHEET);
        renderToolPreview(graphics);
        renderSlots(graphics);
        renderSidebar(graphics, mouseX, mouseY);
        renderInfoPanels(graphics);
        sidePanel.render(graphics, menu, leftPos, topPos, imageHeight, menu.sideSlots);
        if (nameField != null && nameField.isFocused()) {
            graphics.blit(TEXTURE, leftPos + NAME_FIELD_X - 2, topPos + NAME_FIELD_Y - 1,
                    TEXT_FIELD_U, TEXT_FIELD_V, TEXT_FIELD_W, TEXT_FIELD_H, SHEET, SHEET);
        }
    }

    /**
     * Upstream's oversized preview of what the selected tab builds, dimmed by the panel's own
     * translucent cover so the slots on top of it stay legible. Both branches are drawn at
     * upstream's own {@code 3.7x} from its own {@code (10, 22)} origin -- including the repair tab's
     * anvil, which upstream blows up to the same size as a tool rather than leaving slot-sized.
     */
    private void renderToolPreview(GuiGraphics graphics) {
        Tab tab = menu.tab();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 10.0F, topPos + 22.0F, 0.0F);
        graphics.pose().scale(PREVIEW_SCALE, PREVIEW_SCALE, 1.0F);
        if (tab.isRepair()) {
            graphics.blit(ICONS, 0, 0, ANVIL_U, ANVIL_V, SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE, SHEET, SHEET);
        } else {
            renderToolLayers(graphics, tab, 0, 0);
        }
        graphics.pose().popPose();

        graphics.setColor(1.0F, 1.0F, 1.0F, COVER_ALPHA);
        graphics.blit(TEXTURE, leftPos + 7, topPos + 18, COVER_U, COVER_V, COVER_W, COVER_H, SHEET, SHEET);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * A tool drawn the way upstream draws it as a picture: its three model layers blitted in order
     * and tinted with the fixed GUI material colours, never with whatever is in the slots. Blitting
     * the layer files directly rather than rendering the item stack is what makes that tinting
     * possible at all -- the item's own colour handler reads the {@code TOOL_MATERIALS} component,
     * which a preview stack has never had (GUI blits bypass the block atlas, so no atlas entry is
     * needed for these).
     */
    private static void renderToolLayers(GuiGraphics graphics, Tab tab, int x, int y) {
        String path = BuiltInRegistries.ITEM.getKey(tab.tool()).getPath();
        // One layer per part, which is how every tool's model is built (ToolArt): a two-part weapon
        // (battlesign, frying pan, dagger -- issue #155) simply has no binding layer to draw.
        List<ToolConstants.PartSlot> parts = tab.entry().constants().parts();
        List<String> layers = ToolArt.layers(parts);
        List<Integer> layerSlots = ToolArt.layerSlots(parts);
        for (int layer = 0; layer < layers.size(); layer++) {
            int color = TOOL_LAYER_COLORS.get(parts.get(layerSlots.get(layer)).role());
            graphics.setColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
            graphics.blit(
                    ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID,
                            "textures/" + ToolArt.layer(path, layers.get(layer)) + ".png"),
                    x, y, 0, 0, 16, 16, 16, 16);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Slot plate, border and -- while empty -- the ghost of whatever belongs in that slot. */
    private void renderSlots(GuiGraphics graphics) {
        Tab tab = menu.tab();
        // The selected tab's own slot count, not the container's: a build tab hides the repair
        // tab's two extra reagent slots (ToolStationMenu's inputSlot#isActive draws the same line).
        for (int i = 0; i < tab.slots().size(); i++) {
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
                graphics.blit(ICONS, x, y, REPAIR_ICON_U[i], REPAIR_ICON_V,
                        SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE, SHEET, SHEET);
            } else {
                graphics.setColor(1.0F, 1.0F, 1.0F, GHOST_ALPHA);
                graphics.blit(partTexture(tab.part(i)), leftPos + slot.x, topPos + slot.y, 0, 0, 16, 16, 16, 16);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    /** A part item's own inventory sprite, blitted straight from its file (GUI draws bypass the atlas). */
    private static ResourceLocation partTexture(Item part) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(part);
        return derivedOrOriginal(
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/derived/item/" + id.getPath() + ".png"),
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/item/" + id.getPath() + ".png"));
    }

    /**
     * Most part and tool art is ported from the clone and lives under {@code textures/derived/}
     * (CLAUDE.md); the M3 shapes with no upstream counterpart at all -- the katana (#160) and the
     * other tools docs/SCOPE.md M3 calls "ours" -- are freshly authored and live under the plain
     * {@code textures/item/} folder instead, which is also the only folder the block atlas stitches
     * without an {@code atlases/blocks.json} source of its own. This picks whichever of the two
     * actually ships, so neither kind of art needs a hardcoded list here or a copy in the wrong tree.
     */
    private static ResourceLocation derivedOrOriginal(ResourceLocation derived, ResourceLocation original) {
        return Minecraft.getInstance().getResourceManager().getResource(derived).isPresent() ? derived : original;
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int selected = menu.getSelectedTab();
        for (int i = 0; i < ToolStationTabs.TABS.size(); i++) {
            int x = leftPos + buttonX(i);
            int y = topPos + buttonY(i);
            boolean hovered = isHovering(buttonX(i), buttonY(i), BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
            int u = i == selected ? BUTTON_PRESSED_U : hovered ? BUTTON_HOVER_U : BUTTON_IDLE_U;
            graphics.blit(ICONS, x, y, u, buttonV(), BUTTON_SIZE, BUTTON_SIZE, SHEET, SHEET);

            Tab tab = ToolStationTabs.get(i);
            if (tab.isRepair()) {
                graphics.blit(ICONS, x, y, ANVIL_U, ANVIL_V, BUTTON_SIZE, BUTTON_SIZE, SHEET, SHEET);
            } else {
                renderToolLayers(graphics, tab, x + 1, y + 1);
            }
        }
        beam(graphics, leftPos + buttonX(0) - BEAM_END_W, topPos, sidebarWidth());
    }

    private void renderInfoPanels(GuiGraphics graphics) {
        int x = leftPos + BASE_WIDTH + PANEL_GAP;
        int top = topPos + PANEL_TOP;
        beam(graphics, x - BEAM_END_W, topPos, InfoPanel.WIDTH);
        InfoPanel.render(graphics, font, x, top, InfoPanel.WIDTH, InfoPanel.HEIGHT,
                panelStyle(), toolCaption, toolLines, toolScroll);
        InfoPanel.render(graphics, font, x, top + InfoPanel.HEIGHT + PANEL_SPACING,
                InfoPanel.WIDTH, InfoPanel.HEIGHT, panelStyle(), traitCaption, traitLines, traitScroll);
    }

    /** Upstream's horizontal beam that visually ties a side module to the main panel. */
    private void beam(GuiGraphics graphics, int x, int y, int width) {
        int v = beamV();
        graphics.blit(TEXTURE, x, y, 0, v, BEAM_END_W, BEAM_H, SHEET, SHEET);
        graphics.blit(TEXTURE, x + BEAM_END_W, y, width, BEAM_H, BEAM_CENTER_U, v, BEAM_CENTER_W, BEAM_H, SHEET, SHEET);
        graphics.blit(TEXTURE, x + BEAM_END_W + width, y, BEAM_RIGHT_U, v, BEAM_END_W, BEAM_H, SHEET, SHEET);
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

    /**
     * Issue #68 fix 4: the tab sidebar, both info panels and the side panel all hang outside {@code
     * imageWidth}. Issue #79: each of the first two is also topped by a {@link #beam}, which starts
     * {@link #BEAM_END_W} left of the module and ends the same distance past its right edge -- so
     * both rectangles are 2px wider on each side than the module they cover, or JEI draws its item
     * list over the beam end-caps.
     */
    @Override
    public List<Rect2i> extraGuiAreas() {
        List<Rect2i> areas = super.extraGuiAreas(); // the station-group tab row (issue #78)
        areas.add(new Rect2i(leftPos + buttonX(0) - BEAM_END_W, topPos, sidebarWidth() + BEAM_END_W * 2,
                buttonY(ToolStationTabs.TABS.size() - 1) + BUTTON_SIZE));
        areas.add(new Rect2i(leftPos + BASE_WIDTH + PANEL_GAP - BEAM_END_W, topPos,
                InfoPanel.WIDTH + BEAM_END_W * 2, PANEL_TOP + InfoPanel.HEIGHT * 2 + PANEL_SPACING));
        if (!menu.sideSlots.isEmpty()) {
            areas.add(sidePanel.bounds());
        }
        return areas;
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
        if (sidePanel.mouseScrolled(mouseX, mouseY, scrollY, imageHeight, menu.sideSlots)) {
            return true;
        }
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
