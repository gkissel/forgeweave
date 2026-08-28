package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The coloured slime balls and slime drops (issues #635/#649): upstream 1.12's {@code
 * TinkerCommons#matSlimeBall*}/{@code slimedrop*}, both metas of Mantle's {@code ItemEdible} (see
 * {@code ForgeweaveItems#registerSlimeBalls}/{@code #registerSlimeDrops}). Issue #783's audit found
 * these registered as plain {@link Item}s with no hover text at all -- a real parity gap, not a
 * missing translation: Mantle's {@code ItemEdible#addInformation} always lists every potion effect
 * a serving carries ({@code displayEffectsTooltip} defaults {@code true} and is never turned off
 * here), so the player already knows what a slimeball does before eating it. This reproduces that
 * exactly, reading the {@link FoodProperties} data component instead of upstream's per-meta effect
 * table, and needs no new lang key -- {@link net.minecraft.world.effect.MobEffect#getDisplayName()}
 * is vanilla's own localized effect name, the same string Mantle's {@code I18n.translateToLocal}
 * looked up.
 */
public class SlimeFoodItem extends Item {

    public SlimeFoodItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return;
        }
        for (FoodProperties.PossibleEffect possibleEffect : food.effects()) {
            MobEffectInstance effect = possibleEffect.effect();
            tooltip.add(effect.getEffect().value().getDisplayName());
        }
    }
}
