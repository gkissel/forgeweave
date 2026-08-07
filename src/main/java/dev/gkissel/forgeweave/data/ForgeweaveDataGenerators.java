package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;

import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

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
        generator.addProvider(event.includeClient(), new ForgeweaveBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new ForgeweaveLanguageProvider(output));
        generator.addProvider(event.includeServer(), new ForgeweaveRecipeProvider(output, lookupProvider));
        generator.addProvider(event.includeServer(), new ForgeweaveLootTableProvider(output, lookupProvider));
    }

    private ForgeweaveDataGenerators() {}
}
