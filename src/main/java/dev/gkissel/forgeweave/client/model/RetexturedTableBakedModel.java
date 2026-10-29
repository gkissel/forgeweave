package dev.gkissel.forgeweave.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.model.DynamicBlockStateModel;

import dev.gkissel.forgeweave.block.WoodTexturedBlockEntity;

/**
 * A placed table station (issue #43): its baked geometry (every quad baked against a default/oak
 * sprite) and, when the block entity's {@code ModelData} carries a different {@link
 * WoodTexturedBlockEntity#TEXTURE_PROPERTY}, the same geometry with each default-sprite quad's UVs
 * remapped onto the target wood block's particle sprite -- both are ordinary 16x16 block sprites, so
 * a straight normalize/denormalize of the baked UVs reproduces the same face art in the new wood.
 * Parts are cached per wood block, since a placed table changes texture only rarely.
 *
 * <p>Only quads baked against the default sprite get remapped -- the model's {@code #texture},
 * {@code #bottom} and {@code #legBottom} slots, which every station JSON points at the same default
 * wood so this identity check catches all three. Those are upstream's own retexture set ({@code
 * BakedTableModel#getActualModel}); the {@code #top} and {@code #side} slots keep their station art
 * whatever the crafting wood (issue #43 regression).
 */
public final class RetexturedTableBakedModel implements DynamicBlockStateModel {
    private final BlockStateModelPart base;
    private final TextureAtlasSprite defaultSprite;
    private final Map<Block, BlockStateModelPart> parts = new ConcurrentHashMap<>();

    RetexturedTableBakedModel(BlockStateModelPart base, TextureAtlasSprite defaultSprite) {
        this.base = base;
        this.defaultSprite = defaultSprite;
    }

    private record GeometryKey(RetexturedTableBakedModel model, @Nullable Block wood) {}

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
            List<BlockStateModelPart> output) {
        Block wood = wood(level, pos);
        output.add(wood == null ? base : parts.computeIfAbsent(wood, this::retexture));
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return new GeometryKey(this, wood(level, pos));
    }

    @Override
    public Material.Baked particleMaterial() {
        return base.particleMaterial();
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Block wood = wood(level, pos);
        return wood == null ? base.particleMaterial() : woodParticle(wood);
    }

    @Override
    public int materialFlags() {
        return base.materialFlags();
    }

    /** The wood to draw, or {@code null} for the model's own default. */
    @Nullable
    private Block wood(BlockAndTintGetter level, BlockPos pos) {
        Block texture = level.getModelData(pos).get(WoodTexturedBlockEntity.TEXTURE_PROPERTY);
        return texture == null || texture == Blocks.AIR || woodParticle(texture).sprite() == defaultSprite ? null : texture;
    }

    private BlockStateModelPart retexture(Block wood) {
        TextureAtlasSprite target = woodParticle(wood).sprite();
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (Direction side : Direction.values()) {
            for (BakedQuad quad : base.getQuads(side)) {
                builder.addCulledFace(side, retexture(quad, target));
            }
        }
        for (BakedQuad quad : base.getQuads(null)) {
            builder.addUnculledFace(retexture(quad, target));
        }
        return new SimpleModelWrapper(builder.build(), base.useAmbientOcclusion(), woodParticle(wood));
    }

    private BakedQuad retexture(BakedQuad quad, TextureAtlasSprite target) {
        return quad.materialInfo().sprite() == defaultSprite
                ? ForgeweaveQuads.retexture(quad, target, quad.materialInfo().tintIndex())
                : quad;
    }

    /** {@code quads} with every default-sprite quad drawn in {@code target}: the item form's half of the same swap (issue #77). */
    static List<BakedQuad> retexture(List<BakedQuad> quads, TextureAtlasSprite defaultSprite, TextureAtlasSprite target) {
        List<BakedQuad> remapped = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            remapped.add(quad.materialInfo().sprite() == defaultSprite
                    ? ForgeweaveQuads.retexture(quad, target, quad.materialInfo().tintIndex())
                    : quad);
        }
        return List.copyOf(remapped);
    }

    /** The sprite a block's own default-state model uses for particles -- our per-wood swap target. */
    static Material.Baked woodParticle(Block texture) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(texture.defaultBlockState());
    }
}
