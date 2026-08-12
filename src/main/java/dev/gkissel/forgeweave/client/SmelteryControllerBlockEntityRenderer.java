package dev.gkissel.forgeweave.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryStructure;
import dev.gkissel.forgeweave.block.SmelteryTank;

/**
 * Draws a formed smeltery's molten contents inside its interior (#179 -- the fluid was only ever
 * visible in the controller GUI). Ported from upstream 1.12's {@code SmelteryTankRenderer} plus
 * {@code RenderUtil#renderStackedFluidCuboid} (NOTICE.md): one cuboid per fluid, stacked bottom-up in
 * tank order, each layer's height proportional to its share of the smeltery's capacity, spanning the
 * whole interior footprint and split at block boundaries so the sprite tiles per block instead of
 * being stretched across a 9x9.
 *
 * <p>Same idiom as {@link SearedTankBlockEntityRenderer} (#147), whose {@code quad} helper this
 * reuses: still sprite on every face rather than upstream's separate flowing texture for the sides,
 * drawn on {@link RenderType#translucent()}, tinted by the fluid's client extension.
 *
 * <p>Only the bottom layer draws a floor and only the top layer a lid -- the boundary between two
 * stacked layers is two coincident quads that nothing can see between, and skipping them costs
 * nothing but the z-fighting they would otherwise flicker with.
 */
public class SmelteryControllerBlockEntityRenderer implements BlockEntityRenderer<SmelteryControllerBlockEntity> {
    /** Keeps the pool a hair inside the interior walls and floor so it doesn't z-fight them (upstream's {@code FLUID_OFFSET}). */
    private static final float INSET = 0.01f;

    /**
     * Shortest a fluid layer may render, in blocks. Upstream's {@code calcLiquidHeights} min of 100
     * out of 1000 per block: a splash of one metal in a big smeltery still gets a visible band rather
     * than a sub-pixel sliver.
     */
    static final float MIN_LAYER_HEIGHT = 0.1f;

    public SmelteryControllerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SmelteryControllerBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        SmelteryStructure structure = blockEntity.structure();
        SmelteryTank tank = blockEntity.tank();
        List<FluidStack> fluids = tank.fluids();
        if (structure == null || fluids.isEmpty() || tank.getCapacity() <= 0 || blockEntity.getLevel() == null) {
            return;
        }

        BlockPos origin = blockEntity.getBlockPos();
        BlockPos min = structure.interiorMin();
        BlockPos max = structure.interiorMax();
        float x1 = min.getX() - origin.getX() + INSET;
        float x2 = max.getX() - origin.getX() + 1f - INSET;
        float z1 = min.getZ() - origin.getZ() + INSET;
        float z2 = max.getZ() - origin.getZ() + 1f - INSET;
        float y = min.getY() - origin.getY() + INSET;

        float[] heights = layerHeights(fluids, tank.getCapacity(), structure.height() - 2f * INSET);

        // The controller sits in the wall, so the light handed to a block entity renderer is the
        // wall's -- upstream lights the pool from the interior instead, which is what actually shines
        // on it. Fluid luminosity (molten metal is light level 10) floors the block-light channel the
        // same way upstream's getCombinedLight(pos, luminosity) does.
        int interiorLight = LevelRenderer.getLightColor(blockEntity.getLevel(), min);
        int skyLight = LightTexture.sky(interiorLight);
        int blockLight = LightTexture.block(interiorLight);

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack fluid = fluids.get(i);
            IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                    .getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(extensions.getStillTexture(fluid));
            int tint = FastColor.ARGB32.opaque(extensions.getTintColor(fluid));
            int light = LightTexture.pack(
                    Math.max(blockLight, fluid.getFluid().getFluidType().getLightLevel(fluid)), skyLight);

