package dev.gkissel.forgeweave.client.model;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The blockstate side of a table station (issue #43): a plain blockstate variant ({@code model},
 * {@code x}, {@code y}, {@code uvlock}) tagged {@code "type": "forgeweave:retextured_table"}, baked
 * normally and handed to {@link RetexturedTableBakedModel} together with the sprite the model's
 * {@code "texture"} variable points at -- the "default = oak" appearance every other wood replaces.
 *
 * <p>1.21.1 hung this on a model-JSON geometry loader; 26.1 baked models carry no per-placement
 * behaviour any more, so the hook moved to the blockstate, and the station JSONs became plain models.
 */
public final class RetexturedTableGeometry {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Forgeweave.MODID, "retextured_table");
    private static final String TEXTURE_VARIABLE = "texture";

    private RetexturedTableGeometry() {}

    public record Unbaked(Variant variant) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = Variant.MAP_CODEC.xmap(Unbaked::new, Unbaked::variant);

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            ResolvedModel model = baker.getModel(variant.modelLocation());
            return new RetexturedTableBakedModel(variant.bake(baker),
                    baker.materials().resolveSlot(model.getTopTextureSlots(), TEXTURE_VARIABLE, model).sprite());
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            variant.resolveDependencies(resolver);
        }

        @Override
        public MapCodec<Unbaked> codec() {
            return MAP_CODEC;
        }
    }
}
