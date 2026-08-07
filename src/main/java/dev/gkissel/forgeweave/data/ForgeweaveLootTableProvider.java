package dev.gkissel.forgeweave.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Registers {@link ForgeweaveBlockLootSubProvider} as the block-loot sub-provider. */
public class ForgeweaveLootTableProvider extends LootTableProvider {
    public ForgeweaveLootTableProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Set.<ResourceKey<LootTable>>of(),
                List.of(new SubProviderEntry(ForgeweaveBlockLootSubProvider::new, LootContextParamSets.BLOCK)),
                registries);
    }
}