            renderLayer(buffer, pose, sprite, tint, light, packedOverlay,
                    x1, y, z1, x2, y + heights[i], z2, i == 0, i == fluids.size() - 1);
            y += heights[i];
        }
    }

    /**
     * The interior, not the controller block -- the pool is drawn several blocks away from the block
     * entity that owns it, and the default unit cube would cull it the moment the controller's own
     * block left the frustum.
     */
    @Override
    public AABB getRenderBoundingBox(SmelteryControllerBlockEntity blockEntity) {
        SmelteryStructure structure = blockEntity.structure();
        if (structure == null) {
            return new AABB(blockEntity.getBlockPos());
        }
        BlockPos min = structure.interiorMin();
        BlockPos max = structure.interiorMax();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /**
     * How tall each fluid's band renders, in blocks, bottom fluid first -- upstream's {@code
     * SmelteryTankRenderer#calcLiquidHeights}.
     *
     * <p>A layer gets its share of {@code maxHeight} by volume, floored at {@link
     * #MIN_LAYER_HEIGHT} so a trace amount is still visible. That floor can push the stack past the
     * interior, so any overflow is taken back off the layers that have room above the floor, in
     * proportion to how much room each has -- upstream removes a pixel at a time from the tallest
     * until it fits, which is the same shape one pass at a time. Like upstream, a smeltery that is
     * not full keeps {@link #MIN_LAYER_HEIGHT} of headroom clear so a nearly-full smeltery still
     * reads as "not full".
     *
     * <p>Pulled out of {@link #render} as a pure function for the same reason {@code
     * SearedTankBlockEntityRenderer#topY} is: it is the whole height model, and a screenshot is a
     * poor place to check arithmetic. See {@code SmelteryControllerBlockEntityRendererTest}.
     */
    static float[] layerHeights(List<FluidStack> fluids, int capacity, float maxHeight) {
        float[] heights = new float[fluids.size()];
        int total = 0;
        float sum = 0f;
        for (int i = 0; i < fluids.size(); i++) {
            int amount = fluids.get(i).getAmount();
            total += amount;
            heights[i] = Math.max(MIN_LAYER_HEIGHT, amount / (float) capacity * maxHeight);
            sum += heights[i];
        }
        float budget = total < capacity ? maxHeight - MIN_LAYER_HEIGHT : maxHeight;
        float excess = sum - budget;
        if (excess <= 0f) {
            return heights;
        }
        // Compared against a hair rather than zero: twenty layers all sitting exactly on the floor
        // sum to "0.1 * 20" twice over in float, which leaves a headroom of ~1e-7 instead of 0 and
        // would then have the whole overflow taken out of that rounding error.
        float headroom = sum - fluids.size() * MIN_LAYER_HEIGHT;
        for (int i = 0; i < heights.length; i++) {
            heights[i] = headroom > 1e-4f
                    ? heights[i] - (heights[i] - MIN_LAYER_HEIGHT) / headroom * excess
                    // Everything is already at the floor and it still doesn't fit (more fluids than
                    // the interior has room for bands): squeeze them all evenly instead.
                    : heights[i] * budget / sum;
        }
        return heights;
    }

    /**
     * One fluid band as a cuboid in block-local coordinates, split at every block boundary it crosses
     * so each cell gets its own copy of the sprite -- upstream's {@code renderStackedFluidCuboid}
     * walking its {@code xs}/{@code ys}/{@code zs} arrays. Only the outer faces of the whole cuboid
     * are emitted; interior cell walls would never be seen.
     */
    private static void renderLayer(VertexConsumer buffer, Matrix4f pose, TextureAtlasSprite sprite, int tint,
            int light, int overlay, float x1, float y1, float z1, float x2, float y2, float z2,
            boolean floor, boolean lid) {
        if (y2 - y1 <= 0f) {
            return;
        }
        float[] xs = splitAtBlocks(x1, x2);
        float[] ys = splitAtBlocks(y1, y2);
        float[] zs = splitAtBlocks(z1, z2);
        for (int xi = 0; xi < xs.length - 1; xi++) {
            for (int yi = 0; yi < ys.length - 1; yi++) {
                for (int zi = 0; zi < zs.length - 1; zi++) {
                    float cx1 = xs[xi], cx2 = xs[xi + 1];
                    float cy1 = ys[yi], cy2 = ys[yi + 1];
                    float cz1 = zs[zi], cz2 = zs[zi + 1];
                    // Sprite coordinates of this cell's own slice of its block.
                    float ux1 = u(sprite, frac(cx1)), ux2 = u(sprite, frac(cx1) + cx2 - cx1);
                    float uz1 = u(sprite, frac(cz1)), uz2 = u(sprite, frac(cz1) + cz2 - cz1);
                    float vy1 = v(sprite, frac(cy1)), vy2 = v(sprite, frac(cy1) + cy2 - cy1);
                    float vz1 = v(sprite, frac(cz1)), vz2 = v(sprite, frac(cz1) + cz2 - cz1);

                    if (xi == 0) {
                        FluidRenderUtil.quad(buffer, pose,
                                cx1, cy1, cz1, uz1, vy1, cx1, cy1, cz2, uz2, vy1,
                                cx1, cy2, cz2, uz2, vy2, cx1, cy2, cz1, uz1, vy2,
                                -1f, 0f, 0f, tint, light, overlay);
                    }
                    if (xi == xs.length - 2) {
                        FluidRenderUtil.quad(buffer, pose,
                                cx2, cy1, cz1, uz1, vy1, cx2, cy2, cz1, uz1, vy2,
                                cx2, cy2, cz2, uz2, vy2, cx2, cy1, cz2, uz2, vy1,
                                1f, 0f, 0f, tint, light, overlay);
                    }
                    if (zi == 0) {
                        FluidRenderUtil.quad(buffer, pose,
                                cx1, cy1, cz1, ux1, vy1, cx1, cy2, cz1, ux1, vy2,
                                cx2, cy2, cz1, ux2, vy2, cx2, cy1, cz1, ux2, vy1,
                                0f, 0f, -1f, tint, light, overlay);
                    }
                    if (zi == zs.length - 2) {
                        FluidRenderUtil.quad(buffer, pose,
                                cx1, cy1, cz2, ux1, vy1, cx2, cy1, cz2, ux2, vy1,
                                cx2, cy2, cz2, ux2, vy2, cx1, cy2, cz2, ux1, vy2,
                                0f, 0f, 1f, tint, light, overlay);
                    }
                    if (floor && yi == 0) {
                        FluidRenderUtil.quad(buffer, pose,
                                cx1, cy1, cz1, ux1, vz1, cx2, cy1, cz1, ux2, vz1,
                                cx2, cy1, cz2, ux2, vz2, cx1, cy1, cz2, ux1, vz2,
                                0f, -1f, 0f, tint, light, overlay);
                    }
                    if (lid && yi == ys.length - 2) {
                        FluidRenderUtil.quad(buffer, pose,
                                cx1, cy2, cz1, ux1, vz1, cx1, cy2, cz2, ux1, vz2,
                                cx2, cy2, cz2, ux2, vz2, cx2, cy2, cz1, ux2, vz1,
                                0f, 1f, 0f, tint, light, overlay);
                    }
                }
            }
        }
    }

    /**
     * {@code from}, every whole-block boundary strictly between, then {@code to} -- so no cell ever
     * spans more than one block and the sprite tiles instead of stretching. Upstream builds the same
     * arrays inline.
     */
    static float[] splitAtBlocks(float from, float to) {
        int first = (int) Math.floor(from) + 1;
        int last = (int) Math.ceil(to) - 1;
        int inner = Math.max(0, last - first + 1);
        float[] splits = new float[inner + 2];
        splits[0] = from;
        for (int i = 0; i < inner; i++) {
            splits[i + 1] = first + i;
        }
        splits[inner + 1] = to;
        return splits;
    }

    /** Where a coordinate sits inside its own block, 0 to 1. */
    private static float frac(float coordinate) {
        return coordinate - (float) Math.floor(coordinate);
    }

    private static float u(TextureAtlasSprite sprite, float fraction) {
        return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * fraction;
    }

    /** Sprite V runs top-down while world Y runs bottom-up, so this flips {@code fraction}. */
    private static float v(TextureAtlasSprite sprite, float fraction) {
        return sprite.getV1() + (sprite.getV0() - sprite.getV1()) * fraction;
    }
}
