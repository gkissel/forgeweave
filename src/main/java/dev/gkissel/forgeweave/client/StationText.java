package dev.gkissel.forgeweave.client;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
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
    /** Upstream {@code HandleMaterialStats#COLOR_Modifier} (185, 185, 90). */
    public static final TextColor MODIFIER_COLOR = TextColor.fromRgb(0xB9B95A);

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
        return List.of(
                durabilityStat(remaining, stats.durability()),
                stat("mining_speed", stats.miningSpeed(), SPEED_COLOR),
                stat("attack_damage", stats.attackDamage(), ATTACK_COLOR));
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
            lines.add(line);
        }
        lines.add(Component.translatable("gui.forgeweave.tool_station.modifier_slots",
                ForgeweaveModifiers.freeSlots(tool)).withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    /** The materials an assembled tool is made of, each in its own tint (two or three -- #155). */
    public static List<Component> toolMaterials(@Nullable HolderLookup.Provider registries, ToolMaterials materials) {
        return materials.all().stream().map(id -> (Component) MaterialDisplay.name(registries, id)).toList();
    }

    /** What one material contributes, whichever part it ends up in (the Part Builder's info panel). */
    public static List<Component> materialStats(Material material) {
        List<Component> lines = new ArrayList<>(headStats(material));
        lines.addAll(handleStats(material));
        lines.addAll(extraStats(material));
        return List.copyOf(lines);
    }

    /** What a head part of this material contributes: the durability pool, mining speed and attack. */
    public static List<Component> headStats(Material material) {
        return List.of(
                stat("durability", material.head().durability(), DURABILITY_COLOR),
                stat("mining_speed", material.head().miningSpeed(), SPEED_COLOR),
                stat("attack_damage", material.head().attackDamage(), ATTACK_COLOR));
    }

    /** What a handle part of this material contributes: a durability multiplier plus a flat bonus. */
    public static List<Component> handleStats(Material material) {
        return List.of(
                stat("handle_modifier", material.handle().durabilityModifier(), MODIFIER_COLOR),
                stat("handle_durability", material.handle().durability(), DURABILITY_COLOR));
    }

    /** What a binding (upstream's "extra") part of this material contributes: flat durability. */
    public static List<Component> extraStats(Material material) {
        return List.of(stat("extra_durability", material.extraDurability(), DURABILITY_COLOR));
    }

    /** Each trait's name in {@code color} followed by its description, blank line between entries. */
    public static List<Component> traits(@Nullable TextColor color, List<ResourceLocation> traitIds) {
        List<Component> lines = new ArrayList<>();
        for (ResourceLocation id : traitIds) {
            lines.addAll(traitLines(color, id));
            lines.add(null); // spacer
        }
        if (!lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /**
     * An assembled tool's traits, each in the colour of whichever of its three materials granted it
     * -- the same resolution {@code ToolTooltip} does, so a tool's tooltip and the Tool Station's
     * trait panel never disagree about which material a trait came from.
     */
    public static List<Component> toolTraits(@Nullable HolderLookup.Provider registries, ToolMaterials materials,
            List<ResourceLocation> traitIds) {
        List<ResourceLocation> materialIds = materials.all();
        List<Component> lines = new ArrayList<>();
        for (ResourceLocation id : traitIds) {
            lines.addAll(traitLines(MaterialDisplay.traitColor(registries, materialIds, id), id));
            lines.add(null); // spacer
        }
        if (!lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /** The trait ids stored on an assembled tool, or an empty list for anything else. */
    public static List<ResourceLocation> traitIdsOf(ItemStack stack) {
        List<ResourceLocation> ids = stack.get(ForgeweaveDataComponents.TRAITS.get());
        return ids == null ? List.of() : ids;
    }

    /** One {@code Label: value} line with only the value coloured, as upstream's {@code formatNumber}. */
    public static Component stat(String key, float value, TextColor color) {
        return Component.translatable("gui.forgeweave.stat." + key,
                Component.literal(formatNumber(value)).withStyle(Style.EMPTY.withColor(color)));
    }

    /** {@code Durability: 120/160}, the remaining half on upstream's wear ramp and the max on green. */
    public static Component durabilityStat(int remaining, int max) {
        float ratio = max > 0 ? (float) remaining / max : 0.0F;
        Component value = Component.literal(formatNumber(remaining))
                .withStyle(Style.EMPTY.withColor(durabilityColor(ratio)))
                .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatNumber(max)).withStyle(Style.EMPTY.withColor(DURABILITY_COLOR)));
        return Component.translatable("gui.forgeweave.stat.durability", value);
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

    private static List<Component> traitLines(@Nullable TextColor color, ResourceLocation id) {
        String base = "trait." + id.getNamespace() + "." + id.getPath();
        MutableComponent name = Component.translatable(base + ".name");
        return List.of(
                color == null ? name.withStyle(ChatFormatting.WHITE) : name.withStyle(Style.EMPTY.withColor(color)),
                Component.translatable(base + ".description").withStyle(ChatFormatting.GRAY));
    }

    private StationText() {}
}
