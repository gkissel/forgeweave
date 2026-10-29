package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.phys.Vec3;

import com.mojang.math.Axis;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import org.jspecify.annotations.Nullable;

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
public class CastingBlockEntityRenderer implements BlockEntityRenderer<CastingBlockEntity, CastingBlockEntityRenderer.State> {
    /** A generated item model's own depth, which becomes its height once it is laid flat. */
    private static final float ITEM_MODEL_DEPTH = 1f / 16f;
    /**
     * What a flat-laid item is squashed to. A casting table's recess is a single pixel deep, so an
     * unsquashed 1/16 item fills it edge to edge and leaves the pour nowhere to go but through the
     * cast -- #200's second defect. Upstream never hits this because its table items ride the chunk
     * mesh, where the fluid's translucent pass cannot tint them; ours share one pass, so the cast
     * has to physically sit above the pool.
     */
    private static final float FLAT_ITEM_THICKNESS = 1f / 64f;

    private final float surfaceY;
    private final float fluidTopY;
    private final float itemBaseY;
    private final float xzMin;
    private final float xzMax;
    private final float itemScale;

    /** Upstream's {@code CastingRenderer.Table}. */
    static CastingBlockEntityRenderer table() {
        // The fluid sheet's ceiling is a hair above the block so a full table reads as brimming
        // rather than flush with its own rim -- upstream's `1f + 0.001f`. A cast tucks up under the
        // rim instead, its top flush with the block, so the pour fills the recess beneath it.
        return new CastingBlockEntityRenderer(15f / 16f, 1f + 0.001f, 1f - FLAT_ITEM_THICKNESS,
                1f / 16f, 15f / 16f, 0.875f);
    }

    /** Upstream's {@code CastingRenderer.Basin}. */
    static CastingBlockEntityRenderer basin() {
        // A basin is deep enough that whatever it holds simply sits on its floor.
        return new CastingBlockEntityRenderer(4f / 16f, 1f, 4f / 16f, 2f / 16f, 14f / 16f, 12f / 16f);
    }

    private CastingBlockEntityRenderer(float surfaceY, float fluidTopY, float itemBaseY,
            float xzMin, float xzMax, float itemScale) {
        this.surfaceY = surfaceY;
        this.fluidTopY = fluidTopY;
        this.itemBaseY = itemBaseY;
        this.xzMin = xzMin;
        this.xzMax = xzMax;
        this.itemScale = itemScale;
    }

    /** One slot's stack, resolved for {@link #submit}: its item model, orientation and height. */
    record Item(ItemStackRenderState state, boolean flat, float height) {}

    /** What {@link #submit} needs, captured off the block entity. */
    public static class State extends BlockEntityRenderState {
        @Nullable Item input;
        @Nullable Item output;
        float inputHeight;
        @Nullable TextureAtlasSprite fluidSprite;
        int fluidTint;
        float fluidTopY;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CastingBlockEntity blockEntity, State state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        ItemStack input = blockEntity.input();
        ItemStack output = blockEntity.output();
        FluidTank tank = blockEntity.tank();
        FluidStack fluid = tank.getFluid();
        boolean hasFluid = !fluid.isEmpty() && tank.getCapacity() > 0;
        if (input.isEmpty() && output.isEmpty() && !hasFluid) {
            return; // The common case: an empty station costs one branch per frame and no buffers.
        }

        state.inputHeight = itemHeight(input);
        state.input = extractItem(input, blockEntity);
        state.output = extractItem(output, blockEntity);

        if (hasFluid) {
            float fraction = Mth.clamp(fluid.getAmount() / (float) tank.getCapacity(), 0f, 1f);
            state.fluidSprite = FluidRenderUtil.stillSprite(fluid);
            state.fluidTint = FluidRenderUtil.tint(fluid);
            state.fluidTopY = topY(fraction, !input.isEmpty() || !output.isEmpty());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // Items first: the fluid is translucent and pours over whatever is already lying there.
        submitItem(state.input, 0, state.inputHeight, poseStack, collector, state.lightCoords);
        submitItem(state.output, 1, state.inputHeight, poseStack, collector, state.lightCoords);

        TextureAtlasSprite sprite = state.fluidSprite;
        if (sprite != null) {
            int tint = state.fluidTint;
            float topY = state.fluidTopY;
            int light = state.lightCoords;
            collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
                    FluidRenderUtil.cuboid(buffer, pose.pose(), xzMin, surfaceY, xzMin, xzMax, topY, xzMax,
                            sprite, tint, light, OverlayTexture.NO_OVERLAY));
        }
    }

