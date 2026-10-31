package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

import org.jspecify.annotations.Nullable;

import dev.gkissel.forgeweave.block.CastingBlock;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.SearedChannelBlock;

/**
 * Draws the stream a faucet is pouring (#182 -- the faucet moved fluid but showed none of it, so a
 * pour that takes 24 ticks an ingot looked like nothing was happening). Ported from upstream 1.12's
 * {@code FaucetRenderer} and {@code IFaucetDepth} (NOTICE.md): the fluid standing in the spout's
 * trough, the column falling out of its open end, and the continuation of that column down into
 * whatever block is receiving it.
 *
 * <p>Everything is drawn with the fluid's <em>flowing</em> sprite and its tint, upstream's choice --
 * a stream of molten metal should look like it is moving, and the flowing texture is the one that
 * animates.
 *
 * <p><b>No extra sync.</b> {@link FaucetBlockEntity} already ships its buffered fluid to clients in
 * its update tag, and re-sends on every transaction boundary, so a client knows both that a pour is
 * running and what is in it for free.
 */
public class FaucetBlockEntityRenderer implements BlockEntityRenderer<FaucetBlockEntity, FaucetBlockEntityRenderer.State> {
    /** Upstream's default {@code yMin}: how far the stream sinks into a block with no opinion. */
    private static final float DEFAULT_FLOW_DEPTH = 15f / 16f;

    // Upstream's cuboids, mirrored front-to-back: upstream's default orientation is north, and this
    // mod's faucet model sits unrotated at south (ForgeweaveBlockStateProvider maps SOUTH to no
    // rotation), so the canonical frame here is south and every z is upstream's 1 - z.
    private static final float SPOUT_MIN_XZ = 6f / 16f;
    private static final float SPOUT_MAX_XZ = 10f / 16f;
    private static final float TROUGH_FLOOR_Y = 6f / 16f;
    private static final float TROUGH_TOP_Y = 10f / 16f;
    private static final float TROUGH_BACK_Z = 1f;
    private static final float FALL_BACK_Z = 10f / 16f;
    private static final float FALL_FRONT_Z = 8f / 16f;

    /** What {@link #submit} needs, captured off the block entity: no sprite means an idle faucet. */
    public static class State extends BlockEntityRenderState {
        @Nullable TextureAtlasSprite sprite;
        int tint;
        float depth;
        Direction facing = Direction.SOUTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FaucetBlockEntity blockEntity, State state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        FluidStack fluid = blockEntity.buffered();
        if (fluid.isEmpty()) {
            return; // An idle faucet is the overwhelmingly common case; nothing else runs.
        }
        state.sprite = FluidRenderUtil.flowingSprite(fluid);
        state.tint = FluidRenderUtil.tint(fluid);
        state.depth = flowDepth(blockEntity.getLevel(), blockEntity.getBlockPos().below());
        state.facing = blockEntity.getBlockState().getValue(FaucetBlock.FACING);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }
        int tint = state.tint;
        float depth = state.depth;
        Direction facing = state.facing;
        int packedLight = state.lightCoords;
        int packedOverlay = OverlayTexture.NO_OVERLAY;

        poseStack.pushPose();
        if (facing == Direction.UP) {
            // The top faucet has no trough: the fluid falls straight through it. Upstream's own
            // special case, and the only one its model file needs either.
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> {
                FluidRenderUtil.cuboid(buffer, pose.pose(),
                        SPOUT_MIN_XZ, 0f, SPOUT_MIN_XZ, SPOUT_MAX_XZ, 1f, SPOUT_MAX_XZ,
                        sprite, tint, packedLight, packedOverlay);
                FluidRenderUtil.cuboid(buffer, pose.pose(),
                        SPOUT_MIN_XZ, -depth, SPOUT_MIN_XZ, SPOUT_MAX_XZ, 0f, SPOUT_MAX_XZ,
                        sprite, tint, packedLight, packedOverlay);
            });
        } else {
            // A blockstate y-rotation turns the model clockwise seen from above; a PoseStack
            // rotation about +Y turns it the other way, so the pose angle is the negated one the
            // block state provider gave the model for this facing.
            poseStack.translate(0.5f, 0f, 0.5f);
            poseStack.mulPose(Axis.YP.rotationDegrees(-modelRotation(facing)));
            poseStack.translate(-0.5f, 0f, -0.5f);

            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) -> {
                FluidRenderUtil.cuboid(buffer, pose.pose(),
                        SPOUT_MIN_XZ, TROUGH_FLOOR_Y, FALL_BACK_Z, SPOUT_MAX_XZ, TROUGH_TOP_Y, TROUGH_BACK_Z,
                        sprite, tint, packedLight, packedOverlay);
                FluidRenderUtil.cuboid(buffer, pose.pose(),
                        SPOUT_MIN_XZ, 0f, FALL_FRONT_Z, SPOUT_MAX_XZ, TROUGH_TOP_Y, FALL_BACK_Z,
                        sprite, tint, packedLight, packedOverlay);
                FluidRenderUtil.cuboid(buffer, pose.pose(),
                        SPOUT_MIN_XZ, -depth, FALL_FRONT_Z, SPOUT_MAX_XZ, 0f, FALL_BACK_Z,
                        sprite, tint, packedLight, packedOverlay);
            });
        }
        poseStack.popPose();
    }

    /** The y-rotation {@code ForgeweaveBlockStateProvider} gives the faucet model for each facing. */
    static float modelRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 180f;
            case EAST -> 270f;
            case WEST -> 90f;
            default -> 0f;
        };
    }

    /**
     * Upstream's {@code IFaucetDepth#getFlowDepth}: how far into the receiving block the stream is
     * drawn. A casting table's pool is a thin sheet on its top, a basin's is deep, a channel's is the
     * floor of its trough, and anything else gets upstream's fallback. Shared with {@link
     * SearedChannelBlockEntityRenderer}, whose downward column stops in the same place a faucet's
     * would (#441).
     */
    static float flowDepth(Level level, BlockPos below) {
        if (level == null) {
            return DEFAULT_FLOW_DEPTH;
        }
        BlockState state = level.getBlockState(below);
        if (state.getBlock() instanceof CastingBlock casting) {
            return casting.flowDepth();
        }
        if (state.getBlock() instanceof SearedChannelBlock) {
            return SearedChannelBlock.FLOW_DEPTH;
        }
        return DEFAULT_FLOW_DEPTH;
    }
}
