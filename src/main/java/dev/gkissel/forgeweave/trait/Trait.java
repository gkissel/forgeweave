package dev.gkissel.forgeweave.trait;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.DefendedBlow;

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
 * never needed) or a seam M1 had no trait for. Issue #228 (M3.2 mining/durability-economy traits)
 * added {@link #durabilityDamage}, {@link #breakSpeed}, {@link #grantsSilkTouch},
 * {@link #zeroesAttackDamage} and {@link #autoSmelt} on the same rule.
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

    /**
     * Flat attack damage added to the tool's attack damage attribute modifier.
     *
     * @param stack the tool, so a trait can read per-tool state (issue #230: alien's distributed
     *     attack bonus); a build-time-flat trait like fractured simply ignores it
     */
    default float attackDamageBonus(ItemStack stack) {
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
     * {@code ITrait#afterBlockBreak}, which also hands its traits the position and
     * {@code wasEffective} -- issue #230's slimey/baconlicious need both, so the seam carries them
     * like upstream's does).
     *
     * @param pos where the broken block was
     * @param effective whether this tool type is meant for the block (same approximation as
     *     {@link #miningSpeed}'s {@code effective})
     */
    default void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
            LivingEntity breaker, boolean effective) {}

    /** Flat attack speed added to the tool's attack speed attribute, as a fraction of it (0.1 = +10%). */
    default float attackSpeedBonus() {
        return 0.0F;
    }

    /**
     * Fraction added to a launcher's draw speed while this trait is on it (M3.5 issue #396) --
     * upstream {@code TraitLightweight#applyEffect}'s {@code Category.LAUNCHER} branch, {@code
     * drawSpeed += drawSpeed * bonus}, the one trait in the 1.12 clone that touches
     * {@code ProjectileLauncherNBT#drawSpeed}. Summed across traits and read by
     * {@code BowItem#drawSpeed} only. Counted once per trait, not once per part granting it, following
     * {@code ToolAssemblyRecipes#resolveTraits}' de-duplication.
     */
    default float drawSpeedBonus() {
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
     * This trait's contribution to a leveled trait's shared level total -- upstream's
     * {@code AbstractTraitLeveled} sums every applied level onto one shared tag before a leveled
     * trait's effect reads it. Magnetic is the only shipped user (issue #297 parity fix):
     * {@code ForgeweaveTraits#MAGNETIC} is level 1, {@code #MAGNETIC2} level 2, and an all-iron tool
     * sums to 3 -- one pull at the combined range, not two independent ones.
     */
    default int magneticLevel() {
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

    /**
     * The XP an ordinary block break made with this tool in the main hand should drop, given what it
     * would otherwise drop (issue #494/T63; upstream 1.12's {@code TraitEstablished#onBlockBreak}
     * subscribes to a Forge event directly, same as {@link #killExperience};
     * {@link ForgeweaveTraits#onBlockBreakExperience} is the same idea ported to a trait hook, riding
     * NeoForge's {@code BlockDropsEvent}).
     */
    default int blockBreakExperience(RandomSource random, int xp) {
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

    // #230 stateful/special traits.

    /**
     * Called once a blow with this tool has landed and health has been lost, riding
     * {@code ForgeweaveTraits#COMBAT_SEAM}'s {@code onHit} (ADR-0005) rather than
     * {@code ToolItem#postHurtEnemy} like {@link #afterHit} -- which means it sees the full
     * {@link CombatHit}, including the captured attack-strength scale shocking's charge formula
     * needs (upstream {@code TraitShocking#onHit}'s {@code getCooledAttackStrength}). Traits with no
     * stake in the hit record stay on {@link #afterHit}, whose ordering tie to
     * {@link #attackDurabilityBonus} is documented at {@code ForgeweaveTraits#COMBAT_SEAM}.
     */
    default void onCombatHit(CombatHit hit, float damageDealt) {}

    /**
     * Movement speed added to the holder while the tool is held, as a fraction of their total speed
     * (-0.1 = 10% slower) -- vintage's mobility cost (issue #230 maintainer decision). Applied as a
     * main-hand {@code MOVEMENT_SPEED} attribute modifier by
     * {@code ToolItem#getDefaultAttributeModifiers}.
     */
    default float movementSpeedBonus() {
        return 0.0F;
    }

    /**
     * Extra max durability on top of what the tool's materials and modifiers produce -- alien's
     * distributed durability growth (issue #230). Read wherever vanilla's {@code max_damage} is
     * recomputed from stats ({@code ModifierApplication#retuneStats}), which is what keeps the
     * growth from being wiped by a later modifier application -- the same guarantee upstream's
     * {@code TraitProgressiveStats#applyEffect} gives by re-adding its bonus on every tool rebuild.
     */
    default int maxDurabilityBonus(ItemStack stack) {
        return 0;
    }

    // #228 mining/durability-economy traits (duritos, dense, aquadynamic, aridiculous, crumbling,
    // unnatural, squeaky, autosmelt).

    /**
     * This durability loss after this trait has adjusted it, rolled inside
     * {@code ToolItem#damageItem} -- the single choke point every durability loss in the game routes
     * through (that class's javadoc), so it covers mining, attacking and third-party
     * {@code hurtAndBreak} calls alike, exactly where upstream 1.12's {@code ITrait#onToolDamage}
     * sits ({@code ToolHelper#damageTool}). Chained the same way {@link #miningSpeed} is.
     *
     * @param originalAmount the loss before any trait touched it, fixed for the whole chain
     * @param amount the loss as adjusted by every earlier trait in the chain
     */
    default int durabilityDamage(ItemStack stack, RandomSource random, int originalAmount, int amount) {
        return amount;
    }

    /**
     * This block's break speed after this trait has adjusted it, for traits whose bonus depends on
     * where the holder stands or on the block itself -- aquadynamic's water/rain, aridiculous's
     * biome, crumbling's no-tool-needed check, unnatural's tier difference. Distinct from
     * {@link #miningSpeed}, which runs inside {@code Item#getDestroySpeed} and therefore never sees
     * the player; this one chains from NeoForge's {@code PlayerEvent.BreakSpeed}
     * ({@code ForgeweaveTraits#onBreakSpeed}), the same event upstream 1.12's equivalents handle.
     *
     * @param originalSpeed the event's speed before any trait touched it, fixed for the whole chain
     * @param speed the speed as adjusted by every earlier trait in the chain
     */
    default float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
        return speed;
    }

    /**
     * Whether this trait puts vanilla Silk Touch on the tool at assembly (squeaky) -- the trait
     * analog of {@code Modifier#grantsSilkTouch}, granted the same way in
     * {@code ToolAssemblyRecipes#assemble}, the one assembly call site with the registry access an
     * enchantment holder needs.
     */
    default boolean grantsSilkTouch() {
        return false;
    }

    /**
     * Whether this trait forces the tool's attack damage to a hard zero (squeaky, upstream
     * {@code TraitSqueaky#damage}'s unconditional {@code 0f}). Read by {@code ToolItem} for the
     * attribute/tooltip and by {@code ForgeweaveTraits#COMBAT_SEAM} for the blow itself.
     */
    default boolean zeroesAttackDamage() {
        return false;
    }

    /**
     * Whether blocks mined with this tool drop their furnace-smelted result (autosmelt). Consumed by
     * {@code ForgeweaveModifiers#onBlockDrops}, sharing the Searing modifier's smelt path -- one
     * implementation for both, per issue #228.
     */
    default boolean autoSmelt() {
        return false;
    }

    // #229 -- the M3.2 combat trait batch.

    /**
     * The {@link CombatSeam}s this trait rides the shared per-hit pipeline with (ADR-0005 decision
     * 3) -- the trait-side mirror of {@code Modifier#combatSeam}, collected per hit by
     * {@code ForgeweaveTraits#collectCombatSeams}. Implementations hand out pre-built constants, not
     * fresh objects: a seam is a set of numbers (ADR-0004), so one instance serves every hit.
     */
    default void combatSeams(Consumer<CombatSeam> out) {}

    /**
     * Knockback resistance the tool grants while held, as a flat addition to the vanilla attribute
     * (1.0 = full immunity) -- heavy's hook (issue #229, upstream {@code TraitHeavy}'s
     * {@code KNOCKBACK_RESISTANCE} attribute modifier). An attribute, not a seam: knockback is not a
     * damage adjustment, and upstream applies it exactly this way.
     */
    default float knockbackResistance() {
        return 0.0F;
    }

    // #680 -- the M4-5 ARMOR traits.

    /**
     * A blow taken while a piece carrying this trait is <em>worn</em> (issue #680; SCOPE.md D8) --
     * the trait-side face of {@link CombatSeam#onDefend}, reached through
     * {@code ForgeweaveTraits#COMBAT_SEAM} once per worn, non-Broken piece. The one seam every ARMOR
     * trait attaches to, and M7's armor-leveling entry point; see {@link DefendedBlow} for what a
     * trait may do to the blow.
     */
    default void onDefend(CombatDefense defense, DefendedBlow blow) {}

    /**
     * Attribute modifiers a worn piece carrying this trait grants (issue #680): the clone's
     * {@code AttributeModule} on an ARMOR trait -- skyfall's gravity and safe-fall distance,
     * crystalstrike's attack speed, projectile protection's knockback resistance. Read by
     * {@code ArmorPieceItem#getDefaultAttributeModifiers}, so the modifiers come and go with the
     * piece exactly like its armor value and vanish while Broken.
     *
     * @param id a modifier id unique to this trait and slot, for the trait to reuse on each modifier
     */
    default void armorAttributes(ResourceLocation id, EquipmentSlot slot, ItemAttributeModifiers.Builder out) {}
}