    /**
     * The fluid's top face in block-local space: {@code surfaceY} when empty, rising to the station's
     * own ceiling when full -- or only to the underside of whatever is lying in the block, so a cast
     * being poured into always reads above its own metal instead of being tinted and z-fought by it
     * (#200). Split out for {@code CastingBlockEntityRendererTest} the same way {@link
     * SearedTankBlockEntityRenderer#topY} was, since #145 showed fill-height math is exactly what
     * goes wrong unwatched.
     *
     * <p>ponytail: {@code hasItem} does not ask whether the item is lying flat. A standing block item
     * would be submerged rather than a lid, but a basin is the only station that stands one up and it
     * never holds fluid and an item at once -- block casting takes no cast, and its result only
     * appears on the tick the pour is consumed.
     */
    float topY(float fraction, boolean hasItem) {
        float ceiling = hasItem ? itemBaseY : fluidTopY;
        return surfaceY + (ceiling - surfaceY) * Mth.clamp(fraction, 0f, 1f);
    }

    /**
     * The vertical centre, in block-local space, of a layer's item render. Layer 0 (the input) sits
     * directly on {@link #itemBaseY}; layer 1 (the output) is offset by {@code inputHeight} -- the
     * input's own height, not the output's -- so a basin's full-height block output isn't lifted by
     * its own 12/16 scale when there is no input beneath it (#407).
     */
    float centerY(float height, float inputHeight, int layer) {
        float offset = layer == 0 ? 0f : inputHeight;
        return itemBaseY + offset + height * 0.5f;
    }

    /** A stack's own height once laid in the station: flat, or a full block, per {@link #isFlat}. */
    private float itemHeight(ItemStack stack) {
        return stack.isEmpty() ? 0f : (isFlat(stack) ? FLAT_ITEM_THICKNESS : itemScale);
    }

    /**
     * Upstream's rule for orientation: a block lies as a block (a casting basin full of iron shows
     * an iron block), anything else -- a cast, a tool part, an ingot, or a pane, which has no cube
     * to show -- is laid flat like a sheet of paper.
     */
    private static boolean isFlat(ItemStack stack) {
        return !(stack.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() instanceof IronBarsBlock;
    }

    /**
     * One stack lying in the station, on its floor for a basin and tucked up under the rim for a
     * table (see {@link #itemBaseY}).
     *
     * @param layer       0 for the input slot, 1 for the output; stacks the result on top of the
     *                    cast it came out of instead of letting the two z-fight in the same plane.
     * @param inputHeight the input slot's own height (0 if it is empty) -- what the output layer
     *                    rests on top of. Never the output's own height: a basin's block output has
     *                    no cast beneath it, and offsetting by its own 12/16 scale is what popped it
     *                    out over the rim (#407).
     */
    private @Nullable Item extractItem(ItemStack stack, CastingBlockEntity blockEntity) {
        if (stack.isEmpty()) {
            return null;
        }
        ItemStackRenderState itemState = new ItemStackRenderState();
        // ItemDisplayContext.NONE applies no display transform, so the model arrives centred on the
        // pose submitItem sets up -- upstream renders the raw baked model for the same reason.
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(itemState, stack, ItemDisplayContext.NONE,
                blockEntity.getLevel(), null, (int) blockEntity.getBlockPos().asLong());
        boolean flat = isFlat(stack);
        return new Item(itemState, flat, flat ? FLAT_ITEM_THICKNESS : itemScale);
    }

    private void submitItem(@Nullable Item item, int layer, float inputHeight, PoseStack poseStack,
            SubmitNodeCollector collector, int packedLight) {
        if (item == null || item.state().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5f, centerY(item.height(), inputHeight, layer), 0.5f);
        if (item.flat()) {
            // After the rotation the model's own depth axis is the block's vertical one, so the
            // third scale is the item's thickness -- squashed to FLAT_ITEM_THICKNESS, see there.
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            poseStack.scale(itemScale, itemScale, FLAT_ITEM_THICKNESS / ITEM_MODEL_DEPTH);
        } else {
            poseStack.scale(itemScale, itemScale, itemScale);
        }
        item.state().submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
