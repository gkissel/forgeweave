package dev.gkissel.forgeweave.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

import dev.gkissel.forgeweave.block.WoodTexturedBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * Wraps a table station's baked geometry (all quads baked against a default/oak sprite) and, when
 * {@link ModelData} carries a different {@link WoodTexturedBlockEntity#TEXTURE_PROPERTY}, remaps
 * each quad's UVs from the default sprite's atlas region to the target wood block's particle
 * sprite's region -- both are ordinary 16x16 block sprites, so a straight normalize/denormalize of
 * the baked UV floats reproduces the same face art with the new wood's texture (issue #43). Baked
 * quad sets are cached per (wood block, culling side) since a given placed table never changes
 * texture more than rarely (block placement, or a future dye/re-craft feature).
 */
public class RetexturedTableBakedModel implements IDynamicBakedModel {
    private final BakedModel base;
    private final TextureAtlasSprite defaultSprite;
    private final Map<CacheKey, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    /**
     * Per-wood item-form model variants (issue #77), keyed by the resolved {@code texture} block so
     * a wood is only ever wrapped once no matter how many stacks of it exist.
     */
    private final Map<Block, BakedModel> itemVariants = new ConcurrentHashMap<>();

    private final ItemOverrides overrides = new ItemOverrides() {
        @Nullable
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            ResourceLocation textureId = stack.get(ForgeweaveDataComponents.TEXTURE.get());
            if (textureId == null) {
                return model;
            }
            Block texture = BuiltInRegistries.BLOCK.getOptional(textureId).orElse(null);
            if (texture == null || texture == Blocks.AIR) {
                return model;
            }
            return itemVariants.computeIfAbsent(texture, ItemVariant::new);
        }
    };

    public RetexturedTableBakedModel(BakedModel base, TextureAtlasSprite defaultSprite) {
        this.base = base;
        this.defaultSprite = defaultSprite;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData extraData, @Nullable RenderType renderType) {
        Block texture = extraData.has(WoodTexturedBlockEntity.TEXTURE_PROPERTY)
                ? extraData.get(WoodTexturedBlockEntity.TEXTURE_PROPERTY)
                : null;
        if (texture == null || texture == Blocks.AIR) {
            return base.getQuads(state, side, rand, extraData, renderType);
        }

        TextureAtlasSprite targetSprite = particleSprite(texture);
        if (targetSprite == defaultSprite) {
            return base.getQuads(state, side, rand, extraData, renderType);
        }

        return cache.computeIfAbsent(new CacheKey(texture, side),
                key -> retexture(base.getQuads(state, side, rand, extraData, renderType), targetSprite));
    }

    /**
     * Only quads baked against {@link #defaultSprite} get remapped -- i.e. the model's {@code
     * #texture}, {@code #bottom} and {@code #legBottom} slots, which every station JSON points at
     * the same default wood precisely so this identity check catches all three. Those are upstream's
     * own retexture set ({@code BakedTableModel#getActualModel} rebuilds {@code bottom}/{@code
     * leg}/{@code legBottom}); its tables keep their own top art and rim trim (the {@code #top} and
     * {@code #side} slots) regardless of crafting wood, so quads baked with any other sprite pass
     * through untouched (issue #43 regression: the first cut retextured every quad, including the
     * station's own top decal).
     */
    private List<BakedQuad> retexture(List<BakedQuad> quads, TextureAtlasSprite targetSprite) {
        List<BakedQuad> remapped = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            remapped.add(quad.getSprite() == defaultSprite ? remapSprite(quad, defaultSprite, targetSprite) : quad);
        }
        return remapped;
    }

    private static BakedQuad remapSprite(BakedQuad quad, TextureAtlasSprite from, TextureAtlasSprite to) {
        int[] vertices = quad.getVertices().clone();
        float fromU0 = from.getU0();
        float fromU1 = from.getU1();
        float fromV0 = from.getV0();
        float fromV1 = from.getV1();
        float toU0 = to.getU0();
        float toU1 = to.getU1();
        float toV0 = to.getV0();
        float toV1 = to.getV1();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            float u = Float.intBitsToFloat(vertices[offset]);
            float v = Float.intBitsToFloat(vertices[offset + 1]);
            float u01 = fromU1 == fromU0 ? 0F : (u - fromU0) / (fromU1 - fromU0);
            float v01 = fromV1 == fromV0 ? 0F : (v - fromV0) / (fromV1 - fromV0);
            vertices[offset] = Float.floatToRawIntBits(toU0 + u01 * (toU1 - toU0));
            vertices[offset + 1] = Float.floatToRawIntBits(toV0 + v01 * (toV1 - toV0));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), to, quad.isShade());
    }

    /** The sprite a block's own default-state model uses for particles -- our per-wood swap target. */
    private static TextureAtlasSprite particleSprite(Block texture) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(texture.defaultBlockState());
        return model.getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        Block texture = data.get(WoodTexturedBlockEntity.TEXTURE_PROPERTY);
        return texture != null && texture != Blocks.AIR ? particleSprite(texture) : base.getParticleIcon(data);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return base.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return base.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return base.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return base.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return base.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return base.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    private record CacheKey(Block block, @Nullable Direction side) {}

    /**
     * The item-form model for one specific crafted wood (issue #77): item rendering calls {@code
     * getQuads}/{@code getParticleIcon} with {@link ModelData#EMPTY} rather than the placed block
     * entity's model data, so this fixes the requested wood in place of that empty data and delegates
     * everything else to the outer {@link RetexturedTableBakedModel} -- reusing its existing
     * per-(wood, side) quad cache and UV-remap logic rather than re-baking.
     */
    private final class ItemVariant implements IDynamicBakedModel {
        private final ModelData data;

        private ItemVariant(Block texture) {
            this.data = WoodTexturedBlockEntity.modelData(texture);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                ModelData extraData, @Nullable RenderType renderType) {
            return RetexturedTableBakedModel.this.getQuads(state, side, rand, data, renderType);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData extraData) {
            return RetexturedTableBakedModel.this.getParticleIcon(data);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return base.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return base.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return base.isCustomRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return RetexturedTableBakedModel.this.getParticleIcon(data);
        }

        @Override
        public ItemTransforms getTransforms() {
            return base.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
