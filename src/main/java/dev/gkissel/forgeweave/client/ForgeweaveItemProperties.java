package dev.gkissel.forgeweave.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * The {@code forgeweave:broken} item property every assembled tool's model branches on (issue #284):
 * 1 once the tool's {@code BROKEN} component is set, 0 otherwise. {@code
 * ForgeweaveItemModelProvider} writes the matching {@code overrides} entry, which points at the
 * tool's {@code <tool>_broken} model -- the same layers, with the one layer {@code
 * ToolArt#brokenLayer} names swapped for its broken art.
 *
 * <p>Upstream 1.12 does the swap inside its own tool model instead ({@code BakedToolModel#
 * getOverrides} picks per-part broken quads on {@code ToolHelper#isBroken}), which vanilla's model
 * format cannot express; this is the mechanism upstream itself adopted once it was on a modern
 * Minecraft ({@code TinkerItemProperties#registerBrokenProperty} in the 1.20 clone).
 *
 * <p>Registered off {@link ToolAssemblyRecipes#ENTRIES} rather than a hand list, for the same reason
 * {@link ForgeweaveItemColors#tintedToolItems} is: a tool cannot be added to the station and left
 * out here.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveItemProperties {

    /** The property id; {@code ForgeweaveItemModelProvider.BROKEN_PREDICATE} is its other half. */
    private static final ResourceLocation BROKEN =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "broken");

    @SubscribeEvent
    static void registerItemProperties(FMLClientSetupEvent event) {
        // ItemProperties' map is not thread-safe; enqueueWork is the documented way onto the main thread.
        event.enqueueWork(() -> {
            for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
                ItemProperties.register(entry.tool().get(),
                        BROKEN, (stack, level, holder, seed) -> ToolItem.isBroken(stack) ? 1 : 0);
            }
        });
    }

    private ForgeweaveItemProperties() {}
}
