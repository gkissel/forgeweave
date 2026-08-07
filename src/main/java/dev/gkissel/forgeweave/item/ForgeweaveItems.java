package dev.gkissel.forgeweave.item;

import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The blank pattern, the five part patterns, and the five part items (CONTEXT.md glossary;
 * docs/SCOPE.md M1 content manifest). Patterns are reusable templates, so they stack to 1 like
 * upstream Tinkers' 1.12 patterns; parts stack normally since their material is a plain data
 * component rather than per-instance stats.
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

    private ForgeweaveItems() {}
}
