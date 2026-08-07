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
 * (CONTEXT.md glossary; docs/SCOPE.md M1 content manifest). Patterns are reusable templates, so
 * they stack to 1 like upstream Tinkers' 1.12 patterns; parts stack normally since their material
 * is a plain data component rather than per-instance stats.
 */
public final class ForgeweaveItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Forgeweave.MODID);

    public static final DeferredItem<Item> PATTERN_BLANK =
            ITEMS.registerSimpleItem("pattern_blank", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> PATTERN_PICKAXE_HEAD =
            ITEMS.registerSimpleItem("pattern_pickaxe_head", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> PATTERN_SHOVEL_HEAD =
            ITEMS.registerSimpleItem("pattern_shovel_head", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> PATTERN_AXE_HEAD =
            ITEMS.registerSimpleItem("pattern_axe_head", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> PATTERN_TOOL_BINDING =
            ITEMS.registerSimpleItem("pattern_tool_binding", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> PATTERN_TOOL_HANDLE =
            ITEMS.registerSimpleItem("pattern_tool_handle", new Item.Properties().stacksTo(1));

    public static final DeferredItem<PartItem> PART_PICKAXE_HEAD = ITEMS.registerItem("pickaxe_head", PartItem::new);
    public static final DeferredItem<PartItem> PART_SHOVEL_HEAD = ITEMS.registerItem("shovel_head", PartItem::new);
    public static final DeferredItem<PartItem> PART_AXE_HEAD = ITEMS.registerItem("axe_head", PartItem::new);
    public static final DeferredItem<PartItem> PART_TOOL_BINDING = ITEMS.registerItem("tool_binding", PartItem::new);
    public static final DeferredItem<PartItem> PART_TOOL_HANDLE = ITEMS.registerItem("tool_handle", PartItem::new);

    public static final DeferredItem<BlockItem> PART_BUILDER = ITEMS.registerSimpleBlockItem("part_builder", ForgeweaveBlocks.PART_BUILDER);

    // Assembled tools (docs/SCOPE.md M1 issues #10/#11). Deliberately not DiggerItem/TieredItem:
    // a Tier is a fixed table of durability/speed/tier-tag, and a Forgeweave tool's are all derived
    // per stack from its parts' materials, so the vanilla `tool` and `max_damage` components are
    // written at assembly time instead (ToolAssemblyRecipes). What stays fixed per tool type lives
    // here: the mineable/* tag it is meant for, and upstream 1.12's attack speed and damage
    // potential (ToolCore#attackSpeed/#damagePotential in tools/tools/{Pickaxe,Shovel,Hatchet}).
    // stacksTo(1) like every other Forgeweave equipment item.
    public static final DeferredItem<ToolItem> TOOL_PICKAXE = ITEMS.registerItem("pickaxe",
            properties -> new ToolItem(properties, BlockTags.MINEABLE_WITH_PICKAXE, 1.2f, 1.0f),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_SHOVEL = ITEMS.registerItem("shovel",
            properties -> new ToolItem(properties, BlockTags.MINEABLE_WITH_SHOVEL, 1.0f, 0.9f),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_HATCHET = ITEMS.registerItem("hatchet",
            properties -> new ToolItem(properties, BlockTags.MINEABLE_WITH_AXE, 1.1f, 1.1f),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> TOOL_STATION = ITEMS.registerSimpleBlockItem("tool_station", ForgeweaveBlocks.TOOL_STATION);

    private ForgeweaveItems() {}
}
