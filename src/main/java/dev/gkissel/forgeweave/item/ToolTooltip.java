package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
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
 * trait breakdown. Forgeweave has no modifier system yet (M2, not shipped) and no
 * separate Ctrl view, so this keeps upstream's two-tier compact/Shift split but folds the Ctrl-only
 * parts/traits content into the Shift tier: compact shows durability (or Broken) and attack damage,
 * Shift adds mining speed, tool tier, the three parts, and their traits.
 *
 * <p>ponytail: no modifier line (M2, out of scope for this issue) and no third Ctrl view -- one
 * fewer key combo to test and document, and everything upstream's Ctrl view showed still surfaces
 * on Shift.
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

        if (!detailed) {
            return;
        }

        ToolStats.Stats stats = stack.get(ForgeweaveDataComponents.TOOL_STATS.get());
        if (stats != null) {
            tooltip.add(statLine("tooltip.forgeweave.mining_speed", stats.miningSpeed(), StationText.SPEED_COLOR));
        }
        MaterialDisplay.lookup(registries, materials.head()).ifPresent(head -> tooltip.add(tierLine(head)));

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

    private static Component tierLine(Material head) {
        return Component.translatable("tooltip.forgeweave.tool_tier")
                .append(": ")
                .append(Component.literal(tierName(head)));
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

    /** {@code "incorrect_for_stone_tool"} -&gt; {@code "Stone"}; the tag path already names the tier. */
    private static String tierName(Material head) {
        String stripped = head.incorrectForTool().location().getPath()
                .replace("incorrect_for_", "")
                .replace("_tool", "");
        return stripped.isEmpty() ? stripped : Character.toUpperCase(stripped.charAt(0)) + stripped.substring(1);
    }

    private static String formatNumber(float value) {
        return StationText.formatNumber(value);
    }
}
