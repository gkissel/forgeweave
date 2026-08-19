package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

import dev.gkissel.forgeweave.entity.ShurikenEntity;

/**
 * Renders a thrown shuriken as its own item model, laid flat and spinning -- upstream 1.12's
 * {@code RenderShuriken} on {@code RenderProjectileBase} (NOTICE.md; issue #448). The transform
 * chain is upstream's {@code customRendering} in order: scale 0.6, yaw into the throw direction,
 * pitch, a small per-throw roll for variety, rotate flat, then the spin -- 20 degrees per tick
 * while in flight, frozen once stuck ({@code if(!entity.inGround) entity.spin += 20 * partialTicks}).
 *
 * <p>Upstream renders the tool's baked model directly ({@code toolCoreRenderer}); here vanilla's
 * {@link ItemRenderer} draws the carried stack's model, which is the same four tinted blade layers
 * the inventory shows.
 */
public class ShurikenRenderer extends EntityRenderer<ShurikenEntity> {

    private final ItemRenderer itemRenderer;

    public ShurikenRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ShurikenEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.6F, 0.6F, 0.6F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                entity.yRotO + (entity.getYRot() - entity.yRotO) * partialTicks));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                -(entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTicks)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.rollAngle()));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.spin(partialTicks)));
        itemRenderer.renderStatic(entity.getPickupItemStackOrigin(), ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ShurikenEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS; // the item model's own atlas; this renderer never binds it directly
    }
}
