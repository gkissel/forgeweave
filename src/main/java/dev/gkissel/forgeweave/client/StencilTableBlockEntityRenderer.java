package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import dev.gkissel.forgeweave.block.StencilTableBlockEntity;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Draws the pattern lying on the Stencil Table's top (issue #567, T75's leftover half of parity
 * audit 2026-08-18 §3.6). Upstream's {@code TileStencilTable} never overrides {@code TileTable
 * #setInventoryDisplay}, so it renders exactly one item -- its own {@code displaySlot}, always 0 --
 * at {@code TileTable#getTableItem}'s untouched offsets and scale, ported by {@link
 * TableItemRenderer}. Forgeweave's own slot 0 is {@link StencilTableMenu#INPUT_SLOT}; {@link
 * StencilTableMenu#OUTPUT_SLOT} has no upstream analog (upstream's stencil table computes its
 * output on the fly rather than storing it), so it is never drawn -- matching upstream showing only
 * the pattern that is really sitting there.
 */
public class StencilTableBlockEntityRenderer implements BlockEntityRenderer<StencilTableBlockEntity, TableItemRenderer.State> {
    /** Upstream's untouched {@code getTableItem} scale: {@code 0.8f} flat, {@code 0.375f} standing. */
    static final float FLAT_SCALE = 0.8f;
    static final float STANDING_SCALE = 0.375f;

    @Override
    public TableItemRenderer.State createRenderState() {
        return new TableItemRenderer.State();
    }

    @Override
    public void extractRenderState(StencilTableBlockEntity blockEntity, TableItemRenderer.State state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        ItemStack input = blockEntity.container().getItem(StencilTableMenu.INPUT_SLOT);
        state.add(input, 0f, 0f, scaleFor(input), blockEntity);
    }

    @Override
    public void submit(TableItemRenderer.State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        state.submitAll(poseStack, collector);
    }

    static float scaleFor(ItemStack stack) {
        return TableItemRenderer.isFlat(stack) ? FLAT_SCALE : STANDING_SCALE;
    }
}
