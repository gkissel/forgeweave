package dev.gkissel.forgeweave.client;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.block.Block;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.FoliageType;

/**
 * Paints every slimy grass, leaf and plant its foliage colour (issue #449, parity audit T18) --
 * upstream 1.12's {@code WorldClientProxy}, which registers exactly these handlers around
 * {@link SlimeColorizer} (NOTICE.md). In the world each block samples the colour map at its own
 * position, giving an island's surface upstream's mottled shading; in an inventory slot, where there
 * is no position, the flat {@link FoliageType#color()} stands in, which is what upstream's
 * {@code getColorStatic} is for.
 *
 * <p>Same "one greyscale texture + per-instance tint" shape {@link ForgeweaveGlassColors} uses for
 * the clear stained glass, and walked off {@code ForgeweaveBlocks}' own rosters for the same
 * anti-drift reason: a colour added there cannot render white here.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveFoliageColors {

    @SubscribeEvent
    static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) SlimeColorizer::reload);
    }

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        for (ForgeweaveBlocks.SlimeSoil soil : ForgeweaveBlocks.slimeSoils()) {
            register(event, soil.foliage(), soil.grass().get());
        }
        for (ForgeweaveBlocks.SlimePlants plants : ForgeweaveBlocks.slimePlants()) {
            register(event, plants.foliage(), plants.leaves().get());
            register(event, plants.foliage(), plants.tallGrass().get());
            register(event, plants.foliage(), plants.fern().get());
        }
    }

    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (ForgeweaveBlocks.SlimeSoil soil : ForgeweaveBlocks.slimeSoils()) {
            register(event, soil.foliage(), soil.grass().get());
        }
        for (ForgeweaveBlocks.SlimePlants plants : ForgeweaveBlocks.slimePlants()) {
            register(event, plants.foliage(), plants.leaves().get());
            register(event, plants.foliage(), plants.tallGrass().get());
            register(event, plants.foliage(), plants.fern().get());
        }
    }

    private static void register(RegisterColorHandlersEvent.Block event, FoliageType foliage, Block block) {
        BlockColor color = (state, level, pos, tintIndex) -> pos == null
                ? FastColor.ARGB32.opaque(foliage.color())
                : FastColor.ARGB32.opaque(SlimeColorizer.colorAt(foliage, pos));
        event.register(color, block);
    }

    private static void register(RegisterColorHandlersEvent.Item event, FoliageType foliage, Block block) {
        ItemColor color = (stack, tintIndex) -> FastColor.ARGB32.opaque(foliage.color());
        event.register(color, block);
    }

    private ForgeweaveFoliageColors() {}
}
