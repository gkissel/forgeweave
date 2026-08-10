package dev.gkissel.forgeweave.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlockEntities;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/** Registers this mod's block entity renderers (#145: the seared tank family's fluid renderer). */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveBlockEntityRenderers {

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // One block entity type backs the tank, gauge and window (SearedTankBlockEntity's javadoc),
        // so one renderer registration covers all three.
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.SEARED_TANK.get(), SearedTankBlockEntityRenderer::new);
    }

    /**
     * The tank/gauge/window derived textures already carry real alpha-cutout windows (verified by
     * pixel inspection: {@code seared_tank_side.png} et al. have both 0 and 255 alpha values), but
     * every block defaults to {@code RenderType.solid()}, which ignores alpha and draws those
     * fully-transparent texels as opaque black -- an opaque wall sitting exactly where the window
     * (and {@link SearedTankBlockEntityRenderer}'s inset fluid quads behind it) should be visible.
     * This is the other half of #145: without it the BER draws correctly but nothing can be seen
     * through the block's own faces.
     */
    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ForgeweaveBlocks.SEARED_TANK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeweaveBlocks.SEARED_GAUGE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ForgeweaveBlocks.SEARED_WINDOW.get(), RenderType.cutout());
        });
    }

    private ForgeweaveBlockEntityRenderers() {}
}
