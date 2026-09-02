package dev.gkissel.forgeweave.trait;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.DefendedBlow;

/**
 * A trait whose hooks are callbacks set at runtime -- the builder a KubeJS startup script gets
 * from {@code ForgeweaveEvents.traits} (issue #832; {@code dev.gkissel.forgeweave.kubejs}), for
 * behaviour a {@link TraitDefinition}'s JSON parameters cannot express. Every {@link Trait} hook
 * with a plain value or callback shape has a setter here: {@code on*} setters take the callback
 * (Rhino turns a JS function into the nested functional interface), the rest take the constant
 * the hook returns. An unset hook keeps {@link Trait}'s default.
 *
 * <p>No KubeJS type appears here on purpose: this class is plain Java so the trait system never
 * classloads the KubeJS API, and the {@code kubejs} package (never touched unless KubeJS itself
 * asks for it through {@code kubejs.plugins.txt}) is the only place the two meet.
 *
 * <p>Not exposed: {@link Trait#combatSeams} and {@link Trait#armorAttributes} take Java builders a
 * script has no business constructing, and {@link Trait#magneticLevel} is magnetic's own internal
 * stacking tally. {@link #onCombatHit} is the scripted face of the seam pipeline instead.
 */
public final class ScriptTrait implements Trait {

    public interface DurabilityCurve { int apply(int durability); }
    public interface InventoryTick { void run(ItemStack stack, ServerLevel level, LivingEntity holder); }
    public interface StackToFloat { float apply(ItemStack stack); }
    public interface StackToInt { int apply(ItemStack stack); }
    public interface BonusDamage { float apply(ItemStack stack, LivingEntity target, float damage); }
    public interface MiningSpeed { float apply(ItemStack stack, boolean effective, float originalSpeed, float speed); }
    public interface AfterBlockBreak {
        void run(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos, LivingEntity breaker, boolean effective);
    }
    public interface AfterHit { void run(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target); }
    public interface Experience { int apply(RandomSource random, int xp); }
    public interface CombatHitCallback { void run(CombatHit hit, float damageDealt); }
    public interface DurabilityDamage { int apply(ItemStack stack, RandomSource random, int originalAmount, int amount); }
    public interface BreakSpeed { float apply(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed); }
    public interface Defend { void run(CombatDefense defense, DefendedBlow blow); }
    public interface UseOnBlock { InteractionResult apply(ItemStack stack, UseOnContext context); }

    @Nullable private DurabilityCurve headDurability;
    @Nullable private InventoryTick inventoryTick;
    @Nullable private DurabilityCurve repairBonus;
    @Nullable private StackToFloat attackDamageBonus;
    @Nullable private BonusDamage bonusDamageAgainst;
    @Nullable private MiningSpeed miningSpeed;
    @Nullable private AfterBlockBreak afterBlockBreak;
    @Nullable private AfterHit afterHit;
    @Nullable private StackToInt attackDurabilityBonus;
    @Nullable private Experience killExperience;
    @Nullable private Experience blockBreakExperience;
    @Nullable private CombatHitCallback combatHit;
    @Nullable private StackToInt maxDurabilityBonus;
    @Nullable private DurabilityDamage durabilityDamage;
    @Nullable private BreakSpeed breakSpeed;
    @Nullable private Defend defend;
    @Nullable private UseOnBlock useOnBlock;

    private float attackSpeedBonus;
    private float drawSpeedBonus;
    private int bonusSlots;
    private float movementSpeedBonus;
    private int energyCapacity;
    private boolean silkTouch;
    private boolean zeroAttackDamage;
    private boolean autoSmelt;
    private float knockbackResistance;
    private float dropDestroyChance;

    // ------------------------------------------------------------------ callback hooks

    public ScriptTrait onHeadDurability(DurabilityCurve callback) { headDurability = callback; return this; }
    public ScriptTrait onInventoryTick(InventoryTick callback) { inventoryTick = callback; return this; }
    public ScriptTrait onRepairBonus(DurabilityCurve callback) { repairBonus = callback; return this; }
    public ScriptTrait onAttackDamageBonus(StackToFloat callback) { attackDamageBonus = callback; return this; }
    public ScriptTrait onBonusDamageAgainst(BonusDamage callback) { bonusDamageAgainst = callback; return this; }
    public ScriptTrait onMiningSpeed(MiningSpeed callback) { miningSpeed = callback; return this; }
    public ScriptTrait onAfterBlockBreak(AfterBlockBreak callback) { afterBlockBreak = callback; return this; }
    public ScriptTrait onAfterHit(AfterHit callback) { afterHit = callback; return this; }
    public ScriptTrait onAttackDurabilityBonus(StackToInt callback) { attackDurabilityBonus = callback; return this; }
    public ScriptTrait onKillExperience(Experience callback) { killExperience = callback; return this; }
    public ScriptTrait onBlockBreakExperience(Experience callback) { blockBreakExperience = callback; return this; }
    public ScriptTrait onCombatHit(CombatHitCallback callback) { combatHit = callback; return this; }
    public ScriptTrait onMaxDurabilityBonus(StackToInt callback) { maxDurabilityBonus = callback; return this; }
    public ScriptTrait onDurabilityDamage(DurabilityDamage callback) { durabilityDamage = callback; return this; }
    public ScriptTrait onBreakSpeed(BreakSpeed callback) { breakSpeed = callback; return this; }
    public ScriptTrait onDefend(Defend callback) { defend = callback; return this; }
    public ScriptTrait onUseOnBlock(UseOnBlock callback) { useOnBlock = callback; return this; }

