package dev.gkissel.forgeweave.client;

import net.minecraft.util.FastColor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Tints the 16 clear stained glass colors with their upstream 1.12 color (issue #275), the same
 * "one texture + per-instance tint" approach {@link ForgeweaveItemColors} and {@link
 * ForgeweaveFluidClientExtensions} already use elsewhere -- upstream's own {@code
 * BlockClearStainedGlass} paints one shared greyscale texture with {@code
 * EnumGlassColor#getColor()} and relies on {@code CommonsClientProxy}'s block/item color handlers
 * for the rest (NOTICE.md). {@code ForgeweaveBlocks#clearStainedGlassColors} is walked here rather
 * than a hand list, the same anti-drift shape {@code ForgeweaveFluidClientExtensions} uses for the
 * molten metals.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveGlassColors {

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            // FastColor.ARGB32.opaque: a bare 0xRRGGBB tint renders fully transparent otherwise, same
            // fixup ForgeweaveItemColors#opaqueColor and ForgeweaveFluidClientExtensions apply.
            int tint = FastColor.ARGB32.opaque(color.tint());
            event.register((state, level, pos, tintIndex) -> tint, color.block().get());
        }
    }

    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            int tint = FastColor.ARGB32.opaque(color.tint());
            event.register((stack, tintIndex) -> tint, color.block().get());
        }
    }

    private ForgeweaveGlassColors() {}
}
