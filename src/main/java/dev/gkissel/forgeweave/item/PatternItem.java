package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * A tool part pattern, whose only behaviour beyond a plain item is quoting what the part it stamps
 * costs (issue #379). Upstream 1.12's {@code library/tools/Pattern#addInformation} (NOTICE.md) reads
 * the cost straight off the part the pattern is tagged for and prints it in ingots -- {@code
 * getCost() / (float) Material.VALUE_Ingot} through {@code Util.df} -- as {@code tooltip.pattern.cost}.
 *
 * <p>Forgeweave's pattern-to-part-to-cost table lives in {@link PartBuilderRecipes}, so this asks it
 * rather than carrying a second copy (the repo's own anti-drift rule, issue #79): {@link
 * PartBuilderRecipes#patternCost} answers in upstream value units and {@link PartBuilderRecipes#INGOT_VALUE}
 * converts, which is exactly the division upstream does. {@link ForgeweaveItems#PATTERN_BLANK} is
 * not registered through this class -- it stamps no part and so has no cost to quote.
 */
public class PatternItem extends Item {

    public PatternItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        PartBuilderRecipes.patternCost(stack).ifPresent(cost -> tooltip.add(
                Component.translatable("tooltip.forgeweave.pattern_cost",
                        StationText.formatNumber(cost / (float) PartBuilderRecipes.INGOT_VALUE))));
    }
}
