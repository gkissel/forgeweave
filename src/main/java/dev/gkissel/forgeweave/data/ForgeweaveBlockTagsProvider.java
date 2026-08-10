package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;

import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Tool-tier gating for the cobalt + ardite nether ore blocks (docs/SCOPE.md M2 issue #104).
 * CONTEXT.md forbids a custom numeric harvest level, so upstream 1.12's above-diamond
 * {@code HarvestLevels.COBALT} ({@code BlockOre}, NOTICE.md) maps onto the vanilla tag ladder's
 * tightest tier below netherite: {@link BlockTags#NEEDS_DIAMOND_TOOL}. No other Forgeweave block
 * gates tool tier yet (see {@link ForgeweaveBlocks}'s javadoc), so this is the first block tags
 * provider.
 */
public class ForgeweaveBlockTagsProvider extends BlockTagsProvider {
    public ForgeweaveBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ForgeweaveBlocks.COBALT_ORE.get())
                .add(ForgeweaveBlocks.ARDITE_ORE.get());
        tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(ForgeweaveBlocks.COBALT_ORE.get())
                .add(ForgeweaveBlocks.ARDITE_ORE.get());
    }
}
