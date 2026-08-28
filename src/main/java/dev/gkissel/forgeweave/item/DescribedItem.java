package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A plain item whose only behaviour beyond vanilla is a fixed, ordered set of hover lines (issue
 * #783): the modifier reagents, their crafting precursors, casts and the nahuatl board previously
 * registered via {@code ITEMS.registerSimpleItem} and so had no hover text of their own at all --
 * PR #775 gave Mending Moss a JEI ingredient-info page, but never touched the item's own tooltip.
 * Every line is a translation key rather than literal text (repo lang rule, {@code CLAUDE.md}), and
 * every line is styled gray to match the other flavour-line tooltips in this package
 * ({@code SlimeBootsItem}, {@code SlimeSlingItem}, {@code GuideBookItem}).
 */
public class DescribedItem extends Item {

    private final List<String> tooltipKeys;

    public DescribedItem(Properties properties, String... tooltipKeys) {
        super(properties);
        this.tooltipKeys = List.of(tooltipKeys);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (String key : tooltipKeys) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
