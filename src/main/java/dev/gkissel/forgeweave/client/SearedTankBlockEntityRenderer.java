package dev.gkissel.forgeweave.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import dev.gkissel.forgeweave.block.SearedTankBlockEntity;

/**
 * Draws the fluid held by a seared tank/gauge/window inside the block (#145 -- the block entity
 * already syncs its contents, docs/SCOPE.md M2 issue #95, but nothing painted them, so a filled
 * tank rendered as an empty frame). Ported from upstream 1.12's {@code TankRenderer}/{@code
 * RenderUtil#renderFluidCuboid} (NOTICE.md): an inset quad column sized to the fill fraction,
 * textured with the fluid's still sprite and tinted, drawn on {@link RenderType#translucent()} so
 * it composites with the block's own cutout window faces instead of fighting them.
 *
 * <p>Unlike upstream, every face uses the still texture (no separate flowing texture for the
 * sides) and UV coordinates cover the whole sprite per quad rather than a tile of it -- the tank
 * is always exactly one block, so there is no multi-block seam to line up against.
 *
 * <p>The quad/vertex plumbing this used to own moved to {@link FluidRenderUtil} unchanged when #182
 * added the casting and faucet fluid renderers; the face selection below is still this renderer's
 * own, because a tank is only ever seen from outside.
 */
public class SearedTankBlockEntityRenderer implements BlockEntityRenderer<SearedTankBlockEntity> {
    /** Keeps the fluid quads a hair inside the block's own faces so they don't z-fight. */
    private static final float INSET = 0.01f;

    public SearedTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SearedTankBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        FluidTank tank = blockEntity.tank();
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty() || tank.getCapacity() <= 0) {
            return;
        }

        TextureAtlasSprite sprite = FluidRenderUtil.stillSprite(fluid);
        int tint = FluidRenderUtil.tint(fluid);

        float fraction = Mth.clamp(fluid.getAmount() / (float) tank.getCapacity(), 0f, 1f);
        float x1 = INSET, x2 = 1f - INSET;
        float z1 = INSET, z2 = 1f - INSET;
        float y1 = INSET;
        float y2 = topY(fraction);

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());

        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

        // Sides are always drawn (visible through the tank/gauge/window's cutout faces regardless
        // of fill level); the lid is skipped once full since it would sit flush against the
        // block's own opaque top face.
        FluidRenderUtil.quad(buffer, pose, x1, y1, z1, u0, v1, x1, y2, z1, u0, v0, x2, y2, z1, u1, v0, x2, y1, z1, u1, v1,
                0f, 0f, -1f, tint, packedLight, packedOverlay);
        FluidRenderUtil.quad(buffer, pose, x1, y1, z2, u1, v1, x2, y1, z2, u0, v1, x2, y2, z2, u0, v0, x1, y2, z2, u1, v0,
                0f, 0f, 1f, tint, packedLight, packedOverlay);
        FluidRenderUtil.quad(buffer, pose, x1, y1, z1, u1, v1, x1, y1, z2, u0, v1, x1, y2, z2, u0, v0, x1, y2, z1, u1, v0,
                -1f, 0f, 0f, tint, packedLight, packedOverlay);
        FluidRenderUtil.quad(buffer, pose, x2, y1, z1, u0, v1, x2, y2, z1, u0, v0, x2, y2, z2, u1, v0, x2, y1, z2, u1, v1,
                1f, 0f, 0f, tint, packedLight, packedOverlay);
        if (fraction < 1f) {
            FluidRenderUtil.quad(buffer, pose, x1, y2, z1, u0, v0, x1, y2, z2, u0, v1, x2, y2, z2, u1, v1, x2, y2, z1, u1, v0,
                    0f, 1f, 0f, tint, packedLight, packedOverlay);
        }
    }

    /**
     * The fluid column's top Y in block-local [0,1] space: {@link #INSET} (empty) up to
     * {@code 1 - INSET} (full), linear in {@code fraction}. Pulled out of {@link #render} so it can
     * be exercised directly by {@code SearedTankBlockEntityRendererTest} -- the maintainer's #145
     * capture showed a partial fill (2/3) rendering far too low and another (1/4) too high, and this
     * is the one place that height math lives.
     */
    static float topY(float fraction) {
        return INSET + fraction * (1f - 2f * INSET);
    }
}
