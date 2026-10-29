package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * {@link ItemModelResolver} draws the carried stack's model, which is the same three tinted layers the
 * inventory shows -- {@code ShurikenRenderer}'s own approach.
 */
public class ArrowRenderer extends EntityRenderer<ArrowEntity, ArrowRenderer.State> {

    private final ItemModelResolver itemModelResolver;

    public ArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    /** The item model and the angles {@link #submit} turns it by, captured off the entity. */
    public static class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float yRot;
        float xRot;
        float roll;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArrowEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        itemModelResolver.updateForNonLiving(state.item, entity.getPickupItemStackOrigin(), ItemDisplayContext.GROUND,
                entity);
        state.yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        state.roll = entity.roll(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F); // RenderProjectileBase#doRender's glScalef(0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
