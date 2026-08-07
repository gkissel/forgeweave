package dev.gkissel.forgeweave.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

import dev.gkissel.forgeweave.Forgeweave;

/** Reads a table station block model (issue #43); see {@link RetexturedTableGeometry}. */
public final class RetexturedTableGeometryLoader implements IGeometryLoader<RetexturedTableGeometry> {
    public static final RetexturedTableGeometryLoader INSTANCE = new RetexturedTableGeometryLoader();
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "retextured_table");

    private RetexturedTableGeometryLoader() {}

    @Override
    public RetexturedTableGeometry read(JsonObject jsonObject, JsonDeserializationContext context) throws JsonParseException {
        jsonObject.remove("loader");
        BlockModel base = context.deserialize(jsonObject, BlockModel.class);
        return new RetexturedTableGeometry(base);
    }
}
