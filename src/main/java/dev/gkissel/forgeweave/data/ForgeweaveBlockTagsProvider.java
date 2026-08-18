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
 * CONTEXT.md forbids a custom numeric harvest level, so upstream 1.12's
 * {@code setHarvestLevel("pickaxe", HarvestLevels.COBALT)} ({@code shared/block/BlockOre.java:30},
 * NOTICE.md) maps onto the vanilla tag ladder's top rung, netherite -- issue #433 established that
 * {@code HarvestLevels.COBALT} (4) is the netherite tier, not "above diamond with nowhere to go".
 *
 * <p>Vanilla has no {@code needs_netherite_tool} tag because no vanilla block needs one, so the
 * gate is spelled as its two halves: {@link BlockTags#NEEDS_DIAMOND_TOOL} denies everything below
 * diamond, and listing the ores in {@link BlockTags#INCORRECT_FOR_DIAMOND_TOOL} denies diamond
 * itself, leaving netherite-tier tools alone. Those are exactly obsidian, cobalt, ardite and
 * manyullyn heads plus a vanilla netherite pickaxe -- upstream's own level-4 set, with obsidian as
 * the bootstrap (a diamond pickaxe mines obsidian blocks, which the smeltery casts into parts).
 * The diamond modifier caps at {@code HarvestLevels.OBSIDIAN}, so it cannot shortcut the gate
 * either ({@code ForgeweaveModifiers#DIAMOND_TIER_CAP}).
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
        tag(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)
                .add(ForgeweaveBlocks.COBALT_ORE.get())
                .add(ForgeweaveBlocks.ARDITE_ORE.get());

        // #299 -- upstream LumberAxe#effective_materials (GOURD, CACTUS) at bonus axe speed. Vanilla's
        // own mineable/axe already carries pumpkin and melon (Material.GOURD's two blocks); cactus is
        // the one member missing, so this adds only it rather than replacing the vanilla tag.
        tag(BlockTags.MINEABLE_WITH_AXE).add(Blocks.CACTUS);

        // #339 -- upstream BlockSoil's setHarvestLevel("shovel", -1) applies to every one of its
        // types, so grout and both slimy muds take mineable/shovel (shovel = the correct, faster
        // tool). Level -1 means no minimum tier, hence no needs_*_tool tag alongside it.
        tag(BlockTags.MINEABLE_WITH_SHOVEL)
                .add(ForgeweaveBlocks.GROUT.get())
                .add(ForgeweaveBlocks.SLIMY_MUD_GREEN.get())
                .add(ForgeweaveBlocks.SLIMY_MUD_MAGMA.get())
                // #429 -- the two remaining BlockSoil states, same setHarvestLevel("shovel", -1).
                .add(ForgeweaveBlocks.GRAVEYARD_SOIL.get())
                .add(ForgeweaveBlocks.CONSECRATED_SOIL.get());

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

        // T79 (parity audit 2026-08-18, issue #510) -- the block-side half of
        // ForgeweaveItemTagsProvider's c:glass_blocks/c:dyed additions (TinkerOredict registerCommon():
        // blockClearGlass/blockClearStainedGlass -> "blockGlass" (+ per-color "blockGlass<Color>")).
        var glassBlocks = tag(cTag("glass_blocks")).add(ForgeweaveBlocks.CLEAR_GLASS.get());
        for (var color : ForgeweaveBlocks.clearStainedGlassColors()) {
            glassBlocks.add(color.block().get());
            tag(cTag("dyed/" + color.dye().getSerializedName())).add(color.block().get());
        }
    }

    private static TagKey<Block> cTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
