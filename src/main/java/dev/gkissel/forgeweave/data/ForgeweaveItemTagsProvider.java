package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Puts the four metals that lack a vanilla item form (docs/SCOPE.md M2 issue #103: cobalt, ardite,
 * manyullyn, rose gold) into the {@code c:} convention tags {@link dev.gkissel.forgeweave.recipe.MeltingRecipe}'s
 * tag-keyed melting rows expect. NeoForge itself ships {@code c:ingots/*} etc. for vanilla items
 * (iron, copper, gold, netherite -- see the shipped {@code melting_recipe/iron_ingot.json} and
 * friends), but has no reason to know about a Forgeweave-only metal; this is that same convention
 * extended to Forgeweave's own items, exactly as any other mod's ore/ingot would register into it.
 */
public class ForgeweaveItemTagsProvider extends ItemTagsProvider {
    public ForgeweaveItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag("ingots/cobalt").add(ForgeweaveItems.INGOT_COBALT.get());
        tag("nuggets/cobalt").add(ForgeweaveItems.NUGGET_COBALT.get());
        tag("raw_materials/cobalt").add(ForgeweaveItems.RAW_COBALT.get());

        tag("ingots/ardite").add(ForgeweaveItems.INGOT_ARDITE.get());
        tag("nuggets/ardite").add(ForgeweaveItems.NUGGET_ARDITE.get());
        tag("raw_materials/ardite").add(ForgeweaveItems.RAW_ARDITE.get());

        tag("ingots/manyullyn").add(ForgeweaveItems.INGOT_MANYULLYN.get());
        tag("nuggets/manyullyn").add(ForgeweaveItems.NUGGET_MANYULLYN.get());
        tag("raw_materials/manyullyn").add(ForgeweaveItems.RAW_MANYULLYN.get());

        tag("ingots/rose_gold").add(ForgeweaveItems.INGOT_ROSE_GOLD.get());
        tag("nuggets/rose_gold").add(ForgeweaveItems.NUGGET_ROSE_GOLD.get());
        tag("raw_materials/rose_gold").add(ForgeweaveItems.RAW_ROSE_GOLD.get());

        // #104 -- the cobalt + ardite nether ore blocks' own item forms into c:ores/*, the same
        // convention vanilla iron/copper ore already carry (see the shipped iron_ore.json/
        // copper_ore.json melting rows, issue #96) -- lets a smeltery melt the ore block itself
        // (e.g. via /give or a future silk-touch path) at the same base amount as its raw drop.
        tag("ores/cobalt").add(ForgeweaveItems.COBALT_ORE.get());
        tag("ores/ardite").add(ForgeweaveItems.ARDITE_ORE.get());
    }

    private IntrinsicTagAppender<Item> tag(String path) {
        return tag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path)));
    }
}
