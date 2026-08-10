package dev.gkissel.forgeweave.trait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A behavior a {@code Material} grants to every Tool containing it (CONTEXT.md glossary). Trait
 * behavior is Java, the Material -&gt; Trait assignment is data (ADR-0002), so implementations live in
 * {@link ForgeweaveTraits}'s registry and material JSON only names an id.
 *
 * <p>ponytail: exactly the hooks the shipped traits need, no more. Upstream 1.12's
 * {@code library/traits/ITrait.java} has around twenty; every one of them that no shipped trait uses
 * would be an empty seam here. Add a hook when a trait needs it. Issue #102 (M2 metal traits) added
 * {@link #miningSpeed}, {@link #afterBlockBreak}, {@link #attackSpeedBonus}, {@link #afterHit} and
 * {@link #attackDurabilityBonus}, and widened {@link #bonusDamageAgainst} to take the stack, because
 * those traits need either the tool's own {@code ItemStack} (to read/write per-tool state a M1 trait
 * never needed) or a seam M1 had no trait for.
 *
 * <p>Everything here runs server-side only, from the seams in {@code ToolItem},
 * {@code ToolAssemblyRecipes} and {@link ForgeweaveTraits}'s event-bus listeners -- except
 * {@link #headDurability}, which {@code ToolStats} applies while the tool is being assembled.
 */
public interface Trait {

    /**
     * The assembled tool's durability after this trait has adjusted it, applied only when the trait
     * came from the <b>head</b> material. Upstream 1.12's head-only traits reach the finished stat
     * block through {@code TinkerEvent.OnItemBuilding} (see {@code TraitCheapskate}), which fires
     * once at assembly with the whole {@code ToolNBT} in hand; this is the same seam narrowed to the
     * one field an M1 trait touches.
     *
     * @param durability what {@code ToolStats}'s material formula alone produced
     */
    default int headDurability(int durability) {
        return durability;
    }

    /**
     * Called every tick the tool sits in a living entity's inventory, server side, and never while
     * the tool is Broken.
     */
    default void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {}

    /**
     * Extra durability restored on top of {@code amount}, per repair item, at the Tool Station.
     *
     * @param amount what {@code ToolRepair} alone would restore
     */
    default int repairBonus(int amount) {
        return 0;
    }

    /** Flat attack damage added to the tool's attack damage attribute modifier. */
    default float attackDamageBonus() {
        return 0.0F;
    }

    /**
     * Extra damage this hit deals to {@code target}, on top of what the tool already deals.
     *
     * @param stack the weapon dealing the hit, so a trait can read its own per-tool state
     * @param damage the incoming damage before any trait touched it
     */
    default float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
        return 0.0F;
    }

    /**
     * This block's destroy speed after this trait has adjusted it, called from
     * {@code ToolItem#getDestroySpeed} for every trait in order (upstream 1.12's
     * {@code ITrait#miningSpeed}, one {@code PlayerEvent.BreakSpeed} handler per trait, chained the
     * same way).
     *
     * @param effective whether this tool type is meant for the block being mined (upstream's
     *     {@code ToolHelper#isToolEffective2}, approximated here with the {@code mineable/*} tag)
     * @param originalSpeed the speed before any trait touched it, fixed for the whole chain
     * @param speed the speed as adjusted by every earlier trait in the chain
     */
    default float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
        return speed;
    }

    /**
     * Called once a block is actually destroyed by this tool, server side only (upstream 1.12's
     * {@code ITrait#afterBlockBreak}).
     */
    default void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, LivingEntity breaker) {}

    /** Flat attack speed added to the tool's attack speed attribute, as a fraction of it (0.1 = +10%). */
    default float attackSpeedBonus() {
        return 0.0F;
    }

    /**
     * Called after this tool lands a hit, server side only (upstream 1.12's {@code ITrait#afterHit}).
     */
    default void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {}

    /** Extra durability this hit costs the tool, on top of {@code ToolItem#attackDurabilityCost}. */
    default int attackDurabilityBonus(ItemStack stack) {
        return 0;
    }

    /**
     * The XP a kill made with this tool in the main hand should drop, given what it would otherwise
     * drop (upstream 1.12's {@code ITrait} has no exact hook for this -- {@code TraitEstablished}
     * subscribes to a Forge event directly; {@link ForgeweaveTraits#onExperienceDrop} is the same
     * idea ported to a trait hook).
     */
    default int killExperience(RandomSource random, int xp) {
        return xp;
    }

    // #103 metal materials -- netherite's reinforced_core.

    /**
     * Extra modifier slots a tool carrying this trait grants, on top of the tool's base modifier
     * slots -- {@code modifier.ForgeweaveModifiers#freeSlots}'s trait-consulting term, mirroring how
     * {@code Modifier#bonusSlots} works for modifiers. Netherite's {@code reinforced_core} is the only
     * shipped user (issue #103).
     */
    default int bonusSlots() {
        return 0;
    }
}
