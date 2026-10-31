package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

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
 * item.y + 1f} in {@code BakedTableModel#getModelForTableItem}), not block-local space directly --
 * {@code TRSRBakedModel}'s constructor runs that translate (and the flat/standing rotation) through
 * {@code TRSRTransformation.blockCenterToCorner}, which pivots around the block's own centre
 * {@code (0.5, 0.5, 0.5)}, not its {@code (0, 0, 0)} corner. Converting an upstream offset to
 * block-local space is therefore {@code 0.5f + (item.y + 1f)}, not {@code item.y + 1f} alone (issue
 * #795: the missing {@code 0.5f} is exactly why every station's items rendered at half the block's
 * height, inside the leg geometry, instead of on the tabletop plate upstream's own {@code
 * table.json}/{@code crafting_station.json} put at {@code y=12..16}). With the correction: a flat
 * item's default {@code y=-0.46875f} becomes block-local {@code 1.03125f}, and a standing
 * block-cube's default {@code y=-0.3125f} becomes {@code 1.1875f} -- centred on a cube of scale
 * {@code 0.375f} sitting on a {@link #SURFACE_Y} of {@code 1.0f} ({@code 1.1875f - 0.375f / 2f ==
 * 1.0f}). Every per-station override upstream applies to a standing cube's {@code y} ({@code
 * -(1f - item.s) / 2f}) reduces to the same rule algebraically ({@code 0.5f + 1f + -(1f - s) / 2f ==
 * 1.0f + s / 2f}), so {@link #standingCenterY} generalizes it once instead of re-deriving it per
 * station. A flat item's centre never moves with its own scale (only its footprint does, via {@code
 * item.s}), which is why {@link #FLAT_CENTER_Y} is a constant rather than a formula.
 */
final class TableItemRenderer {
    /** Upstream's tabletop plate sits at the very top of the block ({@code y=12..16} in the model), not its middle. */
    static final float SURFACE_Y = 1.0f;
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
     * One stack captured for {@link #submit}: its resolved item model plus where and how it lies. On
     * 26.1 a block entity renderer resolves item models while extracting its render state and only
     * draws them later, so the old single {@code render} call is split into {@link #extract} and
     * {@link #submit}.
     */
    record Item(ItemStackRenderState state, float x, float z, float scale, float centerY, boolean flat) {}

    /** The render state every table renderer shares: the stacks it {@link #extract}ed this frame. */
    static class State extends BlockEntityRenderState {
        final List<Item> items = new ArrayList<>();

        /** {@link #extract}s {@code stack} and keeps it, unless it resolved to nothing. */
        void add(ItemStack stack, float x, float z, float scale, BlockEntity blockEntity) {
            Item item = extract(stack, x, z, scale, blockEntity);
            if (item != null) {
                items.add(item);
            }
        }

        /** {@link #submit}s every kept stack at this state's own light level. */
        void submitAll(PoseStack poseStack, SubmitNodeCollector collector) {
            for (Item item : items) {
                submit(item, poseStack, collector, lightCoords);
            }
        }
    }

    /**
     * Resolves {@code stack} to lie at block-local {@code (0.5f + x, centerY, 0.5f + z)}, scaled to
     * {@code scale} in the horizontal plane. Returns {@code null} for an empty stack or with {@link
     * ForgeweaveClientConfig#RENDER_TABLE_ITEMS} off, matching upstream's {@code
     * Config.renderTableItems} gate.
     */
    static @Nullable Item extract(ItemStack stack, float x, float z, float scale, BlockEntity blockEntity) {
        if (stack.isEmpty() || !ForgeweaveClientConfig.RENDER_TABLE_ITEMS.get()) {
            return null;
        }
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.NONE,
                blockEntity.getLevel(), null, (int) blockEntity.getBlockPos().asLong());
        return new Item(state, x, z, scale, centerY(stack, scale), isFlat(stack));
    }

    /**
     * Draws an {@link #extract}ed stack lying flat or standing -- the same flat/standing {@link
     * PoseStack} transform as {@code CastingBlockEntityRenderer#submit}. A no-op for {@code null}.
     */
    static void submit(@Nullable Item item, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        if (item == null || item.state().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5f + item.x(), item.centerY(), 0.5f + item.z());
        if (item.flat()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            poseStack.scale(item.scale(), item.scale(), FLAT_THICKNESS / ITEM_MODEL_DEPTH);
        } else {
            poseStack.scale(item.scale(), item.scale(), item.scale());
        }
        item.state().submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
