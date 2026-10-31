package dev.gkissel.forgeweave.client;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.ARGB;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * Tints the nine molten metal fluids with their upstream 1.12 color (docs/SCOPE.md M2 issue #92),
 * the same "one greyscale texture + per-instance tint" approach {@link ForgeweaveItemColors} already
 * uses for parts and tools -- upstream's own {@code FluidMolten} shares one still/flowing texture
 * pair across every molten metal and relies on {@code FluidColored}'s color field for the rest.
 */
@EventBusSubscriber(modid = dev.gkissel.forgeweave.Forgeweave.MODID, value = Dist.CLIENT)
public final class ForgeweaveFluidClientExtensions {

    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        // #286: walks ForgeweaveFluids#all rather than the hand list this used to be -- exactly the
        // drift issue #256 fixed for part tints. Each record already carries its own texture pair
        // (blood's water-like liquid, clay's and seared stone's stone pair; NOTICE.md), and a fluid
        // missing from here renders untinted -- since #286, its bucket with it. 26.1 moved fluid
        // textures and tint from IClientFluidTypeExtensions to a FluidModel per fluid.
        for (ForgeweaveFluids.MoltenMetal fluid : ForgeweaveFluids.all()) {
            register(event, fluid);
        }
    }

    private static void register(RegisterFluidModelsEvent event, ForgeweaveFluids.MoltenMetal metal) {
        // Material.color()/TextColor-style raw 0xRRGGBB needs the alpha channel forced opaque, same
        // fixup ForgeweaveItemColors#opaqueColor applies to material tints -- an ARGB tint with a
        // zero alpha renders the fluid fully transparent.
        int tint = ARGB.opaque(metal.color());
        event.register(new FluidModel.Unbaked(new Material(metal.stillTexture()), new Material(metal.flowingTexture()),
                null, FluidTintSources.constant(tint)), metal.still(), metal.flowing());
    }

    private ForgeweaveFluidClientExtensions() {}
}
