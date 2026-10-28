package dev.gkissel.forgeweave.client.model;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Registers Forgeweave's custom model types: the {@code forgeweave:tool}, {@code forgeweave:part}
 * and {@code forgeweave:retextured_table} item models, and the {@code forgeweave:retextured_table}
 * blockstate model (issue #43). Both events also fire during client datagen, which is what lets
 * the model provider serialize these types.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, value = Dist.CLIENT)
public final class ForgeweaveModelLoaders {
    private ForgeweaveModelLoaders() {}

    @SubscribeEvent
    static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(ToolItemModel.ID, ToolItemModel.Unbaked.MAP_CODEC);
        event.register(PartItemModel.ID, PartItemModel.Unbaked.MAP_CODEC);
        event.register(RetexturedTableItemModel.Unbaked.ID, RetexturedTableItemModel.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(RetexturedTableGeometry.ID, RetexturedTableGeometry.Unbaked.MAP_CODEC);
    }
}
