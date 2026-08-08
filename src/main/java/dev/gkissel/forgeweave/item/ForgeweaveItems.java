package dev.gkissel.forgeweave.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

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

    public static final DeferredItem<PartItem> PART_PICKAXE_HEAD = part("pickaxe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_SHOVEL_HEAD = part("shovel_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_AXE_HEAD = part("axe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_TOOL_BINDING = part("tool_binding", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_TOOL_HANDLE = part("tool_handle", PartItem.Kind.HANDLE);

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

    public static final DeferredItem<BlockItem> TOOL_STATION = ITEMS.registerSimpleBlockItem("tool_station", ForgeweaveBlocks.TOOL_STATION);

    // The Crafting Station (docs/SCOPE.md M1 issue #40): same retextured-table item shape as the two
    // blocks above (ForgeweaveDataComponents#TEXTURE carries the crafting wood).
    public static final DeferredItem<BlockItem> CRAFTING_STATION = ITEMS.registerSimpleBlockItem("crafting_station", ForgeweaveBlocks.CRAFTING_STATION);

    // The Stencil Table (docs/SCOPE.md M1 issue #44): same retextured-table item shape as the three
    // stations above.
    public static final DeferredItem<BlockItem> STENCIL_TABLE = ITEMS.registerSimpleBlockItem("stencil_table", ForgeweaveBlocks.STENCIL_TABLE);

    // The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66): plain block items, not
    // retextured-table items -- neither chest carries a TEXTURE component (ChestBlock javadoc).
    public static final DeferredItem<BlockItem> PATTERN_CHEST = ITEMS.registerSimpleBlockItem("pattern_chest", ForgeweaveBlocks.PATTERN_CHEST);
    public static final DeferredItem<BlockItem> PART_CHEST = ITEMS.registerSimpleBlockItem("part_chest", ForgeweaveBlocks.PART_CHEST);

    private ForgeweaveItems() {}
}
