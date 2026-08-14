package dev.gkissel.forgeweave.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;

/**
 * A tool part item (pickaxe head, tool binding, ...). Carries the material it was crafted from as
 * a {@link ForgeweaveDataComponents#MATERIAL} data component and shows that material's name, the
 * stats that material contributes <em>through this kind of part</em>, and its trait. Per-material
 * rendering is a client-side color tint (see {@code ForgeweaveItemColors}), not a distinct texture
 * per material.
 *
 * <h2>Hover stats (issue #64, NOTICE.md)</h2>
 *
 * <p>Upstream 1.12's {@code library/tools/ToolPart#addInformation} shows the part's traits always
 * and, behind Shift, only the stat blocks the part actually uses -- {@code hasUseForStat} filters
 * {@code material.getAllStats()} down by the {@code PartMaterialType}s that part appears in, so a
 * pickaxe head shows the Head block and a tool rod shows the Handle block. Forgeweave's parts each
 * appear in exactly one role, so {@link Kind} names that role at registration instead of being
 * derived from a recipe table, and the lines themselves come straight from {@link StationText} --
 * the same methods the Part Builder's info panel renders, so the two can't drift apart.
 *
 * <p>ponytail: no "Hold Shift for stats" hint line -- {@code ToolItem} doesn't ship one either, and
 * adding it would mean a lang key whose only job is to describe a key the player already pressed.
 */
public class PartItem extends Item {

    /** Which of upstream's three part-stat blocks a part draws from ({@code PartMaterialType}). */
    public enum Kind {
        HEAD,
        HANDLE,
        /** Upstream's "extra" stat block: bindings, and anything else that only adds durability. */
        EXTRA,
        /** No stat block of its own -- the shard, which is a leftover rather than a buildable part. */
        NONE
    }

    private final Kind kind;

    public PartItem(Properties properties) {
        this(properties, Kind.NONE);
    }

    public PartItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    /** Which stat block -- and so which of a material's trait scopes -- this part draws from. */
    public Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        append(stack, context.registries(), ToolTooltip.detailed(flag), tooltip);
    }

    /**
     * The hover lines, with {@code detailed} taken as a parameter rather than read from the Shift key
     * so unit tests can drive both tiers (same split as {@code ToolTooltip#append}).
     *
     * @param registries nullable -- without it the material can't be resolved, so the tooltip stays
     *     at the plain, uncolored material-name line
     */
    void append(ItemStack stack, @Nullable HolderLookup.Provider registries, boolean detailed,
            List<Component> tooltip) {
        // Issue #271: the sharpening kit is the one part that fits no tool, so without a line saying
        // what to do with it the item is a dead end. Upstream says the same thing in the same place
        // (SharpeningKit#addInformation leads with item.tconstruct.sharpening_kit.tooltip), and it
        // leads because it is the only reason to hold one.
        if (stack.is(ForgeweaveItems.PART_SHARPENING_KIT.get())) {
            tooltip.add(Component.translatable("tooltip.forgeweave.sharpening_kit"));
        }
        ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        if (materialId == null) {
            return;
        }
        tooltip.add(MaterialDisplay.name(registries, materialId));

        Optional<Material> material = MaterialDisplay.lookup(registries, materialId);
        if (material.isEmpty()) {
            return;
        }
        List<Component> stats = detailed ? stats(material.get()) : List.of();
        if (!stats.isEmpty()) {
            tooltip.add(Component.empty());
            tooltip.addAll(stats);
        }
        tooltip.add(Component.empty());
        // The traits this material grants through this kind of part, which is upstream's own filter
        // (ToolPart#getTooltipTraitInfo passes the part's stat type to getAllTraitsForStats).
        // StationText separates entries with the null spacer InfoPanel understands; a tooltip list
        // must not carry nulls, so they become blank lines here.
        for (Component line : StationText.traits(material.get().color(), material.get().traits().forPart(kind))) {
            tooltip.add(line == null ? Component.empty() : line);
        }
    }

    private List<Component> stats(Material material) {
        return switch (kind) {
            case HEAD -> headStats(material);
            case HANDLE -> StationText.handleStats(material);
            case EXTRA -> StationText.extraStats(material);
            case NONE -> List.of();
        };
    }

    /**
     * The head stat block plus the tool tier the material grants (issue #254) -- heads are what
     * decide a tool's mining capability, so the tier sits with the other head stats behind Shift,
     * in the same {@code Tool Tier: X} shape {@code ToolTooltip} shows for an assembled tool. A
     * material whose {@code incorrect_for_tool} tag is off the vanilla ladder (gold, modded) gets
     * no tier line rather than a guess.
     */
    private static List<Component> headStats(Material material) {
        List<Component> lines = new ArrayList<>(StationText.headStats(material));
        ToolTooltip.tierLine(material.incorrectForTool()).ifPresent(lines::add);
        return lines;
    }
}
