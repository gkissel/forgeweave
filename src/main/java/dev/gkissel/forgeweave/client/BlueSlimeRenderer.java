package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The blue slime's renderer (issue #451, parity audit T20), upstream 1.12's {@code RenderTinkerSlime}
 * in its {@code FACTORY_BlueSlime} configuration: vanilla's slime body and outer gel, drawn over
 * upstream's own greyscale slime texture (NOTICE.md) and tinted {@code 0xff67f0f5} -- upstream's
 * colour, unchanged.
 *
 * <p>Upstream sets the tint by calling {@code RenderUtil.setColorRGBA} around {@code doRender} and
 * again inside its {@code LayerSlimeGelColored}, because 1.12 tinting is a GL colour on the fixed
 * pipeline. 1.21 threads a per-draw ARGB int down to {@code Model#renderToBuffer} instead, and
 * {@code LivingEntityRenderer#render} hardcodes the white {@code -1} on the way. The single place
 * that colour can still be substituted without copying the whole render method is the model, so the
 * tint lives in {@link Tinted} below and both models -- body and gel -- are tinted instances, which
 * is exactly the pair of surfaces upstream colours.
 *
 * <p>Everything else here is vanilla {@code SlimeRenderer} verbatim (shadow radius per size, the
 * 0.999 inset and the squish scale) because upstream's own renderer extends {@code RenderSlime} and
 * overrides none of it.
 */
@OnlyIn(Dist.CLIENT)
public class BlueSlimeRenderer extends MobRenderer<Slime, SlimeModel<Slime>> {
    /** Upstream {@code RenderTinkerSlime#FACTORY_BlueSlime}'s colour, ARGB. */
    private static final int TINT = 0xff67f0f5;

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/entity/blue_slime.png");

    public BlueSlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new Tinted<>(context.bakeLayer(ModelLayers.SLIME)), 0.25F);
        this.addLayer(new OuterGel(this, new Tinted<>(context.bakeLayer(ModelLayers.SLIME_OUTER))));
    }

    @Override
    public void render(Slime slime, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        this.shadowRadius = 0.25F * slime.getSize();
        super.render(slime, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(Slime slime, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float size = slime.getSize();
        float squish = Mth.lerp(partialTick, slime.oSquish, slime.squish) / (size * 0.5F + 1.0F);
        float inverse = 1.0F / (squish + 1.0F);
        poseStack.scale(inverse * size, 1.0F / inverse * size, inverse * size);
    }

    @Override
    public ResourceLocation getTextureLocation(Slime slime) {
        return TEXTURE;
    }

    /**
     * A slime model that paints itself blue. The caller's colour is honoured whenever it is not the
     * plain white {@code -1}, so vanilla's translucent pass for an invisible-but-glowing entity keeps
     * its own alpha rather than being overpainted opaque.
     */
    private static class Tinted<T extends Slime> extends SlimeModel<T> {
        Tinted(net.minecraft.client.model.geom.ModelPart part) {
            super(part);
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                int packedOverlay, int color) {
            super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color == -1 ? TINT : color);
        }
    }

    /**
     * Vanilla's {@code SlimeOuterLayer} with the model handed in rather than built inside, so the gel
     * is a {@link Tinted} one -- upstream's {@code LayerSlimeGelColored}. Vanilla's own layer bakes a
     * plain {@code SlimeModel} into a private field, which is why it is restated here instead of
     * subclassed.
     */
    private static class OuterGel extends RenderLayer<Slime, SlimeModel<Slime>> {
        private final SlimeModel<Slime> model;

        OuterGel(BlueSlimeRenderer parent, SlimeModel<Slime> model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Slime slime,
                float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                float netHeadYaw, float headPitch) {
            if (slime.isInvisible()) {
                return;
            }
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(slime)));
            this.getParentModel().copyPropertiesTo(this.model);
            this.model.prepareMobModel(slime, limbSwing, limbSwingAmount, partialTick);
            this.model.setupAnim(slime, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            this.model.renderToBuffer(poseStack, consumer, packedLight,
                    LivingEntityRenderer.getOverlayCoords(slime, 0.0F));
        }
    }
}
