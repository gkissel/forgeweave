package dev.gkissel.forgeweave.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ChestKind;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedDuctBlockEntity;
import dev.gkissel.forgeweave.compat.draconic.ForgeweaveDraconicCompat;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PatternItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;
import net.neoforged.neoforge.registries.DeferredHolder;

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
        // #635 (parity audit T57): upstream registers every coloured slime ball under the "slimeball"
        // ore-dict entry alongside vanilla's own (TinkerCommons#registerItems), which is what its
        // vanilla-slime-block replacement recipe matches on. `c:slimeballs` is that entry's modern
        // spelling -- NeoForge already puts vanilla's slime ball in it, so this only adds the five.
        ForgeweaveItems.slimeBalls().forEach(ball -> tag("slimeballs").add(ball.item().get()));

        tag("ingots/cobalt").add(ForgeweaveItems.INGOT_COBALT.get());
        tag("nuggets/cobalt").add(ForgeweaveItems.NUGGET_COBALT.get());
        tag("raw_materials/cobalt").add(ForgeweaveItems.RAW_COBALT.get());

        tag("ingots/ardite").add(ForgeweaveItems.INGOT_ARDITE.get());
        tag("nuggets/ardite").add(ForgeweaveItems.NUGGET_ARDITE.get());
        tag("raw_materials/ardite").add(ForgeweaveItems.RAW_ARDITE.get());

        // #911 -- manyullyn and rose gold are alloys made only in the smeltery, so no raw form/tag.
        tag("ingots/manyullyn").add(ForgeweaveItems.INGOT_MANYULLYN.get());
        tag("nuggets/manyullyn").add(ForgeweaveItems.NUGGET_MANYULLYN.get());

        tag("ingots/rose_gold").add(ForgeweaveItems.INGOT_ROSE_GOLD.get());
        tag("nuggets/rose_gold").add(ForgeweaveItems.NUGGET_ROSE_GOLD.get());

        // #234 -- steel: FW's own ingot/nugget into the same c: convention tags, so the shipped
        // tag-keyed melting rows (steel_ingot.json and friends) pick them up alongside any other
        // mod's steel. No raw form -- steel is alloyed, not mined.
        tag("ingots/steel").add(ForgeweaveItems.INGOT_STEEL.get());
        tag("nuggets/steel").add(ForgeweaveItems.NUGGET_STEEL.get());

        // #232 -- knightslime (docs/SCOPE.md M3.2): alloy-only, so ingot/nugget only, no raw form.
        tag("ingots/knightslime").add(ForgeweaveItems.INGOT_KNIGHTSLIME.get());
        tag("nuggets/knightslime").add(ForgeweaveItems.NUGGET_KNIGHTSLIME.get());

        // #235 -- amethyst bronze: same convention, and alloyed rather than mined too, so no raw form.
        tag("ingots/amethyst_bronze").add(ForgeweaveItems.INGOT_AMETHYST_BRONZE.get());
        tag("nuggets/amethyst_bronze").add(ForgeweaveItems.NUGGET_AMETHYST_BRONZE.get());

        // #843 -- queen's slime and hepatizon (closes #180): same convention, alloyed not mined.
        tag("ingots/queens_slime").add(ForgeweaveItems.INGOT_QUEENS_SLIME.get());
        tag("nuggets/queens_slime").add(ForgeweaveItems.NUGGET_QUEENS_SLIME.get());
        tag("ingots/hepatizon").add(ForgeweaveItems.INGOT_HEPATIZON.get());
        tag("nuggets/hepatizon").add(ForgeweaveItems.NUGGET_HEPATIZON.get());

        // #104 -- the cobalt + ardite nether ore blocks' own item forms into c:ores/*, the same
        // convention vanilla iron/copper ore already carry (see the shipped iron_ore.json/
        // copper_ore.json melting rows, issue #96) -- lets a smeltery melt the ore block itself
        // (e.g. via /give or a future silk-touch path) at the same base amount as its raw drop.
        tag("ores/cobalt").add(ForgeweaveItems.COBALT_ORE.get());
        tag("ores/ardite").add(ForgeweaveItems.ARDITE_ORE.get());
        // #903 -- brimspar's ore block, same convention. Its crystal takes `c:gems/brimspar` rather
        // than a raw_materials entry: it is a gem-shaped drop that melts straight into fuel, with no
        // ingot form to be the "raw" half of.
        tag("ores/brimspar").add(ForgeweaveItems.BRIMSPAR_ORE.get());
        tag("gems/brimspar").add(ForgeweaveItems.BRIMSPAR_CRYSTAL.get());

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
                .addTag(storageBlock("pig_iron")).addTag(storageBlock("amethyst_bronze"))
                .addTag(storageBlock("queens_slime")).addTag(storageBlock("hepatizon")); // #843

        // #233 -- pig iron into the same c: convention tags the other Forgeweave-only metals use.
        // Note storage_blocks/pig_iron also fills in the tool_forge_blocks optional reference below,
        // so a pig iron block now crafts a Tool Forge exactly as upstream's ore-dict list intends.
        tag("ingots/pig_iron").add(ForgeweaveItems.INGOT_PIG_IRON.get());
        tag("nuggets/pig_iron").add(ForgeweaveItems.NUGGET_PIG_IRON.get());
        tag("storage_blocks/pig_iron").add(ForgeweaveItems.PIG_IRON_BLOCK.get());

        // #235 -- amethyst bronze's storage block, item side (parent-chain membership above).
        tag("storage_blocks/amethyst_bronze").add(ForgeweaveItems.AMETHYST_BRONZE_BLOCK.get());

        // #843 -- queen's slime and hepatizon storage blocks, item side (closes #180).
        tag("storage_blocks/queens_slime").add(ForgeweaveItems.QUEENS_SLIME_BLOCK.get());
        tag("storage_blocks/hepatizon").add(ForgeweaveItems.HEPATIZON_BLOCK.get());

        // #839 -- Track B's ore family (M6 epic #824), item side of the same c: convention every
        // other Forgeweave-only metal above already uses: ingot/nugget/raw_materials/ores/
        // storage_blocks per material, plus a raw_<id> storage_blocks entry for the raw-storage block.
        var trackBStorageBlocksItem = tag("storage_blocks");
        for (TrackBOre ore : TrackBOre.ALL) {
            tag("ingots/" + ore.id()).add(ForgeweaveItems.trackBIngot(ore.id()).get());
            tag("nuggets/" + ore.id()).add(ForgeweaveItems.trackBNugget(ore.id()).get());
            tag("ores/" + ore.id()).add(ForgeweaveItems.trackBOreItem(ore.id()).get());
            tag("storage_blocks/" + ore.id()).add(ForgeweaveItems.trackBStorageBlockItem(ore.id()).get());
            trackBStorageBlocksItem.addTag(storageBlock(ore.id()));
            // #929 -- fulmenite has no raw item/raw-storage block (TrackBOre#dropsCrystal); it takes
            // c:gems/<id> instead, the same tag brimspar's own crystal already uses.
            if (ore.dropsCrystal()) {
                tag("gems/" + ore.id()).add(ForgeweaveItems.trackBCrystal(ore.id()).get());
            } else {
                tag("raw_materials/" + ore.id()).add(ForgeweaveItems.trackBRawItem(ore.id()).get());
                tag("storage_blocks/raw_" + ore.id()).add(ForgeweaveItems.trackBRawBlockItem(ore.id()).get());
                trackBStorageBlocksItem.addTag(storageBlock("raw_" + ore.id()));
            }
        }

        // #840 -- Track B's 18 alloy tool materials, item side of the same c: convention: alloy-only,
        // so ingots/nuggets/storage_blocks only, matching pig_iron/knightslime's own tag set above.
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            tag("ingots/" + alloy.id()).add(ForgeweaveItems.trackBAlloyIngot(alloy.id()).get());
            tag("nuggets/" + alloy.id()).add(ForgeweaveItems.trackBAlloyNugget(alloy.id()).get());
            tag("storage_blocks/" + alloy.id()).add(ForgeweaveItems.trackBAlloyBlockItem(alloy.id()).get());
            trackBStorageBlocksItem.addTag(storageBlock(alloy.id()));
        }

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
        //
        // #336 adds the battleaxe on the maintainer's playtest decision (2026-08-14). Upstream never
        // shipped it, but the registration it commented out -- TinkerMeleeWeapons.java:104 -- is the
        // *forge* one, right under the cleaver's, so the tier it was headed for is this one.
        tag(ToolAssemblyRecipes.LARGE_TOOLS)
                .add(ForgeweaveItems.TOOL_WARMACE.get())
                .add(ForgeweaveItems.TOOL_CLEAVER.get())
                .add(ForgeweaveItems.TOOL_BATTLEAXE.get())
                .add(ForgeweaveItems.TOOL_HAMMER.get())
                .add(ForgeweaveItems.TOOL_EXCAVATOR.get())
                .add(ForgeweaveItems.TOOL_LUMBERAXE.get())
                .add(ForgeweaveItems.TOOL_SCYTHE.get())
                .add(ForgeweaveItems.TOOL_VEIN_HAMMER.get())
                // M3.5 #395 -- the two Tool Forge-tier bows. Upstream
                // TinkerRangedWeapons#registerToolBuilding registers the shortbow with
                // registerToolCrafting but the longbow and crossbow with registerToolForgeCrafting.
                .add(ForgeweaveItems.TOOL_LONGBOW.get())
                .add(ForgeweaveItems.TOOL_CROSSBOW.get())
                // #448 -- the shuriken: upstream TinkerRangedWeapons#registerToolBuilding puts it
                // through registerToolForgeCrafting, same as the two bows above.
                .add(ForgeweaveItems.TOOL_SHURIKEN.get());

        // #915 -- the Draconic Evolution fusion upgrade ladder's catalyst set (docs/SCOPE.md M8).
        // Every item either station assembles, read straight off ToolAssemblyRecipes.ENTRIES rather
        // than listed by hand, so a new tool family joins the ladder with no edit here. Which of
        // them a given upgrade line actually accepts is the modifier's own gate, not this tag's --
        // see ForgeweaveDraconicCompat#FUSION_UPGRADABLE.
        var fusionUpgradable = tag(ForgeweaveDraconicCompat.FUSION_UPGRADABLE);
        ToolAssemblyRecipes.ENTRIES.stream()
                .map(entry -> entry.tool().get())
                .distinct()
                .forEach(fusionUpgradable::add);

        // #223 -- wind burst's own gate: vanilla's wind_burst enchantment names
        // `#minecraft:enchantable/mace` as its supported_items, and ModifierApplication reads that
        // tag directly (no Forgeweave-side item check of its own) to decide what the modifier accepts.
        // The warmace rides vanilla's mace mechanics (WarmaceItem) but is its own item, so it has to
        // join the tag the same way vanilla's own mace is already a member of it.
        tag(ItemTags.MACE_ENCHANTABLE).add(ForgeweaveItems.TOOL_WARMACE.get());

        // T54 (#485) -- what `allowVanillaEnchanting` actually turns on. A vanilla enchantment names
        // one of the `minecraft:enchantable/*` item tags as its supported_items, so a tool that is in
        // none of them can be accepted by the table and still be offered nothing. These rows mirror
        // vanilla's own mapping (VanillaItemTagsProvider: #swords -> sword, #swords + #axes ->
        // sharp_weapon, #axes + #pickaxes + #shovels + #hoes -> mining/mining_loot, everything
        // damageable -> durability) onto Forgeweave's tool shapes, keeping to the vanilla tags rather
        // than joining #minecraft:swords/#pickaxes wholesale -- those carry unrelated behavior
        // (villager gifts, piglin interest, #minecraft:tools) this ticket has no business changing.
        //
        // The tags are static; the config flag is not, so ToolItem#supportsEnchantment is what makes
        // the flag's OFF side (1.12's TinkersItem#isBookEnchantable = false, enchantability 0) hold.
        //
        // FIRE_ASPECT_ENCHANTABLE, WEAPON_ENCHANTABLE and VANISHING_ENCHANTABLE are not listed for the
        // shapes that reach them through vanilla's own tag-of-tag references (fire_aspect includes
        // #sword_enchantable, weapon includes #sharp_weapon_enchantable, vanishing includes
        // #durability_enchantable).
        // The sword shapes -- vanilla's #minecraft:swords, which is what feeds both #sword_enchantable
        // (Looting, Knockback, Sweeping Edge, and Fire Aspect through its tag reference) and the sharp
        // line below.
        List<Item> swords = List.of(ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_LONGSWORD.get(),
                ForgeweaveItems.TOOL_RAPIER.get(), ForgeweaveItems.TOOL_DAGGER.get(),
                ForgeweaveItems.TOOL_KATANA.get(), ForgeweaveItems.TOOL_SCIMITAR.get(),
                ForgeweaveItems.TOOL_CLEAVER.get());
        // The axe shapes -- vanilla's #minecraft:axes, sharp but not swords. The battleaxe is here and
        // in no other family: it is a Tool Forge weapon that mines nothing (ToolConstants#BATTLEAXE).
        List<Item> axes = List.of(ForgeweaveItems.TOOL_HATCHET.get(), ForgeweaveItems.TOOL_LUMBERAXE.get(),
                ForgeweaveItems.TOOL_BATTLEAXE.get());
        // Everything with a harvest category (ToolConstants.Category.HARVEST), vanilla's
        // #axes + #pickaxes + #shovels + #hoes.
        List<Item> miningTools = List.of(ForgeweaveItems.TOOL_PICKAXE.get(), ForgeweaveItems.TOOL_SHOVEL.get(),
                ForgeweaveItems.TOOL_HATCHET.get(), ForgeweaveItems.TOOL_MATTOCK.get(),
                ForgeweaveItems.TOOL_KAMA.get(), ForgeweaveItems.TOOL_HAMMER.get(),
                ForgeweaveItems.TOOL_EXCAVATOR.get(), ForgeweaveItems.TOOL_LUMBERAXE.get(),
                ForgeweaveItems.TOOL_SCYTHE.get(), ForgeweaveItems.TOOL_VEIN_HAMMER.get());
        // The blunt weapons: no counterpart in #swords or #axes, so they take vanilla's mace
        // treatment -- WEAPON_ENCHANTABLE but not the sharp line (Sharpness, Smite, Bane of
        // Arthropods) and not Fire Aspect.
        List<Item> bluntWeapons = List.of(ForgeweaveItems.TOOL_BATTLESIGN.get(),
                ForgeweaveItems.TOOL_FRYING_PAN.get(), ForgeweaveItems.TOOL_WARMACE.get());
        // Bows, and the crossbow with them: Forgeweave's crossbow is a BowItem that draws and fires
        // one arrow, so Power/Punch/Flame ride its arrow the same way, while vanilla's three crossbow
        // enchantments (Multishot, Piercing, Quick Charge) are effects only vanilla's own CrossbowItem
        // runs and would be dead offers here. See the PR for #485.
        List<Item> launchers = List.of(ForgeweaveItems.TOOL_SHORTBOW.get(), ForgeweaveItems.TOOL_LONGBOW.get(),
                ForgeweaveItems.TOOL_CROSSBOW.get());

        addAll(ItemTags.SWORD_ENCHANTABLE, swords);
        addAll(ItemTags.SHARP_WEAPON_ENCHANTABLE, swords, axes);
        addAll(ItemTags.WEAPON_ENCHANTABLE, bluntWeapons);
        addAll(ItemTags.MINING_ENCHANTABLE, miningTools);
        addAll(ItemTags.MINING_LOOT_ENCHANTABLE, miningTools);
        addAll(ItemTags.BOW_ENCHANTABLE, launchers);
        // Unbreaking and Mending (and Curse of Vanishing, whose tag references this one): every tool.
        // #448/#653: the shuriken and the arrow join only this line -- vanilla has no shape for
        // ammo, so the "everything damageable -> durability" rule is the whole of their
        // vanilla-enchant surface.
        addAll(ItemTags.DURABILITY_ENCHANTABLE, swords, axes, miningTools, bluntWeapons, launchers,
                List.of(ForgeweaveItems.TOOL_SHURIKEN.get(), ForgeweaveItems.TOOL_ARROW.get()));

        // #464 (parity audit T33) -- tool-class exposure. Upstream 1.12 states a tool's class with
        // Forge's setHarvestLevel("pickaxe"/"shovel"/"axe"/"shears", 0); 1.21 splits that same fact in
        // two, an ItemAbility set for behaviour (ToolItem#canPerformAction) and these tags for
        // everything data-driven that asks "what kind of tool is this" -- other mods' recipes and
        // loot conditions, datapack predicates, tool racks. Membership follows the tool's own upstream
        // class, so the mattock is in both #minecraft:axes and #minecraft:hoes (it chops and it tills)
        // and the kama and scythe are hoes rather than shears (their upstream "shears" class is a
        // mining classification; the 1.21 tag for what a shear-shaped tool is used for is c:tools/shear,
        // below).
        //
        // Joining #minecraft:swords and friends also joins every #minecraft:enchantable/* tag vanilla
        // builds out of them (VanillaItemTagsProvider: sword_enchantable, mining_enchantable,
        // durability_enchantable, ...), which is the whole of what gates an anvil's enchanted-book
        // merge. ToolItem#isBookEnchantable declines that on the existing allowVanillaEnchanting
        // toggle, the same way isEnchantable already declines the enchanting table -- upstream's
        // TinkersItem#isBookEnchantable is a flat false.
        tag(ItemTags.PICKAXES)
                .add(ForgeweaveItems.TOOL_PICKAXE.get(), ForgeweaveItems.TOOL_HAMMER.get(),
                        ForgeweaveItems.TOOL_VEIN_HAMMER.get());
        tag(ItemTags.SHOVELS)
                .add(ForgeweaveItems.TOOL_SHOVEL.get(), ForgeweaveItems.TOOL_EXCAVATOR.get());
        tag(ItemTags.AXES)
                .add(ForgeweaveItems.TOOL_HATCHET.get(), ForgeweaveItems.TOOL_LUMBERAXE.get(),
                        ForgeweaveItems.TOOL_BATTLEAXE.get(), ForgeweaveItems.TOOL_MATTOCK.get());
        tag(ItemTags.HOES)
                .add(ForgeweaveItems.TOOL_MATTOCK.get(), ForgeweaveItems.TOOL_KAMA.get(),
                        ForgeweaveItems.TOOL_SCYTHE.get());
        tag(ItemTags.SWORDS)
                .add(ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_LONGSWORD.get(),
                        ForgeweaveItems.TOOL_RAPIER.get(), ForgeweaveItems.TOOL_DAGGER.get(),
                        ForgeweaveItems.TOOL_SCIMITAR.get(), ForgeweaveItems.TOOL_KATANA.get(),
                        ForgeweaveItems.TOOL_CLEAVER.get());

        // #678 (SCOPE.md D20): the four armor pieces join vanilla's per-slot armor tags, which is
        // what feeds #minecraft:enchantable/{armor,head_armor,chest_armor,leg_armor,foot_armor,
        // durability,equippable} -- the supported_items of every vanilla armor enchantment -- and
        // NeoForge's c:armors. ArmorPieceItem gates all of it on allowVanillaEnchanting like ToolItem.
        tag(ItemTags.HEAD_ARMOR).add(ForgeweaveItems.ARMOR_HELMET.get(), ForgeweaveItems.ARMOR_HEAVY_HELMET.get());
        tag(ItemTags.CHEST_ARMOR).add(ForgeweaveItems.ARMOR_CHESTPLATE.get(), ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE.get());
        tag(ItemTags.LEG_ARMOR).add(ForgeweaveItems.ARMOR_LEGGINGS.get(), ForgeweaveItems.ARMOR_HEAVY_LEGGINGS.get());
        tag(ItemTags.FOOT_ARMOR).add(ForgeweaveItems.ARMOR_BOOTS.get(), ForgeweaveItems.ARMOR_HEAVY_BOOTS.get());

        // The c: half. NeoForge's own c:tools already reads #minecraft:axes/hoes/pickaxes/shovels/
        // swords plus every c:tools/* leaf below (see its shipped tools.json), so nothing here has to
        // name the parent -- the five tags above and these six put every Forgeweave tool in it.
        //
        // c:tools/melee_weapon is "intentionally intended to be used for melee attack as a primary
        // purpose", which is upstream's own Category.WEAPON split: the six station weapons, the
        // battleaxe, the cleaver, the katana, the scimitar and the warmace, but not the hatchet or
        // lumberaxe (Category.HARVEST tools that happen to hit hard). c:tools/mining_tool takes the
        // harvest side of that same split. The two are not exclusive upstream and are not here either.
        tag("tools/melee_weapon")
                .add(ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_LONGSWORD.get(),
                        ForgeweaveItems.TOOL_RAPIER.get(), ForgeweaveItems.TOOL_DAGGER.get(),
                        ForgeweaveItems.TOOL_SCIMITAR.get(), ForgeweaveItems.TOOL_KATANA.get(),
                        ForgeweaveItems.TOOL_CLEAVER.get(), ForgeweaveItems.TOOL_BATTLEAXE.get(),
                        ForgeweaveItems.TOOL_BATTLESIGN.get(), ForgeweaveItems.TOOL_FRYING_PAN.get(),
                        ForgeweaveItems.TOOL_WARMACE.get());
        tag("tools/mining_tool")
                .add(ForgeweaveItems.TOOL_PICKAXE.get(), ForgeweaveItems.TOOL_SHOVEL.get(),
                        ForgeweaveItems.TOOL_HATCHET.get(), ForgeweaveItems.TOOL_MATTOCK.get(),
                        ForgeweaveItems.TOOL_KAMA.get(), ForgeweaveItems.TOOL_HAMMER.get(),
                        ForgeweaveItems.TOOL_EXCAVATOR.get(), ForgeweaveItems.TOOL_LUMBERAXE.get(),
                        ForgeweaveItems.TOOL_SCYTHE.get(), ForgeweaveItems.TOOL_VEIN_HAMMER.get());
        // The kama and scythe shear entities (KamaItem#interactLivingEntity, EntityShear), which is
        // what upstream's own setHarvestLevel("shears", 0) marked them as.
        tag("tools/shear").add(ForgeweaveItems.TOOL_KAMA.get(), ForgeweaveItems.TOOL_SCYTHE.get());
        // The warmace rides vanilla's mace mechanics outright (WarmaceItem), so it belongs in the tag
        // vanilla's own mace is in, exactly as it already joins #minecraft:enchantable/mace above.
        tag("tools/mace").add(ForgeweaveItems.TOOL_WARMACE.get());
        tag("tools/bow").add(ForgeweaveItems.TOOL_SHORTBOW.get(), ForgeweaveItems.TOOL_LONGBOW.get());
        tag("tools/crossbow").add(ForgeweaveItems.TOOL_CROSSBOW.get());
        tag("tools/ranged_weapon")
                .add(ForgeweaveItems.TOOL_SHORTBOW.get(), ForgeweaveItems.TOOL_LONGBOW.get(),
                        ForgeweaveItems.TOOL_CROSSBOW.get());

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

        // #277 -- what a seared duct takes as a fluid filter; see SearedDuctBlockEntity#DUCT_CONTAINERS.
        tag(SearedDuctBlockEntity.DUCT_CONTAINERS).add(Items.BUCKET);

        // #477/T46 -- every cast item, gold (issue #100/#222) and clay (issue #292), into one tag so
        // the Pattern Chest's cast-chest mode (ChestKind#CASTS) can recognise both without a second
        // marker class the way ClayCastItem alone would need for gold casts too. The gold-only half
        // is its own tag (CASTS_GOLD, T69/#500) since that's the one upstream's own `ore:cast` oredict
        // covers (TinkerOredict: TinkerSmeltery.cast/castCustom, never the clay-only clayCast) --
        // reused below by the reinforced-plate recipe center slot.
        tag(CASTS_GOLD)
                .add(ForgeweaveItems.CAST_INGOT.get(), ForgeweaveItems.CAST_NUGGET.get(),
                        ForgeweaveItems.CAST_GEM.get(), ForgeweaveItems.CAST_PLATE.get(), ForgeweaveItems.CAST_GEAR.get(),
                        ForgeweaveItems.CAST_PICKAXE_HEAD.get(), ForgeweaveItems.CAST_SHOVEL_HEAD.get(),
                        ForgeweaveItems.CAST_AXE_HEAD.get(), ForgeweaveItems.CAST_TOOL_BINDING.get(),
                        ForgeweaveItems.CAST_TOOL_HANDLE.get(), ForgeweaveItems.CAST_SWORD_BLADE.get(),
                        ForgeweaveItems.CAST_WIDE_GUARD.get(), ForgeweaveItems.CAST_HAND_GUARD.get(),
                        ForgeweaveItems.CAST_CROSS_GUARD.get(), ForgeweaveItems.CAST_SIGN_PLATE.get(),
                        ForgeweaveItems.CAST_PAN.get(), ForgeweaveItems.CAST_KNIFE_BLADE.get(),
                        ForgeweaveItems.CAST_LARGE_SWORD_BLADE.get(), ForgeweaveItems.CAST_TOUGH_TOOL_ROD.get(),
                        ForgeweaveItems.CAST_TOUGH_BINDING.get(), ForgeweaveItems.CAST_LARGE_PLATE.get(),
                        ForgeweaveItems.CAST_HAMMER_HEAD.get(), ForgeweaveItems.CAST_EXCAVATOR_HEAD.get(),
                        ForgeweaveItems.CAST_SCYTHE_HEAD.get(), ForgeweaveItems.CAST_KAMA_HEAD.get(),
                        ForgeweaveItems.CAST_BROAD_AXE_HEAD.get(), ForgeweaveItems.CAST_VEIN_HAMMER_HEAD.get(),
                        ForgeweaveItems.CAST_WAR_MACE_HEAD.get(), ForgeweaveItems.CAST_CURVED_BLADE.get(),
                        ForgeweaveItems.CAST_KATANA_BLADE.get(), ForgeweaveItems.CAST_BOW_LIMB.get(),
                        ForgeweaveItems.CAST_SHARPENING_KIT.get(), ForgeweaveItems.CAST_SHARD.get(),
                        ForgeweaveItems.CAST_ARROW_HEAD.get(),
                        ForgeweaveItems.CAST_PLATING_HELMET.get(), ForgeweaveItems.CAST_PLATING_CHESTPLATE.get(),
                        ForgeweaveItems.CAST_PLATING_LEGGINGS.get(), ForgeweaveItems.CAST_PLATING_BOOTS.get(),
                        ForgeweaveItems.CAST_MAILLE.get());
        tag(ChestKind.CASTS).addTag(CASTS_GOLD);
        for (var clayCast : ForgeweaveItems.CLAY_CASTS.values()) {
            tag(ChestKind.CASTS).add(clayCast.get());
        }

        // T79 (parity audit 2026-08-18, issue #510) -- the item-side half of TinkerOredict that
        // Forgeweave never picked up: c: convention tags for clear glass (+ its 16 dyed colors),
        // seared brick, and the cast/pattern/part families. See ForgeweaveBlockTagsProvider for the
        // block-side glass tags.
        //
        // registerCommon(): blockClearGlass/blockClearStainedGlass -> "blockGlass", plus each color
        // meta -> "blockGlass" + dyes[i] (e.g. "blockGlassWhite"). c:glass_blocks is the modern
        // "blockGlass" equivalent; c:dyed/<color> is the modern per-color equivalent -- the same tag
        // vanilla's own stained glass already carries (see the shipped c:dyed/white.json and
        // friends), rather than inventing a c:glass_blocks/<color> convention nothing else uses.
        var glassBlocks = tag("glass_blocks").add(ForgeweaveItems.CLEAR_GLASS.get());
        for (var color : ForgeweaveBlocks.clearStainedGlassColors()) {
            var item = color.block().get().asItem();
            glassBlocks.add(item);
            tag("dyed/" + color.dye().getSerializedName()).add(item);
        }

        // registerCommon(): searedBrick -> "ingotBrickSeared". Same c:ingots/<name> convention this
        // file already uses for cobalt/ardite/etc, even though a seared brick isn't a metal ingot --
        // that is upstream's own naming choice, not a Forgeweave one.
        tag("ingots/seared_brick").add(ForgeweaveItems.SEARED_BRICK.get());

        // registerSmeltery(): both TinkerSmeltery.cast and .castCustom -> "cast" (a blank and a
        // stamped gold cast are the same item with different metadata upstream, so one wildcard
        // oredict covers both). CASTS_GOLD is already exactly that reusable-gold-cast set (T69/#500);
        // c:casts exposes it under the convention namespace other mods can hook into.
        tag("casts").addTag(CASTS_GOLD);

        // registerTools(): pattern -> "pattern". Upstream's Pattern is one item with per-shape
        // metadata, so its wildcard oredict covers the blank pattern and every part pattern alike.
        // Forgeweave splits that into one item per shape (PatternItem, plus the plain-Item blank), so
        // c:patterns is every registered PatternItem plus the blank -- derived off the item registry
        // like ForgeweaveItemColors#tintedPartItems, so a new pattern shape inherits membership by
        // being registered rather than needing a second hand list kept in sync with ForgeweaveItems.
        var patterns = tag("patterns").add(ForgeweaveItems.PATTERN_BLANK.get());
        ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof PatternItem)
                .forEach(patterns::add);

        // registerTools(): partPickHead/partBinding/partToolRod -> one tag each. Verified against the
        // clone (TinkerTools.java): upstream registers roughly fifteen ToolPart fields but only
        // oredicts these three -- no partSwordBlade, partWideGuard, etc -- so parity here is the same
        // narrow three, not the full Forgeweave part roster (unlike patterns above, which upstream's
        // single wildcarded item makes deliberately unbounded).
        tag("parts/pickaxe_head").add(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        tag("parts/tool_binding").add(ForgeweaveItems.PART_TOOL_BINDING.get());
        tag("parts/tool_rod").add(ForgeweaveItems.PART_TOOL_HANDLE.get());
    }

    /** The tag naming every block a Tool Forge can be crafted from (issue #152). */
    public static final TagKey<Item> TOOL_FORGE_BLOCKS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "tool_forge_blocks"));

    /**
     * Every reusable gold cast (issue #100/#222/#272), excluding the single-use clay casts -- the
     * Forgeweave-item-per-shape equivalent of upstream's {@code ore:cast} oredict tag ({@code
     * TinkerOredict}: {@code TinkerSmeltery.cast} and {@code .castCustom}, both gold). T69/#500's
     * reinforced-plate recipe keys its center slot off this tag.
     */
    public static final TagKey<Item> CASTS_GOLD =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "casts/gold"));

    private static TagKey<Item> storageBlock(String metal) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/" + metal));
    }

    private IntrinsicTagAppender<Item> tag(String path) {
        return tag(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path)));
    }

    /**
     * Adds several tool families to one tag at once, de-duplicated -- the families overlap (a hatchet
     * is both an axe shape and a mining tool) and a tag file listing the same item twice is a wart.
     */
    @SafeVarargs
    private void addAll(TagKey<Item> tag, List<Item>... families) {
        IntrinsicTagAppender<Item> appender = tag(tag);
        Stream.of(families).flatMap(List::stream).distinct().forEach(appender::add);
    }
}
