package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemStack;

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
public class StencilTableBlockEntityRenderer implements BlockEntityRenderer<StencilTableBlockEntity> {
    /** Upstream's untouched {@code getTableItem} scale: {@code 0.8f} flat, {@code 0.375f} standing. */
    static final float FLAT_SCALE = 0.8f;
    static final float STANDING_SCALE = 0.375f;

    @Override
    public void render(StencilTableBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack input = blockEntity.container().getItem(StencilTableMenu.INPUT_SLOT);
        if (input.isEmpty()) {
            return;
        }
        TableItemRenderer.render(input, 0f, 0f, scaleFor(input), poseStack, bufferSource, packedLight,
                packedOverlay, blockEntity);
    }

    static float scaleFor(ItemStack stack) {
        return TableItemRenderer.isFlat(stack) ? FLAT_SCALE : STANDING_SCALE;
    }
}
