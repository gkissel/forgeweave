package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

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
 * issue #40's follow-up), its slots render in a panel to the right of the info panel via {@link
 * SideInventoryPanel} -- shared with {@link CraftingStationScreen}/{@link ToolStationScreen}'s own
 * side panels.
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

    /** Upstream {@code Icons}: the pattern, ingot and shard glyphs, in slot order. */
    private static final int[] SLOT_ICON_U = {0, 54, -1, 18};
    private static final int[] SLOT_ICON_V = {216, 234, -1, 216};
    private static final int ICON_SIZE = 18;

    private static final int PANEL_GAP = 2;
    private static final int PANEL_TOP = 0;
    /** Upstream gives this station's single panel the full GUI height. */
    private static final int PANEL_HEIGHT = BASE_HEIGHT;
    private static final int MATERIAL_VALUE_Y = 63;

    @Nullable
    private Component caption;
    private List<Component> lines = List.of();
    private int scroll;
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
        ItemStack material = menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem();
        List<Component> body = new ArrayList<>();

        PartBuilderRecipes.patternPart(pattern).ifPresent(part -> {
            body.add(new ItemStack(part).getHoverName().copy().withStyle(ChatFormatting.UNDERLINE));
            PartBuilderRecipes.patternCost(pattern)
                    .ifPresent(cost -> body.add(Component.translatable("gui.forgeweave.part_builder.cost", cost)));
            body.add(null);
        });

        Optional<Material> loaded = materialInSlot(material);
        if (loaded.isPresent()) {
            caption = MaterialDisplay.name(registries(), materialId(material).orElseThrow());
            body.addAll(StationText.materialStats(loaded.get()));
            body.add(null);
            body.addAll(StationText.traits(loaded.get().color(), List.of(loaded.get().trait())));
        } else {
            caption = title;
            if (body.isEmpty()) {
                body.add(Component.translatable("gui.forgeweave.part_builder.info"));
            }
        }

        lines = body;
        scroll = Math.min(scroll, InfoPanel.maxScroll(font, InfoPanel.WIDTH, PANEL_HEIGHT, caption != null, lines));
    }

    private Optional<PartBuilderRecipes.MaterialMatch> matchMaterial(ItemStack stack) {
        HolderLookup.Provider registries = registries();
        return registries == null ? Optional.empty() : PartBuilderRecipes.materialValue(registries, stack);
    }

    private Optional<ResourceLocation> materialId(ItemStack stack) {
        return matchMaterial(stack).map(PartBuilderRecipes.MaterialMatch::id);
    }

    private Optional<Material> materialInSlot(ItemStack stack) {
        return materialId(stack).flatMap(id -> MaterialDisplay.lookup(registries(), id));
    }

    @Nullable
    private HolderLookup.Provider registries() {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        renderSlotIcons(graphics);
        renderMaterialValue(graphics);
        InfoPanel.render(graphics, font, leftPos + BASE_WIDTH + PANEL_GAP, topPos + PANEL_TOP,
                InfoPanel.WIDTH, PANEL_HEIGHT, caption, lines, scroll);
        sidePanel.render(graphics, menu, leftPos, topPos, imageHeight, menu.sideSlots);
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
        ItemStack material = menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).getItem();
        Optional<PartBuilderRecipes.MaterialMatch> matched = matchMaterial(material);
        if (matched.isEmpty()) {
            return;
        }
        int available = matched.get().unitValue() * material.getCount();
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
        List<Rect2i> areas = new ArrayList<>(2);
        areas.add(new Rect2i(leftPos + BASE_WIDTH + PANEL_GAP, topPos + PANEL_TOP, InfoPanel.WIDTH, PANEL_HEIGHT));
        if (!menu.sideSlots.isEmpty()) {
            areas.add(sidePanel.bounds());
        }
        return areas;
    }

    @SubscribeEvent
    static void registerScreen(RegisterMenuScreensEvent event) {
        event.register(ForgeweaveMenus.PART_BUILDER.get(), PartBuilderScreen::new);
    }
}
