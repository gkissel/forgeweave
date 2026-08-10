package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Builds the hover-text lines for an assembled {@link ToolItem} stack: durability (broken-integrated),
 * mining speed, attack damage, tool tier, the three parts, and their traits. Split out of
 * {@code ToolItem#appendHoverText} so the compact/detailed line assembly is a plain static call
 * unit tests can drive with an explicit {@code detailed} flag, instead of only reachable through the
 * client-only Shift-key check that decides it at render time.
 *
 * <h2>Ported structure (NOTICE.md)</h2>
 *
 * <p>Upstream 1.12's {@code TinkersItem#addInformation} (tinkers-1.12
 * {@code library/tinkering/TinkersItem.java}) shows a compact tooltip by default -- the Broken/
 * modifier lines from {@code ToolCore#getTooltip} plus one always-on Attack Damage line -- and only
 * expands to {@code ToolCore#getInformation}'s full stat block
 * ({@code library/utils/TooltipBuilder.java}: durability, harvest level, mining speed, attack) when
 * Shift is held; Ctrl instead reveals {@code ToolCore#getTooltipComponents}'s per-part material and
 * trait breakdown. Forgeweave has no separate Ctrl view, so this keeps upstream's two-tier
 * compact/Shift split but folds the Ctrl-only parts/traits content into the Shift tier: compact
 * shows durability (or Broken), attack damage and -- since issue #105 -- the Modifiers and free
 * modifier slots upstream's compact tier also shows; Shift adds mining speed, tool tier, the three
 * parts, and their traits.
 *
 * <p>ponytail: no third Ctrl view -- one fewer key combo to test and document, and everything
 * upstream's Ctrl view showed still surfaces on Shift.
 */
final class ToolTooltip {

    private ToolTooltip() {}

    /**
     * @param registries nullable -- material names/colors and trait colors degrade to plain
     *     translatable text when unavailable, same as {@link PartItem}
     * @param detailed whether to show the Shift-only stat/parts/traits block
     * @param attackDamage the tool's currently effective attack damage (0 while Broken), computed by
     *     the caller since it needs {@code ToolItem}'s per-tool-type damage potential
     */
    static void append(ItemStack stack, HolderLookup.Provider registries, boolean detailed, float attackDamage,
            List<Component> tooltip) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return;
        }

        if (ToolItem.isBroken(stack)) {
            tooltip.add(Component.translatable("tooltip.forgeweave.broken")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else {
            tooltip.add(durabilityLine(stack));
        }
        tooltip.add(statLine("tooltip.forgeweave.attack_damage", attackDamage, StationText.ATTACK_COLOR));
        appendModifiers(stack, tooltip);

        if (!detailed) {
            return;
        }

        ToolStats.Stats stats = ForgeweaveModifiers.effectiveStats(stack);
        if (stats != null) {
            tooltip.add(statLine("tooltip.forgeweave.mining_speed", stats.miningSpeed(), StationText.SPEED_COLOR));
        }
        MaterialDisplay.lookup(registries, materials.head()).ifPresent(head -> tooltip.add(tierLine(stack, head)));

        tooltip.add(Component.empty());
        tooltip.add(MaterialDisplay.name(registries, materials.head()));
        tooltip.add(MaterialDisplay.name(registries, materials.binding()));
        tooltip.add(MaterialDisplay.name(registries, materials.handle()));

        List<ResourceLocation> traits = stack.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
        if (!traits.isEmpty()) {
            tooltip.add(Component.empty());
            for (ResourceLocation traitId : traits) {
                tooltip.add(traitLine(registries, materials, traitId));
            }
        }
    }

    /**
     * The tool's Modifiers and how many slots it has left, in the compact tier -- where upstream
     * 1.12 puts them ({@code ToolCore#getTooltip} lists each modifier then the free-modifier count,
     * and shows the count only while it is above zero).
     */
    private static void appendModifiers(ItemStack stack, List<Component> tooltip) {
        for (ModifierEntry entry : ForgeweaveModifiers.of(stack)) {
            tooltip.add(modifierLine(entry));
        }
        int free = ForgeweaveModifiers.freeSlots(stack);
        if (free > 0) {
            tooltip.add(Component.translatable("tooltip.forgeweave.modifier_slots", free)
                    .withStyle(Style.EMPTY.withColor(StationText.MODIFIER_COLOR)));
        }
    }

    /**
     * {@code Haste II (51/100)}: the modifier's name, its level in vanilla's own roman-numeral keys
     * once past level 1, and -- for a modifier whose levels take several applications -- how far into
     * the current one it is, which is upstream's {@code ModifierNBT.IntegerNBT#extraInfo}.
     */
    private static Component modifierLine(ModifierEntry entry) {
        MutableComponent line = ModifierApplication.name(entry.id())
                .copy().withStyle(Style.EMPTY.withColor(StationText.MODIFIER_COLOR));
        int level = ForgeweaveModifiers.displayLevel(entry.id(), entry.level());
        if (level > 1) {
            line.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + level));
        }
        int levelEnd = ForgeweaveModifiers.unitsForDisplayLevel(entry.id(), entry.level());
        if (levelEnd > 1) {
            line.append(Component.literal(" (" + entry.level() + "/" + levelEnd + ")")
                    .withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    private static Component durabilityLine(ItemStack stack) {
        int max = stack.getMaxDamage();
        int current = max - stack.getDamageValue();
        float ratio = max > 0 ? (float) current / max : 0f;
        return Component.translatable("tooltip.forgeweave.durability")
                .append(": ")
                .append(Component.literal(Integer.toString(current))
                        .withStyle(Style.EMPTY.withColor(StationText.durabilityColor(ratio))))
                .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Integer.toString(max))
                        .withStyle(Style.EMPTY.withColor(StationText.DURABILITY_COLOR)));
    }

    private static Component statLine(String key, float value, TextColor color) {
        return Component.translatable(key)
                .append(": ")
                .append(Component.literal(formatNumber(value)).withStyle(Style.EMPTY.withColor(color)));
    }

    private static Component tierLine(ItemStack stack, Material head) {
        return Component.translatable("tooltip.forgeweave.tool_tier")
                .append(": ")
                .append(tierName(stack, head));
    }

    private static Component traitLine(HolderLookup.Provider registries, ToolMaterials materials,
            ResourceLocation traitId) {
        String nameKey = "trait." + traitId.getNamespace() + "." + traitId.getPath() + ".name";
        String descKey = "trait." + traitId.getNamespace() + "." + traitId.getPath() + ".description";

        MutableComponent name = Component.translatable(nameKey);
        TextColor color = MaterialDisplay.traitColor(registries,
                List.of(materials.head(), materials.binding(), materials.handle()), traitId);
        if (color != null) {
            name = name.withStyle(Style.EMPTY.withColor(color));
        }
        return name.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
    }

    /**
     * {@code "incorrect_for_stone_tool"} -&gt; {@code tooltip.forgeweave.tier.stone}; the tag path
     * already names the tier, so this is a lookup key rather than player-facing text of its own
     * (issue #65 -- the previous version capitalized the stripped tag path directly, which was
     * untranslatable and, for wood, worded differently than {@code material.forgeweave.wood}).
     *
     * <p>#106 batch: prefers the stack's <em>current</em> deny-drops tag ({@link #effectiveTierTag})
     * over the head material's starting one, so a diamond/emerald bump shows here -- those modifiers
     * rewrite the stack's rule (issue #106, {@code ModifierApplication#retuneToolTier}) without
     * touching the head material record this method used to read exclusively.
     */
    private static Component tierName(ItemStack stack, Material head) {
        TagKey<Block> tag = effectiveTierTag(stack).orElseGet(head::incorrectForTool);
        String stripped = tag.location().getPath()
                .replace("incorrect_for_", "")
                .replace("_tool", "");
        return Component.translatable("tooltip.forgeweave.tier." + stripped);
    }

    /** The stack's actual current deny-drops tag, if its vanilla {@code tool} component has one this ladder knows. */
    private static Optional<TagKey<Block>> effectiveTierTag(ItemStack stack) {
        Tool component = stack.get(DataComponents.TOOL);
        if (component == null) {
            return Optional.empty();
        }
        for (Tool.Rule rule : component.rules()) {
            if (rule.speed().isEmpty()) {
                int index = ForgeweaveModifiers.tierIndexOf(rule.blocks());
                return index < 0 ? Optional.empty() : Optional.of(ForgeweaveModifiers.tierTag(index));
            }
        }
        return Optional.empty();
    }

    private static String formatNumber(float value) {
        return StationText.formatNumber(value);
    }
}
