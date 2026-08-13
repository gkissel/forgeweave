package dev.gkissel.forgeweave.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;

import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * Tints the nine molten metal fluids with their upstream 1.12 color (docs/SCOPE.md M2 issue #92),
 * the same "one greyscale texture + per-instance tint" approach {@link ForgeweaveItemColors} already
 * uses for parts and tools -- upstream's own {@code FluidMolten} shares one still/flowing texture
 * pair across every molten metal and relies on {@code FluidColored}'s color field for the rest.
 */
@EventBusSubscriber(modid = dev.gkissel.forgeweave.Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveFluidClientExtensions {

    @SubscribeEvent
    static void registerFluidClientExtensions(RegisterClientExtensionsEvent event) {
        register(event, ForgeweaveFluids.IRON);
        register(event, ForgeweaveFluids.COPPER);
        register(event, ForgeweaveFluids.GOLD);
        register(event, ForgeweaveFluids.COBALT);
        register(event, ForgeweaveFluids.ARDITE);
        register(event, ForgeweaveFluids.MANYULLYN);
        register(event, ForgeweaveFluids.OBSIDIAN);
        register(event, ForgeweaveFluids.ROSE_GOLD);
        register(event, ForgeweaveFluids.NETHERITE_SCRAP);
        register(event, ForgeweaveFluids.NETHERITE);
        register(event, ForgeweaveFluids.STEEL);
        register(event, ForgeweaveFluids.CARBON);
        // #232 -- the knightslime alloy chain's three fluids (docs/SCOPE.md M3.2).
        register(event, ForgeweaveFluids.SLIME);
        register(event, ForgeweaveFluids.SEARED_STONE);
        register(event, ForgeweaveFluids.KNIGHTSLIME);
        // #233 -- pig iron, plus the two non-metal smeltery fluids, each carrying its own upstream
        // texture pair on the record (blood's water-like liquid, clay's stone texture; NOTICE.md).
        register(event, ForgeweaveFluids.PIG_IRON);
        register(event, ForgeweaveFluids.BLOOD);
        register(event, ForgeweaveFluids.MOLTEN_CLAY);
    }

    private static void register(RegisterClientExtensionsEvent event, ForgeweaveFluids.MoltenMetal metal) {
        // Material.color()/TextColor-style raw 0xRRGGBB needs the alpha channel forced opaque, same
        // fixup ForgeweaveItemColors#opaqueColor applies to material tints -- an ARGB tint with a
        // zero alpha renders the fluid fully transparent.
        int tint = FastColor.ARGB32.opaque(metal.color());
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return metal.stillTexture();
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return metal.flowingTexture();
            }

            @Override
            public int getTintColor() {
                return tint;
            }
        }, metal.fluidType().get());
    }

    private ForgeweaveFluidClientExtensions() {}
}
