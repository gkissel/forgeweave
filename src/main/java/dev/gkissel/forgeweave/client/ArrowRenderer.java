package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

import dev.gkissel.forgeweave.entity.ArrowEntity;

/**
 * Renders a fired material arrow as its own item model heading the way it flies, rolling around
 * its flight axis -- upstream 1.12's {@code RenderArrow} on {@code RenderProjectileBase}
 * (NOTICE.md; issue #653), the same port {@code ShurikenRenderer} is for the shuriken. The
 * transform chain is upstream's {@code customRendering} in order: scale, yaw into the flight
 * direction, pitch, the roll spin ({@code RenderArrow#customCustomRendering}), then the two fixed
 * rotations that align the diagonal item sprite with the flight axis ({@code glRotatef(-90, y)}
 * then {@code glRotatef(-45, z)}).
 *
 * <p>Upstream renders the tool's baked model directly ({@code toolCoreRenderer}); here vanilla's
 * {@link ItemRenderer} draws the carried stack's model, which is the same three tinted layers the
 * inventory shows -- {@code ShurikenRenderer}'s own approach.
 */
public class ArrowRenderer extends EntityRenderer<ArrowEntity> {

    private final ItemRenderer itemRenderer;

    public ArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ArrowEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F); // RenderProjectileBase#doRender's glScalef(0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTicks, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                -Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.roll(partialTicks)));
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        itemRenderer.renderStatic(entity.getPickupItemStackOrigin(), ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ArrowEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS; // the item model's own atlas; this renderer never binds it directly
    }
}
