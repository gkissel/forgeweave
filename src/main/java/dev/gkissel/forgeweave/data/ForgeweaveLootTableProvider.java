package dev.gkissel.forgeweave.data;

import java.util.List;
import java.util.Set;

import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Registers {@link ForgeweaveBlockLootSubProvider} as the block-loot sub-provider and
 * {@link ForgeweaveEntityLootSubProvider} as the entity-loot one (#451).
 */
public class ForgeweaveLootTableProvider extends LootTableProvider {
    /** 26.3: loot tables are a datapack registry, so this is a registry bootstrap rather than a data provider. */
    public ForgeweaveLootTableProvider() {
        super(Set.<ResourceKey<LootTable>>of(),
                List.of(new SubProviderEntry(ForgeweaveBlockLootSubProvider::new, LootContextParamSets.BLOCK),
                        new SubProviderEntry(ForgeweaveEntityLootSubProvider::new, LootContextParamSets.ENTITY)));
    }
}
