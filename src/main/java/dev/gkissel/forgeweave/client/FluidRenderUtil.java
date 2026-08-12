package dev.gkissel.forgeweave.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The quad plumbing every fluid renderer in this mod shares, ported from upstream 1.12's {@code
 * RenderUtil} (NOTICE.md) -- its {@code putATexturedQuad}/{@code renderFluidCuboid} pair, minus the
 * immediate-mode GL state upstream needs and 1.21 does not.
 *
 * <p>{@link #quad} and {@link #vertex} started life inside {@link SearedTankBlockEntityRenderer}
 * (#145); they moved here unchanged when #182 added a second and third fluid renderer rather than
 * being copied into each.
 *
 * <p>UVs follow upstream: a face's texture is anchored at the sprite's origin and spans the
 * cuboid's own size in block units, so a 4/16-wide stream shows a 4/16-wide slice of the fluid
 * texture instead of the whole sprite squeezed into it. Every cuboid this mod draws is at most one
 * block on a side, so that never runs off the end of the sprite.
 */
final class FluidRenderUtil {

    private FluidRenderUtil() {}

    static TextureAtlasSprite stillSprite(FluidStack fluid) {
        return sprite(IClientFluidTypeExtensions.of(fluid.getFluid()).getStillTexture(fluid));
    }

    static TextureAtlasSprite flowingSprite(FluidStack fluid) {
        return sprite(IClientFluidTypeExtensions.of(fluid.getFluid()).getFlowingTexture(fluid));
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(texture);
    }

    /** The fluid's own tint, opaque -- upstream reads the same colour off the fluid for every quad. */
    static int tint(FluidStack fluid) {
        return FastColor.ARGB32.opaque(IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid));
    }

    /**
     * All six faces of an axis-aligned box in block-local coordinates. Callers that only ever show
     * some faces (the seared tank, which is seen only from outside) draw their own; this is for the
     * casting/faucet fluids, which are looked into from above and from the side at once.
     */
    static void cuboid(VertexConsumer buffer, Matrix4f pose,
            float x1, float y1, float z1, float x2, float y2, float z2,
            TextureAtlasSprite sprite, int argb, int light, int overlay) {
        float w = x2 - x1, h = y2 - y1, d = z2 - z1;
        float uw = sprite.getU(w), ud = sprite.getU(d), u0 = sprite.getU(0f);
        float vh = sprite.getV(h), vd = sprite.getV(d), v0 = sprite.getV(0f);

        // North (-Z) and south (+Z): u across x, v down from the cuboid's top edge.
        quad(buffer, pose, x1, y1, z1, u0, vh, x1, y2, z1, u0, v0, x2, y2, z1, uw, v0, x2, y1, z1, uw, vh,
                0f, 0f, -1f, argb, light, overlay);
        quad(buffer, pose, x1, y1, z2, uw, vh, x2, y1, z2, u0, vh, x2, y2, z2, u0, v0, x1, y2, z2, uw, v0,
                0f, 0f, 1f, argb, light, overlay);
        // West (-X) and east (+X): u across z.
        quad(buffer, pose, x1, y1, z1, ud, vh, x1, y1, z2, u0, vh, x1, y2, z2, u0, v0, x1, y2, z1, ud, v0,
                -1f, 0f, 0f, argb, light, overlay);
        quad(buffer, pose, x2, y1, z1, u0, vh, x2, y2, z1, u0, v0, x2, y2, z2, ud, v0, x2, y1, z2, ud, vh,
                1f, 0f, 0f, argb, light, overlay);
        // Up and down: u across x, v across z.
        quad(buffer, pose, x1, y2, z1, u0, v0, x1, y2, z2, u0, vd, x2, y2, z2, uw, vd, x2, y2, z1, uw, v0,
                0f, 1f, 0f, argb, light, overlay);
        quad(buffer, pose, x2, y1, z1, uw, v0, x2, y1, z2, uw, vd, x1, y1, z2, u0, vd, x1, y1, z1, u0, v0,
                0f, -1f, 0f, argb, light, overlay);
    }

    static void quad(VertexConsumer buffer, Matrix4f pose,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float nx, float ny, float nz, int argb, int light, int overlay) {
        vertex(buffer, pose, x0, y0, z0, u0, v0, nx, ny, nz, argb, light, overlay);
        vertex(buffer, pose, x1, y1, z1, u1, v1, nx, ny, nz, argb, light, overlay);
        vertex(buffer, pose, x2, y2, z2, u2, v2, nx, ny, nz, argb, light, overlay);
        vertex(buffer, pose, x3, y3, z3, u3, v3, nx, ny, nz, argb, light, overlay);
    }

    static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, float u, float v,
            float nx, float ny, float nz, int argb, int light, int overlay) {
        buffer.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}
