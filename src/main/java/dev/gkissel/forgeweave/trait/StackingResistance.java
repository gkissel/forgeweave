package dev.gkissel.forgeweave.trait;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.combat.CombatDefense;
import dev.gkissel.forgeweave.api.combat.DefendedBlow;
import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * Protection that builds as the wearer is struck and lapses when the blows stop -- the M6 armor
 * library's {@code stacking_resistance(perHit, cap, decay)} (issue #831).
 *
 * <p>The blow that raises a stack does not benefit from it: {@link #perHit} is paid on the stacks
 * standing <em>when</em> the blow arrives, so the first hit of a fight is unprotected and the
 * {@link #cap}th is at full strength. Like every stacking trait Forgeweave ships
 * ({@code momentum}, {@code insatiable}, {@code magnetic}), the state is a {@link TraitStacks}
 * component on the piece itself, refreshed to {@link #decayTicks} on every hit and dropped whole
 * once it runs out rather than decaying one stack at a time -- {@code ForgeweaveTraits#decayStack}'s
 * shape, and the reason a lull resets a wearer to nothing instead of easing them down.
 *
 * <p>Stateful, so it carries a save-compat fixture:
 * {@code fixtures/save_compat/m831_armor_stacking_state.snbt}.
 *
 * @param perHit protection added per standing stack ({@code 1} is 1/25 of the post-armor blow)
 * @param cap the deepest stack repeat blows may reach
 * @param decayTicks how long the stacks survive without another blow
 */
public record StackingResistance(float perHit, int cap, int decayTicks) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (!Protection.CAN_PROTECT.test(defense.source())) {
            return;
        }
        ItemStack piece = defense.tool();
        int stacks = ForgeweaveTraits.stackLevel(piece, ForgeweaveDataComponents.RESISTANCE_STACKS.get());
        if (stacks > 0) {
            blow.addProtection(perHit * stacks);
        }
        piece.set(ForgeweaveDataComponents.RESISTANCE_STACKS.get(),
                new TraitStacks(Math.min(cap, stacks + 1), decayTicks));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        ForgeweaveTraits.decayStack(stack, ForgeweaveDataComponents.RESISTANCE_STACKS.get());
    }

    /**
     * Issue #1112: how braced the piece is right now, and how much further it can get. Quoted as a
     * percentage of the post-armor blow, the unit {@code bracingplate}'s own description already
     * uses, because one protection point on its own means nothing to a player. No line at all while
     * the stacks have lapsed -- there is no number to report, and this trait deliberately gets no
     * proc cue (see {@link TraitFeedback}: a cue on every blow taken would be noise).
     */
    @Override
    public void stateLines(ItemStack stack, Consumer<Component> out) {
        int stacks = ForgeweaveTraits.stackLevel(stack, ForgeweaveDataComponents.RESISTANCE_STACKS.get());
        if (stacks <= 0) {
            return;
        }
        out.accept(Component.translatable("tooltip.forgeweave.trait.resistance_stacks",
                        StationText.formatNumber(perHit * stacks * PERCENT_PER_POINT), stacks, cap)
                .withStyle(ChatFormatting.GRAY));
    }

    /** {@link Protection}'s own unit: one point blocks a twenty-fifth of the post-armor blow. */
    private static final float PERCENT_PER_POINT = 4.0F;
}
