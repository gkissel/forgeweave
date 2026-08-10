package dev.gkissel.forgeweave.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlockEntities;

/** Registers this mod's block entity renderers (#145: the seared tank family's fluid renderer). */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveBlockEntityRenderers {

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // One block entity type backs the tank, gauge and window (SearedTankBlockEntity's javadoc),
        // so one renderer registration covers all three.
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.SEARED_TANK.get(), SearedTankBlockEntityRenderer::new);
    }

    private ForgeweaveBlockEntityRenderers() {}
}
