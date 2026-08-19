package dev.gkissel.forgeweave.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.tool.AoeHarvest;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * The hatchet (docs/SCOPE.md M1). Upstream {@code tools/tools/Hatchet.java} gives it two behaviors
 * plain {@link ToolItem} can't express, both from the parity audit (2026-08-18, T65, issue #496).
 * The tag/attack-speed/damage-potential/{@code weapon} constants below are the same ones
 * {@code ForgeweaveItems} gave the plain {@code ToolItem} this replaces, and match the private
 * {@code HATCHET} entry {@code ToolAssemblyRecipes} computes assembled stats from.
 *
 * <ul>
 *   <li>{@code Hatchet#buildTagData}: {@code data.attack += 0.5f}, a flat attack bonus applied at
 *       assembly time. That half already had a home -- {@link
 *       dev.gkissel.forgeweave.tool.ToolConstants.Entry#flatAttackBonus()}, set on
 *       {@code ToolAssemblyRecipes}'s {@code HATCHET} entry (issue #153's mechanism, unused by the
 *       hatchet until now).
 *   <li>Leaves: {@code Hatchet#getStrVsBlock} returns full tool speed on leaves regardless of
 *       {@code isEffective} -- upstream's {@code effective_materials} deliberately omits
 *       {@code Material.LEAVES}, so leaves are fast but never "the right tool" -- and
 *       {@code Hatchet#afterBlockBreak} zeroes the durability a leaf break would otherwise cost.
 *       {@link #toolComponent} and {@link #miningDurabilityCost} are those two, respectively.
 * </ul>
 */
public class HatchetItem extends ToolItem {

    public HatchetItem(Properties properties) {
        super(properties, List.of(BlockTags.MINEABLE_WITH_AXE), 1.1f, 1.1f, 1.0f, true, null,
                AoeHarvest.Shape.SINGLE);
    }

    /**
     * Adds a leaves-only speed rule on top of the ordinary {@code mineable/axe} one {@link
     * ToolItem#miningRules} already builds. {@link Tool.Rule#overrideSpeed} sets speed alone, no
     * {@code correctForDrops} -- leaves stay not-effective (upstream never adds them to
     * {@code effective_materials}), just fast, matching {@code Hatchet#getStrVsBlock}.
     *
     * <p>Overriding the rule list rather than the whole component (issue #598) is what puts this rule
     * back on a hasted or re-parted hatchet too: {@code ModifierApplication#retuneStats} rebuilds the
     * speed rules through this same method.
     */
    @Override
    public List<Tool.Rule> miningRules(ToolStats.Stats stats) {
        List<Tool.Rule> rules = new ArrayList<>(super.miningRules(stats));
        rules.add(Tool.Rule.overrideSpeed(BlockTags.LEAVES, miningSpeed(stats)));
        return rules;
    }

    /** {@code Hatchet#afterBlockBreak}: leaves cost no durability, effective or not. */
    @Override
    protected int miningDurabilityCost(BlockState state, boolean effective) {
        return state.is(BlockTags.LEAVES) ? 0 : super.miningDurabilityCost(state, effective);
    }
}
