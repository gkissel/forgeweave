package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

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
public class CraftingStationBlockEntityRenderer implements BlockEntityRenderer<CraftingStationBlockEntity, TableItemRenderer.State> {
    /** Upstream's fixed grid-item scale, used for every item regardless of flat/standing. */
    static final float SCALE = 0.125f;
    static final float CELL = 3f / 16f;

    @Override
    public TableItemRenderer.State createRenderState() {
        return new TableItemRenderer.State();
    }

    @Override
    public void extractRenderState(CraftingStationBlockEntity blockEntity, TableItemRenderer.State state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        Container container = blockEntity.container();
        for (int row = 0; row < CraftingStationMenu.GRID_HEIGHT; row++) {
            for (int col = 0; col < CraftingStationMenu.GRID_WIDTH; col++) {
                state.add(container.getItem(col + row * CraftingStationMenu.GRID_WIDTH), x(col), z(row), SCALE,
                        blockEntity);
            }
        }
    }

    @Override
    public void submit(TableItemRenderer.State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        state.submitAll(poseStack, collector);
    }

    static float x(int col) {
        return CELL - col * CELL;
    }

    static float z(int row) {
        return CELL - row * CELL;
    }
}
