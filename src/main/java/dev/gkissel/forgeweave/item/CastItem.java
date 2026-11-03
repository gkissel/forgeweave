package dev.gkissel.forgeweave.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * A casting cast, whose hover text also quotes how many ingots of metal one pour uses. The number
 * comes from the casting recipes the cast feeds, so a datapack that changes a recipe changes the
 * tooltip with it.
 */
public class CastItem extends DescribedItem {

    public CastItem(Properties properties) {
        this(properties, "tooltip.forgeweave.cast");
    }

    protected CastItem(Properties properties, String tooltipKey) {
        super(properties, tooltipKey);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // Null when a tooltip is built with no level loaded (TooltipContext.EMPTY).
        HolderLookup.Provider registries = context.registries();
        if (registries == null) {
            return;
        }
        registries.lookup(CastingRecipe.REGISTRY).ifPresent(recipes -> {
            Iterable<CastingRecipe> all = recipes.listElements().map(Holder::value)::iterator;
            mostUsedAmount(all, stack).ifPresent(amount -> tooltip.add(
                    Component.translatable("tooltip.forgeweave.cast_cost",
                            StationText.formatNumber(amount / (float) PartBuilderRecipes.INGOT_VALUE))
                            .withStyle(ChatFormatting.GRAY)));
        });
    }

    /**
     * The amount most recipes through {@code cast} pour. Most rather than only, because the ingot cast
     * also makes a seared brick from half an ingot of seared stone, and that should not hide the 1 every
     * metal uses. Empty on a tie: the gem cast's 100 and 666 have no honest single answer.
     */
    static OptionalInt mostUsedAmount(Iterable<CastingRecipe> recipes, ItemStack cast) {
        Map<Integer, Integer> uses = new HashMap<>();
        for (CastingRecipe recipe : recipes) {
            if (recipe.cast().filter(ingredient -> ingredient.test(cast)).isPresent()) {
                uses.merge(recipe.amount(), 1, Integer::sum);
            }
        }
        int top = uses.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> winners = uses.keySet().stream().filter(amount -> uses.get(amount) == top).toList();
        return winners.size() == 1 ? OptionalInt.of(winners.get(0)) : OptionalInt.empty();
    }
}
