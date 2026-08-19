package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;

/**
 * The shared "one item lying on a table top" primitive behind the Crafting Station, Stencil Table,
 * Part Builder and Tool Station renderers (issue #567, the render half of parity audit
 * 2026-08-18 §3.6 that #506 left out). Upstream applies the same rule to a fifth and sixth {@code
 * TileTable} subclass, the casting table and basin, but {@link CastingBlockEntityRenderer} predates
 * this ticket and already reimplements it with its own fluid-pool-aware layering -- see that class's
 * javadoc for why the technique below is a {@link PoseStack} transform rather than upstream's
 * baked-quad composition (same reasoning applies here, so it is not repeated).
 *
 * <p>Position math is derived from upstream's {@code TileTable#getTableItem}, whose {@code y}/{@code
 * s} constants are expressed as an offset from the baked model's own translate origin ({@code
 * item.y + 1f} in {@code BakedTableModel#getModelForTableItem}), not block-local space. Converting:
 * a flat item's default {@code y=-0.46875f} becomes block-local {@code 0.53125f}, and a standing
 * block-cube's default {@code y=-0.3125f} becomes {@code 0.6875f} -- centred on a cube of scale
 * {@code 0.375f} sitting on a {@link #SURFACE_Y} of {@code 0.5f} ({@code 0.6875f - 0.375f / 2f ==
 * 0.5f}). Every per-station override upstream applies to a standing cube's {@code y} ({@code
 * -(1f - item.s) / 2f}) reduces to the same rule algebraically ({@code 1f + -(1f - s) / 2f ==
 * 0.5f + s / 2f}), so {@link #standingCenterY} generalizes it once instead of re-deriving it per
 * station. A flat item's centre never moves with its own scale (only its footprint does, via {@code
 * item.s}), which is why {@link #FLAT_CENTER_Y} is a constant rather than a formula.
 */
final class TableItemRenderer {
    /** Upstream never moves the tabletop -- every station's items sit on the same half-block-high surface. */
    static final float SURFACE_Y = 0.5f;
    /** A generated flat item model's own depth, matching {@code CastingBlockEntityRenderer.ITEM_MODEL_DEPTH}. */
    private static final float ITEM_MODEL_DEPTH = 1f / 16f;
    /** What a flat-laid table item is squashed to; unlike casting's cast/fluid pairing there is no pour to clear. */
    static final float FLAT_THICKNESS = 1f / 16f;
    /** {@code SURFACE_Y + FLAT_THICKNESS / 2f} -- see the class javadoc for the upstream derivation. */
    static final float FLAT_CENTER_Y = SURFACE_Y + FLAT_THICKNESS / 2f;

    private TableItemRenderer() {}

    /** Upstream's rule: a block stands as a block, anything else (a part, a pattern, a pane) lies flat. */
    static boolean isFlat(ItemStack stack) {
        return !(stack.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() instanceof IronBarsBlock;
    }

    /** A standing block-cube's vertical centre: its own bottom flush with {@link #SURFACE_Y}. */
    static float standingCenterY(float scale) {
        return SURFACE_Y + scale / 2f;
    }

    /** The vertical centre for whichever orientation {@code stack} renders in at {@code scale}. */
    static float centerY(ItemStack stack, float scale) {
        return isFlat(stack) ? FLAT_CENTER_Y : standingCenterY(scale);
    }

    /**
     * Renders one stack lying flat or standing at block-local {@code (0.5f + x, centerY, 0.5f + z)},
     * scaled to {@code scale} in the horizontal plane -- the same flat/standing {@link PoseStack}
     * transform as {@code CastingBlockEntityRenderer#renderItem}. A no-op for an empty stack or with
     * {@link ForgeweaveClientConfig#RENDER_TABLE_ITEMS} off, matching upstream's {@code
     * Config.renderTableItems} gate.
     */
    static void render(ItemStack stack, float x, float z, float scale, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay, BlockEntity blockEntity) {
        if (stack.isEmpty() || !ForgeweaveClientConfig.RENDER_TABLE_ITEMS.get()) {
            return;
        }
        boolean flat = isFlat(stack);

        poseStack.pushPose();
        poseStack.translate(0.5f + x, centerY(stack, scale), 0.5f + z);
        if (flat) {
            // After the rotation the model's own depth axis is the block's vertical one, so the
            // third scale is the item's thickness -- squashed to FLAT_THICKNESS, see there.
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            poseStack.scale(scale, scale, FLAT_THICKNESS / ITEM_MODEL_DEPTH);
        } else {
            poseStack.scale(scale, scale, scale);
        }
        // ItemDisplayContext.NONE applies no display transform, so the model arrives centred on the
        // pose above -- upstream renders the raw baked model for the same reason.
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE,
                packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(),
                (int) blockEntity.getBlockPos().asLong());
        poseStack.popPose();
    }
}
