package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.IronBarsBlock;

import com.mojang.math.Axis;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import dev.gkissel.forgeweave.block.CastingBlockEntity;

/**
 * Draws what a casting table or basin is holding: the cast (and the part or ingot moulded in it)
 * lying on the surface, and the fluid poured over it (#182 -- the block entity has synced both slots
 * and its tank to clients since M2 issue #100, but nothing painted them, so a table with a cast on
 * it looked bare and a mid-pour table looked idle).
 *
 * <p>Ported from upstream 1.12's {@code CastingRenderer} and {@code TileCastingTable}/{@code
 * TileCastingBasin}'s {@code setInventoryDisplay} (NOTICE.md). Upstream splits the job in two: the
 * fluid is a TESR, while the items ride an extended-blockstate property into a baked table model.
 * Both are one renderer here -- the model route exists upstream to batch items into the chunk mesh,
 * and a casting block holds at most two, so it buys nothing and costs a custom baked model.
 *
 * <p>Geometry is upstream's, per station: {@code CastingRenderer.Table}'s 15/16..1 fluid sheet in
 * the table's recessed top and 0.875 item scale, {@code CastingRenderer.Basin}'s 4/16..1 pool
 * between the basin walls and 12/16 item scale.
 *
 * <p>ponytail: upstream also rotates the item to the block's placement facing and cross-fades the
 * result in over the cooling time. Forgeweave's casting block has no facing to rotate to and no
 * client-side cooling clock (its cooldown is a scheduled block tick, see {@link CastingBlockEntity}),
 * so both are skipped rather than invented.
 */
public class CastingBlockEntityRenderer implements BlockEntityRenderer<CastingBlockEntity> {
    /** A generated item model is 1/16 deep, so a flat-laid item is that thick after scaling. */
    private static final float ITEM_THICKNESS = 1f / 16f;

    private final float surfaceY;
    private final float fluidTopY;
    private final float xzMin;
    private final float xzMax;
    private final float itemScale;

    /** Upstream's {@code CastingRenderer.Table}. */
    static CastingBlockEntityRenderer table() {
        // The fluid sheet's ceiling is a hair above the block so a full table reads as brimming
        // rather than flush with its own rim -- upstream's `1f + 0.001f`.
        return new CastingBlockEntityRenderer(15f / 16f, 1f + 0.001f, 1f / 16f, 15f / 16f, 0.875f);
    }

    /** Upstream's {@code CastingRenderer.Basin}. */
    static CastingBlockEntityRenderer basin() {
        return new CastingBlockEntityRenderer(4f / 16f, 1f, 2f / 16f, 14f / 16f, 12f / 16f);
    }

    private CastingBlockEntityRenderer(float surfaceY, float fluidTopY, float xzMin, float xzMax, float itemScale) {
        this.surfaceY = surfaceY;
        this.fluidTopY = fluidTopY;
        this.xzMin = xzMin;
        this.xzMax = xzMax;
        this.itemScale = itemScale;
    }

    @Override
    public void render(CastingBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack input = blockEntity.input();
        ItemStack output = blockEntity.output();
        FluidTank tank = blockEntity.tank();
        FluidStack fluid = tank.getFluid();
        boolean hasFluid = !fluid.isEmpty() && tank.getCapacity() > 0;
        if (input.isEmpty() && output.isEmpty() && !hasFluid) {
            return; // The common case: an empty station costs one branch per frame and no buffers.
        }

        // Items first: the fluid is translucent and pours over whatever is already lying there.
        renderItem(input, 0, poseStack, bufferSource, packedLight, packedOverlay, blockEntity);
        renderItem(output, 1, poseStack, bufferSource, packedLight, packedOverlay, blockEntity);

        if (hasFluid) {
            float fraction = Mth.clamp(fluid.getAmount() / (float) tank.getCapacity(), 0f, 1f);
            TextureAtlasSprite sprite = FluidRenderUtil.stillSprite(fluid);
            FluidRenderUtil.cuboid(bufferSource.getBuffer(RenderType.translucent()), poseStack.last().pose(),
                    xzMin, surfaceY, xzMin, xzMax, topY(fraction), xzMax,
                    sprite, FluidRenderUtil.tint(fluid), packedLight, packedOverlay);
        }
    }

    /**
     * The fluid's top face in block-local space: {@code surfaceY} when empty up to the station's own
     * ceiling when full. Split out for {@code CastingBlockEntityRendererTest} the same way {@link
     * SearedTankBlockEntityRenderer#topY} was, since #145 showed fill-height math is exactly what
     * goes wrong unwatched.
     */
    float topY(float fraction) {
        return surfaceY + (fluidTopY - surfaceY) * Mth.clamp(fraction, 0f, 1f);
    }

    /**
     * One stack lying on the station's surface. Upstream's rule for orientation: a block lies as a
     * block (a casting basin full of iron shows an iron block), anything else -- a cast, a tool part,
     * an ingot, or a pane, which has no cube to show -- is laid flat like a sheet of paper.
     *
     * @param layer 0 for the input slot, 1 for the output; stacks the result on top of the cast it
     *              came out of instead of letting the two z-fight in the same plane.
     */
    private void renderItem(ItemStack stack, int layer, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay, CastingBlockEntity blockEntity) {
        if (stack.isEmpty()) {
            return;
        }
        boolean flat = !(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() instanceof IronBarsBlock;
        float height = (flat ? ITEM_THICKNESS : 1f) * itemScale;

        poseStack.pushPose();
        poseStack.translate(0.5f, surfaceY + height * (0.5f + layer), 0.5f);
        poseStack.scale(itemScale, itemScale, itemScale);
        if (flat) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        }
        // ItemDisplayContext.NONE applies no display transform, so the model arrives centred on the
        // pose above -- upstream renders the raw baked model for the same reason.
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE,
                packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(),
                (int) blockEntity.getBlockPos().asLong());
        poseStack.popPose();
    }
}
