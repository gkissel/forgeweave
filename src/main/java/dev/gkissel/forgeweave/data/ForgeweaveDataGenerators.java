package dev.gkissel.forgeweave.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;

import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import dev.gkissel.forgeweave.data.sprite.MaterialPartTextureProvider;

/**
 * Registers Forgeweave's datagen providers (docs/adr/0002: models, blockstates, loot tables,
 * recipes, and lang are generated, not hand-written; generated output is committed and checked for
 * freshness in CI).
 */
public final class ForgeweaveDataGenerators {
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new ForgeweaveItemModelProvider(output, existingFileHelper));
        // #280 -- greyscale texture pipeline proof slice: recolors greyscale part sprites through
        // per-material palettes into textures/staging/part/ (M9 groundwork, epic #30).
        generator.addProvider(event.includeClient(), new MaterialPartTextureProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new ForgeweaveBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new ForgeweaveLanguageProvider(output));
        generator.addProvider(event.includeServer(), new ForgeweaveRecipeProvider(output, lookupProvider));
        generator.addProvider(event.includeServer(), new ForgeweaveLootTableProvider(output, lookupProvider));
        // #104 -- the cobalt + ardite nether ore blocks' tool-tier tags (ForgeweaveBlockTagsProvider
        // javadoc); the first Forgeweave block tags, so ForgeweaveItemTagsProvider's block-tag future
        // now feeds off it instead of staying empty.
        ForgeweaveBlockTagsProvider blockTags = generator.addProvider(event.includeServer(),
                new ForgeweaveBlockTagsProvider(output, lookupProvider, existingFileHelper));
        // #103 -- puts the four metal materials with no vanilla item form into the c: convention tags
        // MeltingRecipe's tag-keyed rows expect (ForgeweaveItemTagsProvider javadoc).
        generator.addProvider(event.includeServer(), new ForgeweaveItemTagsProvider(output, lookupProvider,
                blockTags.contentsGetter(), existingFileHelper));
        // #110 -- the M2 advancement chain (docs/SCOPE.md M2 issue #110): build smeltery -> first
        // melt -> first cast -> first alloy -> first modifier.
        generator.addProvider(event.includeServer(),
                new AdvancementProvider(output, lookupProvider, existingFileHelper, List.of(new ForgeweaveAdvancementProvider())));
    }

    private ForgeweaveDataGenerators() {}
}
