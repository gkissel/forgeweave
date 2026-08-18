package dev.gkissel.forgeweave.client;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix4f;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.CrossbowItem;

/**
 * Issue #484 (parity audit T53): upstream 1.12's draw crosshairs. While a Forgeweave launcher is in
 * hand the vanilla crosshair is replaced by one that reads the draw back to the player -- four
 * corner pieces of a square for the bows, three tips for the crossbow -- flung apart at rest and
 * pulled back together as the draw completes. Derived from
 * {@code library/client/crosshair/{Crosshair,CrosshairInverseT,Crosshairs,CrosshairRenderEvents}}
 * plus {@code ShortBow#getCrosshair(State)} and {@code CrossBow#getCrosshair(State)} at commit
 * {@code c01173c0408352c50a2e8c5017552323ce42f5b4}.
 *
 * <h2>Geometry</h2>
 *
 * <p>Both styles are one 16x16 sprite cut into pieces that are drawn away from the screen centre by
 * {@code spread = (1 - charge) * 25} ({@code Crosshair#render}). SQUARE takes the four 8x8 quarters
 * and pushes each diagonally into its own corner ({@code drawSquareCrosshairPart}), so at full
 * charge they tile back into the intact 16x16 sprite. T masks the sprite along its two diagonals
 * instead and draws the top, left and right wedges as textured triangles pushed up / left / right
 * ({@code CrosshairInverseT#drawTipCrosshairPart}) -- the wedge masking, not a hand-picked crop, so
 * a resource pack can redraw {@code t.png} and still get a sensible split. Upstream asks for a
 * fourth (bottom) wedge and then has no branch to draw it; that no-op is not reproduced, and costs
 * nothing because the bottom wedge of upstream's own sprite is empty.
 *
 * <h2>Charge</h2>
 *
 * <p>{@link #charge} is upstream's {@code getCrosshairState}: a bow reports its draw progress (zero
 * when it is not the item being used), and the crossbow reports 1 whenever it is loaded -- a
 * cranked crossbow is fully accurate whether or not it is currently in the player's hands -- then
 * falls back to the same draw progress while it is being cranked.
 *
 * <h2>Deviations from 1.12, forced by the 1.21 GUI</h2>
 *
 * <ul>
 * <li>Upstream cancels {@code RenderGameOverlayEvent.Pre(CROSSHAIRS)}, which 1.12 only fired in
 * first person, outside spectator mode and with the HUD shown. NeoForge's
 * {@link RenderGuiLayerEvent.Pre} fires for a layer even when that layer is inactive, so those
 * three preconditions are re-checked here rather than inherited.</li>
 * <li>Vertices are placed at {@code z = 0} in the layer's own pose instead of upstream's
 * {@code zLevel = -90}; the 1.21 {@code GuiLayerManager} already separates layers in Z and vanilla
 * draws its own crosshair at 0.</li>
 * <li>Cancelling the layer takes the attack-strength indicator with it, so
 * {@link #renderAttackIndicator} redraws it -- exactly what upstream's handler does after its own
 * {@code event.setCanceled(true)}, ported to 1.21's sprites and its extra "full" state.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class BowCrosshair {

    /** {@code Crosshair#render}: {@code spread = (1 - charge) * 25f}. */
    private static final float MAX_SPREAD = 25.0F;

    /** {@code Crosshair}'s default {@code size} of 16, as its {@code s = size / 4} half-extent. */
    private static final float SQUARE_HALF = 4.0F;

    /** {@code CrosshairInverseT#drawTipCrosshairPart}'s hardcoded {@code s = 8d}. */
    private static final float TIP_HALF = 8.0F;

    /** {@code Crosshairs.T} passes {@code size = 15}, giving these UVs over the 16px sprite. */
    private static final float TIP_UV_MAX = 15.0F / 16.0F;

    private static final float TIP_UV_CENTER = 7.5F / 16.0F;

    private static final ResourceLocation ATTACK_INDICATOR_FULL =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final ResourceLocation ATTACK_INDICATOR_BACKGROUND =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final ResourceLocation ATTACK_INDICATOR_PROGRESS =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

    /** {@code Crosshairs}: the two entries Forgeweave's launchers actually ask for. */
    public enum Style {
        /** {@code Crosshairs.SQUARE} -- {@code ShortBow#getCrosshair}, inherited by the longbow. */
        SQUARE("square"),
        /** {@code Crosshairs.T} -- {@code CrossBow#getCrosshair}. */
        T("t");

        private final ResourceLocation texture;

        Style(String name) {
            this.texture = ResourceLocation.fromNamespaceAndPath(
                    Forgeweave.MODID, "textures/derived/gui/crosshair/" + name + ".png");
        }
    }

    // ---- pure halves ---------------------------------------------------------------------------

    /**
     * {@code ICustomCrosshairUser}, which upstream implements on {@code ShortBow} (so also the
     * longbow) and {@code CrossBow} and nothing else. Null means "leave the vanilla crosshair be".
     */
    @Nullable
    public static Style styleFor(ItemStack stack) {
        if (stack.getItem() instanceof CrossbowItem) {
            return Style.T;
        }
        return stack.getItem() instanceof BowItem ? Style.SQUARE : null;
    }

    /**
     * {@code getCrosshairState}. {@code drawbackProgress} is
     * {@link BowItem#drawbackProgress(ItemStack, LivingEntity)}, which is already 0 unless this is
     * the stack being drawn; {@code isActiveItem} is upstream's {@code player.getActiveItemStack()
     * == itemStack} test, which only the crossbow branch needs separately.
     */
    public static float charge(ItemStack stack, boolean isActiveItem, float drawbackProgress) {
        if (stack.getItem() instanceof CrossbowItem) {
            if (CrossbowItem.isLoaded(stack)) {
                return 1.0F;
            }
            return isActiveItem ? drawbackProgress : 0.0F;
        }
        return drawbackProgress;
    }

    /** {@code Crosshair#render}: how far off centre the pieces sit at this charge. */
    public static float spread(float charge) {
        return (1.0F - Mth.clamp(charge, 0.0F, 1.0F)) * MAX_SPREAD;
    }

    /**
     * {@code Crosshair#drawSquareCrosshairPart}: the screen rectangle {@code {x0, y0, x1, y1}} the
     * quarter {@code part} covers -- 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right, the
     * same order {@code drawCrosshair} emits them in.
     */
    public static float[] squarePart(int part, float centerX, float centerY, float spread) {
        boolean right = part == 1 || part == 3;
        boolean bottom = part >= 2;
        float x = centerX + (right ? spread + SQUARE_HALF : -spread - SQUARE_HALF);
        float y = centerY + (bottom ? spread + SQUARE_HALF : -spread - SQUARE_HALF);
        return new float[] {x - SQUARE_HALF, y - SQUARE_HALF, x + SQUARE_HALF, y + SQUARE_HALF};
    }

    /**
     * {@code CrosshairInverseT#drawCrosshair}: the screen point {@code {x, y}} tip {@code part}
     * is centred on -- 0 top, 1 left, 2 right.
     */
    public static float[] tipCenter(int part, float centerX, float centerY, float spread) {
        return switch (part) {
            case 0 -> new float[] {centerX, centerY - spread};
            case 1 -> new float[] {centerX - spread, centerY};
            default -> new float[] {centerX + spread, centerY};
        };
    }

    // ---- the layer -----------------------------------------------------------------------------

    @SubscribeEvent
    static void onRenderCrosshair(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || minecraft.options.hideGui
                || !minecraft.options.getCameraType().isFirstPerson()
                || player.isSpectator()) {
            return;
        }
        ItemStack stack = launcherInHand(player);
        Style style = styleFor(stack);
        if (style == null) {
            return;
        }

        float charge = charge(stack, player.getUseItem() == stack,
                ((BowItem) stack.getItem()).drawbackProgress(stack, player));
        render(minecraft, event.getGuiGraphics(), style, charge);
        event.setCanceled(true);
    }

    /**
     * {@code CrosshairRenderEvents#getItemstack}: the item being used wins, then the main hand,
     * then the off hand -- each only if it is a launcher at all.
     */
    private static ItemStack launcherInHand(LocalPlayer player) {
        if (player.isUsingItem() && styleFor(player.getUseItem()) != null) {
            return player.getUseItem();
        }
        if (styleFor(player.getMainHandItem()) != null) {
            return player.getMainHandItem();
        }
        return styleFor(player.getOffhandItem()) != null ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    private static void render(Minecraft minecraft, GuiGraphics graphics, Style style, float charge) {
        float spread = spread(charge);
        float centerX = graphics.guiWidth() / 2.0F;
        float centerY = graphics.guiHeight() / 2.0F;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, style.texture);
        RenderSystem.enableBlend();
        // The inverting blend vanilla's own crosshair uses, so the crosshair stays readable on any
        // background -- upstream Crosshair#render sets exactly this.
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        Matrix4f pose = graphics.pose().last().pose();
        if (style == Style.SQUARE) {
            drawSquare(pose, centerX, centerY, spread);
        } else {
            drawTips(pose, centerX, centerY, spread);
        }
        // Vanilla draws the indicator under this same inverting blend, before restoring it.
        renderAttackIndicator(minecraft, graphics);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** The four 8x8 quarters, each drawn from its own quarter of the sprite. */
    private static void drawSquare(Matrix4f pose, float centerX, float centerY, float spread) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int part = 0; part < 4; part++) {
            float[] box = squarePart(part, centerX, centerY, spread);
            float u1 = (part == 1 || part == 3) ? 0.5F : 0.0F;
            float v1 = part >= 2 ? 0.5F : 0.0F;
            buffer.addVertex(pose, box[0], box[1], 0.0F).setUv(u1, v1);
            buffer.addVertex(pose, box[0], box[3], 0.0F).setUv(u1, v1 + 0.5F);
            buffer.addVertex(pose, box[2], box[3], 0.0F).setUv(u1 + 0.5F, v1 + 0.5F);
            buffer.addVertex(pose, box[2], box[1], 0.0F).setUv(u1 + 0.5F, v1);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** The top / left / right wedges, each the sprite masked along its diagonals. */
    private static void drawTips(Matrix4f pose, float centerX, float centerY, float spread) {
        BufferBuilder buffer =
                Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        for (int part = 0; part < 3; part++) {
            float[] tip = tipCenter(part, centerX, centerY, spread);
            float left = tip[0] - TIP_HALF;
            float right = tip[0] + TIP_HALF;
            float top = tip[1] - TIP_HALF;
            float bottom = tip[1] + TIP_HALF;
            switch (part) {
                case 0 -> {
                    buffer.addVertex(pose, left, top, 0.0F).setUv(0.0F, 0.0F);
                    buffer.addVertex(pose, tip[0], tip[1], 0.0F).setUv(TIP_UV_CENTER, TIP_UV_CENTER);
                    buffer.addVertex(pose, right, top, 0.0F).setUv(TIP_UV_MAX, 0.0F);
                }
                case 1 -> {
                    buffer.addVertex(pose, left, top, 0.0F).setUv(0.0F, 0.0F);
                    buffer.addVertex(pose, left, bottom, 0.0F).setUv(0.0F, TIP_UV_MAX);
                    buffer.addVertex(pose, tip[0], tip[1], 0.0F).setUv(TIP_UV_CENTER, TIP_UV_CENTER);
                }
                default -> {
                    buffer.addVertex(pose, tip[0], tip[1], 0.0F).setUv(TIP_UV_CENTER, TIP_UV_CENTER);
                    buffer.addVertex(pose, right, bottom, 0.0F).setUv(TIP_UV_MAX, TIP_UV_MAX);
                    buffer.addVertex(pose, right, top, 0.0F).setUv(TIP_UV_MAX, 0.0F);
                }
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** {@code Gui#renderCrosshair}'s attack-strength indicator, which the cancelled layer owed us. */
    private static void renderAttackIndicator(Minecraft minecraft, GuiGraphics graphics) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) {
            return;
        }
        float strength = player.getAttackStrengthScale(0.0F);
        boolean full = minecraft.crosshairPickEntity instanceof LivingEntity
                && strength >= 1.0F
                && player.getCurrentItemAttackStrengthDelay() > 5.0F
                && minecraft.crosshairPickEntity.isAlive();

        int y = graphics.guiHeight() / 2 - 7 + 16;
        int x = graphics.guiWidth() / 2 - 8;
        if (full) {
            graphics.blitSprite(ATTACK_INDICATOR_FULL, x, y, 16, 16);
        } else if (strength < 1.0F) {
            graphics.blitSprite(ATTACK_INDICATOR_BACKGROUND, x, y, 16, 4);
            graphics.blitSprite(ATTACK_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, (int) (strength * 17.0F), 4);
        }
    }

    private BowCrosshair() {}
}
