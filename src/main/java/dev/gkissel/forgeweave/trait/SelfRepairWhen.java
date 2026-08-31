package dev.gkissel.forgeweave.trait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Passive tool self-repair, gated on a world condition -- ADR-0004's M6 utility/economy library
 * batch (issue #829's reuse audit): {@code ecological} ({@code ForgeweaveTraits#ECOLOGICAL}, upstream
 * {@code TraitEcological}, issue #102) already does exactly this with a fixed unconditional
 * condition, so it becomes {@link SelfRepairCondition#ALWAYS} here rather than getting a competing
 * implementation. {@code sunmend} and {@code duskmend} are the two new instances the M6 design pool
 * asks for (day/night self-repair), sharing the roll-per-tick shape and the "skip while actively
 * using the tool" guard {@code TraitEcological#onUpdate} already has -- generalized to every instance
 * rather than kept as wood's alone.
 *
 * @param condition when the tool may heal at all
 * @param ticksPerPoint the tool has roughly a 1-in-{@code ticksPerPoint} chance, per tick the
 *     condition holds, of healing one durability point -- {@code ecological}'s own 1-in-800 (40s at
 *     20 ticks/s) is the ported magnitude; {@code sunmend}/{@code duskmend}'s are proposed fresh.
 */
public record SelfRepairWhen(SelfRepairCondition condition, int ticksPerPoint) implements Trait {

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        if (holder.getUseItem() == stack || !condition.active(level, holder)) {
            return;
        }
        // 1.21's ItemStack#setDamageValue clamps to [0, maxDamage], same as ecological's own
        // pre-generalization comment already recorded: healing an undamaged tool needs no guard.
        if (level.getRandom().nextInt(ticksPerPoint) == 0) {
            stack.setDamageValue(stack.getDamageValue() - 1);
        }
    }
}
