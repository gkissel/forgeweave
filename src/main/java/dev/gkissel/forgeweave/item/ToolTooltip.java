package dev.gkissel.forgeweave.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.config.ForgeweaveClientConfig; // #276
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolLevel;
import dev.gkissel.forgeweave.tool.ToolLevelName;
import dev.gkissel.forgeweave.tool.ToolLeveling;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trackb.TrackBOre;
import dev.gkissel.forgeweave.trait.EnergyBuffer;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.Trait;

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
 * compact/Shift split but folds the Ctrl-only content into the Shift tier: compact shows durability
 * (or Broken), attack damage and -- since issue #105 -- the Modifiers and free modifier slots
 * upstream's compact tier also shows; Shift adds mining speed, tool tier, and then one section per
 * part slot.
 *
 * <p>Maintainer decision 2026-08-14 (issue #380) supersedes the flat shape this record described
 * before: the Shift tier's flat material-name list plus one global trait list carried no part
 * attribution at all, so a two-material tool never said which part each trait or stat came from and
 * a multi-head tool could not say so even in principle. It is now upstream {@code
 * ToolCore#getTooltipComponents} (ToolCore.java:323-364 at the pinned commit) rendered inline: for
 * each slot in the tool's own part order, the part item's name for that slot's material in the
 * material colour and underlined, that part's stat block, that part's traits in the material colour
 * (de-duplicated within the section, upstream's {@code usedTraits} set), one blank line between
 * sections. Still no third keybind.
 *
 * <p>ponytail: no third Ctrl view -- one fewer key combo to test and document, and everything
 * upstream's Ctrl view showed still surfaces on Shift. A trait no part granted -- today only an
 * embossment's donor trait -- has no section to sit in and so is named by its Modifier line in the
 * compact tier instead, exactly as upstream's per-component view leaves it.
 */
final class ToolTooltip {

    private ToolTooltip() {}

    /**
     * Whether the detailed block should be shown at all: Shift is held <em>and</em> upstream 1.12's
     * {@code extraTooltips} is on ({@link ForgeweaveClientConfig#EXTRA_TOOLTIPS}, its default, issue
     * #276 -- upstream reads the same {@code Config.extraTooltips && shift} pair in {@code
     * TinkersItem#getTooltip}). Shared by {@link ToolItem} and {@link PartItem}, the two items with
     * a Shift tier.
     *
     * <p>The {@code &&} order matters: {@code hasShiftDown()} is NeoForge's {@code
     * TooltipFlagExtension}, false anywhere but a client, so a dedicated server short-circuits out
     * before touching a {@code CLIENT}-type config it never loaded.
     */
    static boolean detailed(TooltipFlag flag) {
        return flag.hasShiftDown() && ForgeweaveClientConfig.EXTRA_TOOLTIPS.get();
    }

    /**
     * Whether the compact tier should close with the "Hold Shift for Stats" hint (issue #379):
     * Shift is not held and the Shift tier it advertises actually exists.
     *
     * <p>Deliberate improvement over upstream 1.12, recorded here because it is a behavior
     * difference: {@code TinkersItem#addInformation} prints {@code tooltip.tool.holdShift}
     * unconditionally (:453-456) but only honours Shift when {@code Config.extraTooltips} is on
     * (:466), so with that option off the hint advertises a key that does nothing. Forgeweave gates
     * the hint on the same {@link ForgeweaveClientConfig#EXTRA_TOOLTIPS} the tier itself reads.
     *
     * <p>Unlike {@link #detailed}, whose {@code hasShiftDown()} is false off-client and
     * short-circuits before the config, this branch is <em>true</em> off-client -- hence the
     * {@code isLoaded()} guard, so a dedicated server (and a unit test) never reads a {@code CLIENT}
     * spec that was never loaded. The line assembly itself is {@link #appendShiftHint}, which takes
     * the decision as a parameter so tests can drive both answers.
     */
    static boolean shiftHint(TooltipFlag flag) {
        return !flag.hasShiftDown()
                && ForgeweaveClientConfig.SPEC.isLoaded()
                && ForgeweaveClientConfig.EXTRA_TOOLTIPS.get();
    }

    /** Upstream's blank line plus {@code tooltip.tool.holdShift}, appended after the compact block. */
    static void appendShiftHint(boolean show, List<Component> tooltip) {
        if (show) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.forgeweave.hold_shift_stats")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

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

        if (stack.getItem() instanceof AmmoToolItem) {
            // #697: upstream ProjectileCore#getInformation leads with TooltipBuilder#addAmmo and
            // never shows the durability line -- ammo is durability / 10, so the raw number read
            // as "each shot costs 10".
            tooltip.add(StationText.ammoStat(stack));
        } else if (ToolItem.isBroken(stack)) {
            // #379: upstream keeps the "Durability: " label on a broken tool and only swaps the
            // number for the bold dark-red word (TooltipBuilder#addDurability(true)), so the line
            // still reads as the durability line it replaces.
            tooltip.add(Component.translatable("tooltip.forgeweave.durability")
                    .append(": ")
                    .append(Component.translatable("tooltip.forgeweave.broken")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        } else {
            tooltip.add(durabilityLine(stack));
        }
        // #830: an energy-buffer trait's stored/capacity, shown right after durability -- the other
        // resource pool a tool can carry. Absent entirely for a tool with no energy trait (a zero
        // capacity), so this costs nothing for the vast majority of tools.
        int energyCapacity = ForgeweaveTraits.energyCapacity(stack);
        if (energyCapacity > 0) {
            tooltip.add(energyLine(EnergyBuffer.stored(stack), energyCapacity));
        }
        appendLevel(stack, ForgeweaveConfig.enabled(ForgeweaveConfig.TOOL_LEVELING), tooltip);
        // M3.5 #394, upstream ToolCore#getInformation's Category.LAUNCHER block: draw speed as
        // seconds to full draw (TooltipBuilder#addDrawSpeed: drawTime / (20 * drawSpeed)), range,
        // bonus damage -- between durability and attack, where upstream puts them.
        appendLauncherLines(stack, tooltip);
        tooltip.add(statLine("tooltip.forgeweave.attack_damage", attackDamage, StationText.ATTACK_COLOR));
        // The M1 tool innate retrofit (issue #164): fixed per-tool-type, so it's shown compact like
        // Attack Damage rather than gated behind Shift like traits, which are per-material.
        ForgeweaveInnates.innateId(stack).ifPresent(id -> tooltip.add(innateLine(id)));
        appendModifiers(stack, tooltip);

        if (!detailed) {
            // Issue #955: a trait's live state that fits on one line (bloodtally, evolved) is worth
            // showing without Shift too; a multi-line breakdown (warmemory) stays Shift-only.
            appendCompactTraitStateLines(stack, tooltip);
            return;
        }

        // Upstream ToolCore#getInformation (ToolCore.java:301-304) gates the harvest level and the
        // mining speed on hasCategory(Category.HARVEST); a bow's only category is the
        // Category.LAUNCHER BowCore's constructor adds (BowCore.java:64), so neither line is its
        // (M3.5 #401). info.addAttack() above is ungated, which is why the attack line stays.
        if (!(stack.getItem() instanceof BowItem)) {
            ToolStats.Stats stats = ForgeweaveModifiers.effectiveStats(stack);
            if (stats != null) {
                tooltip.add(statLine("tooltip.forgeweave.mining_speed", stats.miningSpeed(), StationText.SPEED_COLOR));
            }
            if (stack.getItem() instanceof MattockItem) {
                appendMattockTierLines(registries, materials, tooltip);
            } else {
                MaterialDisplay.lookup(registries, materials.head())
                        .ifPresent(head -> tooltip.add(tierLine(stack, head)));
            }
        }

        appendPartSections(stack, registries, materials, tooltip);
    }

    /**
     * Upstream {@code ToolCore#getTooltipComponents} (ToolCore.java:323-364), inline in the Shift
     * tier per issue #380: one section per part slot, in the tool's own slot order
     * ({@code ToolAssemblyRecipes.Entry}, which is where the multi-head tools' positional sections
     * come from -- the hammer's two large plates get a section each because they occupy two slots).
     *
     * <p>Bails on a stack whose material list is shorter than its part list, upstream's own guard
     * (ToolCore.java:328-330), and on a tool no assembly table row explains -- there is no part order
     * to walk without one.
     */
    private static void appendPartSections(ItemStack stack, HolderLookup.Provider registries,
            ToolMaterials materials, List<Component> tooltip) {
        Optional<ToolAssemblyRecipes.Entry> found = ToolAssemblyRecipes.entryFor(stack);
        if (found.isEmpty()) {
            return;
        }
        ToolAssemblyRecipes.Entry entry = found.get();
        List<ResourceLocation> materialIds = materials.all();
        if (materialIds.size() < entry.slotCount()) {
            return;
        }

        for (int slot = 0; slot < entry.slotCount(); slot++) {
            ResourceLocation materialId = materialIds.get(slot);
            PartItem part = entry.part(slot);
            Optional<Material> material = MaterialDisplay.lookup(registries, materialId);
            TextColor color = material.map(Material::color).orElse(null);

            tooltip.add(Component.empty());
            tooltip.add(partNameLine(registries, materialId, part, color));
            material.ifPresent(resolved -> {
                tooltip.addAll(part.stats(resolved));
                // Upstream's usedTraits set: a trait its material grants twice is named once.
                for (ResourceLocation traitId : resolved.traits().forPart(part.kind()).stream().distinct().toList()) {
                    tooltip.add(traitLine(traitId, color));
                    appendTraitStateLines(traitId, stack, tooltip);
                }
            });
        }
    }

    /**
     * {@code Stone Pickaxe Head}: the part item's own name for this slot's material, in that
     * material's colour and underlined -- upstream's {@code material.getTextColor() +
     * UNDERLINE + partStack.getDisplayName()}. The material name and the part name are separate
     * translatable pieces joined by {@code tooltip.forgeweave.part_name}, so a language that orders
     * them the other way round can say so.
     */
    private static Component partNameLine(HolderLookup.Provider registries, ResourceLocation materialId,
            PartItem part, TextColor color) {
        Style style = Style.EMPTY.withUnderlined(true);
        return Component.translatable("tooltip.forgeweave.part_name",
                        MaterialDisplay.name(registries, materialId), new ItemStack(part).getHoverName())
                .withStyle(color == null ? style : style.withColor(color));
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
     * {@code Haste II (51/100)}: the modifier's name for its current level ({@code
     * StationText#modifierName} -- since parity audit T26/issue #457 that is upstream's leveled name
     * where one exists, "Haster" rather than "Haste II"), in the modifier's own colour ({@code
     * ForgeweaveModifiers#color}, upstream's {@code ModifierNBT#getColorString}), and -- for a
     * modifier whose levels take several applications -- how far into the current one it is, which is
     * upstream's {@code ModifierNBT.IntegerNBT#extraInfo}.
     */
    private static Component modifierLine(ModifierEntry entry) {
        MutableComponent line = StationText.modifierName(entry)
                .withStyle(Style.EMPTY.withColor(ForgeweaveModifiers.color(entry.id())));
        int levelEnd = ForgeweaveModifiers.unitsForDisplayLevel(entry.id(), entry.level());
        if (levelEnd > 1) {
            line.append(Component.literal(" (" + entry.level() + "/" + levelEnd + ")")
                    .withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    /** {@code Pierce: Deals ...}, same name-colon-description shape as {@link #traitLine} without a material color. */
    private static Component innateLine(ResourceLocation id) {
        String base = "tooltip.forgeweave.innate." + id.getPath();
        return Component.translatable(base + ".name").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable(base + ".description").withStyle(ChatFormatting.GRAY));
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

    /** {@code Stored Energy: 12000/32000} -- same shape as {@link #durabilityLine}, in Forge Energy's colour. */
    private static Component energyLine(int stored, int capacity) {
        return Component.translatable("tooltip.forgeweave.energy")
                .append(": ")
                .append(Component.literal(Integer.toString(stored))
                        .withStyle(Style.EMPTY.withColor(StationText.ENERGY_COLOR)))
                .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Integer.toString(capacity))
                        .withStyle(Style.EMPTY.withColor(StationText.ENERGY_COLOR)));
    }

    /**
     * The level name and XP progress lines (docs/SCOPE.md M7, D-M7-8; issue #922), right after the
     * durability/energy block -- upstream {@code Tooltips#addTooltips}' own placement. Omitted
     * entirely when {@code toolLeveling} is off, or the stack carries no {@code tool_level} component
     * at all (a tool that has never earned or banked any XP) -- {@link ToolLevel#of} degrading an
     * absent component to {@link ToolLevel#NONE} is not distinguishable from an explicit level-0
     * component here, hence the direct {@code stack.has} check rather than reading through it.
     *
     * <p>Package-private overload with the flag passed in, so a unit test can exercise the off path a
     * {@code SERVER} config spec no test environment loads would otherwise always answer "on" for --
     * same seam as {@link ToolLeveling#addXp(ItemStack, int, net.minecraft.server.level.ServerPlayer,
     * boolean)}.
     */
    static void appendLevel(ItemStack stack, boolean levelingEnabled, List<Component> tooltip) {
        if (!levelingEnabled || !stack.has(ForgeweaveDataComponents.TOOL_LEVEL.get())) {
            return;
        }
        ToolLevel level = ToolLevel.of(stack);
        tooltip.add(levelNameLine(level.level()));
        if (ToolLeveling.canLevelUp(level.level(), ForgeweaveConfig.maximumLevels())) {
            int needed = ToolLeveling.xpForLevelup(level.level(), ToolLeveling.baseXp(stack), ForgeweaveConfig.levelMultiplier());
            tooltip.add(xpLine(level.xp(), needed));
        }
    }

    /** {@code Level: Like new}, upstream {@code tooltip.level: <name>} -- the name itself carries {@link ToolLevelName#color}. */
    private static Component levelNameLine(int level) {
        return Component.translatable("tooltip.forgeweave.level")
                .append(": ")
                .append(ToolLevelName.name(level));
    }

    /** {@code XP: 120 / 500}, upstream {@code tooltip.xp: <xp> / <needed>} in plain white. */
    private static Component xpLine(int xp, int needed) {
        return Component.translatable("tooltip.forgeweave.xp")
                .append(": ")
                .append(Component.literal(xp + " / " + needed));
    }

    /** The three launcher lines, for a bow only -- see {@link StationText#launcherStats}. */
    private static void appendLauncherLines(ItemStack stack, List<Component> tooltip) {
        if (stack.getItem() instanceof BowItem bow) {
            tooltip.addAll(StationText.launcherStats(stack, bow));
        }
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

    /**
     * Upstream {@code Mattock#getInformation} (Mattock.java:161-186, parity audit T66) shows two
     * separate harvest-level lines -- "Axe Level" / "Shovel Level" -- instead of the generic
     * {@link #tierLine} every other tool gets. Each one is read straight off that head's own
     * material, not the tool's single blended tier ({@code ToolAssemblyRecipes#highestTierHead},
     * issue #294): {@code Mattock#buildTagData} stores {@code axeLevel}/{@code shovelLevel} off each
     * head's {@code HeadMaterialStats.harvestLevel} at assembly, and {@code ModDiamond}/{@code
     * ModEmerald} (ModDiamond.java:18-31) only ever bump the generic blended field -- which {@code
     * Mattock#getHarvestLevel} never consults for its two toolClasses ("axe"/"shovel" always match
     * the override, so {@code super.getHarvestLevel} is unreachable). So unlike {@link #tierLine},
     * these two stay fixed at assembly time and deliberately do not read {@link #effectiveTierTag}.
     *
     * <p>Mattock's part order ({@code ToolConstants#MATTOCK}) is {@code (handle, axe_head,
     * shovel_head)} -- slots 1 and 2 of {@link ToolMaterials#parts()}.
     */
    private static void appendMattockTierLines(HolderLookup.Provider registries, ToolMaterials materials,
            List<Component> tooltip) {
        List<ResourceLocation> parts = materials.parts();
        if (parts.size() < 3) {
            return;
        }
        MaterialDisplay.lookup(registries, parts.get(1))
                .ifPresent(axe -> tooltip.add(familyTierLine("tooltip.forgeweave.axe_level", axe)));
        MaterialDisplay.lookup(registries, parts.get(2))
                .ifPresent(shovel -> tooltip.add(familyTierLine("tooltip.forgeweave.shovel_level", shovel)));
    }

    private static Component familyTierLine(String key, Material head) {
        return Component.translatable(key).append(": ").append(tierNameForTag(head.incorrectForTool()));
    }

    /**
     * The full {@code incorrect_for_*_tool} ladder mapped to the {@code tooltip.forgeweave.tier.*}
     * keys (issue #254). Gold is deliberately absent -- it is a speed tier, not a mining-capability
     * one -- as is any modded tag, so callers omit the line rather than guess.
     *
     * <p>Issue #877 (the JC10 reversal) adds the three rungs above netherite -- {@code hardcinder},
     * {@code warspar}, {@code resonite} -- {@link TrackBOre}'s own tags, named after the Track B
     * material anchoring each rung.
     */
    private static final Map<TagKey<Block>, String> TIER_LANG_KEYS = Map.of(
            BlockTags.INCORRECT_FOR_WOODEN_TOOL, "wooden",
            BlockTags.INCORRECT_FOR_STONE_TOOL, "stone",
            BlockTags.INCORRECT_FOR_IRON_TOOL, "iron",
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, "diamond",
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, "netherite",
            TrackBOre.INCORRECT_FOR_HARDCINDER_TOOL, "hardcinder",
            TrackBOre.INCORRECT_FOR_WARSPAR_TOOL, "warspar",
            TrackBOre.INCORRECT_FOR_RESONITE_TOOL, "resonite");

    /**
     * The {@code Tool Tier: X} line for a bare {@code incorrect_for_tool} tag -- head-part tooltips
     * (issue #254), where there is no assembled stack whose Modifiers could have retuned the tier,
     * so the material's own tag is the whole answer. Empty for a tag off the ladder above.
     */
    static Optional<Component> tierLine(TagKey<Block> incorrectForTool) {
        return Optional.ofNullable(TIER_LANG_KEYS.get(incorrectForTool))
                .map(tier -> Component.translatable("tooltip.forgeweave.tool_tier")
                        .append(": ")
                        .append(Component.translatable("tooltip.forgeweave.tier." + tier)));
    }

    /**
     * A trait in the colour of the material granting it. Since issue #380 that material is the one
     * in the section's own slot rather than the first of the tool's materials to grant the id, so a
     * trait two materials share is attributed to the part actually being described.
     */
    private static Component traitLine(ResourceLocation traitId, TextColor color) {
        String base = "trait." + traitId.getNamespace() + "." + traitId.getPath();
        MutableComponent name = Component.translatable(base + ".name");
        if (color != null) {
            name = name.withStyle(Style.EMPTY.withColor(color));
        }
        return name.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable(base + ".description").withStyle(ChatFormatting.GRAY));
    }

    /**
     * {@link Trait#stateLines}, under {@code traitId}'s own name line (issue #955) -- bloodtally's
     * kill count and bonus, warmemory's per-type breakdown, evolved's Draconic-upgrade count. A no-op
     * for every trait that overrides nothing, which is most of them.
     */
    private static void appendTraitStateLines(ResourceLocation traitId, ItemStack stack, List<Component> tooltip) {
        Trait trait = ForgeweaveTraits.lookup(traitId);
        if (trait != null) {
            trait.stateLines(stack, tooltip::add);
        }
    }

    /**
     * The compact tier's half of issue #955: every trait the tool carries (read straight off {@code
     * ForgeweaveDataComponents#TRAITS}, since the compact tier has no per-part sections or material
     * color to attribute a line to), but only the ones whose {@link Trait#stateLines} comes to exactly
     * one line -- bloodtally and evolved, never warmemory's multi-line breakdown.
     */
    private static void appendCompactTraitStateLines(ItemStack stack, List<Component> tooltip) {
        for (ResourceLocation traitId : stack.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.<ResourceLocation>of())) {
            Trait trait = ForgeweaveTraits.lookup(traitId);
            if (trait == null) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            trait.stateLines(stack, lines::add);
            if (lines.size() == 1) {
                tooltip.add(lines.get(0));
            }
        }
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
        return tierNameForTag(tag);
    }

    private static Component tierNameForTag(TagKey<Block> tag) {
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
