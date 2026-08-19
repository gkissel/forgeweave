package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.CraftingStationBlockEntity;
import dev.gkissel.forgeweave.menu.CraftingStationMenu;

/**
 * Draws the 3x3 grid lying on the Crafting Station's top (issue #567, T75's leftover half of parity
 * audit 2026-08-18 §3.6). Upstream's {@code TileCraftingStation#setInventoryDisplay} shows all nine
 * grid slots in a 3x3 layout ({@code o = 3f / 16f} per cell) at one fixed scale ({@code s = 0.125f})
 * regardless of whether an item lies flat or stands as a block -- unlike every other station, which
 * keeps {@code getTableItem}'s own flat/standing scale and only shrinks it. Forgeweave's {@link
 * CraftingStationMenu} grid slots are indexed {@code col + row * 3} the same as upstream's own {@code
 * i % 3}/{@code i / 3}, so the port is a direct 1:1 index match with no role remapping needed.
 */
public class CraftingStationBlockEntityRenderer implements BlockEntityRenderer<CraftingStationBlockEntity> {
    /** Upstream's fixed grid-item scale, used for every item regardless of flat/standing. */
    static final float SCALE = 0.125f;
    static final float CELL = 3f / 16f;

    @Override
    public void render(CraftingStationBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Container container = blockEntity.container();
        for (int row = 0; row < CraftingStationMenu.GRID_HEIGHT; row++) {
            for (int col = 0; col < CraftingStationMenu.GRID_WIDTH; col++) {
                ItemStack stack = container.getItem(col + row * CraftingStationMenu.GRID_WIDTH);
                if (stack.isEmpty()) {
                    continue;
                }
                TableItemRenderer.render(stack, x(col), z(row), SCALE, poseStack, bufferSource, packedLight,
                        packedOverlay, blockEntity);
            }
        }
    }

    static float x(int col) {
        return CELL - col * CELL;
    }

    static float z(int row) {
        return CELL - row * CELL;
    }
}
