package dev.gkissel.forgeweave.client.model;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.math.Quadrant;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.model.ExtraFaceData;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/**
 * The quad-level helpers Forgeweave's per-stack item and block models share on 26.1: re-pointing a
 * baked quad at another sprite (the table's crafting wood, a tool's material finish), extruding one
 * sprite into item-layer geometry at render time (a modifier overlay), and filling the single
 * {@link ItemStackRenderState} layer a model resolves to -- {@code CuboidItemModelWrapper#update}
 * with the quads and tints chosen per stack.
 */
public final class ForgeweaveQuads {
    /** Runtime baking has no model baker to intern through; nothing here is shared widely enough to need it. */
    private static final ModelBaker.Interner NO_INTERNING = new ModelBaker.Interner() {
        @Override
        public Vector3fc vector(Vector3fc vector) {
            return vector;
        }

        @Override
        public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return material;
        }
    };

    /** {@code ItemModelGenerator}'s own front and back face UVs. */
    private static final CuboidFace.UVs SOUTH_FACE_UVS = new CuboidFace.UVs(0.0F, 0.0F, 16.0F, 16.0F);
    private static final CuboidFace.UVs NORTH_FACE_UVS = new CuboidFace.UVs(16.0F, 0.0F, 0.0F, 16.0F);

    private ForgeweaveQuads() {}

    /**
     * {@code quad} drawing the same region of {@code target} it drew of its own sprite -- a straight
     * normalise/denormalise of the baked UVs, so both sprites must share a layout (the 16x16 block and
     * item sprites every caller swaps between do). The chunk layer and item render type stay the
     * source quad's.
     */
    public static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite target, int tintIndex) {
        BakedQuad.MaterialInfo info = quad.materialInfo();
        return new MutableQuad().setFrom(quad)
                .setSpriteAndMoveUv(target, info.layer(), info.itemRenderType(), info.itemGlintRenderType(),
                        info.itemGlintSpecialRenderType())
                .setTintIndex(tintIndex)
                .toBakedQuad();
    }

    /**
     * One {@code item/generated} layer of {@code sprite} -- the front and back faces plus the pixel
     * edges vanilla's {@code ItemModelGenerator} extrudes -- baked at {@code tintIndex} under {@code
     * state}. The runtime twin of what a model JSON's {@code layerN} bakes to.
     */
    public static List<BakedQuad> extrude(TextureAtlasSprite sprite, int tintIndex, ModelState state) {
        BakedQuad.MaterialInfo info = BakedQuad.MaterialInfo.of(new Material.Baked(sprite, false), sprite.transparency(),
                tintIndex, null, 0, true);
        Vector3f from = new Vector3f(0.0F, 0.0F, 7.5F);
        Vector3f to = new Vector3f(16.0F, 16.0F, 8.5F);
        QuadCollection.Builder builder = new QuadCollection.Builder();
        builder.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, SOUTH_FACE_UVS, Quadrant.R0, info,
                Direction.SOUTH, state, null, ExtraFaceData.DEFAULT));
        builder.addUnculledFace(FaceBakery.bakeQuad(NO_INTERNING, from, to, NORTH_FACE_UVS, Quadrant.R0, info,
                Direction.NORTH, state, null, ExtraFaceData.DEFAULT));
        ItemModelGenerator.bakeSideFaces(builder, NO_INTERNING, state, info, ExtraFaceData.DEFAULT);
        return builder.build().getAll();
    }

    /** {@code id} on the block atlas, or {@code null} when no such sprite was stitched. */
    @Nullable
    public static TextureAtlasSprite blockSprite(Identifier id) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, id));
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : sprite;
    }

    /**
     * Appends the one layer a Forgeweave item model draws: {@code CuboidItemModelWrapper#update}
     * verbatim, with {@code quads} and {@code tints} picked per stack by the caller and {@code
     * identity} standing in for them in the GUI render cache's key.
     */
    public static void fillLayer(ItemStackRenderState output, ItemStack stack, ItemDisplayContext displayContext,
            Object identity, List<BakedQuad> quads, int[] tints, ModelRenderProperties properties,
            Matrix4fc transformation, Supplier<Vector3fc[]> extents) {
        output.appendModelIdentityElement(identity);
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        if (stack.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
            output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
        }
        for (int tint : tints) {
            layer.tintLayers().add(tint);
            output.appendModelIdentityElement(tint);
        }
        layer.setExtents(extents);
        layer.setLocalTransform(transformation);
        properties.applyToLayer(layer, displayContext);
        layer.setQuads(ItemQuads.split(quads));
        for (BakedQuad quad : quads) {
            if ((quad.materialInfo().flags() & BakedQuad.FLAG_ANIMATED) != 0) {
                output.setAnimated();
                break;
            }
        }
    }
}
