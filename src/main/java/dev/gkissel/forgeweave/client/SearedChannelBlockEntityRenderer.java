package dev.gkissel.forgeweave.client;

import java.util.EnumSet;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;

import org.jspecify.annotations.Nullable;

import dev.gkissel.forgeweave.block.SearedChannelBlock;
import dev.gkissel.forgeweave.block.SearedChannelBlockEntity;

/**
 * Draws the fluid standing in a channel and the streams running out of it (issue #441, parity audit
 * T9), ported from upstream 1.12's {@code ChannelRenderer} (NOTICE.md).
 *
 * <p>Upstream's three pieces, kept: a shallow pool in the centre trough, an arm along every
 * connected side, and -- when the bottom is open and pouring -- a column falling out of it and on
 * into whatever is below, stopping at that block's own flow depth the way a faucet's stream does.
 *
 * <p>The pool uses the fluid's still sprite and everything that is moving uses its flowing one,
 * which is upstream's choice: a channel that is carrying metal should look like it is carrying it.
 *
 * <p><b>ponytail:</b> upstream additionally re-orients the centre pool's texture toward the single
 * output when there is exactly one, so the animation runs the way the metal is going. That is one
 * rotation on a 4x1.5x4 patch; the pool is drawn unrotated here. Add the rotation if the still patch
 * ever reads as static next to a moving arm.
 */
public class SearedChannelBlockEntityRenderer implements BlockEntityRenderer<SearedChannelBlockEntity, SearedChannelBlockEntityRenderer.State> {
    /** Upstream's default {@code yMin}: how far the stream sinks into a block with no opinion. */
    private static final float DEFAULT_FLOW_DEPTH = 15f / 16f;

    /** Upstream's fluid box: 6/16 to 10/16 across, sitting between 6/16 and 7.5/16 high. */
    private static final float MIN_XZ = 6f / 16f;
    private static final float MAX_XZ = 10f / 16f;
    private static final float FLOOR_Y = 6f / 16f;
    private static final float SURFACE_Y = 7.5f / 16f;
    /** Where an arm stops: the outer wall of the block it is reaching into. */
    private static final float EDGE = 1f;

    /** The column drawn out of the bottom reaches into the block below, so the box has to too. */
    @Override
    public AABB getRenderBoundingBox(SearedChannelBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0, -1, 0);
    }

    /** What {@link #submit} needs, captured off the block entity: no sprite means an empty channel. */
    public static class State extends BlockEntityRenderState {
        @Nullable TextureAtlasSprite still;
        @Nullable TextureAtlasSprite flowing;
        int tint;
        final Set<Direction> flowingSides = EnumSet.noneOf(Direction.class);
        boolean pouringDown;
        float depth;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SearedChannelBlockEntity blockEntity, State state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        FluidStack fluid = blockEntity.fluid();
        if (fluid.isEmpty()) {
            return; // An empty channel is the overwhelmingly common case; nothing else runs.
        }

        state.still = FluidRenderUtil.stillSprite(fluid);
        state.flowing = FluidRenderUtil.flowingSprite(fluid);
        state.tint = FluidRenderUtil.tint(fluid);
        BlockState blockState = blockEntity.getBlockState();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (blockState.getValue(SearedChannelBlock.SIDES.get(side)).canFlow() && blockEntity.isFlowing(side)) {
                state.flowingSides.add(side);
            }
        }
        state.pouringDown = blockState.getValue(SearedChannelBlock.DOWN) && blockEntity.isFlowing(Direction.DOWN);
        state.depth = DEFAULT_FLOW_DEPTH;
        if (state.pouringDown && blockEntity.getLevel() != null) {
            state.depth = FaucetBlockEntityRenderer.flowDepth(blockEntity.getLevel(), blockEntity.getBlockPos().below());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite still = state.still;
        TextureAtlasSprite flowing = state.flowing;
        if (still == null || flowing == null) {
            return;
        }
        int tint = state.tint;
        int packedLight = state.lightCoords;
        int packedOverlay = OverlayTexture.NO_OVERLAY;
        Set<Direction> flowingSides = state.flowingSides;
        boolean pouringDown = state.pouringDown;
        float depth = state.depth;

        collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (last, buffer) -> {
            Matrix4f pose = last.pose();
            FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, FLOOR_Y, MIN_XZ, MAX_XZ, SURFACE_Y, MAX_XZ,
                    still, tint, packedLight, packedOverlay);

            for (Direction side : flowingSides) {
                switch (side) {
                    case NORTH -> FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, FLOOR_Y, 0f, MAX_XZ, SURFACE_Y, MIN_XZ,
                            flowing, tint, packedLight, packedOverlay);
                    case SOUTH -> FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, FLOOR_Y, MAX_XZ, MAX_XZ, SURFACE_Y, EDGE,
                            flowing, tint, packedLight, packedOverlay);
                    case WEST -> FluidRenderUtil.cuboid(buffer, pose, 0f, FLOOR_Y, MIN_XZ, MIN_XZ, SURFACE_Y, MAX_XZ,
                            flowing, tint, packedLight, packedOverlay);
                    default -> FluidRenderUtil.cuboid(buffer, pose, MAX_XZ, FLOOR_Y, MIN_XZ, EDGE, SURFACE_Y, MAX_XZ,
                            flowing, tint, packedLight, packedOverlay);
                }
            }

            if (pouringDown) {
                FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, 0f, MIN_XZ, MAX_XZ, FLOOR_Y, MAX_XZ,
                        flowing, tint, packedLight, packedOverlay);
                FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, -depth, MIN_XZ, MAX_XZ, 0f, MAX_XZ,
                        flowing, tint, packedLight, packedOverlay);
            }
        });
    }
}
