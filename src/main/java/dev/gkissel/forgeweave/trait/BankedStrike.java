package dev.gkissel.forgeweave.trait;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A kill banks a charge, and the next blow spends the whole bank at once (issue #1114's
 * {@code banked_strike}).
 *
 * <p>The shape a player reads: kill three mobs, watch the tooltip climb to three charges, then put
 * the whole bank into one swing and start again. It is the opposite trade from
 * {@link ForgeweaveTraits#BLOODTALLY}'s, which is permanent and tiny; this one is temporary and
 * large, and the decision it asks for -- spend the bank on the next mob you meet, or save it for
 * something worth hitting -- is the character. A bank left idle for {@link #decayTicks} lapses, so
 * it cannot be hoarded across a session.
 *
 * <p>One hook does both halves. {@link Trait#bonusDamageAgainst} pays the bank into the blow being
 * calculated and {@link Trait#afterHit} clears it once that blow has landed, so a bank never pays
 * twice; a killing blow spends what was standing and banks one fresh charge on the way out.
 *
 * @param perCharge damage each banked charge adds to the blow that spends the bank
 * @param cap how many charges the bank holds
 * @param decayTicks how long the bank survives without a kill or a spend
 */
public record BankedStrike(float perCharge, int cap, int decayTicks) implements Trait {

    @Override
    public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
        return banked(stack) * perCharge;
    }

    @Override
    public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
        boolean killed = target.isDeadOrDying();
        if (banked(stack) > 0) {
            stack.remove(ForgeweaveDataComponents.BANKED_CHARGES.get());
            // #1112: TraitFeedback.fire(this, TraitFeedback.Kind.STRIKE, level, target);
            SignatureFeedback.fire(SignatureFeedback.Kind.STRIKE, level, target);
        }
        if (killed) {
            stack.set(ForgeweaveDataComponents.BANKED_CHARGES.get(), new TraitStacks(1, decayTicks));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        ForgeweaveTraits.decayStack(stack, ForgeweaveDataComponents.BANKED_CHARGES.get());
    }

    /** Issue #955's live-state line: one line, so the compact tooltip carries it too. */
    @Override
    public void stateLines(ItemStack stack, Consumer<Component> out) {
        int charges = banked(stack);
        if (charges <= 0) {
            return;
        }
        out.accept(Component.translatable("tooltip.forgeweave.trait.banked_strike", charges,
                        StationText.formatNumber(charges * perCharge))
                .withStyle(ChatFormatting.GRAY));
    }

    private static int banked(ItemStack stack) {
        return ForgeweaveTraits.stackLevel(stack, ForgeweaveDataComponents.BANKED_CHARGES.get());
    }
}
