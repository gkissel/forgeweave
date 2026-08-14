package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

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
        // #152: upstream 1.12 gives the Tool Forge -- and only it, of the tables -- a
        // setHarvestLevel("pickaxe", 0), so a pickaxe is its correct tool. Level 0 means any
        // pickaxe, i.e. no needs_*_tool tag alongside it.
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ForgeweaveBlocks.TOOL_FORGE.get())
                .add(ForgeweaveBlocks.COBALT_ORE.get())
                .add(ForgeweaveBlocks.ARDITE_ORE.get());
        tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(ForgeweaveBlocks.COBALT_ORE.get())
                .add(ForgeweaveBlocks.ARDITE_ORE.get());

        // #299 -- upstream LumberAxe#effective_materials (GOURD, CACTUS) at bonus axe speed. Vanilla's
        // own mineable/axe already carries pumpkin and melon (Material.GOURD's two blocks); cactus is
        // the one member missing, so this adds only it rather than replacing the vanilla tag.
        tag(BlockTags.MINEABLE_WITH_AXE).add(Blocks.CACTUS);

        // #206 -- the block-side counterpart of ForgeweaveItemTagsProvider's c:storage_blocks/*
        // additions (same reasoning: NeoForge's own storage_blocks tag only unions the metals it
        // knows about).
        tag(cTag("storage_blocks/cobalt")).add(ForgeweaveBlocks.COBALT_BLOCK.get());
        tag(cTag("storage_blocks/ardite")).add(ForgeweaveBlocks.ARDITE_BLOCK.get());
        tag(cTag("storage_blocks/manyullyn")).add(ForgeweaveBlocks.MANYULLYN_BLOCK.get());
        tag(cTag("storage_blocks/rose_gold")).add(ForgeweaveBlocks.ROSE_GOLD_BLOCK.get());
        tag(cTag("storage_blocks/steel")).add(ForgeweaveBlocks.STEEL_BLOCK.get());
        tag(cTag("storage_blocks/knightslime")).add(ForgeweaveBlocks.KNIGHTSLIME_BLOCK.get()); // #232
        // #233 -- pig iron's storage block, block side.
        tag(cTag("storage_blocks/pig_iron")).add(ForgeweaveBlocks.PIG_IRON_BLOCK.get());
        // #235 -- amethyst bronze's storage block, block side.
        tag(cTag("storage_blocks/amethyst_bronze")).add(ForgeweaveBlocks.AMETHYST_BRONZE_BLOCK.get());
        tag(cTag("storage_blocks")).addTag(cTag("storage_blocks/cobalt")).addTag(cTag("storage_blocks/ardite"))
                .addTag(cTag("storage_blocks/manyullyn")).addTag(cTag("storage_blocks/rose_gold"))
                .addTag(cTag("storage_blocks/steel")).addTag(cTag("storage_blocks/knightslime"))
                .addTag(cTag("storage_blocks/pig_iron")).addTag(cTag("storage_blocks/amethyst_bronze"));
    }

    private static TagKey<Block> cTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
