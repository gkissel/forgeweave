package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.cubemob.Slime;


import dev.gkissel.forgeweave.Forgeweave;

/**
 * The blue slime's renderer (issue #451, parity audit T20), upstream 1.12's {@code RenderTinkerSlime}
 * in its {@code FACTORY_BlueSlime} configuration: vanilla's slime body and outer gel, drawn over
 * upstream's own greyscale slime texture (NOTICE.md) and tinted {@code 0xff67f0f5} -- upstream's
 * colour, unchanged.
 *
 * <p>Upstream sets the tint by calling {@code RenderUtil.setColorRGBA} around {@code doRender} and
 * again inside its {@code LayerSlimeGelColored}, because 1.12 tinting is a GL colour on the fixed
 * pipeline. 26.1 takes a per-model ARGB tint instead: {@code LivingEntityRenderer#getModelTint} for
 * the body and the colour {@link OuterGel} submits the gel with -- exactly the pair of surfaces
 * upstream colours.
 *
 * <p>Everything else here is vanilla {@code SlimeRenderer} verbatim (shadow radius per size, the
 * 0.999 inset and the squish scale) because upstream's own renderer extends {@code RenderSlime} and
 * overrides none of it.
 */
public class BlueSlimeRenderer extends MobRenderer<Slime, SlimeRenderState, SlimeModel> {
    /** Upstream {@code RenderTinkerSlime#FACTORY_BlueSlime}'s colour, ARGB. */
    private static final int TINT = 0xff67f0f5;

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/entity/blue_slime.png");

    public BlueSlimeRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel(context.bakeLayer(ModelLayers.SLIME)), 0.25F);
        this.addLayer(new OuterGel(this, new SlimeModel(context.bakeLayer(ModelLayers.SLIME_OUTER))));
    }

    @Override
    protected float getShadowRadius(SlimeRenderState state) {
        return state.size * 0.25F;
    }

    @Override
    protected void scale(SlimeRenderState state, PoseStack poseStack) {
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float size = state.size;
        float squish = state.squish / (size * 0.5F + 1.0F);
        float inverse = 1.0F / (squish + 1.0F);
        poseStack.scale(inverse * size, 1.0F / inverse * size, inverse * size);
    }

    /** Paints the body blue -- upstream's colour around {@code doRender}. */
    @Override
    protected int getModelTint(SlimeRenderState state) {
        return TINT;
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }

    @Override
    public SlimeRenderState createRenderState() {
        return new SlimeRenderState();
    }

    @Override
    public void extractRenderState(Slime slime, SlimeRenderState state, float partialTick) {
        super.extractRenderState(slime, state, partialTick);
        state.squish = Mth.lerp(partialTick, slime.oSquish, slime.squish);
        state.size = slime.getSize();
    }

    /**
     * Vanilla's {@code SlimeOuterLayer} drawn over this renderer's texture and tinted -- upstream's
     * {@code LayerSlimeGelColored}. Vanilla's own layer hardcodes the vanilla slime texture, which
     * is why it is restated here instead of reused.
     */
    private static class OuterGel extends RenderLayer<SlimeRenderState, SlimeModel> {
        private final SlimeModel model;

        OuterGel(RenderLayerParent<SlimeRenderState, SlimeModel> parent, SlimeModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                SlimeRenderState state, float yRot, float xRot) {
            boolean glowingWhileInvisible = state.appearsGlowing() && state.isInvisible;
            if (state.isInvisible && !glowingWhileInvisible) {
                return;
            }
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            RenderType renderType = glowingWhileInvisible ? RenderTypes.outline(TEXTURE)
                    : RenderTypes.entityTranslucent(TEXTURE);
            collector.order(1).submitModel(this.model, state, poseStack, renderType, packedLight, overlay, TINT, null,
                    state.outlineColor, null);
        }
    }
}
