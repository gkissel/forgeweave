package dev.gkissel.forgeweave.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlockEntities;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;

/**
 * Registers this mod's block entity renderers (#145: the seared tank family's fluid renderer; #182:
 * the casting table/basin contents and the faucet's pour stream) and, since #447, its one entity
 * renderer -- {@code EntityRenderersEvent.RegisterRenderers} carries both, so they share a listener.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveBlockEntityRenderers {

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // One block entity type backs the tank, gauge and window (SearedTankBlockEntity's javadoc),
        // so one renderer registration covers all three.
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.SEARED_TANK.get(), SearedTankBlockEntityRenderer::new);
        // #179: the molten pool inside a formed smeltery. One renderer per core tier because each
        // tier is its own block entity type (SmelteryCore#blockEntityType).
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.STANDARD_CORE.get(),
                SmelteryControllerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.NETHER_CORE.get(),
                SmelteryControllerBlockEntityRenderer::new);
        // #182: two instances of one renderer rather than two classes -- upstream's Table/Basin
        // subclasses differ only in the four numbers each passes up its constructor.
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.CASTING_TABLE.get(),
                context -> CastingBlockEntityRenderer.table());
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.CASTING_BASIN.get(),
                context -> CastingBlockEntityRenderer.basin());
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.FAUCET.get(),
                context -> new FaucetBlockEntityRenderer());
        // #441 (parity audit T9) -- the fluid standing in a channel and the streams out of it.
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.SEARED_CHANNEL.get(),
                context -> new SearedChannelBlockEntityRenderer());
        // #567 (parity audit T75's leftover half) -- whatever sits in a station's input slot(s),
        // lying (or standing) on its top. One registration each; the Tool Station and Tool Forge
        // share ToolStationBlockEntity's own block entity type, so one covers both blocks.
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.CRAFTING_STATION.get(),
                context -> new CraftingStationBlockEntityRenderer());
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.STENCIL_TABLE.get(),
                context -> new StencilTableBlockEntityRenderer());
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.PART_BUILDER.get(),
                context -> new PartBuilderBlockEntityRenderer());
        event.registerBlockEntityRenderer(ForgeweaveBlockEntities.TOOL_STATION.get(),
                context -> new ToolStationBlockEntityRenderer());
        // #447 -- a dropped tool is an item entity in every respect but dying, so it draws with
        // vanilla's item entity renderer (see entity.IndestructibleItemEntity).
        event.registerEntityRenderer(ForgeweaveEntities.INDESTRUCTIBLE_ITEM.get(), ItemEntityRenderer::new);
        // #448 -- the thrown shuriken, drawn as its own item model spinning flat (ShurikenRenderer).
        event.registerEntityRenderer(ForgeweaveEntities.SHURIKEN.get(), ShurikenRenderer::new);
        // #653 -- the fired material arrow, its item model heading the way it flies (ArrowRenderer).
        event.registerEntityRenderer(ForgeweaveEntities.ARROW.get(), ArrowRenderer::new);
        // #451 (parity audit T20) -- the blue slime, vanilla's slime drawn over upstream's greyscale
        // slime texture and tinted upstream's 0xff67f0f5 (BlueSlimeRenderer).
        event.registerEntityRenderer(ForgeweaveEntities.BLUE_SLIME.get(), BlueSlimeRenderer::new);
    }

    // #145's cutout render type moved to the block models themselves (ForgeweaveBlockStateProvider's
    // tankBlock: "render_type": "minecraft:cutout") -- NeoForge 1.21 declares chunk render type on
    // the model, and mixing that with the legacy ItemBlockRenderTypes.setRenderLayer Java map left
    // the block with an empty render-type set (rendered nothing at all, not even opaque geometry).

    private ForgeweaveBlockEntityRenderers() {}
}
