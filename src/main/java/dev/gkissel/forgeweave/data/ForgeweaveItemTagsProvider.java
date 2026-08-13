package dev.gkissel.forgeweave.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

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

        // #234 -- steel: FW's own ingot/nugget into the same c: convention tags, so the shipped
        // tag-keyed melting rows (steel_ingot.json and friends) pick them up alongside any other
        // mod's steel. No raw form -- steel is alloyed, not mined.
        tag("ingots/steel").add(ForgeweaveItems.INGOT_STEEL.get());
        tag("nuggets/steel").add(ForgeweaveItems.NUGGET_STEEL.get());

        // #232 -- knightslime (docs/SCOPE.md M3.2): alloy-only, so ingot/nugget only, no raw form.
        tag("ingots/knightslime").add(ForgeweaveItems.INGOT_KNIGHTSLIME.get());
        tag("nuggets/knightslime").add(ForgeweaveItems.NUGGET_KNIGHTSLIME.get());

        // #104 -- the cobalt + ardite nether ore blocks' own item forms into c:ores/*, the same
        // convention vanilla iron/copper ore already carry (see the shipped iron_ore.json/
        // copper_ore.json melting rows, issue #96) -- lets a smeltery melt the ore block itself
        // (e.g. via /give or a future silk-touch path) at the same base amount as its raw drop.
        tag("ores/cobalt").add(ForgeweaveItems.COBALT_ORE.get());
        tag("ores/ardite").add(ForgeweaveItems.ARDITE_ORE.get());

        // #206 -- the four new storage blocks' own c:storage_blocks/* membership (item side), plus
        // this pack's own extension of the parent c:storage_blocks tag: NeoForge's own tag only
        // unions the child tags it knows about (iron/gold/copper/netherite/...), so a Forgeweave-only
        // metal has to add itself in, exactly like any other mod's storage block would.
        tag("storage_blocks/cobalt").add(ForgeweaveItems.COBALT_BLOCK.get());
        tag("storage_blocks/ardite").add(ForgeweaveItems.ARDITE_BLOCK.get());
        tag("storage_blocks/manyullyn").add(ForgeweaveItems.MANYULLYN_BLOCK.get());
        tag("storage_blocks/rose_gold").add(ForgeweaveItems.ROSE_GOLD_BLOCK.get());
        tag("storage_blocks/steel").add(ForgeweaveItems.STEEL_BLOCK.get());
        tag("storage_blocks/knightslime").add(ForgeweaveItems.KNIGHTSLIME_BLOCK.get()); // #232
        tag("storage_blocks").addTag(storageBlock("cobalt")).addTag(storageBlock("ardite"))
                .addTag(storageBlock("manyullyn")).addTag(storageBlock("rose_gold"))
                .addTag(storageBlock("steel")).addTag(storageBlock("knightslime"))
                .addTag(storageBlock("pig_iron"));

        // #233 -- pig iron into the same c: convention tags the other Forgeweave-only metals use.
        // Note storage_blocks/pig_iron also fills in the tool_forge_blocks optional reference below,
        // so a pig iron block now crafts a Tool Forge exactly as upstream's ore-dict list intends.
        tag("ingots/pig_iron").add(ForgeweaveItems.INGOT_PIG_IRON.get());
        tag("nuggets/pig_iron").add(ForgeweaveItems.NUGGET_PIG_IRON.get());
        tag("storage_blocks/pig_iron").add(ForgeweaveItems.PIG_IRON_BLOCK.get());

        // #152 -- the "large tool" classification: tools only the Tool Forge can assemble. See
        // ToolAssemblyRecipes#LARGE_TOOLS, which is the whole gate: a tool issue adds its row here and
        // inherits it with no code change.
        //
        // #152 shipped this tag empty, with an optional reference to a tag only the GameTest datapack
        // defined, because it had to prove the gate before M3 had anything to gate. #157 fills it with
        // the five real large harvest tools and drops that fixture, so the reference goes too --
        // ToolForgeGameTests now proves the gate against a real hammer. The Tool Forge tier's other
        // two, #161's warmace and #158's cleaver, are here for the same reason upstream registers
        // them through TinkerRegistry.registerToolForgeCrafting rather than registerToolCrafting.
        tag(ToolAssemblyRecipes.LARGE_TOOLS)
                .add(ForgeweaveItems.TOOL_WARMACE.get())
                .add(ForgeweaveItems.TOOL_CLEAVER.get())
                .add(ForgeweaveItems.TOOL_HAMMER.get())
                .add(ForgeweaveItems.TOOL_EXCAVATOR.get())
                .add(ForgeweaveItems.TOOL_LUMBERAXE.get())
                .add(ForgeweaveItems.TOOL_SCYTHE.get())
                .add(ForgeweaveItems.TOOL_VEIN_HAMMER.get());

        // #223 -- wind burst's own gate: vanilla's wind_burst enchantment names
        // `#minecraft:enchantable/mace` as its supported_items, and ModifierApplication reads that
        // tag directly (no Forgeweave-side item check of its own) to decide what the modifier accepts.
        // The warmace rides vanilla's mace mechanics (WarmaceItem) but is its own item, so it has to
        // join the tag the same way vanilla's own mace is already a member of it.
        tag(ItemTags.MACE_ENCHANTABLE).add(ForgeweaveItems.TOOL_WARMACE.get());

        // #152 -- what a Tool Forge can be crafted from. Upstream 1.12 keeps this as an ore-dict list
        // on BlockToolForge#baseBlocks, filled from TinkerIntegration's `.toolforge()` calls: iron,
        // gold, copper, cobalt, ardite, manyullyn, pig iron, knightslime, bronze, lead, silver,
        // electrum, steel, brass, alubrass, tin, nickel, zinc, aluminum. The `c:storage_blocks/*`
        // convention tags are that list's modern equivalent, so naming them here gives a modded
        // metal's block the same recipe upstream's ore dict did -- and only the metals: the parent
        // c:storage_blocks tag would also pull in redstone, lapis, coal, diamond, emerald and raw-ore
        // blocks, none of which upstream's list has.
        //
        // addOptionalTag rather than addTag for everything without a vanilla item behind it: a tag
        // reference that no loaded mod defines is an error, not an empty set, so the required form
        // would make a single missing metal break the whole file.
        var toolForge = tag(TOOL_FORGE_BLOCKS);
        toolForge.addTag(storageBlock("iron")).addTag(storageBlock("gold")).addTag(storageBlock("copper"));
        for (String metal : List.of("cobalt", "ardite", "manyullyn", "pig_iron", "knightslime", "bronze",
                "lead", "silver", "electrum", "steel", "brass", "aluminum_brass", "tin", "nickel", "zinc",
                "aluminum", "rose_gold")) {
            toolForge.addOptionalTag(storageBlock(metal));
        }
    }

    /** The tag naming every block a Tool Forge can be crafted from (issue #152). */
    public static final TagKey<Item> TOOL_FORGE_BLOCKS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "tool_forge_blocks"));

    private static TagKey<Item> storageBlock(String metal) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/" + metal));
    }

    private IntrinsicTagAppender<Item> tag(String path) {
        return tag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path)));
    }
}
