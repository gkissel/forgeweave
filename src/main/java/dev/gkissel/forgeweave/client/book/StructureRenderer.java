package dev.gkissel.forgeweave.client.book;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Draws a {@link StructureElement} frame. 26.1 GUIs cannot tessellate 3D blocks straight into the
 * screen any more, so the schematic goes through a picture-in-picture texture the size of the
 * element's box, the way vanilla draws entities in the inventory. The matrix stack is the 1.21
 * one: centre of the box, {@code scale(scale, -scale, scale)}, the X then Y tilt, then the
 * structure centred on the origin, each visible cell a full-bright block model.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, value = Dist.CLIENT)
public final class StructureRenderer extends PictureInPictureRenderer<StructureRenderer.State> {

    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();

    /** One visible block of the frame, at its cell inside the structure. */
    public record Cell(int l, int h, int w, BlockState state) {}

    /** One frame's snapshot: the structure keeps animating while the texture is drawn. */
    public record State(List<Cell> cells, int length, int height, int width, float rotX, float rotY,
            int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {

        public State(List<Cell> cells, int length, int height, int width, float rotX, float rotY,
                int x0, int y0, int x1, int y1, float scale) {
            this(cells, length, height, width, rotX, rotY, x0, y0, x1, y1, scale, null,
                    PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
        }
    }

    public StructureRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @SubscribeEvent
    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(State.class, StructureRenderer::new);
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    @Override
    protected void renderToTexture(State state, PoseStack pose) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        // The base pose is scale(s, s, -s); flipping y and z gives the 1.21 scale(s, -s, s).
        pose.scale(1f, -1f, -1f);
        pose.mulPose(Axis.XP.rotationDegrees(state.rotX()));
        pose.mulPose(Axis.YP.rotationDegrees(state.rotY()));
        pose.translate(state.length() / -2f, state.height() / -2f, state.width() / -2f);

        FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
        BlockModelRenderState model = new BlockModelRenderState();
        for (Cell cell : state.cells()) {
            minecraft.getBlockModelResolver().update(model, cell.state(), DISPLAY_CONTEXT);
            pose.pushPose();
            pose.translate(cell.l(), cell.h(), cell.w());
            model.submitMultiLayer(pose, features.getSubmitNodeStorage(), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        features.renderAllFeatures();
    }

    /** Centred vertically like the entity renderer, so the box centre is the pose origin. */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    protected String getTextureLabel() {
        return "forgeweave_book_structure";
    }
}
