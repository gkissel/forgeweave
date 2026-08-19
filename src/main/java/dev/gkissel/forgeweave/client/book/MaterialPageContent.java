package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * What a material's book page shows, as data: the display-item bar, the per-stat-block groups and
 * the flavour quote's key. Upstream's {@code library/book/content/ContentMaterial#build} (1.12 clone,
 * pinned commit in NOTICE.md, MIT) computes all of this inline while placing elements; here it is
 * split out so {@link BookScreen} is left with nothing but measuring and drawing -- and so the
 * layout decisions are unit-testable without a client, the same seam {@link BookLayout} and
 * {@link BookGeometry} already are (issue #633).
 *
 * <p>Everything upstream reads is read here from the same places:
 *
 * <ul>
 *   <li>{@code addDisplayItems} builds a column of at most {@value #DISPLAY_ITEMS} items -- the
 *       material's representative item, then a Part Builder if it {@code isCraftable()} and a
 *       Casting Basin if it {@code isCastable()}, each with a "how to make it" tooltip, then demo
 *       tools built entirely out of the material from a fixed nine-tool bar.
 *   <li>{@code addStatsDisplay} emits one block per stat type the material carries: the parts that
 *       use that stat as a cycling icon, the stat type's own name underlined beside it, then that
 *       block's stat lines and its traits, each explaining itself on hover.
 *   <li>the {@code <material>.flavour} string, italicised in quotes, when the book's language file
 *       defines one -- upstream's own defines exactly two (wood and stone).
 * </ul>
 */
public final class MaterialPageContent {

    /** {@code addDisplayItems}: the side bar stops at nine items, icons and demo tools together. */
    public static final int DISPLAY_ITEMS = 9;

    /**
     * {@code ElementItem.ITEM_SWITCH_TICKS} (Mantle 1.12, pinned commit in NOTICE.md): a cycling
     * item element advances every 90 draws, which is what the stat blocks' part icons cycle at.
     */
    public static final int ITEM_SWITCH_TICKS = 90;

    /**
     * Upstream {@code addDisplayItems}' hardcoded bar, verbatim and in its order: {@code pickaxe,
     * mattock, broadSword, hammer, cleaver, shuriken, fryPan, lumberAxe, battleSign}. Forgeweave has
     * every one of the nine (the shuriken since issue #448), so nothing is dropped.
     */
    private static final List<Supplier<? extends Item>> DEMO_TOOLS = List.of(
            ForgeweaveItems.TOOL_PICKAXE,
            ForgeweaveItems.TOOL_MATTOCK,
            ForgeweaveItems.TOOL_BROADSWORD,
            ForgeweaveItems.TOOL_HAMMER,
            ForgeweaveItems.TOOL_CLEAVER,
            ForgeweaveItems.TOOL_SHURIKEN,
            ForgeweaveItems.TOOL_FRYING_PAN,
            ForgeweaveItems.TOOL_LUMBERAXE,
            ForgeweaveItems.TOOL_BATTLESIGN);

    private MaterialPageContent() {
    }

    /**
     * One item of the display bar. A {@code null} tooltip means "show the stack's own name", which
     * is upstream's {@code ElementItem} default; the two station icons override it with the
     * sentence that says how the material is made.
     */
    public record Icon(ItemStack stack, @Nullable Component tooltip) {}

    /**
     * One stat type's block: the parts that draw from it (a cycling icon upstream), the underlined
     * name of the stat type, its stat lines and the traits a part of that kind grants.
     */
    public record StatGroup(PartItem.Kind kind, String nameKey, List<Component> stats, List<Component> traits) {}

    /**
     * The stat blocks this material carries, in upstream's {@code HEAD, HANDLE, EXTRA} order with
     * the two ranged blocks after them. A material with no block of a kind contributes no group at
     * all -- upstream's {@code addStatsDisplay} returns early on a {@code null} stat.
     *
     * <p>Deviation from upstream's page, deliberate: the {@code BOW}/{@code BOWSTRING} groups are
     * here rather than in a separate bow-materials section, which is where upstream's
     * {@code BowMaterialSectionTransformer} puts them (audit §3.9, still open as its own follow-up).
     */
    public static List<StatGroup> statGroups(Material material) {
        List<StatGroup> groups = new ArrayList<>();
        group(groups, material, PartItem.Kind.HEAD, "head", StationText.headStats(material));
        group(groups, material, PartItem.Kind.HANDLE, "handle", StationText.handleStats(material));
        group(groups, material, PartItem.Kind.EXTRA, "extra", StationText.extraStats(material));
        group(groups, material, PartItem.Kind.BOW, "bow", StationText.bowStats(material));
        group(groups, material, PartItem.Kind.BOWSTRING, "bowstring", StationText.bowstringStats(material));
        return List.copyOf(groups);
    }

    private static void group(List<StatGroup> groups, Material material, PartItem.Kind kind, String key,
            List<Component> stats) {
        if (stats.isEmpty()) {
            return;
        }
        groups.add(new StatGroup(kind, "tooltip.forgeweave.stat_type." + key, stats, traitLines(material, kind)));
    }

    /**
     * {@code getTraitLines}: one line per trait the given part kind grants, dark grey and underlined,
     * explaining itself on hover in the material's own colour. Upstream asks
     * {@code getAllTraitsForStats} once per stat block, which falls back to the material's general
     * list, so a material whose traits are all general repeats them under every block -- reproduced
     * here through {@link Material.Traits#forPart}, which is the same fallback.
     *
     * <p>Deliberately not {@code StationText#traits}: that is the info panels' shape (the name in
     * the material's colour, the hover a heading plus a grey description), and upstream's book page
     * words the same two facts the other way round.
     */
    private static List<Component> traitLines(Material material, PartItem.Kind kind) {
        return material.traits().forPart(kind).stream().map(id -> traitLine(material, id)).toList();
    }

    private static Component traitLine(Material material, ResourceLocation id) {
        String base = "trait." + id.getNamespace() + "." + id.getPath();
        Component hover = Component.translatable(base + ".description")
                .withStyle(Style.EMPTY.withColor(material.color()));
        return Component.translatable(base + ".name")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /**
     * Every registered part that draws from {@code kind}, stamped with this material -- upstream's
     * {@code TinkerRegistry.getToolParts()} filtered by {@code part.hasUseForStat(...)}, which is
     * {@code Material#hasStatsFor} read from the other end. Shown as one cycling icon beside the
     * stat type's name.
     */
    public static List<ItemStack> partsFor(PartItem.Kind kind, ResourceLocation materialId) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof PartItem part && part.kind() == kind) {
                ItemStack stack = new ItemStack(item);
                stack.set(ForgeweaveDataComponents.MATERIAL.get(), materialId);
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    /**
     * The display bar's leading icons: the representative item, then the stations that turn this
     * material into parts. Upstream gates the Part Builder on {@code Material#isCraftable} -- which
     * is exactly {@link PartBuilderRecipes#craftableInPartBuilder}, config and all -- and the Casting
     * Basin on {@code isCastable()}, i.e. the material having a molten fluid. Forgeweave states
     * castability as casting recipes rather than a flag (see {@code Material#castOnly}), so the
     * question asked here is whether the material has a molten fluid at all, which is the fact
     * upstream's flag actually stands for and the one the tooltip has to name anyway.
     */
    public static List<Icon> craftIcons(ResourceLocation id, Material material) {
        List<Icon> icons = new ArrayList<>();
        ItemStack representative = representativeItem(material);
        if (!representative.isEmpty()) {
            icons.add(new Icon(representative, null));
        }
        if (PartBuilderRecipes.craftableInPartBuilder(material)) {
            icons.add(new Icon(new ItemStack(ForgeweaveItems.PART_BUILDER.get()),
                    Component.translatable("book.forgeweave.material.craft_partbuilder")));
        }
        moltenFluid(id).ifPresent(fluid -> icons.add(new Icon(new ItemStack(ForgeweaveItems.CASTING_BASIN.get()),
                Component.translatable("book.forgeweave.material.craft_casting",
                        fluid.getFluidType().getDescription()))));
        return List.copyOf(icons);
    }

    /** This material's molten fluid, if the mod registered one: {@code forgeweave:molten_<material>}. */
    public static Optional<Fluid> moltenFluid(ResourceLocation id) {
        return BuiltInRegistries.FLUID.getOptional(
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "molten_" + id.getPath()));
    }

    /**
     * The item a material's icon shows -- upstream's {@code Material#getRepresentativeItem()},
     * which is the item the material is repaired and built with. Forgeweave has no separate
     * representative field, so the repair ingredient stands in for it; an ingredient with no items
     * (a tag no pack fills) yields an empty stack and simply contributes no icon.
     */
    public static ItemStack representativeItem(Material material) {
        Ingredient repair = material.repairItem();
        ItemStack[] items = repair.getItems();
        return items.length > 0 ? items[0] : ItemStack.EMPTY;
    }

    /**
     * The tools of upstream's display bar this material can build every part of, capped at
     * {@code limit}. Upstream builds each one and keeps it only when {@code hasValidMaterials}, which
     * for a tool whose every slot is filled with the one material is precisely "the material carries
     * every stat block those slots read".
     */
    public static List<ToolAssemblyRecipes.Entry> demoTools(Material material, int limit) {
        List<ToolAssemblyRecipes.Entry> entries = new ArrayList<>();
        for (Supplier<? extends Item> tool : DEMO_TOOLS) {
            if (entries.size() >= limit) {
                break;
            }
            Item item = tool.get();
            ToolAssemblyRecipes.ENTRIES.stream()
                    .filter(entry -> entry.tool().get() == item)
                    .findFirst()
                    .filter(entry -> entry.parts().stream().allMatch(part -> material.hasStatsFor(part.kind())))
                    .ifPresent(entries::add);
        }
        return List.copyOf(entries);
    }

    /**
     * The lang key of a material's inspirational quote. Upstream reads {@code <material>.flavour}
     * out of the book's own language file and skips the quote entirely when it is absent, which for
     * its shipped book is every material but wood and stone.
     */
    public static String flavourKey(ResourceLocation id) {
        return "material." + id.getNamespace() + "." + id.getPath() + ".flavour";
    }
}