    // ------------------------------------------------------------------ constant hooks

    public ScriptTrait attackSpeedBonus(float fraction) { attackSpeedBonus = fraction; return this; }
    public ScriptTrait drawSpeedBonus(float fraction) { drawSpeedBonus = fraction; return this; }
    public ScriptTrait bonusSlots(int count) { bonusSlots = count; return this; }
    public ScriptTrait movementSpeedBonus(float fraction) { movementSpeedBonus = fraction; return this; }
    public ScriptTrait energyCapacity(int capacity) { energyCapacity = capacity; return this; }
    public ScriptTrait silkTouch() { silkTouch = true; return this; }
    public ScriptTrait zeroAttackDamage() { zeroAttackDamage = true; return this; }
    public ScriptTrait autoSmelting() { autoSmelt = true; return this; }
    public ScriptTrait knockbackResistance(float amount) { knockbackResistance = amount; return this; }
    public ScriptTrait dropDestroyChance(float chance) { dropDestroyChance = chance; return this; }

    // ------------------------------------------------------------------ Trait

    @Override
    public int headDurability(int durability) {
        return headDurability == null ? durability : headDurability.apply(durability);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        if (inventoryTick != null) {
            inventoryTick.run(stack, level, holder);
        }
    }

    @Override
    public int repairBonus(int amount) {
        return repairBonus == null ? 0 : repairBonus.apply(amount);
    }

    @Override
    public float attackDamageBonus(ItemStack stack) {
        return attackDamageBonus == null ? 0.0F : attackDamageBonus.apply(stack);
    }

    @Override
    public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
        return bonusDamageAgainst == null ? 0.0F : bonusDamageAgainst.apply(stack, target, damage);
    }

    @Override
    public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
        return miningSpeed == null ? speed : miningSpeed.apply(stack, effective, originalSpeed, speed);
    }

    @Override
    public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
            LivingEntity breaker, boolean effective) {
        if (afterBlockBreak != null) {
            afterBlockBreak.run(stack, level, state, pos, breaker, effective);
        }
    }

    @Override
    public float attackSpeedBonus() {
        return attackSpeedBonus;
    }

    @Override
    public float drawSpeedBonus() {
        return drawSpeedBonus;
    }

    @Override
    public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
        if (afterHit != null) {
            afterHit.run(stack, level, attacker, target);
        }
    }

    @Override
    public int attackDurabilityBonus(ItemStack stack) {
        return attackDurabilityBonus == null ? 0 : attackDurabilityBonus.apply(stack);
    }

    @Override
    public int killExperience(RandomSource random, int xp) {
        return killExperience == null ? xp : killExperience.apply(random, xp);
    }

    @Override
    public int blockBreakExperience(RandomSource random, int xp) {
        return blockBreakExperience == null ? xp : blockBreakExperience.apply(random, xp);
    }

    @Override
    public int bonusSlots() {
        return bonusSlots;
    }

    @Override
    public void onCombatHit(CombatHit hit, float damageDealt) {
        if (combatHit != null) {
            combatHit.run(hit, damageDealt);
        }
    }

    @Override
    public float movementSpeedBonus() {
        return movementSpeedBonus;
    }

    @Override
    public int maxDurabilityBonus(ItemStack stack) {
        return maxDurabilityBonus == null ? 0 : maxDurabilityBonus.apply(stack);
    }

    @Override
    public int energyCapacity() {
        return energyCapacity;
    }

    @Override
    public int durabilityDamage(ItemStack stack, RandomSource random, int originalAmount, int amount) {
        return durabilityDamage == null ? amount : durabilityDamage.apply(stack, random, originalAmount, amount);
    }

    @Override
    public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
        return breakSpeed == null ? speed : breakSpeed.apply(stack, player, state, originalSpeed, speed);
    }

    @Override
    public boolean grantsSilkTouch() {
        return silkTouch;
    }

    @Override
    public boolean zeroesAttackDamage() {
        return zeroAttackDamage;
    }

    @Override
    public boolean autoSmelt() {
        return autoSmelt;
    }

    @Override
    public float knockbackResistance() {
        return knockbackResistance;
    }

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (defend != null) {
            defend.run(defense, blow);
        }
    }

    @Override
    public InteractionResult useOnBlock(ItemStack stack, UseOnContext context) {
        return useOnBlock == null ? InteractionResult.PASS : useOnBlock.apply(stack, context);
    }

    @Override
    public float dropDestroyChance() {
        return dropDestroyChance;
    }
}
