package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.FluidTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * Convention tags for Forgeweave's smeltery fluids (issue #992, M8-8), the fluid-side counterpart of
 * {@link ForgeweaveItemTagsProvider}. Every fluid in {@link ForgeweaveFluids#all()} joins
 * {@code c:<its own registry path>}, so a molten Forgeweave metal is reachable as
 * {@code c:molten_cobalt} rather than only as {@code forgeweave:molten_cobalt}. Both the still and
 * the flowing fluid go in, since a consumer holding a {@code FluidStack} may have either.
 *
 * <p><b>Why that convention, surveyed rather than guessed (the issue's own requirement).</b>
 * NeoForge 21.1's {@code Tags.Fluids} has no molten-metal entry at all: it ships water, lava, milk,
 * gaseous, honey, experience, potion and a handful of food fluids, and stops there. So there is no
 * NeoForge-owned {@code c:molten_metals} to join, and minting one here would be squatting on the
 * shared namespace for a tag nothing else reads. The only established spelling for a molten metal
 * fluid is per-fluid: Tinkers' Construct on 1.20.1 generates {@code forge:molten_<material>} for
 * each of its own, one tag file per fluid, and {@code forge:} is exactly the namespace NeoForge
 * renamed to {@code c:} for 1.21 -- so {@code c:molten_<material>} is that same convention carried
 * forward, and it is what this provider emits. Mekanism, Create and Immersive Engineering on 1.21.1
 * turn out not to settle the question either way: none of the three has molten metals as fluids at
 * all (their fluid rosters are chemicals, creosote, honey and the like), so none of them tags one.
 * If the ecosystem later agrees on a parent tag, adding it is one line here.
 */
public class ForgeweaveFluidTagsProvider extends FluidTagsProvider {

    public ForgeweaveFluidTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        for (ForgeweaveFluids.MoltenMetal fluid : ForgeweaveFluids.all()) {
            tag(conventionTag(fluid.name())).add(fluid.still().get(), fluid.flowing().get());
        }
    }

    private static TagKey<Fluid> conventionTag(String path) {
        return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
