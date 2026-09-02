package dev.gkissel.forgeweave.trait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.DefendedBlow;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A blow that would kill the wearer is spent on the piece instead -- the M6 armor library's
 * {@code death_save(cooldown, cost)} (issue #831), covering both the self-sacrificing revive and
 * the energy-funded variant: the cost is durability today, and #830's {@code EnergyBuffer} can
 * become a second cost kind when a material wants one.
 *
 * <p>Two honest limits, both deliberate:
 *
 * <ul>
 *   <li>It reads the <em>pre-mitigation</em> damage, because that is what {@code Trait#onDefend}
 *       sees. A blow armor alone would have survived can therefore spend the save. ponytail: the
 *       alternative is a second hook at {@code LivingDamageEvent.Pre} for one behaviour; a save
 *       that fires slightly early is a smaller cost than a seam nothing else needs.
 *   <li>It leaves the wearer on whatever health they had. Negating the blow is the whole grant --
 *       no heal, no invulnerability -- so the cooldown is what stops the next blow from being free.
 * </ul>
 *
 * <p>The piece must be able to pay {@link #durabilityCost} without breaking, so a nearly-dead piece
 * simply has no save left in it. Stateful (the cooldown is a {@link TraitStacks} component on the
 * piece, ticked down by {@code ForgeweaveTraits#decayStack} like every other stacking trait), so it
 * carries a save-compat fixture: {@code fixtures/save_compat/m831_armor_stacking_state.snbt}.
 *
 * @param cooldownTicks how long after a save before this piece can save again
 * @param durabilityCost what the save costs the piece
 */
public record DeathSave(int cooldownTicks, int durabilityCost) implements Trait {

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        LivingEntity defender = defense.defender();
        if (blow.damage() < defender.getHealth() || blow.damage() <= 0.0F) {
            return;
        }
        ItemStack piece = defense.tool();
        if (ForgeweaveTraits.stackTicksRemaining(piece, ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get()) > 0) {
            return;
        }
        if (piece.getMaxDamage() - piece.getDamageValue() <= durabilityCost) {
            return;
        }
        blow.setDamage(0.0F);
        piece.hurtAndBreak(durabilityCost, defender, defense.slot());
        piece.set(ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get(), new TraitStacks(1, cooldownTicks));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        ForgeweaveTraits.decayStack(stack, ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get());
    }
}
