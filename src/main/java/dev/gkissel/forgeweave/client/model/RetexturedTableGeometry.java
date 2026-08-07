package dev.gkissel.forgeweave.client.model;

import java.util.function.Function;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.model.ElementsModel;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/**
 * Unbaked geometry for a table station block model (issue #43): wraps a normal {@link BlockModel}
 * (its {@code elements}, baked once against whatever the JSON's {@code "texture"} variable points
 * at -- the "default = oak" appearance) and hands the baked result to
 * {@link RetexturedTableBakedModel}, which substitutes a different wood's sprite at render time
 * based on the placed block entity's stored texture ({@code WoodTexturedBlockEntity}). The geometry
 * loader/baking split here is the standard NeoForge approach the issue calls for: Mantle's real
 * retexturing baked-model code (which this would otherwise port) isn't in the 1.20.1 reference
 * clone.
 */
public class RetexturedTableGeometry implements IUnbakedGeometry<RetexturedTableGeometry> {
    private static final String TEXTURE_VARIABLE = "texture";

    private final BlockModel base;

    public RetexturedTableGeometry(BlockModel base) {
        this.base = base;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        BakedModel bakedBase = new ElementsModel(base.getElements()).bake(context, baker, spriteGetter, modelState, overrides);
        TextureAtlasSprite defaultSprite = spriteGetter.apply(context.getMaterial(TEXTURE_VARIABLE));
        return new RetexturedTableBakedModel(bakedBase, defaultSprite);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        base.resolveParents(modelGetter);
    }
}
