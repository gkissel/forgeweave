package dev.gkissel.forgeweave.item;

import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

/**
 * The coloured slime balls and slime drops (issues #635/#649): upstream 1.12's {@code
 * TinkerCommons#matSlimeBall*}/{@code slimedrop*}, both metas of Mantle's {@code ItemEdible} (see
 * {@code ForgeweaveItems#registerSlimeBalls}/{@code #registerSlimeDrops}). Issue #783's audit found
 * these registered as plain {@link Item}s with no hover text at all -- a real parity gap, not a
 * missing translation: Mantle's {@code ItemEdible#addInformation} always lists every potion effect
 * a serving carries ({@code displayEffectsTooltip} defaults {@code true} and is never turned off
 * here), so the player already knows what a slimeball does before eating it. This reproduces that
 * exactly, reading the {@code consumable} data component's status effects (where 1.21.2 moved a
 * food's effects) instead of upstream's per-meta effect
 * table, and needs no new lang key -- {@link net.minecraft.world.effect.MobEffect#getDisplayName()}
 * is vanilla's own localized effect name, the same string Mantle's {@code I18n.translateToLocal}
 * looked up.
 */
public class SlimeFoodItem extends Item {

    public SlimeFoodItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) {
            return;
        }
        for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
            if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect apply) {
                for (MobEffectInstance effect : apply.effects()) {
                    tooltip.accept(effect.getEffect().value().getDisplayName());
                }
            }
        }
    }
}
