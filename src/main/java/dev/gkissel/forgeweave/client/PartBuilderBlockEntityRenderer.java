package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;

/**
 * Draws the four items lying on the Part Builder's top (issue #567, T75's leftover half of parity
 * audit 2026-08-18 §3.6). Upstream's {@code TilePartBuilder#setInventoryDisplay} shows all four of
 * its own slots, one per corner ({@code c = 0.2125f}, {@code item.s *= 0.46875f}) -- upstream's own
 * slots are material1/material2/pattern/secondary-output (from {@code ContainerPartBuilder}'s {@code
 * InventoryCraftingPersistent(tile, 1, 3)} grid plus its {@code SlotOut(tile, 3, ...)}), an order
 * that has no equivalent in Forgeweave's own {@link PartBuilderMenu} slot layout ({@link
 * PartBuilderMenu#PATTERN_SLOT} is 0, not 2; {@link PartBuilderMenu#CHANGE_SLOT}, a pattern-swap
 * slot upstream never had, does not correspond to any upstream position at all and is left off).
 * <b>Deviation:</b> corners are assigned by matching each upstream slot's own role to Forgeweave's
 * slot of the same role (pattern to pattern's corner, material to material's, etc.) rather than by
 * raw index, since the two containers order their slots differently and role is what a player
 * actually recognizes.
 */
public class PartBuilderBlockEntityRenderer implements BlockEntityRenderer<PartBuilderBlockEntity> {
    private static final float C = 0.2125f;
    /** {@code item.s *= 0.46875f}, upstream's corner-item scale-down from the default 0.8f/0.375f. */
    static final float SCALE_FACTOR = 0.46875f;
    static final float FLAT_SCALE = 0.8f * SCALE_FACTOR;
    static final float STANDING_SCALE = 0.375f * SCALE_FACTOR;

    /** Corner offsets keyed by upstream's own slot role, matching {@code TilePartBuilder}'s i=0..3. */
    static final float[] X_BY_ROLE = {C, -C, C, -C};
    static final float[] Z_BY_ROLE = {-C, -C, C, C};
    static final int ROLE_MATERIAL = 0;
    static final int ROLE_MATERIAL_2 = 1;
    static final int ROLE_PATTERN = 2;
    static final int ROLE_OUTPUT = 3;

    @Override
    public void render(PartBuilderBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Container container = blockEntity.container();
        renderCorner(container.getItem(PartBuilderMenu.MATERIAL_SLOT), ROLE_MATERIAL, poseStack, bufferSource,
                packedLight, packedOverlay, blockEntity);
        renderCorner(container.getItem(PartBuilderMenu.MATERIAL_SLOT_2), ROLE_MATERIAL_2, poseStack, bufferSource,
                packedLight, packedOverlay, blockEntity);
        renderCorner(container.getItem(PartBuilderMenu.PATTERN_SLOT), ROLE_PATTERN, poseStack, bufferSource,
                packedLight, packedOverlay, blockEntity);
        renderCorner(container.getItem(PartBuilderMenu.OUTPUT_SLOT), ROLE_OUTPUT, poseStack, bufferSource,
                packedLight, packedOverlay, blockEntity);
    }

    private static void renderCorner(ItemStack stack, int role, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay, PartBuilderBlockEntity blockEntity) {
        if (stack.isEmpty()) {
            return;
        }
        TableItemRenderer.render(stack, X_BY_ROLE[role], Z_BY_ROLE[role], scaleFor(stack), poseStack, bufferSource,
                packedLight, packedOverlay, blockEntity);
    }

    static float scaleFor(ItemStack stack) {
        return TableItemRenderer.isFlat(stack) ? FLAT_SCALE : STANDING_SCALE;
    }
}
