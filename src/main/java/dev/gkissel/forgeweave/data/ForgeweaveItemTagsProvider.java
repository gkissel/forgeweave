package dev.gkissel.forgeweave.data;

import java.util.List;
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

        // #104 -- the cobalt + ardite nether ore blocks' own item forms into c:ores/*, the same
        // convention vanilla iron/copper ore already carry (see the shipped iron_ore.json/
        // copper_ore.json melting rows, issue #96) -- lets a smeltery melt the ore block itself
        // (e.g. via /give or a future silk-touch path) at the same base amount as its raw drop.
        tag("ores/cobalt").add(ForgeweaveItems.COBALT_ORE.get());
        tag("ores/ardite").add(ForgeweaveItems.ARDITE_ORE.get());

        // #152 -- the "large tool" classification: tools only the Tool Forge can assemble. Ships with
        // no members of its own, because no M1/M2 tool is large; M3's tool issues (#157-#161) add
        // `.add(ForgeweaveItems.TOOL_HAMMER.get())` and friends here and inherit the gate with no
        // code change. See ToolAssemblyRecipes#LARGE_TOOLS.
        //
        // The one entry is an optional reference to a tag only the GameTest datapack defines
        // (src/gametest/resources): #152 has to prove its gate before M3 has a large tool to gate, and
        // a fixture that redefined *this* file instead would collide with it -- src/generated and
        // src/gametest are the same resource root, so the two would be duplicate entries rather than
        // merged tags. An optional reference to a tag nothing defines is an empty set, so a shipped
        // jar (which excludes the fixture) sees exactly what it would have seen with no entry at all.
        // #157 can drop this line along with the fixture.
        //
        // #161 adds the first real member: the warmace is Tool Forge tier (docs/SCOPE.md M3), and
        // #158's cleaver is the second -- upstream registers it through
        // TinkerRegistry.registerToolForgeCrafting, i.e. Tool Forge only.
        tag(ToolAssemblyRecipes.LARGE_TOOLS)
                .add(ForgeweaveItems.TOOL_WARMACE.get())
                .add(ForgeweaveItems.TOOL_CLEAVER.get())
                .addOptionalTag(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "gametest_large_tools"));

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
