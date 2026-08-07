package dev.gkissel.forgeweave.client.model;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import dev.gkissel.forgeweave.Forgeweave;

/** Registers Forgeweave's custom block model geometry loaders (issue #43). */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveModelLoaders {
    private ForgeweaveModelLoaders() {}

    @SubscribeEvent
    static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(RetexturedTableGeometryLoader.ID, RetexturedTableGeometryLoader.INSTANCE);
    }
}
