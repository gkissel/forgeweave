package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.fluids.FluidStack;

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
public class SearedChannelBlockEntityRenderer implements BlockEntityRenderer<SearedChannelBlockEntity> {
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

    @Override
    public void render(SearedChannelBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        FluidStack fluid = blockEntity.fluid();
        if (fluid.isEmpty()) {
            return; // An empty channel is the overwhelmingly common case; nothing else runs.
        }

        TextureAtlasSprite still = FluidRenderUtil.stillSprite(fluid);
        TextureAtlasSprite flowing = FluidRenderUtil.flowingSprite(fluid);
        int tint = FluidRenderUtil.tint(fluid);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());
        var pose = poseStack.last().pose();
        BlockState state = blockEntity.getBlockState();

        FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, FLOOR_Y, MIN_XZ, MAX_XZ, SURFACE_Y, MAX_XZ,
                still, tint, packedLight, packedOverlay);

        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!state.getValue(SearedChannelBlock.SIDES.get(side)).canFlow()
                    || !blockEntity.isFlowing(side)) {
                continue;
            }
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

        if (state.getValue(SearedChannelBlock.DOWN) && blockEntity.isFlowing(Direction.DOWN)) {
            FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, 0f, MIN_XZ, MAX_XZ, FLOOR_Y, MAX_XZ,
                    flowing, tint, packedLight, packedOverlay);
            float depth = DEFAULT_FLOW_DEPTH;
            if (blockEntity.getLevel() != null) {
                depth = FaucetBlockEntityRenderer.flowDepth(blockEntity.getLevel(),
                        blockEntity.getBlockPos().below());
            }
            FluidRenderUtil.cuboid(buffer, pose, MIN_XZ, -depth, MIN_XZ, MAX_XZ, 0f, MAX_XZ,
                    flowing, tint, packedLight, packedOverlay);
        }
    }
}
