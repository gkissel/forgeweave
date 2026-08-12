package dev.gkissel.forgeweave.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.block.Block;

import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The warmace (docs/SCOPE.md M3 issue #161): a Tool Forge-tier smash weapon whose innate <em>is</em>
 * vanilla 1.21's mace, not a copy of it.
 *
 * <h2>Riding vanilla, not re-implementing it</h2>
 *
 * <p>ADR-0005 decision 4 says the smash "rides vanilla mace mechanics rather than a parallel
 * implementation", and the maintainer decision on the issue (2026-08-12) pins the wind-charge
 * synergy to "matches vanilla mace exactly". The strongest available reading of both is to call
 * vanilla's own {@link MaceItem} methods on the registered {@link Items#MACE} instance rather than
 * to restate its rules here -- every one of the three hooks vanilla's mace uses is a plain
 * {@code Item} method with no {@code MaceItem}-specific gate on the caller's side:
 *
 * <table>
 *   <tr><th>Hook</th><th>What vanilla's mace does</th><th>What this class does</th></tr>
 *   <tr><td>{@link #getAttackDamageBonus}</td>
 *       <td>the fall-distance damage curve (4x up to 3 blocks, +2/block to 8, +1/block beyond) plus
 *           the Density/Breach {@code modifyFallBasedDamage} enchantment term, all gated on
 *           {@link MaceItem#canSmashAttack} ({@code fallDistance > 1.5} and not elytra-flying) --
 *           {@code Player#attack} calls this on every item, unconditionally</td>
 *       <td>delegates to {@code Items.MACE}</td></tr>
 *   <tr><td>{@link #hurtEnemy}</td>
 *       <td>the smash itself: the wind-charge impulse bookkeeping
 *           ({@code currentImpulseImpactPos} / {@code setIgnoreFallDamageFromCurrentImpulse}), the
 *           velocity kill, the ground/air smash sounds and particles, and the 3.5-block radial
 *           knockback</td>
 *       <td>delegates to {@code Items.MACE} after this tool's own Broken check</td></tr>
 *   <tr><td>{@link #postHurtEnemy}</td>
 *       <td>{@code hurtAndBreak(1)} then, on a smash, {@code resetFallDistance()} -- the
 *           fall-damage negation</td>
 *       <td>keeps Forgeweave's own durability cost (upstream 1.12's formula, {@link ToolItem
 *           #postHurtEnemy}) and reproduces only the one line vanilla's durability charge is welded
 *           to</td></tr>
 * </table>
 *
 * <p>So the only vanilla behavior restated here is {@code attacker.resetFallDistance()}, and only
 * because it shares a method with a flat durability charge Forgeweave replaces. Nothing else --
 * no fall-distance curve, no smash threshold, no knockback radius, no wind-charge rule -- exists in
 * Forgeweave code, which is what makes "matches vanilla exactly" true by construction across game
 * updates rather than by a number kept in sync by hand.
 *
 * <h2>Why not a combat seam</h2>
 *
 * <p>ADR-0005 decision 3 makes {@code combat.CombatSeams} the only attachment point for combat
 * innates, and every other M3 innate belongs there. The smash does not, because it is not Forgeweave
 * behavior being attached to a hit: the three hooks above are vanilla's own per-item hooks, and
 * routing them through the seam pipeline would mean re-deriving from {@code CombatHit} what vanilla
 * hands the item directly -- i.e. exactly the parallel implementation decision 4 rules out. The seam
 * pipeline still sees these hits like any other; it just has nothing to add to them.
 */
public class WarmaceItem extends ToolItem {

    /**
     * Same shape as every other M3 weapon's constructor (issue #155's {@code ForgeweaveItems#weapon}),
     * so the warmace is registered like one. {@code innate} is always {@code null} here -- see "Why
     * not a combat seam" above.
     */
    public WarmaceItem(Properties properties, ToolConstants.Entry constants, TagKey<Block> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        super(properties, constants, mineableBlocks, weapon, innate);
    }

    /**
     * Vanilla's fall-distance smash bonus, verbatim. Zero on a Broken tool for the same reason
     * {@link ToolItem#getDefaultAttributeModifiers} strips its attack attributes there; the stack
     * comes off the damage source because this hook is not handed one (vanilla's own
     * {@code MaceItem} reads its weapon the same way, via {@code LivingEntity#getWeaponItem}).
     */
    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        ItemStack weapon = damageSource.getWeaponItem();
        if (weapon != null && isBroken(weapon)) {
            return 0.0F;
        }
        return Items.MACE.getAttackDamageBonus(target, damage, damageSource);
    }

    /**
     * The smash. {@code super} keeps this tool's Broken/unassembled refusal (upstream 1.12 refuses
     * the attack outright while Broken), and only a blow that gets past it reaches vanilla's mace.
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!super.hurtEnemy(stack, target, attacker)) {
            return false;
        }
        Items.MACE.hurtEnemy(stack, target, attacker);
        return true;
    }

    /**
     * Durability first ({@code super}: upstream 1.12's per-hit cost and the trait hooks that ride
     * it), then vanilla's fall-damage negation -- the same {@code canSmashAttack} gate and the same
     * {@code resetFallDistance()} call {@code MaceItem#postHurtEnemy} makes, which is why a smash
     * that lands leaves the attacker to touch down unhurt.
     */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        if (MaceItem.canSmashAttack(attacker)) {
            attacker.resetFallDistance();
        }
    }
}
