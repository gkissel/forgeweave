package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialStage;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

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

    /** Issue #1104: "Stage: %s", right under the material's name. */
    public static final String STAGE_LINE = "book.forgeweave.material.stage";
    /** Issue #1104: the header over the items a cast-only material melts out of. */
    public static final String MADE_BY_MELTING = "book.forgeweave.material.made_by_melting";
    /** Issue #1104: the header over an alloy's inputs. */
    public static final String MADE_BY_ALLOYING = "book.forgeweave.material.made_by_alloying";
    /** Issue #1104: one alloy input, "%1$s parts %2$s". */
    public static final String ALLOY_INPUT = "book.forgeweave.material.alloy_input";
    /** Issue #1104: the header over the materials that grant one level of a trait. */
    public static final String TRAIT_GRANTED_BY = "book.forgeweave.trait.granted_by";
    /** Issue #1104: a trait family's level heading, "%s" being the roman numeral. */
    public static final String TRAIT_LEVEL = "book.forgeweave.trait.level";

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
     * One trait line of a stat block: the drawn line, and the id it came from so the page can link
     * it to that trait's reference entry (issue #1104).
     */
    public record TraitLine(ResourceLocation id, Component line) {}

    /**
     * One stat type's block: the parts that draw from it (a cycling icon upstream), the underlined
     * name of the stat type, its stat lines and the traits a part of that kind grants.
     */
    public record StatGroup(PartItem.Kind kind, String nameKey, List<Component> stats, List<TraitLine> traits) {}

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
        group(groups, material, PartItem.Kind.SHAFT, "shaft", StationText.shaftStats(material));
        group(groups, material, PartItem.Kind.FLETCHING, "fletching", StationText.fletchingStats(material));
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
    private static List<TraitLine> traitLines(Material material, PartItem.Kind kind) {
        return material.traits().forPart(kind).stream()
                .map(id -> new TraitLine(id, traitLine(material, id))).toList();
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

    /**
     * The stage line under a material's name (issue #1104): the stage's own name, explaining on
     * hover what a player needs before the stage opens.
     */
    public static Component stageLine(MaterialStage stage) {
        Component hover = Component.translatable(stage.unlockKey()).withStyle(ChatFormatting.GRAY);
        return Component.translatable(STAGE_LINE, Component.translatable(stage.nameKey()))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /**
     * One row of the "how it is made" block: what to draw, what it says, and the material whose own
     * page the row jumps to, or {@code null} for a row that is not a link.
     */
    public record Source(ItemStack icon, Component label, @Nullable ResourceLocation material) {}

    /**
     * How this material is made, for the block under its display bar (issue #1104). Before this the
     * page said only "Can be cast from Molten Glowveil" and never that molten glowveil is dreadalloy
     * plus sparkalloy plus brimspar -- the recipe was in JEI and nowhere in the book
     * (review 05-book.md &sect;5).
     *
     * <p>Both registries this reads are datapack registries with a network codec
     * ({@code Forgeweave#registerDataPackRegistries}), so the client has them on join and this runs
     * without asking the server anything. An alloy shows its inputs, each linking to the input
     * material's own page so a multi-step chain can be walked backwards; anything else with a molten
     * fluid shows what melts into it.
     *
     * @return the header key and its rows, or an empty optional for a material made no other way
     *         than in the Part Builder (the display bar's own icon already says so)
     */
    public static Optional<Made> madeFrom(HolderLookup.Provider registries, ResourceLocation id, Material material) {
        Fluid fluid = moltenFluid(id).orElse(null);
        if (fluid == null) {
            return Optional.empty();
        }
        List<Source> alloy = alloyInputs(registries, fluid);
        if (!alloy.isEmpty()) {
            return Optional.of(new Made(MADE_BY_ALLOYING, alloy));
        }
        List<Source> melting = meltingInputs(registries, fluid, material);
        return melting.isEmpty() ? Optional.empty() : Optional.of(new Made(MADE_BY_MELTING, melting));
    }

    /** A "how it is made" block: one header and its rows. */
    public record Made(String headerKey, List<Source> sources) {

        public Made {
            sources = List.copyOf(sources);
        }
    }

    private static List<Source> alloyInputs(HolderLookup.Provider registries, Fluid result) {
        return registries.lookupOrThrow(AlloyRecipe.REGISTRY).listElements()
                .map(Holder::value)
                .filter(recipe -> recipe.result().getFluid() == result)
                .findFirst()
                .map(recipe -> recipe.inputs().stream().map(input -> alloyInput(registries, input)).toList())
                .orElse(List.of());
    }

    private static Source alloyInput(HolderLookup.Provider registries, FluidStack input) {
        ResourceLocation material = materialOf(input.getFluid());
        Component label = Component.translatable(ALLOY_INPUT, input.getAmount(),
                input.getFluid().getFluidType().getDescription());
        ItemStack icon = registries.lookupOrThrow(Material.REGISTRY)
                .get(ResourceKey.create(Material.REGISTRY, material))
                .map(holder -> representativeItem(holder.value()))
                .orElse(ItemStack.EMPTY);
        return new Source(icon, label, material);
    }

    /** The material a {@code forgeweave:molten_<material>} fluid belongs to. */
    private static ResourceLocation materialOf(Fluid fluid) {
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        return ResourceLocation.fromNamespaceAndPath(fluidId.getNamespace(),
                fluidId.getPath().startsWith("molten_") ? fluidId.getPath().substring("molten_".length())
                        : fluidId.getPath());
    }

    /**
     * How many melting sources a page shows. Iron melts from nineteen things -- its ingot, its
     * block, its ore, a chain, an anvil, a crossbow, four pieces of chainmail -- and listing them
     * all pushed the stat blocks onto the next leaf. Three is the ingot, the ore and one more.
     */
    public static final int MELTING_SOURCES = 3;

    /**
     * Where one melting recipe sorts in the "melts from" list: the material's own repair item
     * first, then the ore melts, then everything else. Split out from {@link #meltingInputs} so the
     * ordering is testable without a datapack registry.
     *
     * @param representative the material's repair item, or an empty stack when its ingredient has
     *                       no item on this install
     */
    public static int meltingRank(MeltingRecipe recipe, ItemStack representative) {
        if (!representative.isEmpty() && recipe.input().test(representative)) {
            return 0;
        }
        return recipe.ore() ? 1 : 2;
    }

    /**
     * What melts into this fluid: the material's own repair item first, then the ore melts, then
     * whatever else, capped at {@link #MELTING_SOURCES}. The repair item leads because it is the
     * form a player actually holds ("iron ingot"), not a thing they could recycle into it; JEI is
     * still where the full list of melts lives.
     */
    private static List<Source> meltingInputs(HolderLookup.Provider registries, Fluid result, Material material) {
        ItemStack representative = representativeItem(material);
        List<Source> sources = new ArrayList<>();
        registries.lookupOrThrow(MeltingRecipe.REGISTRY).listElements()
                .map(Holder::value)
                .filter(recipe -> recipe.fluid() == result)
                .sorted(Comparator.comparingInt(recipe -> meltingRank(recipe, representative)))
                .forEach(recipe -> {
                    ItemStack[] items = recipe.input().getItems();
                    if (items.length > 0 && sources.size() < MELTING_SOURCES) {
                        sources.add(new Source(items[0], items[0].getHoverName(), null));
                    }
                });
        return List.copyOf(sources);
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
     * A trait reference entry's level heading, e.g. "Level II" (issue #1104). Only worth drawing on
     * a family that has more than one level; a single-level trait's page is its name and its
     * description, the way the material page's own hover already words it.
     *
     * <p>The numeral is vanilla's {@code enchantment.level.<n>}, which is the same string the
     * station panel puts after a modifier's name ({@code ModifierApplication#displayName}), so a
     * level reads the same everywhere. Past ten vanilla has no numeral and the digit stands in.
     */
    public static Component traitLevel(int level) {
        Component numeral = level <= 10 ? Component.translatable("enchantment.level." + level)
                : Component.literal(String.valueOf(level));
        return Component.translatable(TRAIT_LEVEL, numeral).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** A trait reference entry's own description line: the rung's {@code .description} string. */
    public static Component traitDescription(ResourceLocation trait) {
        return Component.translatable("trait." + trait.getNamespace() + "." + trait.getPath() + ".description");
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
