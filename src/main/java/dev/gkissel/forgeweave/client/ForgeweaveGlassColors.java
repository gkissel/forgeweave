package dev.gkissel.forgeweave.client;

import java.util.List;

import net.minecraft.util.ARGB;

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
 *
 * <p>26.1 moved the item half into the client item: {@code ForgeweaveBlockStateProvider} gives each
 * color's block item a {@code minecraft:constant} tint off the same roster.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, value = Dist.CLIENT)
public final class ForgeweaveGlassColors {

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            // ARGB.opaque: a bare 0xRRGGBB tint renders fully transparent otherwise, same fixup
            // ForgeweaveItemColors#opaqueColor and ForgeweaveFluidClientExtensions apply.
            int tint = ARGB.opaque(color.tint());
            event.register(List.of(state -> tint), color.block().get());
        }
    }

    private ForgeweaveGlassColors() {}
}
