package dev.gkissel.forgeweave.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The blank pattern, the five part patterns, the five part items, and the Part Builder block item
 * (CONTEXT.md glossary; docs/SCOPE.md M1 content manifest). Patterns and parts all stack normally:
 * upstream 1.12's {@code library/tools/Pattern} never calls {@code setMaxStackSize}, so every
 * pattern -- blank and part alike -- stacks to the vanilla 64 there, and neither patterns nor parts
 * carry per-instance state beyond a plain data component (issue #64).
 *
 * <p>Each {@link PartItem} declares the {@link PartItem.Kind} it plays in a build, which is what its
 * tooltip shows stats for -- upstream derives the same thing from the {@code PartMaterialType}s the
 * part appears in, but every Forgeweave part appears in exactly one role.
 */
public final class ForgeweaveItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Forgeweave.MODID);

    public static final DeferredItem<Item> PATTERN_BLANK = ITEMS.registerSimpleItem("pattern_blank");
    public static final DeferredItem<Item> PATTERN_PICKAXE_HEAD = ITEMS.registerSimpleItem("pattern_pickaxe_head");
    public static final DeferredItem<Item> PATTERN_SHOVEL_HEAD = ITEMS.registerSimpleItem("pattern_shovel_head");
    public static final DeferredItem<Item> PATTERN_AXE_HEAD = ITEMS.registerSimpleItem("pattern_axe_head");
    public static final DeferredItem<Item> PATTERN_TOOL_BINDING = ITEMS.registerSimpleItem("pattern_tool_binding");
    public static final DeferredItem<Item> PATTERN_TOOL_HANDLE = ITEMS.registerSimpleItem("pattern_tool_handle");

    // M3 tool part patterns (docs/SCOPE.md M3 issue #151), one per part below.
    public static final DeferredItem<Item> PATTERN_SWORD_BLADE = ITEMS.registerSimpleItem("pattern_sword_blade");
    public static final DeferredItem<Item> PATTERN_WIDE_GUARD = ITEMS.registerSimpleItem("pattern_wide_guard");
    public static final DeferredItem<Item> PATTERN_HAND_GUARD = ITEMS.registerSimpleItem("pattern_hand_guard");
    public static final DeferredItem<Item> PATTERN_CROSS_GUARD = ITEMS.registerSimpleItem("pattern_cross_guard");
    public static final DeferredItem<Item> PATTERN_SIGN_PLATE = ITEMS.registerSimpleItem("pattern_sign_plate");
    public static final DeferredItem<Item> PATTERN_PAN = ITEMS.registerSimpleItem("pattern_pan");
    public static final DeferredItem<Item> PATTERN_KNIFE_BLADE = ITEMS.registerSimpleItem("pattern_knife_blade");
    public static final DeferredItem<Item> PATTERN_LARGE_SWORD_BLADE = ITEMS.registerSimpleItem("pattern_large_sword_blade");
    public static final DeferredItem<Item> PATTERN_TOUGH_TOOL_ROD = ITEMS.registerSimpleItem("pattern_tough_tool_rod");
    public static final DeferredItem<Item> PATTERN_TOUGH_BINDING = ITEMS.registerSimpleItem("pattern_tough_binding");
    public static final DeferredItem<Item> PATTERN_LARGE_PLATE = ITEMS.registerSimpleItem("pattern_large_plate");
    public static final DeferredItem<Item> PATTERN_HAMMER_HEAD = ITEMS.registerSimpleItem("pattern_hammer_head");
    public static final DeferredItem<Item> PATTERN_EXCAVATOR_HEAD = ITEMS.registerSimpleItem("pattern_excavator_head");
    public static final DeferredItem<Item> PATTERN_SCYTHE_HEAD = ITEMS.registerSimpleItem("pattern_scythe_head");
    public static final DeferredItem<Item> PATTERN_KAMA_HEAD = ITEMS.registerSimpleItem("pattern_kama_head");
    public static final DeferredItem<Item> PATTERN_BROAD_AXE_HEAD = ITEMS.registerSimpleItem("pattern_broad_axe_head");
    public static final DeferredItem<Item> PATTERN_VEIN_HAMMER_HEAD = ITEMS.registerSimpleItem("pattern_vein_hammer_head");
    // #161's own new-shape head part; see PART_WAR_MACE_HEAD below.
    public static final DeferredItem<Item> PATTERN_WAR_MACE_HEAD = ITEMS.registerSimpleItem("pattern_war_mace_head");

    public static final DeferredItem<PartItem> PART_PICKAXE_HEAD = part("pickaxe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_SHOVEL_HEAD = part("shovel_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_AXE_HEAD = part("axe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_TOOL_BINDING = part("tool_binding", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_TOOL_HANDLE = part("tool_handle", PartItem.Kind.HANDLE);

    // M3 tool parts (docs/SCOPE.md M3 issue #151): the roster's part list, exactly as read off the
    // clone's per-tool constructors (tools/melee/item/*, tools/tools/*) plus the two modern-branch
    // shapes docs/SCOPE.md authorizes (knife blade for the dagger, vein hammer head for the vein
    // hammer -- see NOTICE.md and ForgeweaveItemModelProvider for which of these have upstream art
    // and which don't). Not registered: distinct heads for scimitar/katana/warmace -- those three are
    // Forgeweave's own new-shape tools (docs/SCOPE.md: "new modern-era shapes, ours") with no clone
    // part list to read and no design decided yet; their own issues (#159/#160/#161) are where the
    // maintainer picks their part composition, so inventing parts for them here would be speculative.
    public static final DeferredItem<PartItem> PART_SWORD_BLADE = part("sword_blade", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_WIDE_GUARD = part("wide_guard", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_HAND_GUARD = part("hand_guard", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_CROSS_GUARD = part("cross_guard", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_SIGN_PLATE = part("sign_plate", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_PAN = part("pan", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_KNIFE_BLADE = part("knife_blade", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_LARGE_SWORD_BLADE = part("large_sword_blade", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_TOUGH_TOOL_ROD = part("tough_tool_rod", PartItem.Kind.HANDLE);
    public static final DeferredItem<PartItem> PART_TOUGH_BINDING = part("tough_binding", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_LARGE_PLATE = part("large_plate", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_HAMMER_HEAD = part("hammer_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_EXCAVATOR_HEAD = part("excavator_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_SCYTHE_HEAD = part("scythe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_KAMA_HEAD = part("kama_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_BROAD_AXE_HEAD = part("broad_axe_head", PartItem.Kind.HEAD);
    // No 1.12 or 1.20 counterpart (1.20's vein hammer reuses the plain hammer_head part) -- freshly
    // authored art (NOTICE.md has no row for it), unlike every other part above.
    public static final DeferredItem<PartItem> PART_VEIN_HAMMER_HEAD = part("vein_hammer_head", PartItem.Kind.HEAD);
    // #161: the warmace's head, one of the three new-shape heads #151 deliberately left
    // unregistered ("their own issues are where the maintainer picks their part composition").
    // The composition is ToolConstants#WARMACE's -- tough tool rod, this head, tough binding.
    // Neither clone has a mace-alike, so per issue #198's decision the art is derived from the
    // closest upstream equivalent -- the 1.12 hammer's head, minimally reshaped into a round knob
    // (scripts/derive_warmace_art.py, NOTICE.md) -- rather than freshly authored.
    public static final DeferredItem<PartItem> PART_WAR_MACE_HEAD = part("war_mace_head", PartItem.Kind.HEAD);

    private static DeferredItem<PartItem> part(String name, PartItem.Kind kind) {
        return ITEMS.registerItem(name, properties -> new PartItem(properties, kind));
    }

    // The Part Builder's crafting change (issue #45): leftover material value below a part's cost,
    // paid out as shards. One item id shared by every material -- like the parts above, per-material
    // rendering is the MATERIAL data component plus a client-side tint, not a distinct item/texture
    // per material (PartItem already provides that machinery, so this just reuses it).
    public static final DeferredItem<PartItem> SHARD = ITEMS.registerItem("shard", PartItem::new);

    public static final DeferredItem<BlockItem> PART_BUILDER = ITEMS.registerSimpleBlockItem("part_builder", ForgeweaveBlocks.PART_BUILDER);

    // Assembled tools (docs/SCOPE.md M1 issues #10/#11). Deliberately not DiggerItem/TieredItem:
    // a Tier is a fixed table of durability/speed/tier-tag, and a Forgeweave tool's are all derived
    // per stack from its parts' materials, so the vanilla `tool` and `max_damage` components are
    // written at assembly time instead (ToolAssemblyRecipes). What stays fixed per tool type lives
    // here: the mineable/* tag it is meant for, upstream 1.12's attack speed and damage potential
    // (ToolCore#attackSpeed/#damagePotential in tools/tools/{Pickaxe,Shovel,Hatchet}), and whether
    // that class adds Category.WEAPON -- only Hatchet does, and it halves what a hit costs the tool
    // (ToolCore#reduceDurabilityOnHit, see ToolItem#postHurtEnemy).
    // stacksTo(1) like every other Forgeweave equipment item.
    public static final DeferredItem<ToolItem> TOOL_PICKAXE = ITEMS.registerItem("pickaxe",
            properties -> new ToolItem(properties, BlockTags.MINEABLE_WITH_PICKAXE, 1.2f, 1.0f, false),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_SHOVEL = ITEMS.registerItem("shovel",
            properties -> new ToolItem(properties, BlockTags.MINEABLE_WITH_SHOVEL, 1.0f, 0.9f, false),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_HATCHET = ITEMS.registerItem("hatchet",
            properties -> new ToolItem(properties, BlockTags.MINEABLE_WITH_AXE, 1.1f, 1.1f, true),
            new Item.Properties().stacksTo(1));

    // M3 station tools (docs/SCOPE.md issue #156): mattock (axe+shovel dual tool, tills soil) and
    // kama (shears, right-click crop harvest). Both take their constants off ToolConstants and their
    // combat rider off ForgeweaveInnates like every other M3 tool; the subclasses exist only for the
    // block/entity right-click behaviors, which no innate hook covers (upstream
    // tools/tools/Mattock.java's Category.HARVEST -> weapon=false; Kama.java's Category.HARVEST +
    // Category.WEAPON -> weapon=true).
    public static final DeferredItem<MattockItem> TOOL_MATTOCK = ITEMS.registerItem("mattock",
            MattockItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<KamaItem> TOOL_KAMA = ITEMS.registerItem("kama",
            KamaItem::new, new Item.Properties().stacksTo(1));

    // M3 Tool Station weapons (docs/SCOPE.md M3 issue #155). Attack speed and damage potential come
    // from ToolConstants (issue #153) rather than being repeated here, so the numbers the station's
    // stat formula uses and the ones the attribute modifiers use are the same numbers. Every one of
    // these is Category.WEAPON upstream (each constructor calls addCategory(Category.WEAPON)), which
    // is what halves the durability a hit costs -- see ToolItem#postHurtEnemy. None of them is a
    // mining tool: upstream's melee weapons carry no harvest category at all, so they get the axe tag
    // only to have a non-null one and their (unmodified) mining speed makes them poor at it anyway.
    public static final DeferredItem<ToolItem> TOOL_BROADSWORD =
            weapon("broadsword", ToolConstants.BROADSWORD, ForgeweaveInnates.PARRY);
    public static final DeferredItem<ToolItem> TOOL_LONGSWORD =
            weapon("longsword", ToolConstants.LONGSWORD, ForgeweaveInnates.CHARGED_LEAP);
    public static final DeferredItem<ToolItem> TOOL_RAPIER =
            weapon("rapier", ToolConstants.RAPIER, ForgeweaveInnates.VITAL_THRUST);
    public static final DeferredItem<ToolItem> TOOL_BATTLESIGN =
            weapon("battlesign", ToolConstants.BATTLESIGN, ForgeweaveInnates.DEFLECT);
    public static final DeferredItem<ToolItem> TOOL_FRYING_PAN =
            weapon("frying_pan", ToolConstants.FRYING_PAN, ForgeweaveInnates.HEAVY_SWING);
    public static final DeferredItem<ToolItem> TOOL_DAGGER =
            weapon("dagger", ToolConstants.DAGGER, ForgeweaveInnates.BACKSTAB);

    // #161: the warmace, the Tool Forge tier's smash weapon (docs/SCOPE.md M3). Registered here
    // rather than through weapon() above because its innate is not a ForgeweaveInnates seam at all:
    // the smash is vanilla 1.21's mace, called through rather than copied -- see WarmaceItem.
    public static final DeferredItem<ToolItem> TOOL_WARMACE = ITEMS.registerItem("warmace",
            properties -> new WarmaceItem(properties, ToolConstants.WARMACE, BlockTags.MINEABLE_WITH_AXE, true, null),
            new Item.Properties().stacksTo(1));

    private static DeferredItem<ToolItem> weapon(String name, ToolConstants.Entry constants,
            ForgeweaveInnates.Innate innate) {
        return ITEMS.registerItem(name,
                properties -> new ToolItem(properties, constants, BlockTags.MINEABLE_WITH_AXE, true, innate),
                new Item.Properties().stacksTo(1));
    }

    public static final DeferredItem<BlockItem> TOOL_STATION = ITEMS.registerSimpleBlockItem("tool_station", ForgeweaveBlocks.TOOL_STATION);

    // The Crafting Station (docs/SCOPE.md M1 issue #40): same retextured-table item shape as the two
    // blocks above (ForgeweaveDataComponents#TEXTURE carries the crafting wood).
    public static final DeferredItem<BlockItem> CRAFTING_STATION = ITEMS.registerSimpleBlockItem("crafting_station", ForgeweaveBlocks.CRAFTING_STATION);

    // The Stencil Table (docs/SCOPE.md M1 issue #44): same retextured-table item shape as the three
    // stations above.
    public static final DeferredItem<BlockItem> TOOL_FORGE = ITEMS.registerSimpleBlockItem("tool_forge", ForgeweaveBlocks.TOOL_FORGE);
    public static final DeferredItem<BlockItem> STENCIL_TABLE = ITEMS.registerSimpleBlockItem("stencil_table", ForgeweaveBlocks.STENCIL_TABLE);

    // The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66): plain block items, not
    // retextured-table items -- neither chest carries a TEXTURE component (ChestBlock javadoc).
    public static final DeferredItem<BlockItem> PATTERN_CHEST = ITEMS.registerSimpleBlockItem("pattern_chest", ForgeweaveBlocks.PATTERN_CHEST);
    public static final DeferredItem<BlockItem> PART_CHEST = ITEMS.registerSimpleBlockItem("part_chest", ForgeweaveBlocks.PART_CHEST);

    // Grout (docs/SCOPE.md M2 issue #93; placeable block per issue #129, overruling PR #115's
    // "plain item" deviation). Upstream 1.12 ships grout as one state of a multi-purpose "soil"
    // block shared with graveyard/consecrated soil and slimy mud (BlockSoil.SoilTypes, NOTICE.md) --
    // none of those other states are in Forgeweave's scope (no world-content milestone yet), but
    // grout itself is still a placeable block upstream, so it gets a real ForgeweaveBlocks.GROUT
    // block instead of being folded into a plain item. Registering the BlockItem under the same id
    // "grout" keeps existing inventories' stacks decoding fine (save compat).
    public static final DeferredItem<BlockItem> GROUT = ITEMS.registerSimpleBlockItem("grout", ForgeweaveBlocks.GROUT);

    // Seared brick (docs/SCOPE.md M2 issue #93): upstream 1.12's plain crafting-material item
    // (TinkerCommons#searedBrick, "materials" item meta 0, NOTICE.md) -- produced by furnace-smelting
    // grout, and itself crafted 2x2 into the Seared Bricks block below.
    public static final DeferredItem<Item> SEARED_BRICK = ITEMS.registerSimpleItem("seared_brick");

    public static final DeferredItem<BlockItem> SEARED_STONE = ITEMS.registerSimpleBlockItem("seared_stone", ForgeweaveBlocks.SEARED_STONE);
    public static final DeferredItem<BlockItem> SEARED_COBBLESTONE = ITEMS.registerSimpleBlockItem("seared_cobblestone", ForgeweaveBlocks.SEARED_COBBLESTONE);
    public static final DeferredItem<BlockItem> SEARED_PAVER = ITEMS.registerSimpleBlockItem("seared_paver", ForgeweaveBlocks.SEARED_PAVER);
    public static final DeferredItem<BlockItem> SEARED_BRICKS = ITEMS.registerSimpleBlockItem("seared_bricks", ForgeweaveBlocks.SEARED_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_CRACKED_BRICKS = ITEMS.registerSimpleBlockItem("seared_cracked_bricks", ForgeweaveBlocks.SEARED_CRACKED_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_FANCY_BRICKS = ITEMS.registerSimpleBlockItem("seared_fancy_bricks", ForgeweaveBlocks.SEARED_FANCY_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SQUARE_BRICKS = ITEMS.registerSimpleBlockItem("seared_square_bricks", ForgeweaveBlocks.SEARED_SQUARE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_TRIANGLE_BRICKS = ITEMS.registerSimpleBlockItem("seared_triangle_bricks", ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SMALL_BRICKS = ITEMS.registerSimpleBlockItem("seared_small_bricks", ForgeweaveBlocks.SEARED_SMALL_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_ROAD = ITEMS.registerSimpleBlockItem("seared_road", ForgeweaveBlocks.SEARED_ROAD);
    public static final DeferredItem<BlockItem> SEARED_TILE = ITEMS.registerSimpleBlockItem("seared_tile", ForgeweaveBlocks.SEARED_TILE);
    public static final DeferredItem<BlockItem> SEARED_CREEPER = ITEMS.registerSimpleBlockItem("seared_creeper", ForgeweaveBlocks.SEARED_CREEPER);

    // The smeltery multiblock's blocks (docs/SCOPE.md M2 issue #95).
    public static final DeferredItem<BlockItem> STANDARD_CORE = ITEMS.registerSimpleBlockItem("standard_core", ForgeweaveBlocks.STANDARD_CORE);
    public static final DeferredItem<BlockItem> NETHER_CORE = ITEMS.registerSimpleBlockItem("nether_core", ForgeweaveBlocks.NETHER_CORE);
    public static final DeferredItem<BlockItem> SEARED_TANK = ITEMS.registerSimpleBlockItem("seared_tank", ForgeweaveBlocks.SEARED_TANK);
    public static final DeferredItem<BlockItem> SEARED_GAUGE = ITEMS.registerSimpleBlockItem("seared_gauge", ForgeweaveBlocks.SEARED_GAUGE);
    public static final DeferredItem<BlockItem> SEARED_WINDOW = ITEMS.registerSimpleBlockItem("seared_window", ForgeweaveBlocks.SEARED_WINDOW);
    public static final DeferredItem<BlockItem> SEARED_DRAIN = ITEMS.registerSimpleBlockItem("seared_drain", ForgeweaveBlocks.SEARED_DRAIN);

    // #107 batch: modifier reagent items (docs/SCOPE.md M2 issue #107) -- silky jewel, reinforced
    // plate, mending moss (plus its "moss" precursor), and the extra-slot item. Soulbound reuses the
    // vanilla nether star (modifier.ForgeweaveModifiers) so it needs no item of its own here.
    public static final DeferredItem<Item> MOSS = ITEMS.registerSimpleItem("moss");
    public static final DeferredItem<Item> MENDING_MOSS = ITEMS.registerSimpleItem("mending_moss");
    public static final DeferredItem<Item> REINFORCED_PLATE = ITEMS.registerSimpleItem("reinforced_plate");
    public static final DeferredItem<Item> SILKY_CLOTH = ITEMS.registerSimpleItem("silky_cloth");
    public static final DeferredItem<Item> SILKY_JEWEL = ITEMS.registerSimpleItem("silky_jewel");
    public static final DeferredItem<Item> EXTRA_MODIFIER = ITEMS.registerSimpleItem("extra_modifier");

    // #100 -- casting (docs/SCOPE.md M2 issue #100). The two casting blocks and the faucet, plus the
    // seven casts. Upstream 1.12 ships one `cast` item whose NBT names the part it was moulded around
    // and whose texture is generated at load time by compositing the blank cast with that part's
    // sprite (CustomTextureCreator); Forgeweave registers one item per cast instead -- the same
    // one-block-per-variant split issue #93 made for the seared bricks -- so a cast is a plain item
    // with a plain two-layer model and an Ingredient can match it without NBT.
    //
    // Casts are gold-only and reusable, which is upstream parity: no clay casts (upstream gates
    // those behind a config flag, off by default) and no sand casts (docs/SCOPE.md M2 non-goals).
    public static final DeferredItem<Item> CAST_INGOT = ITEMS.registerSimpleItem("cast_ingot");
    public static final DeferredItem<Item> CAST_NUGGET = ITEMS.registerSimpleItem("cast_nugget");
    public static final DeferredItem<Item> CAST_PICKAXE_HEAD = ITEMS.registerSimpleItem("cast_pickaxe_head");
    public static final DeferredItem<Item> CAST_SHOVEL_HEAD = ITEMS.registerSimpleItem("cast_shovel_head");
    public static final DeferredItem<Item> CAST_AXE_HEAD = ITEMS.registerSimpleItem("cast_axe_head");
    public static final DeferredItem<Item> CAST_TOOL_BINDING = ITEMS.registerSimpleItem("cast_tool_binding");
    public static final DeferredItem<Item> CAST_TOOL_HANDLE = ITEMS.registerSimpleItem("cast_tool_handle");

    public static final DeferredItem<BlockItem> CASTING_TABLE = ITEMS.registerSimpleBlockItem("casting_table", ForgeweaveBlocks.CASTING_TABLE);
    public static final DeferredItem<BlockItem> CASTING_BASIN = ITEMS.registerSimpleBlockItem("casting_basin", ForgeweaveBlocks.CASTING_BASIN);
    public static final DeferredItem<BlockItem> FAUCET = ITEMS.registerSimpleBlockItem("faucet", ForgeweaveBlocks.FAUCET);

    // #103 -- the four metal materials with no vanilla item forms yet (docs/SCOPE.md M2 issue #103):
    // cobalt, ardite, manyullyn (upstream 1.12 ingot/nugget art, NOTICE.md) and rose gold (no 1.12
    // counterpart -- its ingot/nugget are a recoloured derivation of upstream's copper ones, NOTICE.md).
    // Iron/copper/gold/netherite are vanilla items already; netherite scrap is vanilla too. Raw forms
    // have no 1.12 counterpart at all (1.12 predates the raw-ore item split). Raw cobalt/ardite are
    // maintainer-specified vanilla recolors (issue #140, NOTICE.md: raw_gold hue-shifted blue, and
    // netherite_scrap recoloured yellowish-orange, both preserving source shading -- see
    // scripts/recolor_raw_ore.py). Raw manyullyn/rose gold have no ore block source in this PR's
    // scope either, so they stay freshly authored placeholders, not derived (CLAUDE.md).
    public static final DeferredItem<Item> INGOT_COBALT = ITEMS.registerSimpleItem("cobalt_ingot");
    public static final DeferredItem<Item> NUGGET_COBALT = ITEMS.registerSimpleItem("cobalt_nugget");
    public static final DeferredItem<Item> RAW_COBALT = ITEMS.registerSimpleItem("raw_cobalt");
    public static final DeferredItem<Item> INGOT_ARDITE = ITEMS.registerSimpleItem("ardite_ingot");
    public static final DeferredItem<Item> NUGGET_ARDITE = ITEMS.registerSimpleItem("ardite_nugget");
    public static final DeferredItem<Item> RAW_ARDITE = ITEMS.registerSimpleItem("raw_ardite");
    public static final DeferredItem<Item> INGOT_MANYULLYN = ITEMS.registerSimpleItem("manyullyn_ingot");
    public static final DeferredItem<Item> NUGGET_MANYULLYN = ITEMS.registerSimpleItem("manyullyn_nugget");
    public static final DeferredItem<Item> RAW_MANYULLYN = ITEMS.registerSimpleItem("raw_manyullyn");
    public static final DeferredItem<Item> INGOT_ROSE_GOLD = ITEMS.registerSimpleItem("rose_gold_ingot");
    public static final DeferredItem<Item> NUGGET_ROSE_GOLD = ITEMS.registerSimpleItem("rose_gold_nugget");
    public static final DeferredItem<Item> RAW_ROSE_GOLD = ITEMS.registerSimpleItem("raw_rose_gold");

    // #104 -- cobalt + ardite nether ore block items (docs/SCOPE.md M2 issue #104).
    public static final DeferredItem<BlockItem> COBALT_ORE = ITEMS.registerSimpleBlockItem("cobalt_ore", ForgeweaveBlocks.COBALT_ORE);
    public static final DeferredItem<BlockItem> ARDITE_ORE = ITEMS.registerSimpleBlockItem("ardite_ore", ForgeweaveBlocks.ARDITE_ORE);

    private ForgeweaveItems() {}
}
