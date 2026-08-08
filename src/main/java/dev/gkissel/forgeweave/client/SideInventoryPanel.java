package dev.gkissel.forgeweave.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.menu.SideInventorySlots;

/**
 * Shared side-inventory panel rendering for the three stations that show a neighboring block's
 * items in a GUI side panel (docs/SCOPE.md issue #40, extended from the Crafting Station to the
 * Part Builder and Tool Station in the same issue's follow-up). Originally {@code
 * CraftingStationScreen#renderSideInventoryBackground}; extracted here (paired with {@link
 * SideInventorySlots} on the menu side) so the other two screens reuse the same panel-sizing and
 * -drawing logic instead of a third copy. Each screen still supplies its own backing texture and
 * slot-tile sprite -- there's no single shared source of that art across the three stations.
 */
final class SideInventoryPanel {
    private static final int BACKDROP_COLOR = 0x88000000;
    private static final int BACKDROP_MARGIN = 3;

    private SideInventoryPanel() {}

    static int panelWidth(int slotCount) {
        return SideInventorySlots.columns(slotCount) * SideInventorySlots.SLOT_SIZE;
    }

    static int panelHeight(int slotCount) {
        return SideInventorySlots.rows(slotCount) * SideInventorySlots.SLOT_SIZE;
    }

    /**
     * Draws a backdrop (so the panel reads as a distinct region rather than floating slot tiles)
     * plus one repeated blit of {@code (tileU, tileV)} per slot from {@code texture}.
     *
     * @param panelX the panel's left edge and {@code panelY} its top edge, in GUI pixels from the
     *     screen's top-left (i.e. relative to {@code leftPos}/{@code topPos}, matching the same
     *     coordinates the menu built its {@link SideInventorySlots} at)
     */
    static void render(GuiGraphics graphics, ResourceLocation texture, int textureWidth, int textureHeight,
            int tileU, int tileV, int leftPos, int topPos, int panelX, int panelY, int slotCount) {
        if (slotCount == 0) {
            return;
        }
        int size = SideInventorySlots.SLOT_SIZE;
        int width = panelWidth(slotCount);
        int height = panelHeight(slotCount);

        int backdropLeft = leftPos + panelX - BACKDROP_MARGIN;
        int backdropTop = topPos + panelY - BACKDROP_MARGIN;
        graphics.fill(backdropLeft, backdropTop, backdropLeft + width + BACKDROP_MARGIN * 2,
                backdropTop + height + BACKDROP_MARGIN * 2, BACKDROP_COLOR);

        for (int i = 0; i < slotCount; i++) {
            int x = leftPos + panelX + (i % SideInventorySlots.MAX_COLUMNS) * size;
            int y = topPos + panelY + (i / SideInventorySlots.MAX_COLUMNS) * size;
            graphics.blit(texture, x, y, tileU, tileV, size, size, textureWidth, textureHeight);
        }
    }
}
