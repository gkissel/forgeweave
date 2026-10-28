package dev.gkissel.forgeweave.data;

import java.util.List;

import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import dev.gkissel.forgeweave.data.sprite.MaterialPartTextureProvider;

/**
 * Registers Forgeweave's datagen providers (docs/adr/0002: models, blockstates, loot tables,
 * recipes, and lang are generated, not hand-written; generated output is committed and checked for
 * freshness in CI).
 *
 * <p>26.1 splits datagen into client and server events. The build runs one {@code clientData} run,
 * which fires the client event with every provider in it, so server data registers here too.
 */
public final class ForgeweaveDataGenerators {
    public static void gatherData(GatherDataEvent.Client event) {
        // Every blockstate, block and item model, and client item: one 26.1 ModelProvider, which
        // runs ForgeweaveItemModelProvider's half itself.
        event.createProvider(ForgeweaveBlockStateProvider::new);
        // #280 -- greyscale texture pipeline proof slice: recolors greyscale part sprites through
        // per-material palettes into textures/staging/part/ (M9 groundwork, epic #30).
        event.createProvider(output -> new MaterialPartTextureProvider(output, event.getResourceManager(PackType.CLIENT_RESOURCES)));
        event.createProvider(ForgeweaveLanguageProvider::new);
        event.createProvider(ForgeweaveRecipeProvider.Runner::new);
        // #915 -- the Draconic Evolution fusion recipes (docs/SCOPE.md M8). A plain DataProvider
        // rather than a RecipeProvider: both layers' serializers live behind the mod_loaded gate, so
        // there is no Recipe object runData could build. See the provider's own javadoc.
        event.createProvider(ForgeweaveDraconicRecipeProvider::new);
        // #997 -- the Occultism crushing, miner and ritual rows (docs/SCOPE.md M8). Same shape and
        // same reason: all three serializers are Occultism's, behind the same mod_loaded gate.
        event.createProvider(ForgeweaveOccultismRecipeProvider::new);
        // #993 -- the one Mekanism nucleosynthesizing recipe (docs/SCOPE.md M8, D-M8-13). Same shape
        // and the same reason: Mekanism's serializer is compileOnly, so there is no Recipe object here
        // either.
        event.createProvider(ForgeweaveMekanismRecipeProvider::new);
        event.createProvider(ForgeweaveLootTableProvider::new);
        // #104 -- the cobalt + ardite nether ore blocks' tool-tier tags (ForgeweaveBlockTagsProvider
        // javadoc).
        event.createProvider(ForgeweaveBlockTagsProvider::new);
        // #103 -- puts the four metal materials with no vanilla item form into the c: convention tags
        // MeltingRecipe's tag-keyed rows expect (ForgeweaveItemTagsProvider javadoc). It copies no
        // block tags, so it no longer waits on the block tags provider.
        event.createProvider((output, lookupProvider) ->
                new ForgeweaveItemTagsProvider(output, lookupProvider, event.getResourceManager(PackType.SERVER_DATA)));
        // #680 -- the five protection damage-type tags the ARMOR traits and #681's modifiers filter on.
        event.createProvider(ForgeweaveDamageTypeTagsProvider::new);
        event.createProvider(ForgeweaveFluidTagsProvider::new); // #992
        // #110 -- the M2 advancement chain (docs/SCOPE.md M2 issue #110): build smeltery -> first
        // melt -> first cast -> first alloy -> first modifier.
        event.createProvider((output, lookupProvider) ->
                new AdvancementProvider(output, lookupProvider, List.of(new ForgeweaveAdvancementProvider())));
    }

    private ForgeweaveDataGenerators() {}
}
