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

    // #145's cutout render type moved to the block models themselves (ForgeweaveBlockStateProvider's
    // tankBlock: "render_type": "minecraft:cutout") -- NeoForge 1.21 declares chunk render type on
    // the model, and mixing that with the legacy ItemBlockRenderTypes.setRenderLayer Java map left
    // the block with an empty render-type set (rendered nothing at all, not even opaque geometry).

    private ForgeweaveBlockEntityRenderers() {}
}
