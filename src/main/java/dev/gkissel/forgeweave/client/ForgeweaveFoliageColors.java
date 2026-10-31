package dev.gkissel.forgeweave.client;

import java.util.List;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.FoliageType;

/**
 * Paints every slimy grass, leaf and plant its foliage colour (issue #449, parity audit T18) --
 * upstream 1.12's {@code WorldClientProxy}, which registers exactly these handlers around
 * {@link SlimeColorizer} (NOTICE.md). In the world each block samples the colour map at its own
 * position, giving an island's surface upstream's mottled shading; anywhere without a position the
 * flat {@link FoliageType#color()} stands in, which is what upstream's {@code getColorStatic} is for.
 *
 * <p>26.1 moved the inventory half into the client item: {@code ForgeweaveBlockStateProvider} gives
 * each of these block items a {@code minecraft:constant} tint of the flat colour, off the same
 * {@code ForgeweaveBlocks} rosters walked here, so a colour added there cannot render white.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, value = Dist.CLIENT)
public final class ForgeweaveFoliageColors {

    @SubscribeEvent
    static void registerReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Forgeweave.MODID, "slime_colormaps"),
                (ResourceManagerReloadListener) SlimeColorizer::reload);
    }

    @SubscribeEvent
    static void registerBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        for (ForgeweaveBlocks.SlimeSoil soil : ForgeweaveBlocks.slimeSoils()) {
            register(event, soil.foliage(), soil.grass().get());
        }
        for (ForgeweaveBlocks.SlimePlants plants : ForgeweaveBlocks.slimePlants()) {
            register(event, plants.foliage(), plants.leaves().get());
            register(event, plants.foliage(), plants.tallGrass().get());
            register(event, plants.foliage(), plants.fern().get());
            plants.vines().forEach(vine -> register(event, plants.foliage(), vine.get())); // #488 (T57)
        }
    }

    private static void register(RegisterColorHandlersEvent.BlockTintSources event, FoliageType foliage, Block block) {
        BlockTintSource color = new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return ARGB.opaque(foliage.color());
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return ARGB.opaque(SlimeColorizer.colorAt(foliage, pos));
            }
        };
        event.register(List.of(color), block);
    }

    private ForgeweaveFoliageColors() {}
}
