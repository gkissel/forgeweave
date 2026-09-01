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

import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.tool.VeinmineKey;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

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
                .add(ForgeweaveBlocks.SLIMY_MUD_BLUE.get())
                // #429 -- the two remaining BlockSoil states, same setHarvestLevel("shovel", -1).
                .add(ForgeweaveBlocks.GRAVEYARD_SOIL.get())
                .add(ForgeweaveBlocks.CONSECRATED_SOIL.get());

        // #449 (parity audit T18) -- the slime island's blocks. Upstream sets no harvest level on any
        // of them, so none takes a needs_*_tool tag; what they do take is the "correct, faster tool"
        // half their vanilla counterparts have -- shovel for the soils, hoe for the leaves, sword for
        // the plants. Slime dirt and grass also join minecraft:dirt, which is modern Minecraft's form
        // of upstream's canSustainPlant accepting EnumPlantType.Plains (vanilla saplings and flowers
        // grow on slime soil upstream too). Vanilla grass does not spread onto them: GrassBlock only
        // converts minecraft:dirt the block, not the tag.
        var slimeShovel = tag(BlockTags.MINEABLE_WITH_SHOVEL);
        var slimeDirt = tag(BlockTags.DIRT);
        for (ForgeweaveBlocks.SlimeSoil soil : ForgeweaveBlocks.slimeSoils()) {
            slimeShovel.add(soil.dirt().get()).add(soil.grass().get());
            slimeDirt.add(soil.dirt().get()).add(soil.grass().get());
        }
        var slimeLeaves = tag(BlockTags.LEAVES);
        var slimeHoe = tag(BlockTags.MINEABLE_WITH_HOE);
        var slimeSword = tag(BlockTags.SWORD_EFFICIENT);
        var slimeSaplings = tag(BlockTags.SAPLINGS);
        var slimeClimbable = tag(BlockTags.CLIMBABLE);
        for (ForgeweaveBlocks.SlimePlants plants : ForgeweaveBlocks.slimePlants()) {
            slimeLeaves.add(plants.leaves().get());
            slimeHoe.add(plants.leaves().get());
            slimeSword.add(plants.tallGrass().get()).add(plants.fern().get());
            // #488 (parity audit T57): the sapling and the vines. Vanilla puts vines in
            // sword_efficient and its saplings in minecraft:saplings (what bone meal and the
            // sapling-growth advancement look at), and neither carries a harvest-level tag upstream.
            slimeSaplings.add(plants.sapling().get());
            plants.vines().forEach(vine -> {
                slimeSword.add(vine.get());
                slimeClimbable.add(vine.get());
            });
        }

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
        // #843 -- queen's slime and hepatizon storage blocks, block side (closes #180).
        tag(cTag("storage_blocks/queens_slime")).add(ForgeweaveBlocks.QUEENS_SLIME_BLOCK.get());
        tag(cTag("storage_blocks/hepatizon")).add(ForgeweaveBlocks.HEPATIZON_BLOCK.get());
        tag(cTag("storage_blocks")).addTag(cTag("storage_blocks/cobalt")).addTag(cTag("storage_blocks/ardite"))
                .addTag(cTag("storage_blocks/manyullyn")).addTag(cTag("storage_blocks/rose_gold"))
                .addTag(cTag("storage_blocks/steel")).addTag(cTag("storage_blocks/knightslime"))
                .addTag(cTag("storage_blocks/pig_iron")).addTag(cTag("storage_blocks/amethyst_bronze"))
                .addTag(cTag("storage_blocks/queens_slime")).addTag(cTag("storage_blocks/hepatizon"));

        // #839/#877 -- Track B's ore family (M6 epic #824): tool-tier gating per material. #877 (the
        // JC10 reversal) mints three rungs above netherite, so an ore at one of those rungs is denied
        // to every tool at or below it -- the same "add directly to every lower rung's own tag" style
        // already used for the netherite boundary (needs_diamond_tool + incorrect_for_diamond_tool),
        // just carried one/two/three rungs further for hardcinder/warspar/resonite. The diamond-tier
        // ore (fulmenite) needs an iron pickaxe or better, matching vanilla diamond_ore's own
        // needs_iron_tool; the stone-tier ore (cinderstone) takes no needs_*_tool tag at all, mineable
        // with any pickaxe, matching vanilla coal/copper ore.
        var trackBPickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        var trackBNeedsDiamond = tag(BlockTags.NEEDS_DIAMOND_TOOL);
        var trackBIncorrectForDiamond = tag(BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
        var trackBIncorrectForNetherite = tag(BlockTags.INCORRECT_FOR_NETHERITE_TOOL);
        var trackBIncorrectForHardcinder = tag(TrackBOre.INCORRECT_FOR_HARDCINDER_TOOL);
        var trackBIncorrectForWarspar = tag(TrackBOre.INCORRECT_FOR_WARSPAR_TOOL);
        var trackBNeedsIron = tag(BlockTags.NEEDS_IRON_TOOL);
        var trackBStorageBlocks = tag(cTag("storage_blocks"));
        for (TrackBOre ore : TrackBOre.ALL) {
            Block oreBlock = ForgeweaveBlocks.trackBOre(ore.id()).get();
            trackBPickaxe.add(oreBlock);
            switch (ore.tier()) {
                case RESONITE -> {
                    trackBNeedsDiamond.add(oreBlock);
                    trackBIncorrectForDiamond.add(oreBlock);
                    trackBIncorrectForNetherite.add(oreBlock);
                    trackBIncorrectForHardcinder.add(oreBlock);
                    trackBIncorrectForWarspar.add(oreBlock);
                }
                case WARSPAR -> {
                    trackBNeedsDiamond.add(oreBlock);
                    trackBIncorrectForDiamond.add(oreBlock);
                    trackBIncorrectForNetherite.add(oreBlock);
                    trackBIncorrectForHardcinder.add(oreBlock);
                }
                case HARDCINDER -> {
                    trackBNeedsDiamond.add(oreBlock);
                    trackBIncorrectForDiamond.add(oreBlock);
                    trackBIncorrectForNetherite.add(oreBlock);
                }
                case NETHERITE -> {
                    trackBNeedsDiamond.add(oreBlock);
                    trackBIncorrectForDiamond.add(oreBlock);
                }
                case DIAMOND -> trackBNeedsIron.add(oreBlock);
                case STONE -> { /* mineable with any pickaxe, no minimum-tier tag */ }
            }
            tag(cTag("storage_blocks/" + ore.id())).add(ForgeweaveBlocks.trackBStorageBlock(ore.id()).get());
            tag(cTag("storage_blocks/raw_" + ore.id())).add(ForgeweaveBlocks.trackBRawBlock(ore.id()).get());
            trackBStorageBlocks.addTag(cTag("storage_blocks/" + ore.id())).addTag(cTag("storage_blocks/raw_" + ore.id()));
        }

        // #840 -- Track B's 18 alloy tool materials, block side: alloy-only, so just the one storage
        // block's c:storage_blocks/<id> membership, matching pig_iron/hepatizon's own block-side tags.
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            tag(cTag("storage_blocks/" + alloy.id())).add(ForgeweaveBlocks.trackBAlloyBlock(alloy.id()).get());
            trackBStorageBlocks.addTag(cTag("storage_blocks/" + alloy.id()));
        }

        // T79 (parity audit 2026-08-18, issue #510) -- the block-side half of
        // ForgeweaveItemTagsProvider's c:glass_blocks/c:dyed additions (TinkerOredict registerCommon():
        // blockClearGlass/blockClearStainedGlass -> "blockGlass" (+ per-color "blockGlass<Color>")).
        var glassBlocks = tag(cTag("glass_blocks")).add(ForgeweaveBlocks.CLEAR_GLASS.get());
        for (var color : ForgeweaveBlocks.clearStainedGlassColors()) {
            glassBlocks.add(color.block().get());
            tag(cTag("dyed/" + color.dye().getSerializedName())).add(color.block().get());
        }

        // #719 -- what each tool family may vein-mine while the veinmine key is held (maintainer
        // decision, beta.1 playtest 2026-08-25): axes logs only, pickaxes ores only, shovels loose
        // soil only. Pack-editable under data/forgeweave/tags/block/veinmine/; a family with no tag
        // here (hoe) never veins. See VeinmineKey.
        tag(VeinmineKey.family("axe")).addTag(BlockTags.LOGS);
        tag(VeinmineKey.family("pickaxe")).addTag(Tags.Blocks.ORES)
                .add(ForgeweaveBlocks.COBALT_ORE.get())
                .add(ForgeweaveBlocks.ARDITE_ORE.get());
        tag(VeinmineKey.family("shovel")).addTag(BlockTags.DIRT).addTag(Tags.Blocks.GRAVELS).addTag(Tags.Blocks.SANDS)
                .add(Blocks.CLAY, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.SNOW_BLOCK);
    }

    private static TagKey<Block> cTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
