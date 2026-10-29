package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * {@link ItemModelResolver} draws the carried stack's model, which is the same four tinted blade layers
 * the inventory shows.
 */
public class ShurikenRenderer extends EntityRenderer<ShurikenEntity, ShurikenRenderer.State> {

    private final ItemModelResolver itemModelResolver;

    public ShurikenRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    /** The item model and the angles {@link #submit} turns it by, captured off the entity. */
    public static class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float yRot;
        float xRot;
        float roll;
        float spin;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShurikenEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        itemModelResolver.updateForNonLiving(state.item, entity.getPickupItemStackOrigin(), ItemDisplayContext.GROUND,
                entity);
        state.yRot = entity.yRotO + (entity.getYRot() - entity.yRotO) * partialTicks;
        state.xRot = entity.xRotO + (entity.getXRot() - entity.xRotO) * partialTicks;
        state.roll = entity.rollAngle();
        state.spin = entity.spin(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(0.6F, 0.6F, 0.6F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.spin));
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
