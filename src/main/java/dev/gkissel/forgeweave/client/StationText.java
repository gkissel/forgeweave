package dev.gkissel.forgeweave.client;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.LauncherStats;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * The lines the stations' info panels and the part/tool item tooltips show, and the one place stat
 * text gets its colour (issues #47 and #64). Everything here is a read of data that already exists
 * on the stack or in the material registry -- an assembled tool carries its {@link ToolStats.Stats},
 * its {@link ToolMaterials} and its trait ids as data components written at assembly time -- so no
 * stat formula is restated: {@code ToolStats} stays the only place that computes them, and this is
 * only how they are worded and coloured.
 *
 * <p>Trait names and descriptions come from the {@code trait.<namespace>.<path>.name}/{@code
 * .description} keys {@code ForgeweaveLanguageProvider} already ships (issue #12), keyed by the ids
 * on the stack; nothing here needs {@code ForgeweaveTraits}, so an unknown id from a datapack
 * material degrades to a visible untranslated key rather than to nothing at all.
 *
 * <h2>Colours (issue #64, NOTICE.md)</h2>
 *
 * <p>Upstream 1.12 gives each stat its own colour and leaves the label uncoloured
 * ({@code library/materials/HeadMaterialStats#COLOR_*} and friends, applied by
 * {@code AbstractMaterialStats#formatNumber}); durability instead uses
 * {@code CustomFontColor#valueToColorCode}'s red-to-green ramp so a worn tool reads as worn, and
 * trait names take the granting material's own colour. Those constants and that ramp live here
 * only, and {@code ToolTooltip}/{@code PartItem} read them from here rather than restating them.
 *
 * <p>ponytail: this class sits in the {@code client} package because that is where its only original
 * callers were, but it deliberately touches <em>no</em> {@code net.minecraft.client} type -- item
 * tooltips are built on both sides and read it too. Keep it that way.
 */
public final class StationText {
    /** Upstream {@code HeadMaterialStats#COLOR_Durability} -- {@code valueToColorCode(1f)}. */
    public static final TextColor DURABILITY_COLOR = durabilityColor(1.0F);
    /** Upstream {@code HeadMaterialStats#COLOR_Speed} (120, 160, 205). */
    public static final TextColor SPEED_COLOR = TextColor.fromRgb(0x78A0CD);
    /** Upstream {@code HeadMaterialStats#COLOR_Attack} (215, 100, 100). */
    public static final TextColor ATTACK_COLOR = TextColor.fromRgb(0xD76464);
    /** Upstream {@code HandleMaterialStats#COLOR_Modifier} (185, 185, 90), shared by the bowstring row. */
    public static final TextColor MODIFIER_COLOR = TextColor.fromRgb(0xB9B95A);
    /** Upstream {@code BowMaterialStats#COLOR_Drawspeed} (128, 128, 128). */
    public static final TextColor DRAWSPEED_COLOR = TextColor.fromRgb(0x808080);
    /** Upstream {@code BowMaterialStats#COLOR_Range} (140, 175, 175). */
    public static final TextColor RANGE_COLOR = TextColor.fromRgb(0x8CAFAF);
    /** Upstream {@code BowMaterialStats#COLOR_Damage} (155, 80, 65). */
    public static final TextColor BOW_DAMAGE_COLOR = TextColor.fromRgb(0x9B5041);

    /** Trailing-zero-free numbers, so 1.0 reads "1" and 1.25 reads "1.25" (upstream's {@code Util.df}). */
    private static final DecimalFormat FORMAT =
            new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    /**
     * Durability, mining speed and attack damage of an assembled tool, in upstream's order and with
     * its Modifiers applied ({@code ForgeweaveModifiers#effectiveStats}) -- the panel shows what the
     * tool actually does, not what its materials alone would have given it.
     */
    public static List<Component> toolStats(ItemStack tool) {
        ToolStats.Stats stats = ForgeweaveModifiers.effectiveStats(tool);
        if (stats == null) {
            return List.of();
        }
        int remaining = stats.durability() - tool.getDamageValue();
        List<Component> lines = new ArrayList<>();
        lines.add(durabilityStat(remaining, stats.durability()));
        lines.add(stat("mining_speed", stats.miningSpeed(), SPEED_COLOR));
        if (tool.getItem() instanceof BowItem bow) {
            lines.addAll(launcherStats(tool, bow.drawTime())); // M3.5 #394, upstream's LAUNCHER block
        }
        lines.add(stat("attack_damage", stats.attackDamage(), ATTACK_COLOR));
        return List.copyOf(lines);
    }

    /**
     * An assembled bow's ranged stats (M3.5 #394), upstream {@code ToolCore#getInformation}'s
     * {@code Category.LAUNCHER} block: draw speed shown as seconds to full draw
     * ({@code TooltipBuilder#addDrawSpeed}: {@code drawTime / (20 * drawSpeed)}), the range
     * multiplier, the bonus damage. Empty for a stack with no launcher stats. Reads the stored
     * component rather than {@code BowItem#drawSpeed} on purpose: this is the assembled number the
     * limbs gave; a draw-speed modifier (M3.5-5) will decide then whether the line shows its effect.
     */
    public static List<Component> launcherStats(ItemStack tool, int drawTime) {
        LauncherStats launcher = BowItem.launcherStats(tool);
        if (launcher == null) {
            return List.of();
        }
        return List.of(
                stat("drawspeed", drawTime / (20.0F * launcher.drawSpeed()), DRAWSPEED_COLOR),
                stat("range", launcher.range(), RANGE_COLOR),
                stat("bonus_damage", launcher.bonusDamage(), BOW_DAMAGE_COLOR));
    }

    /**
     * The Modifiers on an assembled tool and the slots it has left, for the Tool Station's second
     * info panel. Names come from {@code modifier.<namespace>.<path>.name}, keyed by the ids on the
     * stack exactly as trait lines are, so an id this version doesn't implement still shows up
     * (as a visible untranslated key) rather than vanishing.
     */
    public static List<Component> toolModifiers(ItemStack tool) {
        List<Component> lines = new ArrayList<>();
        for (ModifierEntry entry : ForgeweaveModifiers.of(tool)) {
            MutableComponent line = ModifierApplication.name(entry.id()).copy()
                    .withStyle(Style.EMPTY.withColor(MODIFIER_COLOR));
            int level = ForgeweaveModifiers.displayLevel(entry.id(), entry.level());
            if (level > 1) {
                line.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + level));
            }
            lines.add(withHover(line, ModifierApplication.description(entry.id())));
        }
        lines.add(Component.translatable("gui.forgeweave.tool_station.modifier_slots",
                ForgeweaveModifiers.freeSlots(tool)).withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    /** The materials an assembled tool is made of, each in its own tint (two or three -- #155). */
    public static List<Component> toolMaterials(@Nullable HolderLookup.Provider registries, ToolMaterials materials) {
        return materials.all().stream().map(id -> (Component) MaterialDisplay.name(registries, id)).toList();
    }

    /**
     * What one material contributes, whichever part it ends up in (the Part Builder's info panel),
     * grouped as upstream's {@code GuiPartBuilder#setDisplayForMaterial} groups it: one underlined
     * heading per stat type, its stats under it, and a blank line between groups (with no trailing
     * one). Issue #376 -- this used to be all six stats run together with nothing to read them by.
     *
     * <p>Contains {@code null} spacers, which is what {@link InfoPanel} wants and what any other
     * caller has to cope with; the three group accessors below are the flat alternative.
     */
    public static List<Component> materialStats(Material material) {
        List<Component> lines = new ArrayList<>();
        statGroup(lines, "head", headStats(material));
        statGroup(lines, "handle", handleStats(material));
        statGroup(lines, "extra", extraStats(material));
        statGroup(lines, "bow", bowStats(material));
        statGroup(lines, "bowstring", bowstringStats(material));
        if (!lines.isEmpty()) {
            lines.remove(lines.size() - 1); // upstream drops the last group's trailing spacer
        }
        // Not List.copyOf: that rejects the null spacers this list is made of.
        return Collections.unmodifiableList(lines);
    }

    /**
     * The heading key is issue #379's {@code tooltip.forgeweave.stat_type.*}, not a second family of
     * its own: both surfaces are upstream's one {@code stat.<type>.name}, so a part's item tooltip
     * and the Part Builder's panel cannot end up calling the same stat block different things.
     */
    private static void statGroup(List<Component> lines, String key, List<Component> stats) {
        // Issue #392: a material only carries some of the blocks (a bowstring material has nothing
        // but its bowstring), and a heading over nothing is worse than no heading.
        if (stats.isEmpty()) {
            return;
        }
        lines.add(Component.translatable("tooltip.forgeweave.stat_type." + key).withStyle(ChatFormatting.UNDERLINE));
        lines.addAll(stats);
        lines.add(null); // spacer
    }

    /** What a head part of this material contributes: the durability pool, mining speed and attack. */
    public static List<Component> headStats(Material material) {
        return material.head().<List<Component>>map(head -> List.of(
                stat("durability", head.durability(), DURABILITY_COLOR),
                stat("mining_speed", head.miningSpeed(), SPEED_COLOR),
                stat("attack_damage", head.attackDamage(), ATTACK_COLOR)))
                .orElseGet(List::of);
    }

    /** What a handle part of this material contributes: a durability multiplier plus a flat bonus. */
    public static List<Component> handleStats(Material material) {
        return material.handle().<List<Component>>map(handle -> List.of(
                stat("handle_modifier", handle.durabilityModifier(), MODIFIER_COLOR),
                stat("handle_durability", handle.durability(), DURABILITY_COLOR)))
                .orElseGet(List::of);
    }

    /** What a binding (upstream's "extra") part of this material contributes: flat durability. */
    public static List<Component> extraStats(Material material) {
        return material.extraDurability().<List<Component>>map(extra -> List.of(
                stat("extra_durability", extra, DURABILITY_COLOR)))
                .orElseGet(List::of);
    }

    /**
     * What a bow limb of this material contributes (issue #392, upstream {@code
     * BowMaterialStats#getLocalizedInfo}). The draw speed is shown as {@code 1/drawspeed} -- upstream
     * stores "how fast", displays "how long" -- which is why steel's 0.4 reads as 2.5 while paper's
     * 1.5 reads as 0.67.
     */
    public static List<Component> bowStats(Material material) {
        return material.bow().<List<Component>>map(bow -> List.of(
                stat("drawspeed", 1.0F / bow.drawspeed(), DRAWSPEED_COLOR),
                stat("range", bow.range(), RANGE_COLOR),
                stat("bonus_damage", bow.bonusDamage(), BOW_DAMAGE_COLOR)))
                .orElseGet(List::of);
    }

    /** What a bow string of this material contributes: one multiplier (upstream {@code BowStringMaterialStats}). */
    public static List<Component> bowstringStats(Material material) {
        return material.bowstring().<List<Component>>map(bowstring -> List.of(
                stat("bowstring_modifier", bowstring.modifier(), MODIFIER_COLOR)))
                .orElseGet(List::of);
    }

    /**
     * One line per trait: its name in {@code color}, its description as hover text.
     *
     * <p>Issue #376 (maintainer decision, 2026-08-14) collapsed this from three lines per trait
     * (name, grey description, spacer) to one, which is what upstream's {@code GuiPartBuilder} and
     * {@code GuiToolStation} both show -- a name in the material's colour with
     * {@code getLocalizedDesc()} handed to the panel as that row's tooltip. It roughly thirds the
     * scroll length of a many-trait material, which is the point.
     */
    public static List<Component> traits(@Nullable TextColor color, List<ResourceLocation> traitIds) {
        return traitIds.stream().map(id -> traitLine(color, id)).toList();
    }

    /**
     * An assembled tool's traits, each in the colour of whichever of its three materials granted it
     * -- the same resolution {@code ToolTooltip} does, so a tool's tooltip and the Tool Station's
     * trait panel never disagree about which material a trait came from.
     */
    public static List<Component> toolTraits(@Nullable HolderLookup.Provider registries, ToolMaterials materials,
            List<ResourceLocation> traitIds) {
        List<ResourceLocation> materialIds = materials.all();
        return traitIds.stream()
                .map(id -> traitLine(MaterialDisplay.traitColor(registries, materialIds, id), id))
                .toList();
    }

    /** The trait ids stored on an assembled tool, or an empty list for anything else. */
    public static List<ResourceLocation> traitIdsOf(ItemStack stack) {
        List<ResourceLocation> ids = stack.get(ForgeweaveDataComponents.TRAITS.get());
        return ids == null ? List.of() : ids;
    }

    /**
     * One {@code Label: value} line with only the value coloured, as upstream's {@code formatNumber},
     * explaining itself on hover from {@code gui.forgeweave.stat.<key>.desc} (issue #376, ported from
     * upstream's {@code stat.<type>.<stat>.desc} entries -- {@code IMaterialStats#getLocalizedDesc},
     * which every panel that shows a stat row hands to the panel as that row's tooltip).
     */
    public static Component stat(String key, float value, TextColor color) {
        return withHover(Component.translatable("gui.forgeweave.stat." + key,
                Component.literal(formatNumber(value)).withStyle(Style.EMPTY.withColor(color))),
                Component.translatable("gui.forgeweave.stat." + key + ".desc"));
    }

    /** {@code Durability: 120/160}, the remaining half on upstream's wear ramp and the max on green. */
    public static Component durabilityStat(int remaining, int max) {
        float ratio = max > 0 ? (float) remaining / max : 0.0F;
        Component value = Component.literal(formatNumber(remaining))
                .withStyle(Style.EMPTY.withColor(durabilityColor(ratio)))
                .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatNumber(max)).withStyle(Style.EMPTY.withColor(DURABILITY_COLOR)));
        return withHover(Component.translatable("gui.forgeweave.stat.durability", value),
                Component.translatable("gui.forgeweave.stat.durability.desc"));
    }

    /**
     * Upstream's {@code CustomFontColor#valueToColorCode}: a red-through-green ramp over the first
     * third of the hue wheel, clamped so a nearly-dead tool still reads as red rather than wrapping.
     */
    public static TextColor durabilityColor(float ratio) {
        float hue = Mth.clamp(ratio / 3.0F, 0.01F, 0.5F);
        return TextColor.fromRgb(Color.HSBtoRGB(hue, 0.65F, 0.8F) & 0xFFFFFF);
    }

    public static String formatNumber(float value) {
        return FORMAT.format(value);
    }

    /**
     * The Part Builder's centred readout, upstream's {@code gui.partbuilder.material_value} =
     * {@code "Material Value: %s %s"} ({@code GuiPartBuilder:116-138}, issue #378). Two things had
     * been missing: the amount is normalised to <b>ingots</b> and may be fractional
     * ({@code matchAmount / (float) Material.VALUE_Ingot} through {@code Util.df}, which is
     * {@link #formatNumber}) where this used to print the raw shard-unit count, and the material's
     * own name follows it -- with two material slots (issue #306) that name is the only thing saying
     * which of the two stacks the total was counted against.
     *
     * <p>Only the <em>amount</em> goes {@code DARK_RED} when it falls short of the pattern's cost;
     * upstream wraps just that substring and lets the label stay in the line's own grey.
     */
    public static Component materialValue(float ingots, boolean enough, ResourceLocation materialId) {
        Component amount = Component.literal(formatNumber(ingots));
        return Component.translatable("gui.forgeweave.part_builder.material_value",
                enough ? amount : amount.copy().withStyle(ChatFormatting.DARK_RED),
                MaterialDisplay.plainName(materialId));
    }

    private static Component traitLine(@Nullable TextColor color, ResourceLocation id) {
        String base = "trait." + id.getNamespace() + "." + id.getPath();
        MutableComponent name = Component.translatable(base + ".name");
        return withHover(
                color == null ? name.withStyle(ChatFormatting.WHITE) : name.withStyle(Style.EMPTY.withColor(color)),
                Component.translatable(base + ".description"));
    }

    /**
     * A panel row that explains itself on hover (issue #258 for modifier rows, generalised to stat
     * and trait rows by #376): the row itself as a heading over {@code description} in grey, carried
     * as a {@code SHOW_TEXT} event on the row's own style so the screen's hit-testing needs no
     * parallel tooltip list -- which is upstream's shape, a {@code tips} list indexed in lockstep
     * with the text list. A {@code HoverEvent} is chat-common, so this class stays client-free.
     */
    private static Component withHover(MutableComponent line, Component description) {
        Component hover = line.copy().append("\n").append(description.copy().withStyle(ChatFormatting.GRAY));
        return line.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private StationText() {}
}
