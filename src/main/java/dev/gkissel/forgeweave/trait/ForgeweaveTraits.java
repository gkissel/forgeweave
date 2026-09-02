package dev.gkissel.forgeweave.trait;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.combat.AbsorbFireWhileBlocking;
import dev.gkissel.forgeweave.combat.BleedEffect;
import dev.gkissel.forgeweave.combat.BlockingDamageReduction;
import dev.gkissel.forgeweave.combat.BonusDamageFraction;
import dev.gkissel.forgeweave.combat.BonusDamageVsSeam;
import dev.gkissel.forgeweave.combat.ChainArc;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.ThornsCounterSeam;
import dev.gkissel.forgeweave.combat.ConditionalSeam;
import dev.gkissel.forgeweave.combat.CritMultiplierBonus;
import dev.gkissel.forgeweave.combat.DamageRamp;
import dev.gkissel.forgeweave.combat.DamageScalesWith;
import dev.gkissel.forgeweave.combat.DefendedBlow;
import dev.gkissel.forgeweave.combat.EffectOnHit;
import dev.gkissel.forgeweave.combat.EffectOnSelfOnHit;
import dev.gkissel.forgeweave.combat.FlatBonusDamage;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.combat.ForgeweaveMobEffects;
import dev.gkissel.forgeweave.combat.GaussianArmorPiercingHit;
import dev.gkissel.forgeweave.combat.HitCondition;
import dev.gkissel.forgeweave.combat.IgniteAttackerSeam;
import dev.gkissel.forgeweave.combat.KnockbackOnHitSeam;
import dev.gkissel.forgeweave.combat.Lacerate;
import dev.gkissel.forgeweave.combat.Lifesteal;
import dev.gkissel.forgeweave.combat.LightningOnHit;
import dev.gkissel.forgeweave.combat.PotionEffectOnHitSeam;
import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.combat.ReduceTargetHealing;
import dev.gkissel.forgeweave.combat.StackingHitBonus;
import dev.gkissel.forgeweave.combat.SecondaryDamage;
import dev.gkissel.forgeweave.combat.ShortenInvulnerability;
import dev.gkissel.forgeweave.combat.StackingSlownessOnHitSeam;
import dev.gkissel.forgeweave.combat.StripEffects;
import dev.gkissel.forgeweave.combat.ThornsReflectSeam;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.particle.ForgeweaveParticles;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * The trait ids Forgeweave ships and the behavior behind each, plus the aggregation the seams in
 * {@code ToolItem} and {@code ToolAssemblyRecipes} call. Ported from upstream 1.12's
 * {@code tools/traits/} (pinned commit in NOTICE.md); each constant's javadoc cites its class.
 *
 * <h2>Registry</h2>
 *
 * <p>A plain immutable map, not a Minecraft registry: trait behavior is Java and only ever added by
 * a mod update (ADR-0002), so there is nothing for a registry event to contribute. A material naming
 * an id that isn't here logs once and does nothing, which is what lets a datapack ship a material
 * before the Java that gives it a trait.
 *
 * <p>Since issue #832 (ADR-0004 item 3) two additive sources sit behind the same {@link #lookup}:
 * datapack {@link TraitDefinition}s, snapshotted from the synced registry by {@link #onTagsUpdated},
 * and KubeJS script traits ({@link #registerScripted}). The Java map always wins for an id it
 * owns, so the shipped roster is never redefined from data; serialization stays the plain id list.
 *
 * <h2>Stacking</h2>
 *
 * <p>A tool has three materials, each contributing the traits it scopes to that part -- but the
 * <b>same id applies once</b>, no
 * matter how many parts grant it. That is upstream's rule too: {@code TraitBonusDamage#applyEffect}
 * guards on {@code !TinkerUtil.hasTrait(...)} and {@code AbstractTraitLeveled#applyEffect} on a
 * per-identifier {@code boolean} tag, so a second application of the same trait is a no-op.
 * {@link #resolve} therefore de-duplicates while keeping head/binding/handle order, and the result
 * is stored on the tool as {@code forgeweave:traits} at assembly -- reading it back needs no
 * registry access, which is what lets {@code ToolItem#getDefaultAttributeModifiers} (which gets a
 * bare {@code ItemStack} and nothing else) see traits at all.
 */
public final class ForgeweaveTraits {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ids already reported as unknown, so a bad material JSON logs once rather than every tick. */
    private static final Set<ResourceLocation> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();

    /** Upstream {@code TraitEcological#chance}: one durability roughly every 40 seconds (20 ticks/s). */
    private static final int ECOLOGICAL_TICKS_PER_POINT = 20 * 40;

    /**
     * Wood. Upstream {@code TraitEcological}: server side, a 1-in-{@code 20 * 40} chance per tick to
     * regenerate one durability, skipped while the holder is actively using the tool. Upstream
     * routes this through {@code ToolHelper#healTool} -&gt; {@code #damageTool}, which returns early on
     * a broken tool; the {@code ToolItem} seam applies that same guard to every trait.
     *
     * <p>Issue #829's M6 reuse audit: this is now {@link SelfRepairWhen} with {@link
     * SelfRepairCondition#ALWAYS} rather than its own bespoke implementation -- the M6 utility/economy
     * library batch's {@code self_repair_when(condition, ticksPerPoint)} generalization, kept under
     * this same id and magnitude so shipped Wood tools are untouched. See {@link SelfRepairWhen} for
     * the healing rule itself, including why an already-undamaged tool needs no clamp of its own.
     */
    public static final Trait ECOLOGICAL = new SelfRepairWhen(SelfRepairCondition.ALWAYS, ECOLOGICAL_TICKS_PER_POINT);

    /**
     * Stone, general (issue #493 split; this id used to also carry {@code cheapskate}'s
     * head-durability effect, folded in because M1's material schema gave a material exactly one
     * trait id -- issue #94 lifted that limit, and stone's material JSON now carries the two ids
     * separately). Upstream {@code TraitCheap#onToolHeal}:
     * {@code newAmount + amount * 5 / 100}, i.e. 5% more durability per repair, integer-truncated
     * exactly as upstream truncates it. Upstream grants this on every part <em>except</em> the head
     * ({@code stone.addTrait(cheap)}, the default trait a head-scoped list replaces rather than
     * adds to -- see {@link #CHEAPSKATE}), so a stone head alone grants no repair bonus.
     */
    public static final Trait CHEAP = new Trait() {
        @Override
        public int repairBonus(int amount) {
            return amount * 5 / 100;
        }
    };

    /**
     * Stone, head only (issue #493 split). Upstream {@code TraitCheapskate#onToolBuilding}
     * ({@code stone.addTrait(cheapskate, HEAD)}): {@code max(1, durability * 80 / 100)} on the
     * assembled tool, i.e. a 20% durability penalty -- head-only upstream, head-only here via
     * {@link Trait#headDurability} rather than a hook every part could trigger.
     */
    public static final Trait CHEAPSKATE = new Trait() {
        @Override
        public int headDurability(int durability) {
            return Math.max(1, durability * 80 / 100);
        }
    };

    /**
     * Flint. Upstream {@code TraitCrude#damage}: {@code newDamage += damage * 0.05f * level} when
     * {@code target.getTotalArmorValue() == 0}. Upstream flint grants {@code crude2} (level 2) on the
     * head part and {@code crude} (level 1) elsewhere, so an all-flint tool stacks to level 3 (+15%
     * vs unarmored) -- issue #231's retrofit ports that pair the way {@link #MAGNETIC}/
     * {@link #MAGNETIC2} already ports iron's.
     */
    public static final Trait CRUDE = crude(1);

    /** Flint, head part only. Upstream's {@code crude2}: the same trait at level 2. */
    public static final Trait CRUDE2 = crude(2);

    /** Upstream {@code TraitCrude#bonusModifier}: {@code 0.05f * level} of the blow's own damage. */
    private static final float CRUDE_FRACTION_PER_LEVEL = 0.05F;

    private static Trait crude(int level) {
        return new Trait() {
            @Override
            public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
                return target.getArmorValue() > 0 ? 0.0F : damage * CRUDE_FRACTION_PER_LEVEL * level;
            }
        };
    }

    /**
     * Bone. Upstream registers it as {@code new TraitBonusDamage("fractured", 1.5f)}, whose
     * {@code applyEffect} does {@code data.attack += damage} once at build time -- a flat +1.5 on the
     * tool's own attack stat, not a conditional hit-time bonus, so it belongs on the attribute
     * modifier and shows up in the tool's stats like upstream's does.
     */
    public static final Trait FRACTURED = new Trait() {
        @Override
        public float attackDamageBonus(ItemStack stack) {
            return 1.5F;
        }
    };

    // -- M2 metal traits (issue #102). Material -> trait wiring is issue #103, not here.
    private static final float MAGNETIC_STRENGTH = 0.07F;
    private static final int MAGNETIC_MAX_PULLED = 200;

    /** Upstream {@code MagneticPotion#performEffect}: {@code range = 1.8 + amplifier * 0.3}. */
    private static final double MAGNETIC_BASE_RANGE = 1.8;
    private static final double MAGNETIC_RANGE_PER_LEVEL = 0.3;

    /**
     * Upstream {@code TraitMagnetic}'s {@code afterBlockBreak}/{@code onHit}: {@code
     * Magnetic.apply(player, 30, level)}, a 30-tick hidden potion effect (issue #459 parity fix).
     */
    private static final int MAGNETIC_DURATION_TICKS = 30;

    /**
     * Iron. Upstream {@code TraitMagnetic}'s {@code MagneticPotion#performEffect}: pulls every item
     * drop within {@code 1.8 + level * 0.3} blocks toward the holder at a constant 0.07 blocks/tick
     * in all three axes (a plain vector subtract + normalize, not a horizontal-only projection, and
     * with no 1/distance falloff -- the pull magnitude is the same at 0.1 blocks out as at the edge
     * of range), at most 200 items, applied every other tick ({@code isReady}: {@code duration & 1
     * == 0}), only while the hidden 30-tick potion {@code afterBlockBreak}/{@code onHit} re-applies
     * is active. Only the range scales with level ({@code 1.8 + amplifier * 0.3}); the 0.07 strength
     * itself is flat regardless of level (issue #603 corrected a parity audit claim that strength
     * scales too).
     *
     * <p>Upstream reaches the timer through a player-scoped potion effect; Forgeweave has no
     * player-scoped potion-effect plumbing (issue #459 corrects the parity audit's claim that this
     * still justifies always-on: {@code ForgeweaveMobEffects} exists for combat marks, but those are
     * entity-scoped hit effects, not a tool-carry timer, so the same tool-scoped-component adaptation
     * as {@link #MOMENTUM}/{@link #INSATIABLE} applies here too), so {@link #afterUse} stores the
     * 30-tick window on the tool's own stack ({@link ForgeweaveDataComponents#MAGNETIC_STACKS}) and
     * {@link ForgeweaveTraits#inventoryTick} only pulls while it is still counting down, decaying it
     * one tick at a time like {@code MOMENTUM_STACKS} does. The window's {@code ticksRemaining} takes
     * over the role upstream's hidden potion's own {@code duration} played, so
     * {@link #inventoryTick} checks its parity the same way {@code isReady} checks the potion
     * duration's -- issue #603 parity fix: an earlier version ran the pull every tick at half
     * strength (0.035) instead, reasoning the two were equivalent because the summed impulse over one
     * window matches (0.035 * 30 == 0.07 * 15); that reasoning ignored that vanilla applies gravity
     * (0.04 blocks/tick, downward, every tick unconditionally) to the item the same way regardless of
     * the pull's cadence -- 0.035 vertical pull can never exceed 0.04 gravity on any single tick, so
     * an item held below the holder never won a single tick against gravity, where 0.07 does (see
     * {@code MetalTraitGameTests#magneticPullsItemsVerticallyAtFullStrength}). Gravity still wins a
     * sustained 30-tick window even at full strength (0.07 every other tick against 0.04 every tick
     * nets downward overall too, just far more slowly) -- that's upstream's own math, not a claim
     * that magnetic defies gravity outright.
     *
     * <p>Iron grants both this (general, level 1) and {@link #MAGNETIC2} (head only, upstream's
     * separately identified {@code magnetic2}, level 2). Upstream's {@code AbstractTraitLeveled} sums
     * every applied level onto one shared tag before the one potion effect reads it -- an all-iron
     * tool's amplifier is level 3, not two independently-strengthed effects -- so {@link Trait#magneticLevel}
     * reports each id's own level and {@link #magneticLevel(ItemStack)} sums them for one pull at the
     * combined range (issue #297 parity fix: this used to run two independent half-strength pulls,
     * doubling the force where their ranges overlapped and running no pull at all past either one's
     * own range).
     */
    public static final Trait MAGNETIC = magnetic(1);

    /** Iron, head part only. Upstream's {@code magnetic2}: the same trait at level 2. */
    public static final Trait MAGNETIC2 = magnetic(2);

    private static Trait magnetic(int level) {
        return new Trait() {
            @Override
            public int magneticLevel() {
                return level;
            }

            @Override
            public void afterBlockBreak(ItemStack stack, ServerLevel serverLevel, BlockState state, BlockPos pos,
                    LivingEntity breaker, boolean effective) {
                afterUse(stack);
            }

            @Override
            public void afterHit(ItemStack stack, ServerLevel serverLevel, LivingEntity attacker, LivingEntity target) {
                afterUse(stack);
            }
        };
    }

    /** Opens (or refreshes) {@code MAGNETIC}/{@code MAGNETIC2}'s 30-tick pull window -- see {@link #MAGNETIC}. */
    private static void afterUse(ItemStack stack) {
        stack.set(ForgeweaveDataComponents.MAGNETIC_STACKS.get(), new TraitStacks(1, MAGNETIC_DURATION_TICKS));
    }

    /** The combined level of every leveled magnetic trait on {@code stack} -- see {@link #MAGNETIC}. */
    private static int magneticLevel(ItemStack stack) {
        int level = 0;
        for (Trait trait : of(stack)) {
            level += trait.magneticLevel();
        }
        return level;
    }

    /** The one pull {@link #magneticLevel(ItemStack)}'s combined level performs -- see {@link #MAGNETIC}. */
    private static void pullMagneticItems(ServerLevel serverLevel, LivingEntity holder, double range) {
        Vec3 center = holder.position();
        List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class,
                new AABB(center.x - range, center.y - range, center.z - range,
                        center.x + range, center.y + range, center.z + range));
        int pulled = 0;
        for (ItemEntity item : items) {
            if (item.getItem().isEmpty() || item.isRemoved()) {
                continue;
            }
            if (pulled > MAGNETIC_MAX_PULLED) {
                break;
            }
            Vec3 delta = center.subtract(item.position());
            if (delta.lengthSqr() > 1.0e-6) {
                // Entity#push, not setDeltaMovement: it also sets hasImpulse, without which the
                // server only syncs an item's motion every 20 ticks (EntityType.ITEM updateInterval)
                // and ItemEntity#tick only self-flags on velocity changes > 0.1/tick -- a 0.07 pull
                // never does. Issue #694: on a dedicated server the client simulated its own gravity
                // between syncs, so the pull looked horizontal-only and jumped every 20 ticks.
                item.push(delta.normalize().scale(MAGNETIC_STRENGTH));
            }
            pulled++;
        }
    }

    /**
     * Cobalt, head part only. Upstream {@code TraitMomentum}: mining speed grows by
     * {@code level / 80} (max 40% at level 32) while continuously breaking blocks with it. The
     * buildup decays like a potion effect whose duration ({@code afterBlockBreak}) is
     * {@code (10 / actualMiningSpeed) * 1.5 * 20} ticks -- roughly the time to mine 10 more blocks at
     * the tool's current speed, with a 50% buffer.
     *
     * <p>Upstream stores the buildup as a potion effect on the player, shared by every Momentum tool
     * they hold; this stores {@code {level, ticksRemaining}} on the tool's own {@code ItemStack}
     * instead ({@link ForgeweaveDataComponents#MOMENTUM_STACKS}), decayed one tick at a time from
     * {@link Trait#inventoryTick} -- same player-scoped-potion-to-tool-data-component adaptation as
     * {@link #INSATIABLE}. Recorded in the PR.
     */
    public static final Trait MOMENTUM = new Trait() {
        @Override
        public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
            int level = stackLevel(stack, ForgeweaveDataComponents.MOMENTUM_STACKS.get());
            return speed + originalSpeed * (level / 80.0F);
        }

        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            int next = Math.min(32, stackLevel(stack, ForgeweaveDataComponents.MOMENTUM_STACKS.get()) + 1);
            ToolStats.Stats stats = stack.get(ForgeweaveDataComponents.TOOL_STATS.get());
            float speed = stats == null ? 1.0F : stats.miningSpeed();
            int duration = (int) (10.0F / speed * 1.5F * 20.0F);
            stack.set(ForgeweaveDataComponents.MOMENTUM_STACKS.get(), new TraitStacks(next, duration));
        }

        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            decayStack(stack, ForgeweaveDataComponents.MOMENTUM_STACKS.get());
        }
    };

    private static final float LIGHTWEIGHT_BONUS = 0.1F;

    /**
     * Cobalt. Upstream {@code TraitLightweight}: flat +10% mining speed ({@code miningSpeed}) and
     * +10% attack speed ({@code applyEffect} scaling {@code attackSpeedMultiplier} at build time),
     * unconditional -- unlike {@link #STONEBOUND}, no effectiveness check.
     *
     * <p>Upstream scales the tool's own computed attack-speed stat; Forgeweave's attack speed is a
     * fixed per-tool-type constant with no material contribution ({@code ToolItem}), so this instead
     * adds a second flat {@code ATTACK_SPEED} attribute modifier worth 10% of that constant
     * ({@code ToolItem#getDefaultAttributeModifiers} via {@link #attackSpeedBonus}). Recorded in the PR.
     */
    public static final Trait LIGHTWEIGHT = new Trait() {
        @Override
        public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
            return speed * (1.0F + LIGHTWEIGHT_BONUS);
        }

        @Override
        public float attackSpeedBonus() {
            return LIGHTWEIGHT_BONUS;
        }

        @Override
        public float drawSpeedBonus() {
            // M3.5 #396: TraitLightweight#applyEffect's Category.LAUNCHER branch, the same 10%.
            return LIGHTWEIGHT_BONUS;
        }
    };

    /**
     * Ardite, head part only. Upstream {@code TraitStonebound}:
     * {@code log((maxDurability - durability) / 72d + 1d) * 2} bonus mining speed, added only when
     * the tool is effective for the block being mined -- i.e. rises as the tool wears down. The
     * #102 issue text also describes a damage penalty; the clone source
     * ({@code TraitStonebound.java}, pinned commit c01173c0) has none, so none is ported here.
     *
     * <p>"Effective" is approximated as "this tool type's {@code mineable/*} tag", the same rule
     * {@code ToolItem#toolComponent} already gates drops on; 1.21 has no direct equivalent of
     * upstream's {@code ToolHelper#isToolEffective2}. Recorded in the PR.
     */
    public static final Trait STONEBOUND = new Trait() {
        @Override
        public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
            if (!effective) {
                return speed;
            }
            return speed + wearCurve(stack);
        }
    };

    /**
     * The "old tcon" wear curve {@code TraitStonebound} and {@code TraitJagged} share verbatim:
     * {@code log((maxDurability - durability) / 72d + 1d) * 2}, rising as the tool wears down.
     *
     * <p>{@code stack.getDamageValue()} is the durability already lost, i.e. upstream's
     * {@code (maxDurability - currentDurability)}: {@code getMaxDamage() - getDamageValue()} would be
     * durability remaining, the opposite of what the formula wants.
     */
    static float wearCurve(ItemStack stack) {
        return (float) (Math.log(stack.getDamageValue() / 72.0 + 1.0) * 2.0);
    }

    private static final float PETRAMOR_CHANCE = 0.1F;
    private static final int PETRAMOR_HEAL = 5;

    /**
     * Ardite. Upstream {@code TraitPetramor}: 10% chance per stone-material block mined to restore 5
     * durability ({@code ToolHelper.healTool}), server side only.
     *
     * <p>1.21 removed the {@code Material.ROCK} block classification upstream tested against;
     * {@code BlockTags.MINEABLE_WITH_PICKAXE} is the closest modern stand-in for "stone-type block".
     * Recorded in the PR.
     */
    public static final Trait PETRAMOR = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && level.getRandom().nextFloat() < PETRAMOR_CHANCE) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - PETRAMOR_HEAL));
            }
        }
    };

    /**
     * Manyullyn, head part only. Upstream {@code TraitInsatiable}: a hit adds {@code level / 3} bonus
     * damage from the current stack (checked in {@code damage}, before the stack grows), then
     * {@code afterHit} grows the stack by one (capped at 10) with a 5-second (100-tick) refresh, and
     * {@code onToolDamage} adds {@code level / 3} extra durability loss to that same hit.
     *
     * <p>Same player-scoped-potion-to-tool-data-component adaptation as {@link #MOMENTUM}, stored in
     * {@link ForgeweaveDataComponents#INSATIABLE_STACKS}. Recorded in the PR.
     */
    public static final Trait INSATIABLE = new Trait() {
        @Override
        public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
            return stackLevel(stack, ForgeweaveDataComponents.INSATIABLE_STACKS.get()) / 3.0F;
        }

        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            int next = Math.min(10, stackLevel(stack, ForgeweaveDataComponents.INSATIABLE_STACKS.get()) + 1);
            stack.set(ForgeweaveDataComponents.INSATIABLE_STACKS.get(), new TraitStacks(next, 5 * 20));
        }

        @Override
        public int attackDurabilityBonus(ItemStack stack) {
            return stackLevel(stack, ForgeweaveDataComponents.INSATIABLE_STACKS.get()) / 3;
        }

        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            decayStack(stack, ForgeweaveDataComponents.INSATIABLE_STACKS.get());
        }
    };

    /**
     * Manyullyn. Upstream {@code TraitColdblooded}: {@code +damage / 2} (50% bonus) against a target
     * at full health -- despite the name, this has nothing to do with cold biomes or temperature. The
     * #102 issue text's "cold biomes/conditions" description does not match the clone source
     * ({@code TraitColdblooded.java}, pinned commit c01173c0) and is not implemented; "cold-blooded"
     * describes striking prey before it can react, not the environment. Recorded in the PR.
     */
    public static final Trait COLDBLOODED = new Trait() {
        @Override
        public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
            return target.getHealth() == target.getMaxHealth() ? damage / 2.0F : 0.0F;
        }
    };

    /** Upstream {@code TraitEstablished#onBlockBreak}'s roll: {@code r < 0.33f || (xp == 0 && r < 0.03f)}. */
    private static final float ESTABLISHED_BLOCK_XP_CHANCE = 0.33F;

    /**
     * Copper. Upstream {@code TraitEstablished}'s kill-XP bonus ({@code onXpDrop}/{@code getUpdateXP}):
     * 0 XP has a 3% chance of becoming 1, otherwise {@code round(xp * 1.25 + random * 0.25) + 1}.
     *
     * <p>Upstream also grants bonus XP on ordinary block breaking ({@code onBlockBreak}, via
     * {@code BlockEvent.BreakEvent#getExpToDrop}/{@code #setExpToDrop}). Issue #494/T63: the parity
     * audit's original note claiming NeoForge's {@code BlockEvent.BreakEvent} has no XP field and thus
     * no per-tool interception point was checked against the clone and found wrong for this NeoForge
     * version -- {@link net.neoforged.neoforge.event.level.BlockDropsEvent} (fired after drops are
     * determined, before they enter the world) carries both the breaking tool and a mutable
     * {@code getDroppedExperience}/{@code setDroppedExperience}, the same seam
     * {@code modifier.ForgeweaveModifiers#onBlockDrops} already rides for Searing/Magnetic
     * Pull/Resonant/autosmelt (issue #108). Ported via {@link #onBlockBreakExperience}.
     *
     * <p>Upstream's roll is {@code r < 0.33f || (expToDrop == 0 && r < 0.03f)} for a single draw of
     * {@code r}: since 0.03 &lt; 0.33, the second clause can only be true where the first already is,
     * making the whole check a flat 33% chance of +1 regardless of the current XP -- ported as that
     * reduced form ({@link #ESTABLISHED_BLOCK_XP_CHANCE}); behavior is bit-for-bit identical, not an
     * approximation. Recorded in the PR.
     */
    public static final Trait ESTABLISHED = new Trait() {
        @Override
        public int killExperience(RandomSource random, int xp) {
            if (xp == 0) {
                return random.nextFloat() < 0.03F ? 1 : 0;
            }
            return 1 + Math.round(xp * 1.25F + random.nextFloat() * 0.25F);
        }

        @Override
        public int blockBreakExperience(RandomSource random, int xp) {
            return random.nextFloat() < ESTABLISHED_BLOCK_XP_CHANCE ? xp + 1 : xp;
        }
    };

    // -- #103 metal materials: rose gold's quick, netherite's reinforced_core. Maintainer decision
    // recorded on issue #103 (2026-08-10): rose gold gets quick; netherite gets reinforced_core plus a
    // netherite-ingot application recipe for the existing extra_slot modifier (see the
    // modifier_recipe/extra_slot_netherite.json shipped alongside this class). Issue #103 also gave
    // netherite a `fireproof` trait whose whole effect was fire immunity for its dropped ItemEntity;
    // #447 retired it, because upstream 1.12 makes EVERY dropped tool indestructible (parity audit
    // T16, entity.IndestructibleItemEntity) and that subsumes it.

    /** Upstream has no rose gold material at all, so these magnitudes are this PR's own -- see below. */
    private static final float QUICK_MINING_BONUS = 0.25F;
    private static final float QUICK_ATTACK_SPEED_BONUS = 0.2F;

    /**
     * Rose gold. No upstream trait to port -- rose gold has no 1.12 counterpart (issue #103). Follows
     * {@link #LIGHTWEIGHT}'s pattern (flat mining-speed multiplier plus a flat attack-speed fraction)
     * but at more than double the magnitude, since "fast" is rose gold's entire identity per the issue
     * body and the material's own stats trade away durability and attack for it (see rose_gold.json).
     * Recorded in the PR.
     */
    public static final Trait QUICK = new Trait() {
        @Override
        public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
            return speed * (1.0F + QUICK_MINING_BONUS);
        }

        @Override
        public float attackSpeedBonus() {
            return QUICK_ATTACK_SPEED_BONUS;
        }
    };

    /**
     * Netherite, the other maintainer-decided trait (issue #103): +1 modifier slot on a tool with a
     * netherite part, applied through {@link Trait#bonusSlots} -- {@code
     * modifier.ForgeweaveModifiers#freeSlots}'s trait-consulting term. No upstream trait to port either.
     */
    public static final Trait REINFORCED_CORE = new Trait() {
        @Override
        public int bonusSlots() {
            return 1;
        }
    };

    // -- M3.2 stateful/special traits (issue #230). Material -> trait wiring is the roster issues'.

    /** Upstream {@code TraitAlien}: one stat step every 3.6 seconds, 1000 applications an hour. */
    private static final int ALIEN_TICKS_PER_STAT = 72;
    /** Upstream {@code TraitAlien#getPoolLazily}: "we distribute a whopping X points worth of stats!" */
    private static final int ALIEN_POOL_POINTS = 800;
    private static final int ALIEN_DURABILITY_STEP = 1;
    private static final float ALIEN_SPEED_STEP = 0.007F;
    private static final float ALIEN_ATTACK_STEP = 0.005F;

    /**
     * End stone. Upstream {@code TraitAlien} (extends {@code TraitProgressiveStats}): the first time
     * the trait ticks it rolls a pool of {@value #ALIEN_POOL_POINTS} stat points, each point randomly
     * a durability ({@code +1}), mining speed ({@code +0.007}) or attack ({@code +0.005}) step; then
     * every {@value #ALIEN_TICKS_PER_STAT} ticks the tool is carried it distributes one step --
     * attack on every third application, speed on every second, durability otherwise (upstream's
     * {@code ticksExisted % (72*3) / % (72*2)} cascade) -- until that stat's share of the pool runs
     * out. Skipped while the holder is actively using the tool, like upstream skips a block-breaking
     * player.
     *
     * <p>Upstream mutates the tool's own stat NBT and re-applies the distributed bonus on every tool
     * rebuild ({@code TraitProgressiveStats#applyEffect}); Forgeweave's {@code tool_stats} component
     * stays the untouched materials-derived base (CONTEXT.md hard rule), so the distributed block is
     * instead the single source of truth: mining speed and attack ride {@link Trait#miningSpeed} /
     * {@link Trait#attackDamageBonus} at read time, and durability grows {@code max_damage} directly
     * plus re-applies through {@link Trait#maxDurabilityBonus} wherever {@code max_damage} is
     * recomputed. Recorded in the PR.
     */
    public static final Trait ALIEN = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            // Upstream TraitAlien#onUpdate's guard (issue #297 parity fix): a FakePlayer (hopper-fed
            // crafting/automation rigs holding the tool) never distributes stat growth.
            if (holder instanceof FakePlayer || holder.tickCount % ALIEN_TICKS_PER_STAT != 0
                    || holder.getUseItem() == stack) {
                return;
            }
            AlienProgress progress = stack.get(ForgeweaveDataComponents.ALIEN_PROGRESS.get());
            if (progress == null) {
                progress = new AlienProgress(rollAlienPool(level.getRandom()), AlienProgress.Portion.ZERO);
            }
            AlienProgress.Portion pool = progress.pool();
            AlienProgress.Portion given = progress.distributed();
            if (holder.tickCount % (ALIEN_TICKS_PER_STAT * 3) == 0) {
                if (given.attackDamage() < pool.attackDamage()) {
                    given = new AlienProgress.Portion(given.durability(), given.miningSpeed(),
                            given.attackDamage() + ALIEN_ATTACK_STEP);
                }
            } else if (holder.tickCount % (ALIEN_TICKS_PER_STAT * 2) == 0) {
                if (given.miningSpeed() < pool.miningSpeed()) {
                    given = new AlienProgress.Portion(given.durability(),
                            given.miningSpeed() + ALIEN_SPEED_STEP, given.attackDamage());
                }
            } else if (given.durability() < pool.durability()) {
                given = new AlienProgress.Portion(given.durability() + ALIEN_DURABILITY_STEP,
                        given.miningSpeed(), given.attackDamage());
                stack.set(DataComponents.MAX_DAMAGE, stack.getMaxDamage() + ALIEN_DURABILITY_STEP);
            }
            stack.set(ForgeweaveDataComponents.ALIEN_PROGRESS.get(), new AlienProgress(pool, given));
        }

        @Override
        public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
            return speed + alienDistributed(stack).miningSpeed();
        }

        @Override
        public float attackDamageBonus(ItemStack stack) {
            return alienDistributed(stack).attackDamage();
        }

        @Override
        public int maxDurabilityBonus(ItemStack stack) {
            return alienDistributed(stack).durability();
        }
    };

    /** Upstream {@code TraitAlien#getPoolLazily}'s roll, one {@code nextInt(3)} per point. */
    private static AlienProgress.Portion rollAlienPool(RandomSource random) {
        int durability = 0;
        int speed = 0;
        int attack = 0;
        for (int point = 0; point < ALIEN_POOL_POINTS; point++) {
            switch (random.nextInt(3)) {
                case 0 -> durability++;
                case 1 -> speed++;
                default -> attack++;
            }
        }
        return new AlienProgress.Portion(durability * ALIEN_DURABILITY_STEP,
                speed * ALIEN_SPEED_STEP, attack * ALIEN_ATTACK_STEP);
    }

    private static AlienProgress.Portion alienDistributed(ItemStack stack) {
        AlienProgress progress = stack.get(ForgeweaveDataComponents.ALIEN_PROGRESS.get());
        return progress == null ? AlienProgress.Portion.ZERO : progress.distributed();
    }

    /** Upstream {@code TraitShocking}'s magnitudes, cited on {@link #SHOCKING}. */
    private static final float SHOCKING_CHARGE_PER_HIT = 15.0F;
    private static final float SHOCKING_CHARGE_PER_BREAK = 15.0F;
    private static final float SHOCKING_CHARGE_PER_BLOCK_MOVED = 2.0F;
    private static final double SHOCKING_MOVE_CAP = 5.0;
    private static final double SHOCKING_MOVE_MIN = 0.1;
    private static final int SHOCKING_MOVE_SAMPLE_TICKS = 5;
    private static final float SHOCKING_DISCHARGE_DAMAGE = 5.0F;

    /**
     * Vanilla stand-ins for upstream's {@code charged}/{@code discharge} cues (issue #415). Upstream's
     * own sounds are freesound CC-BY 3.0 / CC0 ({@code resources/assets/tconstruct/sounds/Credits.txt}
     * -- {@code charged} by FreqMan CC0, {@code discharge} by JoelAUdio CC-BY 3.0), not MIT, so porting
     * them adds a CC-BY attribution obligation the same way Spartan Weaponry's Apache-2.0 art did --
     * that needs an explicit maintainer decision before Forgeweave ships them; recorded in the PR
     * instead of assumed. {@link net.minecraft.sounds.SoundEvents#TRIDENT_THUNDER} over the issue's
     * other candidate ({@code LIGHTNING_BOLT_IMPACT}, a real bolt's ground-impact boom, sized for an
     * actual {@code LightningBolt} entity that never spawns here): TConstruct's own 1.20 branch already
     * reuses {@code TRIDENT_THUNDER} for exactly this shape of effect -- a hit-triggered lightning cue
     * with no bolt entity ({@code ChannelingModule#tryStrike}) -- at low volume so it doesn't compete
     * with combat noise on every proc. The particle is no longer a stand-in: issue #482 derived
     * upstream's own {@code HEART_ELECTRO} sprite, so both cues now spawn it at upstream's own count
     * ({@code TraitShocking#onHit}: {@code spawnEffectParticle(HEART_ELECTRO, target, 5)}). Only the
     * <em>full-charge</em> burst is Forgeweave's addition -- upstream marks reaching full charge with
     * a sound alone -- and it reuses the same particle and count so the trait reads as one cue.
     */
    private static final float SHOCKING_FEEDBACK_VOLUME = 0.4F;

    /** Upstream {@code TraitShocking#onHit}: {@code spawnEffectParticle(HEART_ELECTRO, target, 5)}. */
    private static final int SHOCKING_HEARTS = 5;

    /**
     * Electrum. Upstream {@code TraitShocking}: a 0-100 charge built three ways -- {@code +15 *
     * attackStrength} per landed hit ({@code onHit}), {@code +15} per block broken
     * ({@code afterBlockBreak}), and {@code +2} per block moved while the tool is held, sampled every
     * 5 ticks with each sample's distance capped at 5 ({@code onUpdate}). A hit swung while fully
     * charged discharges it: 5 bonus lightning-type damage dealt as a secondary blow past the
     * target's invulnerability window (upstream's {@code attackEntitySecondary} with an
     * {@code EntityDamageSource("lightningBolt", ...)}) plus Speed VI for 2.5s on the attacker; a
     * block break that fills the charge discharges immediately into Haste III for 2.5s instead. The
     * tool shows an enchantment glint while fully charged (upstream's {@code setEnchantEffect}).
     * Reaching full charge and discharging each fire a server-side particle burst + sound cue (issue
     * #415: {@link #SHOCKING_FEEDBACK_VOLUME}'s javadoc) -- at the holder on full charge, at the
     * struck target (or the broken block, for the mining path) on discharge; a break that completes
     * the charge fires both in the same tick, matching upstream's {@code addCharge} then
     * {@code discharge} back-to-back call.
     *
     * <p>Launchers (issue #416): a bow's traits reach an arrow's impact in Forgeweave, which upstream
     * 1.12 has no branch for at all -- {@code TraitShocking} only ever sees a melee swing. Of the
     * two halves, the <b>discharge rides the arrow</b> (it is a hit effect, and M3.5-5's decision puts
     * hit effects on the projectile) but <b>an arrow hit builds no charge</b>: a projectile hit is
     * reported at full strength ({@code attackStrengthScale} 1.0, no swing cooldown to gate it), so
     * {@code +15} per arrow would fill a bow in seven shots fired as fast as they draw -- far cheaper
     * than the melee accrual upstream priced. The movement and mining halves are unchanged, and a bow
     * held in the main hand still charges by walking.
     *
     * <p>Deviations, recorded in the PR: the hit half rides {@link Trait#onCombatHit} (ADR-0005's
     * seam) with the {@link CombatHit}'s captured attack-strength scale; movement is sampled on the
     * holder's own {@code tickCount} rather than world time (world time is constant across one
     * test-staged tick, holder ticks aren't); charge is clamped at 100 on write so the serialized
     * range is honest ({@link ShockingCharge}); the feedback cues are vanilla stand-ins, not upstream's
     * own CC-BY/CC0 sounds or its {@code HEART_ELECTRO} particle (see {@link #SHOCKING_FEEDBACK_VOLUME}).
     */
    public static final Trait SHOCKING = new Trait() {
        @Override
        public void onCombatHit(CombatHit hit, float damageDealt) {
            LivingEntity attacker = hit.attacker();
            if (attacker == null) {
                return;
            }
            ItemStack stack = hit.weapon();
            ShockingCharge charge = shockingCharge(stack, attacker);
            if (charge.isFull()) {
                LivingEntity target = hit.target();
                SecondaryDamage.deal(target, hit.level().damageSources().lightningBolt(), SHOCKING_DISCHARGE_DAMAGE);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 5));
                dischargeShockingCharge(stack, hit.level(), target.getX(),
                        target.getY() + target.getBbHeight() * 0.5, target.getZ(), charge);
            } else if (attacker instanceof Player && !hit.isProjectile()) {
                // Upstream TraitShocking#onHit's else-if EntityPlayer gate (issue #297 parity fix): a
                // non-player attacker (a mob wielding the tool) never builds charge from a hit.
                // Melee only (issue #416): see the trait javadoc's launcher note.
                addShockingCharge(stack, hit.level(), attacker,
                        charge.plus(SHOCKING_CHARGE_PER_HIT * hit.attackStrengthScale()));
            }
        }

        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            ShockingCharge charged = shockingCharge(stack, breaker).plus(SHOCKING_CHARGE_PER_BREAK);
            if (charged.isFull()) {
                // Upstream discharges a mining-filled charge on the spot, into haste rather than damage --
                // both the full-charge and discharge cues fire in this one tick, same as upstream's
                // addCharge-then-discharge back-to-back calls.
                breaker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 2));
                spawnShockingFullChargeFeedback(level, breaker);
                dischargeShockingCharge(stack, level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, charged);
            } else {
                setShockingCharge(stack, charged);
            }
        }

        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder.getMainHandItem() != stack || holder.tickCount % SHOCKING_MOVE_SAMPLE_TICKS != 0) {
                return;
            }
            ShockingCharge charge = stack.get(ForgeweaveDataComponents.SHOCKING_CHARGE.get());
            if (charge == null) {
                // First sample only establishes the position baseline, like upstream's zero-valued tag.
                setShockingCharge(stack, new ShockingCharge(0.0F, holder.getX(), holder.getY(), holder.getZ()));
                return;
            }
            if (charge.isFull()) {
                return;
            }
            double dx = holder.getX() - charge.x();
            double dy = holder.getY() - charge.y();
            double dz = holder.getZ() - charge.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < SHOCKING_MOVE_MIN) {
                return;
            }
            dist = Math.min(dist, SHOCKING_MOVE_CAP);
            float next = Math.min(ShockingCharge.FULL,
                    charge.charge() + (float) (dist * SHOCKING_CHARGE_PER_BLOCK_MOVED));
            addShockingCharge(stack, level, holder, new ShockingCharge(next, holder.getX(), holder.getY(), holder.getZ()));
        }
    };

    /** Writes accrued charge, firing the full-charge cue on the transition to full (upstream's addCharge sound). */
    private static void addShockingCharge(ItemStack stack, ServerLevel level, LivingEntity holder, ShockingCharge newCharge) {
        setShockingCharge(stack, newCharge);
        if (newCharge.isFull()) {
            spawnShockingFullChargeFeedback(level, holder);
        }
    }

    /** Discharges {@code charge} to zero and fires the discharge cue at {@code x, y, z}. */
    private static void dischargeShockingCharge(ItemStack stack, ServerLevel level, double x, double y, double z,
            ShockingCharge charge) {
        setShockingCharge(stack, charge.discharged());
        spawnShockingDischargeFeedback(level, x, y, z);
    }

    /** The holder's full-charge cue (issue #415): {@link #SHOCKING_FEEDBACK_VOLUME}'s javadoc picks the sound. */
    private static void spawnShockingFullChargeFeedback(ServerLevel level, LivingEntity holder) {
        ForgeweaveParticles.spawnHearts(ForgeweaveParticles.HEART_ELECTRO.get(), level, holder, SHOCKING_HEARTS);
        level.playSound(null, holder.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS,
                SHOCKING_FEEDBACK_VOLUME, 1.4F);
    }

    /** The discharge cue at {@code x, y, z} (issue #415): {@link #SHOCKING_FEEDBACK_VOLUME}'s javadoc picks the sound. */
    private static void spawnShockingDischargeFeedback(ServerLevel level, double x, double y, double z) {
        ForgeweaveParticles.spawnHearts(ForgeweaveParticles.HEART_ELECTRO.get(), level, x, y, z, SHOCKING_HEARTS);
        level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS,
                SHOCKING_FEEDBACK_VOLUME, 1.0F);
    }

    /** The stack's charge, initialized at the holder's position if it never had one. */
    private static ShockingCharge shockingCharge(ItemStack stack, LivingEntity holder) {
        ShockingCharge charge = stack.get(ForgeweaveDataComponents.SHOCKING_CHARGE.get());
        return charge != null ? charge : new ShockingCharge(0.0F, holder.getX(), holder.getY(), holder.getZ());
    }

    /** Writes the charge back, keeping the glint in step with it (upstream's {@code setEnchantEffect}). */
    private static void setShockingCharge(ItemStack stack, ShockingCharge charge) {
        stack.set(ForgeweaveDataComponents.SHOCKING_CHARGE.get(), charge);
        if (charge.isFull()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    /** Upstream {@code TraitSlimey#chance}: 0.33% per effective block break or killing blow. */
    private static final float SLIMEY_CHANCE = 0.0033F;

    /**
     * Green slime (this id) and blue slime ({@link #SLIMEY_BLUE}). Upstream {@code TraitSlimey}: a
     * 0.33% chance on an effective block break or on a killing blow to spawn a size-1 slime that
     * aggros the tool's holder. Upstream's green variant spawns the vanilla {@code EntitySlime} and
     * the blue variant its own {@code EntityBlueSlime} -- which each id now really does, since #451
     * (parity audit T20) registered the blue slime.
     */
    public static final Trait SLIMEY_GREEN = slimey(() -> EntityType.SLIME);

    /** See {@link #SLIMEY_GREEN}: upstream's own blue slime, since #451. */
    public static final Trait SLIMEY_BLUE = slimey(ForgeweaveEntities.BLUE_SLIME);

    private static Trait slimey(Supplier<? extends EntityType<? extends Slime>> type) {
        return new Trait() {
            @Override
            public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                    LivingEntity breaker, boolean effective) {
                if (effective && rollsSlimeyProc(level.getRandom())) {
                    spawnTraitSlime(type.get(), level, breaker, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            }

            @Override
            public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
                if (target.isDeadOrDying() && rollsSlimeyProc(level.getRandom())) {
                    spawnTraitSlime(type.get(), level, attacker, target.getX(), target.getY(), target.getZ());
                }
            }
        };
    }

    /**
     * Slimey's proc roll, public and pure so the chance is testable off a seeded
     * {@link RandomSource} without spawn randomness in any assertion (same idiom as
     * {@code combat.Beheading#rollsHead}).
     */
    public static boolean rollsSlimeyProc(RandomSource random) {
        return random.nextFloat() < SLIMEY_CHANCE;
    }

    /**
     * Slimey's spawn path (upstream {@code TraitSlimey#spawnSlime}): a size-1 slime of {@code type}
     * at the given spot, remembering {@code owner} as its attacker. Public so a GameTest can drive it
     * deterministically, past the roll above.
     */
    public static void spawnTraitSlime(EntityType<? extends Slime> type, ServerLevel level, LivingEntity owner,
            double x, double y, double z) {
        Slime slime = type.create(level);
        if (slime == null) {
            return;
        }
        slime.setSize(1, true);
        slime.setPos(x, y, z);
        level.addFreshEntity(slime);
        slime.setLastHurtByMob(owner);
        slime.playAmbientSound();
    }

    /** Upstream {@code TraitBaconlicious}'s two chances: 0.5% per block break, 5% per kill. */
    private static final float BACON_BREAK_CHANCE = 0.005F;
    private static final float BACON_KILL_CHANCE = 0.05F;

    /**
     * Magma slime. Upstream {@code TraitBaconlicious}: a 0.5% chance per block broken and a 5% chance
     * per killing blow to drop a piece of bacon where it happened. Forgeweave ships no bacon item, so
     * the drop is {@code minecraft:cooked_porkchop} -- the nearest vanilla bacon; recorded in the PR.
     */
    public static final Trait BACONLICIOUS = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            if (level.getRandom().nextFloat() < BACON_BREAK_CHANCE) {
                dropBacon(level, pos.getX(), pos.getY(), pos.getZ());
            }
        }

        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            if (target.isDeadOrDying() && level.getRandom().nextFloat() < BACON_KILL_CHANCE) {
                dropBacon(level, target.getX(), target.getY(), target.getZ());
            }
        }
    };

    /**
     * Baconlicious's drop path (upstream {@code TraitBaconlicious#dropBacon}, minus the roll). Public
     * so a GameTest can drive it deterministically, same split as {@link #spawnTraitSlime}.
     */
    public static void dropBacon(ServerLevel level, double x, double y, double z) {
        level.addFreshEntity(new ItemEntity(level, x, y, z, new ItemStack(Items.COOKED_PORKCHOP)));
    }

    /** Upstream {@code TraitTasty}: a food point is two "chicken wings"; a bite costs 5 durability. */
    private static final int TASTY_CHICKENWING = 2;
    private static final int TASTY_NOM_COST = 5;

    /**
     * Slime wood. Upstream {@code TraitTasty}: while held by a hungry player, a random chance per
     * tick to take a bite out of the tool -- +1 food for 5 durability. Base chance 1% (+2% while the
     * holder is missing health); below 10 food the chance grows by 0.25% per missing food point and
     * shrinks by 0.5% per saturation point; above 10 food only the base chance applies; at full
     * hunger ({@code needsFood()} false) it never eats. A bite that the tool lacks the durability
     * for is skipped ({@code nom}'s guard).
     */
    public static final Trait TASTY = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (!(holder instanceof Player player) || player.getMainHandItem() != stack) {
                return;
            }
            FoodData food = player.getFoodData();
            if (!food.needsFood()) {
                return;
            }
            float chance = 0.01F;
            if (player.getHealth() < player.getMaxHealth()) {
                chance += 0.02F;
            }
            if (food.getFoodLevel() <= 5 * TASTY_CHICKENWING) {
                chance += (5 * TASTY_CHICKENWING - food.getFoodLevel()) * 0.0025F;
                chance -= food.getSaturationLevel() * 0.005F;
            }
            if (level.getRandom().nextFloat() < chance) {
                tastyNom(stack, level, player);
            }
        }
    };

    /**
     * Tasty's bite (upstream {@code TraitTasty#nom}): +1 food, no saturation, 5 durability. Public so
     * a GameTest can drive it deterministically, past the per-tick roll above.
     */
    public static void tastyNom(ItemStack stack, ServerLevel level, Player player) {
        if (ToolItem.isBroken(stack) || stack.getMaxDamage() - stack.getDamageValue() < TASTY_NOM_COST) {
            return;
        }
        player.getFoodData().eat(1, 0.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 1.0F);
        stack.hurtAndBreak(TASTY_NOM_COST, player, EquipmentSlot.MAINHAND);
    }

    /** Vintage's two magnitudes: the maintainer decision recorded on issue #230 (2026-08-13). */
    private static final int VINTAGE_BONUS_SLOTS = 1;
    private static final float VINTAGE_MOVEMENT_MALUS = -0.10F;

    /**
     * Ancient. Not a 1.12 port -- a Forgeweave adaptation of the 1.20 branch's Vintage modifier
     * (upstream {@code ModifierProvider}: +1 ability slot, -10% mining/attack/draw speed per level),
     * reshaped by the maintainer decision on issue #230: <b>+1 modifier slot</b>
     * ({@link Trait#bonusSlots}, same mechanism as {@link #REINFORCED_CORE}) at the cost of <b>-10%
     * movement speed while the tool is held</b> ({@link Trait#movementSpeedBonus}, applied as a
     * main-hand attribute modifier by {@code ToolItem#getDefaultAttributeModifiers}).
     */
    public static final Trait VINTAGE = new Trait() {
        @Override
        public int bonusSlots() {
            return VINTAGE_BONUS_SLOTS;
        }

        @Override
        public float movementSpeedBonus() {
            return VINTAGE_MOVEMENT_MALUS;
        }
    };

    // -- M3.2 mining/durability-economy traits (issue #228). Material -> trait wiring is the roster
    // batches, not here; magnitudes are clone constants (docs/SCOPE.md M3.2 trait table, NOTICE.md).

    /** Upstream {@code TraitDuritos#onToolDamage}'s two thresholds: 10% double cost, then 40% no cost. */
    private static final float DURITOS_DOUBLE_CHANCE = 0.1F;
    private static final float DURITOS_FREE_CHANCE = 0.5F;

    /**
     * Obsidian. Upstream {@code TraitDuritos#onToolDamage}: per durability loss, a 10% chance to pay
     * double, a 40% chance to pay nothing, and 50% unchanged -- 70% cost on average, per the class's
     * own comment.
     */
    public static final Trait DURITOS = new Trait() {
        @Override
        public int durabilityDamage(ItemStack stack, RandomSource random, int originalAmount, int amount) {
            float r = random.nextFloat();
            if (r < DURITOS_DOUBLE_CHANCE) {
                return amount + originalAmount;
            }
            if (r < DURITOS_FREE_CHANCE) {
                return Math.max(0, amount - originalAmount);
            }
            return amount;
        }
    };

    /**
     * Prismarine, head part only. Upstream {@code TraitJagged#calcBonus}:
     * {@code log((maxDurability - durability) / 72d + 1d) * 2} bonus attack damage -- exactly
     * {@link #STONEBOUND}'s wear curve pointed at attack instead of mining speed (upstream's two
     * classes share the "old tcon jagged formula" comment). Same {@code getDamageValue()} reading
     * as stonebound: durability already lost, not remaining.
     */
    public static final Trait JAGGED = new Trait() {
        @Override
        public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
            return wearCurve(stack);
        }
    };

    /** Upstream {@code TraitAquadynamic#miningSpeed}: +5.5 coeff in water, + rainfall / 1.6 in rain. */
    private static final float AQUADYNAMIC_WATER_BONUS = 5.5F;
    private static final float AQUADYNAMIC_RAINFALL_DIVISOR = 1.6F;

    /**
     * Prismarine. Upstream {@code TraitAquadynamic#miningSpeed}: adds {@code originalSpeed * coeff}
     * where {@code coeff} starts at 1 (so the trait always at least doubles the pre-trait speed --
     * upstream's counter to water's 1/5th mining penalty), +5.5 while the holder is in water, plus
     * the biome's rainfall / 1.6 while it is raining.
     */
    public static final Trait AQUADYNAMIC = new Trait() {
        @Override
        public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
            float coeff = 1.0F;
            if (player.isInWater()) {
                coeff += AQUADYNAMIC_WATER_BONUS;
            }
            if (player.level().isRaining()) {
                coeff += downfall(player.level(), player.blockPosition()) / AQUADYNAMIC_RAINFALL_DIVISOR;
            }
            return speed + originalSpeed * coeff;
        }
    };

    /**
     * Netherrack, head part only. Upstream {@code TraitAridiculous}: mining speed gains
     * {@code originalSpeed * calc / 10} and every hit gains {@code 2 * calc} flat damage, where
     * {@code calc} ({@link #aridiculousness}) grows with biome heat and goes negative in cold or wet
     * ones. Upstream reads the biome at the attacking player's position; this hook only sees the
     * target, whose position is the same biome for any melee hit -- recorded in the PR.
     */
    public static final Trait ARIDICULOUS = new Trait() {
        @Override
        public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
            return speed + originalSpeed * (aridiculousness(player.level(), player.blockPosition()) / 10.0F);
        }

        @Override
        public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
            return 2.0F * aridiculousness(target.level(), target.blockPosition());
        }
    };

    /**
     * Upstream {@code TraitAridiculous#calcAridiculousness}:
     * {@code 1.25^(3 * (0.5 + temperature - rainfall)) - 1.25}, minus half the rainfall while it is
     * raining. 1.12's {@code Biome#getTemperature()}/{@code getRainfall()} map to 1.21's base
     * temperature and climate downfall.
     */
    private static float aridiculousness(Level level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float rainfall = biome.getModifiedClimateSettings().downfall();
        float rain = level.isRaining() ? rainfall / 2.0F : 0.0F;
        return (float) (Math.pow(1.25, 3.0 * (0.5F + biome.getBaseTemperature() - rainfall)) - 1.25) - rain;
    }

    /** The biome's rainfall at {@code pos}, upstream's {@code Biome#getRainfall()}. */
    private static float downfall(Level level, BlockPos pos) {
        return level.getBiome(pos).value().getModifiedClimateSettings().downfall();
    }

    /** Upstream {@code TraitCrumbling#miningSpeed}'s multiplier on the tool's own mining speed. */
    private static final float CRUMBLING_SPEED_FACTOR = 0.5F;

    /**
     * Knightslime (and M3.2's amethyst bronze), head part only. Upstream
     * {@code TraitCrumbling#miningSpeed}: on a block whose material needs no tool, multiplies the
     * break speed by half the tool's own mining speed ({@code getActualMiningSpeed * 0.5}). 1.21's
     * "needs no tool" is {@code !BlockState#requiresCorrectToolForDrops} (dirt, wood, ...); the
     * tool's speed is read off its stored stat block, same as {@link #MOMENTUM} reads it.
     */
    public static final Trait CRUMBLING = new Trait() {
        @Override
        public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
            if (state.requiresCorrectToolForDrops()) {
                return speed;
            }
            ToolStats.Stats stats = stack.get(ForgeweaveDataComponents.TOOL_STATS.get());
            return stats == null ? speed : speed * (stats.miningSpeed() * CRUMBLING_SPEED_FACTOR);
        }
    };

    /**
     * Knightslime. Upstream {@code TraitUnnatural#miningSpeed}: +1 break speed per harvest level the
     * tool sits above the block's requirement. 1.21 has no numeric harvest levels (CONTEXT.md:
     * vanilla tool-tier tags only), so the tool's level is its index on
     * {@code ForgeweaveModifiers#TIER_TAGS}' ladder -- read off the stack's own deny-drops rule, so a
     * diamond-modifier tier bump counts, as upstream's {@code getHarvestLevel} query does -- and the
     * block's requirement comes from the vanilla {@code needs_*_tool} tags. Both scales are
     * wood=0/stone=1/iron=2/diamond=3, upstream's own {@code HarvestLevels} numbering.
     */
    public static final Trait UNNATURAL = new Trait() {
        @Override
        public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
            int dif = toolTierLevel(stack) - blockTierLevel(state);
            return dif > 0 ? speed + dif : speed;
        }
    };

    /** See {@link #UNNATURAL}: the stack's deny-drops rule mapped onto the tier ladder, 0 off-ladder. */
    private static int toolTierLevel(ItemStack stack) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return 0;
        }
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed().isEmpty()) {
                return Math.max(0, ForgeweaveModifiers.tierIndexOf(rule.blocks()));
            }
        }
        return 0;
    }

    /** See {@link #UNNATURAL}: the vanilla {@code needs_*_tool} tags as upstream's harvest levels. */
    private static int blockTierLevel(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return 2;
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return 1;
        }
        return 0;
    }

    /** Upstream {@code TraitDense#onToolDamage}: {@code (0.75 * missingFraction)^3}, ~42% at fully worn. */
    private static final float DENSE_CHANCE_FACTOR = 0.75F;

    /**
     * Bronze (M3.2 roster). Upstream {@code TraitDense#onToolDamage}: a chance, growing cubically as
     * the tool wears down, to halve the durability cost ({@code newDamage -= max(damage / 2, 1)}).
     */
    public static final Trait DENSE = new Trait() {
        @Override
        public int durabilityDamage(ItemStack stack, RandomSource random, int originalAmount, int amount) {
            if (stack.getMaxDamage() <= 0) {
                return amount;
            }
            float chance = DENSE_CHANCE_FACTOR * ((float) stack.getDamageValue() / stack.getMaxDamage());
            chance = chance * chance * chance;
            return chance > random.nextFloat() ? amount - Math.max(originalAmount / 2, 1) : amount;
        }
    };

    /**
     * Paper. +1 free modifier slot through {@link Trait#bonusSlots}, {@link #REINFORCED_CORE}'s
     * mechanism. Upstream ships this as the {@code TraitWritable} pair ({@code TinkerMaterials}:
     * {@code paper.addTrait(writable2, HEAD); paper.addTrait(writable)}), each {@code new
     * TraitWritable(levels)} adding its own {@code levels} to {@code Tags.FREE_MODIFIERS} once per
     * distinct trait id ({@code AbstractTraitLeveled#applyModifierEffect}'s once-per-identifier
     * guard). Forgeweave keeps the pair-of-ids shape ({@link #MAGNETIC}/{@link #MAGNETIC2}'s
     * precedent, since {@link #resolve} counts one id once): this id is upstream's
     * {@code writable} (+1), {@link #WRITABLE2} is {@code writable2} (+2), so an all-paper tool
     * totals +3 exactly as upstream (issue #344, reversing the flat-pair +2 deviation).
     *
     * <p>Issue #829's M6 reuse audit: {@code writable}/{@code writable2} were already two ids for one
     * idea ({@link Trait#bonusSlots}, a fixed count), so both are now built from the
     * {@link #extraModifierSlots} factory -- the M6 utility/economy library batch's {@code
     * extra_modifier_slots(count)} generalization -- with their existing ids and counts kept exactly,
     * per the issue's explicit "keep the ids" instruction.
     */
    public static final Trait WRITABLE = extraModifierSlots(1);

    /**
     * Paper's head-scoped half: upstream {@code TinkerTraits.writable2 = new TraitWritable(2)},
     * a +2 of its own next to {@link #WRITABLE}'s +1 -- see that javadoc for the whole pair.
     */
    public static final Trait WRITABLE2 = extraModifierSlots(2);

    /** The M6 {@code extra_modifier_slots(count)} library shape (issue #829): see {@link #WRITABLE}. */
    private static Trait extraModifierSlots(int count) {
        return new ExtraModifierSlots(count);
    }

    /** Upstream {@code TraitSqueaky#afterHit}: {@code 1.0f} volume, {@code 0.8f + 0.4f * random} pitch. */
    private static final float SQUEAKY_VOLUME = 1.0F;
    private static final float SQUEAKY_PITCH_BASE = 0.8F;
    private static final float SQUEAKY_PITCH_SPREAD = 0.4F;

    /**
     * Sponge. Upstream {@code TraitSqueaky}: always-on Silk Touch ({@code applyEffect}'s
     * {@code ToolBuilder#addEnchantment}, here the assembly-time grant behind
     * {@link Trait#grantsSilkTouch}), a hard-zero hit ({@code damage} returns {@code 0f}
     * unconditionally, here {@link Trait#zeroesAttackDamage}), and a sound cue on every landed hit
     * ({@code afterHit}'s {@code Sounds.playSoundForAll(player, toy_squeak, 1.0f, 0.8f + 0.4f *
     * random)}, here {@link Trait#afterHit}). Upstream's own {@code toy_squeak.ogg} has no
     * Forgeweave sound asset (parity audit T64, issue #495) -- matching the project's zero-custom-
     * sound-asset convention, {@code SLIME_SQUISH} stands in for it at upstream's own volume/pitch,
     * the same way issue #415's shocking cues stand in for TConstruct's own sounds. Its
     * {@code canApplyTogether} luck/silky/fortune/looting guards live in
     * {@code modifier.ModifierCompatibility} (T23, #454).
     */
    public static final Trait SQUEAKY = new Trait() {
        @Override
        public boolean grantsSilkTouch() {
            return true;
        }

        @Override
        public boolean zeroesAttackDamage() {
            return true;
        }

        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            float pitch = SQUEAKY_PITCH_BASE + SQUEAKY_PITCH_SPREAD * level.getRandom().nextFloat();
            level.playSound(null, attacker.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS,
                    SQUEAKY_VOLUME, pitch);
        }
    };

    /**
     * Firewood. Upstream {@code TraitAutosmelt#blockHarvestDrops}: mined blocks drop their
     * furnace-smelted result. The smelting itself is the M2 Searing modifier's
     * ({@code ForgeweaveModifiers#onBlockDrops} -&gt; {@code smelt}), shared rather than duplicated
     * (issue #228); this trait only opts the tool in through {@link Trait#autoSmelt}. Upstream's
     * fortune-multiplies-smelted-drops config rider follows Searing's (absent) behavior -- recorded in
     * the PR; its silk-touch/squeaky {@code canApplyTogether} guard lives in
     * {@code modifier.ModifierCompatibility} (T23, #454).
     */
    public static final Trait AUTOSMELT = new Trait() {
        @Override
        public boolean autoSmelt() {
            return true;
        }
    };

    // -- M3.2 combat-seam trait batch (issue #229). Behavior only; the materials that grant these
    // (cactus, netherrack, magma slime, silver, lead, steel, bone's retrofit, nahuatl, endstone,
    // chorus) arrive in later M3.2 issues. Every combat effect below rides the ADR-0005 seams via
    // Trait#combatSeams -- collected by collectCombatSeams, the same provider slot COMBAT_SEAM has
    // always occupied -- and each seam is a parameterized ADR-0004 library candidate. Constants are
    // upstream 1.12's, cited per trait (NOTICE.md rows per class).

    /** Upstream {@code TraitPrickly#causeDamage}: {@code 0.5 + max(-0.5, gaussian * 0.75)}. */
    private static final float PRICKLY_BASE = 0.5F;
    private static final float PRICKLY_SPREAD = 0.75F;
    private static final float PRICKLY_MIN_OFFSET = -0.5F;

    /** Cactus, head part only. See {@link GaussianArmorPiercingHit}. */
    public static final Trait PRICKLY =
            seamTrait(new GaussianArmorPiercingHit(PRICKLY_BASE, PRICKLY_SPREAD, PRICKLY_MIN_OFFSET));

    /** Upstream {@code TraitSpiky#dealSpikyDamage}: {@code damage /= 2} when not blocking. */
    private static final float SPIKY_HELD_FRACTION = 0.5F;

    /** Cactus. See {@link ThornsReflectSeam}. */
    public static final Trait SPIKY = seamTrait(new ThornsReflectSeam(SPIKY_HELD_FRACTION));

    /** Upstream {@code TraitHellish#bonusDamage}: a flat 4. */
    private static final float HELLISH_BONUS_DAMAGE = 4.0F;

    /**
     * Netherrack, head part only. Upstream {@code TraitHellish#damage}: {@code +4} against any
     * target that is not fire-immune ({@code !target.isImmuneToFire()} -- "non-Nether mobs").
     */
    public static final Trait HELLISH = seamTrait(
            new ConditionalSeam(HitCondition.NOT_FIRE_IMMUNE, 1.0F, new FlatBonusDamage(HELLISH_BONUS_DAMAGE)));

    /** Upstream {@code TraitSuperheat#bonus}: 35% of the blow's own damage. */
    private static final float SUPERHEAT_BONUS_FRACTION = 0.35F;

    /**
     * Magma slime, head part only. Upstream {@code TraitSuperheat#damage}:
     * {@code newDamage += damage * 0.35} while the target is burning.
     */
    public static final Trait SUPERHEAT = seamTrait(
            new ConditionalSeam(HitCondition.BURNING, 1.0F, new BonusDamageFraction(SUPERHEAT_BONUS_FRACTION)));

    /** Upstream {@code TraitHoly#bonusDamage} and its {@code afterHit} weakness: 50 ticks, level I. */
    private static final float HOLY_BONUS_DAMAGE = 5.0F;
    private static final int HOLY_WEAKNESS_TICKS = 50;

    /**
     * Silver. Upstream {@code TraitHoly}: {@code +5} against undead plus Weakness I for 2.5 seconds
     * on a landed hit. 1.21's {@code minecraft:undead} entity-type tag stands in for upstream's
     * {@code EnumCreatureAttribute.UNDEAD} in both halves ({@link HitCondition#UNDEAD}).
     */
    public static final Trait HOLY = seamTrait(
            new BonusDamageVsSeam(EntityTypeTags.UNDEAD, HOLY_BONUS_DAMAGE),
            new ConditionalSeam(HitCondition.UNDEAD, 1.0F,
                    new PotionEffectOnHitSeam(MobEffects.WEAKNESS, 0, HOLY_WEAKNESS_TICKS)));

    /** Upstream {@code TraitPoisonous#afterHit}: {@code new PotionEffect(POISON, 101)} -- level I. */
    private static final int POISONOUS_TICKS = 101;

    /**
     * Lead. Upstream {@code TraitPoisonous}: Poison I for ~5 seconds on every landed hit.
     *
     * <p><b>Migrated onto {@link EffectOnHit}</b> (issue #828, M6 on-hit effect library): {@code
     * effect_on_hit(POISON, 101, 0, chance=1, stackingCap=0)}, the same magnitudes {@link
     * PotionEffectOnHitSeam} carried, so this id's shipped behavior and every existing fixture
     * (material JSON, saved tools) are unchanged -- {@code poisonousPoisonsOnHit} below still passes
     * unmodified, which is this migration's own regression test.
     */
    public static final Trait POISONOUS = seamTrait(new EffectOnHit(MobEffects.POISON, POISONOUS_TICKS, 0, 0));

    /**
     * Lead. Upstream {@code TraitHeavy#getAttributeModifiers}: a flat +1 knockback-resistance
     * attribute modifier while the tool is held -- full immunity, since the attribute caps at 1. An
     * attribute rather than a seam ({@link Trait#knockbackResistance}), exactly as upstream has it;
     * {@code ToolItem#getDefaultAttributeModifiers} is what applies it.
     */
    public static final Trait HEAVY = new Trait() {
        @Override
        public float knockbackResistance() {
            return 1.0F;
        }
    };

    /** Upstream {@code TraitStiff#onBlock}: {@code max(1, amount - 1)}. */
    private static final float STIFF_REDUCTION = 1.0F;
    private static final float STIFF_FLOOR = 1.0F;

    /** Steel. See {@link BlockingDamageReduction}. */
    public static final Trait STIFF = seamTrait(new BlockingDamageReduction(STIFF_REDUCTION, STIFF_FLOOR));

    /**
     * Steel, head part only. Upstream {@code TraitSharp}: a landed hit leaves a non-stacking,
     * armor-ignoring bleed -- 1/3 damage every 15 ticks for 121 ticks ({@link BleedEffect}). The
     * seam is {@link Lacerate} at {@code maxStacks} 1: re-application refreshes, never deepens.
     */
    public static final Trait SHARP =
            seamTrait(new Lacerate(ForgeweaveMobEffects.BLEED, BleedEffect.DURATION_TICKS, 1));

    /** Upstream {@code TraitSplintering}: +0.3 per mark, amplifier capped at 5 (6 stacks), 40-tick marks. */
    private static final float SPLINTERING_BONUS_PER_STACK = 0.3F;
    private static final int SPLINTERING_MAX_STACKS = 6;
    private static final int SPLINTERING_MARK_TICKS = 40;

    /** Bone, head part only (M3.2 retrofit). See {@link StackingHitBonus}. */
    public static final Trait SPLINTERING = seamTrait(new StackingHitBonus(ForgeweaveMobEffects.SPLINTER,
            SPLINTERING_BONUS_PER_STACK, SPLINTERING_MAX_STACKS, SPLINTERING_MARK_TICKS));

    /** Upstream {@code TraitFlammable}: 3 seconds of fire, 3 durability per absorbed fire hit. */
    private static final int FLAMMABLE_FIRE_SECONDS = 3;
    private static final int FLAMMABLE_DURABILITY_COST = 3;

    /**
     * Magma slime. Upstream {@code TraitFlammable}: whoever hits the holder catches fire (held or
     * blocking -- {@link IgniteAttackerSeam}), and blocking negates fire damage outright for 3
     * durability ({@link AbsorbFireWhileBlocking}). Two seams, one trait.
     */
    public static final Trait FLAMMABLE = seamTrait(
            new IgniteAttackerSeam(FLAMMABLE_FIRE_SECONDS),
            new AbsorbFireWhileBlocking(FLAMMABLE_DURABILITY_COST));

    /** Upstream {@code TraitEnderference#onHit}: a 100-tick (5 s) mark. */
    private static final int ENDERFERENCE_TICKS = 100;

    /**
     * Endstone; chorus later. Upstream {@code TraitEnderference}: a landed hit marks the target and
     * a marked entity cannot teleport ({@link #onEnderTeleport}/{@link #onChorusFruitTeleport},
     * upstream's {@code EnderTeleportEvent} cancel). Upstream only marks {@code EntityEnderman};
     * this marks every target instead -- the mark is inert on anything that never teleports, and
     * gating on an entity class would put a predicate inside what ADR-0004 wants to stay a plain
     * {@code potion_effect_on_hit} parameter set. Recorded in the PR.
     */
    public static final Trait ENDERFERENCE =
            seamTrait(new PotionEffectOnHitSeam(ForgeweaveMobEffects.ENDERFERENCE, 0, ENDERFERENCE_TICKS));

    /**
     * Nahuatl. The scimitar's lacerate innate as a material trait (SCOPE M3.2 trait table:
     * "reuses the scimitar Lacerate seam"): the very same seam instance, so the magnitudes stay the
     * issue #159 maintainer decision recorded on {@code LacerateEffect} -- no upstream 1.12
     * counterpart (nahuatl is a 1.20-branch material; the by-name deviation is recorded in SCOPE).
     */
    public static final Trait LACERATING = seamTrait(ForgeweaveInnates.LACERATE_SEAM);

    // ---------------------------------------------------------------- M6 damage-scaling trait
    // behavior library (issue #827, ADR-0004). Ideas are inspiration-only (TAIGA/PlusTiC/Moar
    // Tinkers/Tinkers' Evolution, all non-MIT -- CLAUDE.md); every name, description and magnitude
    // below is Forgeweave's own, not ported. Material wiring is a later M6 issue -- these are
    // registered but not yet assigned to any material. Magnitudes are proposed here and flagged for
    // a maintainer decision on issue #827, the same pattern issue #160's DamageRamp.KATANA numbers
    // and #103's metal traits used.

    /** Proposed (issue #827 maintainer decision): +3 at full durability, scaling to 0 as it wears. */
    private static final float PRISTINE_COEFFICIENT = 3.0F;
    private static final float PRISTINE_CAP = 3.0F;

    /**
     * {@code damage_scales_with(REMAINING_DURABILITY, coefficient, cap)}: bonus damage rises with
     * how undamaged the weapon still is. See {@link DamageScalesWith} for why {@code jagged}'s
     * inverse-durability curve was not folded into this same class.
     */
    public static final Trait PRISTINE = seamTrait(
            new DamageScalesWith(DamageScalesWith.Source.REMAINING_DURABILITY, PRISTINE_COEFFICIENT, PRISTINE_CAP));

    /** Proposed (issue #827 maintainer decision): +2 at full health, scaling to 0 near death. */
    private static final float VIGOROUS_COEFFICIENT = 2.0F;
    private static final float VIGOROUS_CAP = 2.0F;

    /** {@code damage_scales_with(WIELDER_HEALTH, coefficient, cap)}: hit harder while healthy. */
    public static final Trait VIGOROUS = seamTrait(
            new DamageScalesWith(DamageScalesWith.Source.WIELDER_HEALTH, VIGOROUS_COEFFICIENT, VIGOROUS_CAP));

    /** Proposed (issue #827 maintainer decision): +0.15 per point of health the target already lost. */
    private static final float PREDATORY_COEFFICIENT = 0.15F;
    private static final float PREDATORY_CAP = 4.0F;

    /**
     * {@code damage_scales_with(TARGET_MISSING_HEALTH, coefficient, cap)}: finishing blows against
     * an already-wounded target hit harder.
     */
    public static final Trait PREDATORY = seamTrait(new DamageScalesWith(
            DamageScalesWith.Source.TARGET_MISSING_HEALTH, PREDATORY_COEFFICIENT, PREDATORY_CAP));

    /** Proposed (issue #827 maintainer decision): +0.05 per point of the target's own max health. */
    private static final float COLOSSAL_COEFFICIENT = 0.05F;
    private static final float COLOSSAL_CAP = 6.0F;

    /**
     * {@code damage_scales_with(TARGET_MAX_HEALTH, coefficient, cap)}: bonus damage against
     * naturally tough targets (an iron golem, a wither) that a fixed number can't scale down for.
     */
    public static final Trait COLOSSAL = seamTrait(
            new DamageScalesWith(DamageScalesWith.Source.TARGET_MAX_HEALTH, COLOSSAL_COEFFICIENT, COLOSSAL_CAP));

    /** Proposed (issue #827 maintainer decision): +3 per block/tick of the wielder's own motion. */
    private static final float KINETIC_COEFFICIENT = 3.0F;
    private static final float KINETIC_CAP = 6.0F;

    /**
     * {@code damage_scales_with(IMPACT_VELOCITY, coefficient, cap)}: a falling or sprinting blow
     * lands harder.
     */
    public static final Trait KINETIC = seamTrait(
            new DamageScalesWith(DamageScalesWith.Source.IMPACT_VELOCITY, KINETIC_COEFFICIENT, KINETIC_CAP));

    /** Proposed (issue #827 maintainer decision): a flat +2 for each {@code bonus_damage_vs} instance below. */
    private static final float BONUS_DAMAGE_VS_AMOUNT = 2.0F;

    /**
     * {@code bonus_damage_vs(BELOW_WIELDER_HEALTH, amount)}: bonus damage against a target already
     * weaker than the wielder. {@link HitCondition}/{@link ConditionalSeam} already generalize
     * "vs what" for a flat bonus ({@link FlatBonusDamage}'s javadoc), so {@code bonus_damage_vs}'s
     * three M6 predicates ride that existing pair rather than a fourth near-identical damage class.
     */
    public static final Trait DOMINANT = seamTrait(new ConditionalSeam(
            HitCondition.BELOW_WIELDER_HEALTH, 1.0F, new FlatBonusDamage(BONUS_DAMAGE_VS_AMOUNT)));

    /** {@code bonus_damage_vs(ARMORED, amount)}: bonus damage against an armored target. */
    public static final Trait ARMOR_BREAKER = seamTrait(
            new ConditionalSeam(HitCondition.ARMORED, 1.0F, new FlatBonusDamage(BONUS_DAMAGE_VS_AMOUNT)));

    /** {@code bonus_damage_vs(HARMFUL_EFFECT, amount)}: bonus damage against an already-debuffed target. */
    public static final Trait OPPORTUNIST = seamTrait(
            new ConditionalSeam(HitCondition.HARMFUL_EFFECT, 1.0F, new FlatBonusDamage(BONUS_DAMAGE_VS_AMOUNT)));

    /** Proposed (issue #827 maintainer decision): +1.5 damage per level on a fully-charged swing. */
    private static final float CHARGED_BONUS_PER_LEVEL = 1.5F;

    /**
     * {@code charged_bonus_damage(I)}: extra damage on a fully-charged swing only ({@link
     * CombatHit#attackStrengthScale} past {@link CombatHit#FULL_CHARGE}) -- the "radioactive I-III"
     * leveled-instances-share-one-class shape ADR-0004 asks for, {@link #chargedStrike(int)} the
     * shared factory ({@code MAGNETIC}/{@code MAGNETIC2}'s precedent).
     */
    public static final Trait SURGING = chargedStrike(1);

    /** {@code charged_bonus_damage(II)}. */
    public static final Trait SURGING2 = chargedStrike(2);

    /** {@code charged_bonus_damage(III)}. */
    public static final Trait SURGING3 = chargedStrike(3);

    private static Trait chargedStrike(int level) {
        return seamTrait(new ConditionalSeam(HitCondition.FULL_CHARGE, 1.0F,
                new FlatBonusDamage(CHARGED_BONUS_PER_LEVEL * level)));
    }

    /** Proposed (issue #827 maintainer decision): +0.5 to the effective crit multiplier. */
    private static final float RUTHLESS_CRIT_BONUS = 0.5F;

    /**
     * {@code crit_multiplier_bonus(extra)}: a critical hit lands for even more. See {@link
     * CritMultiplierBonus} for the arithmetic against {@link CombatSeams}' attackStrengthScale x
     * critMultiplier unwind (issue #422).
     */
    public static final Trait RUTHLESS = seamTrait(new CritMultiplierBonus(RUTHLESS_CRIT_BONUS));

    /**
     * The M6 "consecutive-charge ramp" library instance: stacks build only from consecutive
     * fully-charged landed hits and decay the same way the katana's ramp does -- {@link
     * DamageRamp#ESCALATING}, sharing its component and machinery rather than adding a second
     * stateful ramp (issue #827's instruction, followed literally).
     */
    public static final Trait ESCALATING = seamTrait(DamageRamp.ESCALATING);

    // ---------------------------------------------------------------- #829 M6 utility/economy trait
    // behavior library (ADR-0004). Reuse audit, recorded here and on the issue thread: mining/
    // attacking explosions is ForgeweaveModifiers#BLASTING (#455) -- reuse, no new class; struck
    // enemies glow is effect_on_hit's job in the sibling on-hit batch (#828), out of scope here;
    // knockback scaling already rides KnockbackMultiplierSeam (see KnockbackMultiplierGameTests);
    // drops pulled to the holder is MAGNETIC/MAGNETIC2 above (#102); drops replaced by their smelted
    // result is AUTOSMELT/ForgeweaveModifiers#SEARING above (#228); faster on blocks needing no tool
    // is CRUMBLING above (#228); speed rising as the tool wears is STONEBOUND above (#102). None of
    // the seven get a new class -- the four genuinely new shapes follow.

    /** {@code sunmend}/{@code duskmend}'s own rate: twice ecological's, since each condition holds
     *  only about half of every day -- proposed to land near the same daily total. */
    private static final int CONDITIONAL_SELF_REPAIR_TICKS_PER_POINT = ECOLOGICAL_TICKS_PER_POINT / 2;

    /**
     * The M6 {@code self_repair_when(condition, ticksPerPoint)} instance for direct sunlight -- see
     * {@link SelfRepairWhen} and {@link SelfRepairCondition#SUNLIT}. Not yet assigned to a material;
     * that wiring is a later M6 issue.
     */
    public static final Trait SUNMEND =
            new SelfRepairWhen(SelfRepairCondition.SUNLIT, CONDITIONAL_SELF_REPAIR_TICKS_PER_POINT);

    /**
     * The M6 {@code self_repair_when(condition, ticksPerPoint)} instance for night -- see {@link
     * SelfRepairWhen} and {@link SelfRepairCondition#NIGHT}. Not yet assigned to a material; that
     * wiring is a later M6 issue.
     */
    public static final Trait DUSKMEND =
            new SelfRepairWhen(SelfRepairCondition.NIGHT, CONDITIONAL_SELF_REPAIR_TICKS_PER_POINT);

    /**
     * The M6 {@code cascading_break(blockPredicate)} instance: breaking one gravity-affected block
     * ({@link FallingBlock}) takes the whole column above it in one swing -- see {@link
     * CascadingBreak}. Not yet assigned to a material; that wiring is a later M6 issue.
     */
    public static final Trait CASCADING = new CascadingBreak(state -> state.getBlock() instanceof FallingBlock);

    /** {@code fertilizing}'s proposed cost/chance -- one crop-harvest durability point (see {@code
     *  CropHarvest}), a coin-flip success rate so a miss is a real possibility, not a rounding error. */
    private static final int FERTILIZING_DURABILITY_COST = 1;
    private static final float FERTILIZING_CHANCE = 0.5F;

    /**
     * The M6 {@code fertilize_on_use(durabilityCost, chance)} instance -- see {@link FertilizeOnUse}.
     * Not yet assigned to a material; that wiring is a later M6 issue.
     */
    public static final Trait FERTILIZING = new FertilizeOnUse(FERTILIZING_DURABILITY_COST, FERTILIZING_CHANCE);

    // ---------------------------------------------------------------- M6 energy buffer trait
    // behavior library (issue #830, ADR-0004). Ideas are inspiration-only (design pool
    // docs/research/m6-material-expansion-references.md §3, §6.5 -- TAIGA/PlusTiC/Moar
    // Tinkers/Tinkers' Evolution, all non-MIT -- CLAUDE.md); every name, description and magnitude
    // below is Forgeweave's own, not ported. Material wiring is a later M6 issue -- these are
    // registered but not yet assigned to any material. Magnitudes are proposed here and flagged for
    // a maintainer decision on issue #830, the pattern issue #160's DamageRamp.KATANA numbers,
    // #103's metal traits and #827's damage-scaling batch all used.

    /**
     * Proposed (issue #830 maintainer decision): 32,000 FE capacity, 40 FE buys back one point of
     * durability -- a full buffer covers 800 durability points before falling back to the tool's
     * own pool, roughly a diamond tool's whole lifespan.
     */
    private static final int ENERGIZED_CAPACITY = 32000;
    private static final float ENERGIZED_FE_PER_DURABILITY_POINT = 40.0F;

    /**
     * {@code energized(capacity, perDurabilityPoint)}: the tool spends stored energy before
     * durability. See {@link EnergyBuffer} for the buffer shape, the item capability and why only
     * the current amount persists.
     */
    public static final Trait ENERGIZED = new EnergyBuffer(ENERGIZED_CAPACITY, ENERGIZED_FE_PER_DURABILITY_POINT);

    /**
     * Proposed (issue #830 maintainer decision): 2 FE/tick (40 FE/s) -- a full 32,000 FE buffer
     * takes roughly 13 minutes of continuous daylight, a slow trickle rather than a primary source.
     */
    private static final int SOLAR_RECHARGE_RATE = 2;

    /** {@code solar_recharge(ratePerTick)}: refills the buffer while the holder stands in daylight. */
    public static final Trait SOLAR_RECHARGE = new SolarRecharge(SOLAR_RECHARGE_RATE);

    /**
     * Proposed (issue #830 maintainer decision): 5 FE stored per point of damage dealt -- a
     * supplementary top-up, not a primary source; filling the proposed 32,000 FE buffer from combat
     * alone takes on the order of a hundred solid hits.
     */
    private static final float KINETIC_CHARGE_FRACTION = 5.0F;

    /** {@code kinetic_charge(fractionOfDamage)}: converts damage dealt into stored energy. */
    public static final Trait KINETIC_CHARGE = seamTrait(new KineticCharge(KINETIC_CHARGE_FRACTION));

    // ---------------------------------------------------------------- M6 on-hit effect trait
    // behavior library (issue #828, ADR-0004). Ideas are inspiration-only (TAIGA/PlusTiC/Moar
    // Tinkers/Tinkers' Evolution, all non-MIT -- CLAUDE.md); every id, description and magnitude
    // below is Forgeweave's own, not ported. Material wiring is a later M6 issue -- these are
    // registered but not yet assigned to any material. Magnitudes are proposed here and flagged for
    // a maintainer decision on issue #828, the same pattern issue #827's batch used.

    /** Proposed (issue #828 maintainer decision): Wither I, stacking to III, 3 seconds a hit. */
    private static final int BLIGHTED_TICKS = 60;
    private static final int BLIGHTED_STACKING_CAP = 2;

    /**
     * {@code effect_on_hit(WITHER, duration, 0, stackingCap)}: repeat hits stack Wither up to III
     * instead of only refreshing -- issue #828's "wither-stacking" instance.
     */
    public static final Trait BLIGHTED =
            seamTrait(new EffectOnHit(MobEffects.WITHER, BLIGHTED_TICKS, 0, BLIGHTED_STACKING_CAP));

    /** Proposed (issue #828 maintainer decision): Weakness I for 5 seconds, refreshed each hit. */
    private static final int ENFEEBLING_TICKS = 100;

    /** {@code effect_on_hit(WEAKNESS, duration, 0, 0)}: issue #828's "weakness" instance. */
    public static final Trait ENFEEBLING = seamTrait(new EffectOnHit(MobEffects.WEAKNESS, ENFEEBLING_TICKS, 0, 0));

    /** Proposed (issue #828 maintainer decision): Slowness IV for 1 second -- a brief near-root. */
    private static final int SHACKLING_TICKS = 20;
    private static final int SHACKLING_AMPLIFIER = 3;

    /** {@code effect_on_hit(SLOWNESS, duration, amplifier, 0)}: issue #828's "brief root/slow" instance. */
    public static final Trait SHACKLING = seamTrait(
            new EffectOnHit(MobEffects.MOVEMENT_SLOWDOWN, SHACKLING_TICKS, SHACKLING_AMPLIFIER, 0));

    /** Proposed (issue #828 maintainer decision): 10 seconds of Glowing, refreshed each hit. */
    private static final int REVEALING_TICKS = 200;

    /** {@code effect_on_hit(GLOWING, duration, 0, 0)}: issue #828's "glowing" instance. */
    public static final Trait REVEALING = seamTrait(new EffectOnHit(MobEffects.GLOWING, REVEALING_TICKS, 0, 0));

    /** Proposed (issue #828 maintainer decision): Regeneration I for 5 seconds -- on the target, not the wielder. */
    private static final int MERCIFUL_TICKS = 100;

    /**
     * {@code effect_on_hit(REGENERATION, duration, 0, 0)}: issue #828's own "deliberately unhelpful"
     * novelty -- heals whatever it hits, same shape as {@link #ENDERFERENCE}'s upstream-precedented
     * joke trait.
     */
    public static final Trait MERCIFUL = seamTrait(new EffectOnHit(MobEffects.REGENERATION, MERCIFUL_TICKS, 0, 0));

    /** Proposed (issue #828 maintainer decision): Speed II on the wielder for 3 seconds, full charge only. */
    private static final int QUICKSTEP_TICKS = 60;
    private static final int QUICKSTEP_AMPLIFIER = 1;

    /**
     * {@code effect_on_self_on_hit(SPEED, duration, amplifier, chargedOnly=true)}: issue #828's
     * "Foot Fleet" reference instance, a self speed burst on a fully-charged swing.
     */
    public static final Trait QUICKSTEP = seamTrait(new ConditionalSeam(HitCondition.FULL_CHARGE, 1.0F,
            new EffectOnSelfOnHit(MobEffects.MOVEMENT_SPEED, QUICKSTEP_TICKS, QUICKSTEP_AMPLIFIER)));

    /**
     * {@code strip_effects(chance, count=1, chargedOnly=true)}: a leveled I-III buff strip on a
     * fully-charged swing, one class shared across the three levels ({@link #chargedStrike}'s
     * precedent) -- issue #828's own "Leveled instances share the class" instruction for "Purging".
     * Proposed chances (issue #828 maintainer decision): 25% / 50% / 75%.
     */
    public static final Trait UNRAVELING = purge(1);

    /** {@code strip_effects} level II. */
    public static final Trait UNRAVELING2 = purge(2);

    /** {@code strip_effects} level III. */
    public static final Trait UNRAVELING3 = purge(3);

    private static final float UNRAVELING_CHANCE_PER_LEVEL = 0.25F;
    private static final int UNRAVELING_COUNT = 1;

    private static Trait purge(int level) {
        return seamTrait(new ConditionalSeam(
                HitCondition.FULL_CHARGE, UNRAVELING_CHANCE_PER_LEVEL * level, new StripEffects(UNRAVELING_COUNT)));
    }

    /** Proposed (issue #828 maintainer decision): shave 50% of incoming healing for 5 seconds. */
    private static final float GRIEVOUS_FRACTION = 0.5F;
    private static final int GRIEVOUS_TICKS = 100;

    /**
     * {@code reduce_target_healing(fraction, duration)}: issue #828's "Mortal Wounds" reference
     * instance. See {@link ReduceTargetHealing} for the per-target-marker mechanism the issue asked
     * to pick the cheapest of.
     */
    public static final Trait GRIEVOUS = seamTrait(new ReduceTargetHealing(GRIEVOUS_FRACTION, GRIEVOUS_TICKS));

    /**
     * Proposed (issue #828 maintainer decision, <b>flagged for explicit sign-off</b> per the issue's
     * own instruction): shave 10 of vanilla's default 20-tick post-hit invulnerability window, i.e.
     * halve it. See {@link ShortenInvulnerability}'s javadoc for why this is the batch's riskiest
     * magnitude.
     */
    private static final int HARRYING_TICKS = 10;

    /** {@code shorten_invulnerability(ticks)}: issue #828's "Relentless" reference instance. */
    public static final Trait HARRYING = seamTrait(new ShortenInvulnerability(HARRYING_TICKS));

    /** Proposed (issue #828 maintainer decision): heal 15% of damage dealt, capped at 4 (2 hearts). */
    private static final float LEECHING_FRACTION = 0.15F;
    private static final float LEECHING_CAP = 4.0F;

    /**
     * {@code lifesteal(fraction, cap)}: issue #828's "Vampiric" reference instance. Not a migration
     * of necrotic's {@code LifestealOnHitSeam} -- see {@link Lifesteal}'s javadoc.
     */
    public static final Trait LEECHING = seamTrait(new Lifesteal(LEECHING_FRACTION, LEECHING_CAP));

    /**
     * Proposed (issue #828 maintainer decision): 35% chance on a fully-charged hit, arcing to up to 2
     * enemies within 3 blocks for half the landed damage.
     */
    private static final float ARCING_CHANCE = 0.35F;
    private static final double ARCING_RANGE = 3.0;
    private static final float ARCING_DAMAGE_FRACTION = 0.5F;
    private static final int ARCING_MAX_TARGETS = 2;

    /**
     * {@code chain_arc(chance, range, damageFraction, maxTargets)}, full charge only: issue #828's
     * "Chain Lightning" reference instance. Target selection reuses the scythe's AoE box query -- see
     * {@link ChainArc}.
     */
    public static final Trait ARCING = seamTrait(new ConditionalSeam(HitCondition.FULL_CHARGE, ARCING_CHANCE,
            new ChainArc(ARCING_RANGE, ARCING_DAMAGE_FRACTION, ARCING_MAX_TARGETS)));

    /**
     * {@code lightning_on_hit(WIELDER_FULL_HEALTH)}: issue #828's "Thundergod's Wrath" reference
     * instance, unconditional beyond the wielder's own health -- see {@link LightningOnHit}.
     */
    public static final Trait STORMCALLER =
            seamTrait(new ConditionalSeam(HitCondition.WIELDER_FULL_HEALTH, 1.0F, new LightningOnHit()));

    // ---------------------------------------------------------------- #626 (parity audit T17): the
    // five ammo-side traits, TinkerTraits:106-110, registered inert by #626's first slice and given
    // their entity-side behavior with the material arrow (#653). Freezing rides the combat seams
    // like any on-hit trait; breakable, hovering and endspeed live on ArrowEntity (the flight
    // callbacks upstream's AbstractProjectileTrait exposes) and splitting on BowItem#shoot (the
    // OnBowShoot moment), all keyed off {@link #has} membership.

    /** Reed. Upstream {@code TraitBreakable}: 50% chance the projectile breaks on hitting a block ({@code ArrowEntity}). */
    public static final Trait BREAKABLE = new Trait() {};

    /** Endrod. Upstream {@code TraitEndspeed}: projectiles fly near-instantly to their target ({@code ArrowEntity}, {@code BowItem}). */
    public static final Trait ENDSPEED = new Trait() {};

    /** {@code TraitFreezing#onHit}: 30 ticks a hit, amplifier capped at 4 (Slowness V). */
    private static final int FREEZING_TICKS = 30;
    private static final int FREEZING_MAX_AMPLIFIER = 4;

    /**
     * Ice. Upstream {@code TraitFreezing#onHit}: each landed hit stacks Slowness on the target, one
     * amplifier deeper per hit up to IV, 30 ticks each -- a combat seam, so it rides the arrow's
     * impact through the same pipeline every on-hit trait does (#653).
     */
    public static final Trait FREEZING =
            seamTrait(new StackingSlownessOnHitSeam(FREEZING_TICKS, FREEZING_MAX_AMPLIFIER));

    /** Blaze. Upstream {@code TraitHovering}: projectiles move slower but barely mind gravity ({@code ArrowEntity}). */
    public static final Trait HOVERING = new Trait() {};

    /** Bone shafts. Upstream {@code TraitSplitting}: a fired arrow may split into two ({@code BowItem#shoot}). */
    public static final Trait SPLITTING = new Trait() {};


    // ------------------------------------------------------------------ #680 (M4-5): the 1.20
    // clone's ARMOR-scope traits (MaterialTraitsDataProvider; behavior per ModifierProvider's
    // module definitions, NOTICE.md). Each rides Trait#onDefend -- the one armor seam (SCOPE.md
    // D8) -- or Trait#armorAttributes for the clone's AttributeModules. A trait is the modifier at
    // level 1, so every magnitude below is the clone's eachLevel constant times one.

    /**
     * Iron plating/maille. {@code ModifierIds.projectileProtection}: {@link Protection} 2 against
     * {@code #forgeweave:projectile_protection}, plus the clone's {@code MaxArmorAttributeModule}
     * of +0.05 knockback resistance.
     *
     * <p>Deviation, recorded: the clone takes the <em>maximum</em> 0.05 across worn pieces; here each
     * piece adds its own 0.05 (ponytail: one attribute hook, no cross-piece max). Four iron pieces
     * give 0.2 rather than 0.05.
     */
    public static final Trait PROJECTILE_PROTECTION = new Trait() {
        private final Protection protection = Protection.against(Protection.PROJECTILE_PROTECTION, PROJECTILE_PROTECTION_PER_LEVEL);

        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            protection.onDefend(defense, blow);
        }

        @Override
        public void armorAttributes(ResourceLocation id, EquipmentSlot slot, ItemAttributeModifiers.Builder out) {
            out.add(Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(id, PROJECTILE_PROTECTION_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.bySlot(slot));
        }
    };

    private static final float PROJECTILE_PROTECTION_PER_LEVEL = 2.0F;
    private static final float PROJECTILE_PROTECTION_KNOCKBACK_RESISTANCE = 0.05F;

    private static final float BLAST_PROTECTION_PER_LEVEL = 2.5F;

    /** Obsidian plating/maille. {@code ModifierIds.blastProtection}: {@link Protection} 2.5 against {@code #forgeweave:blast_protection}. */
    public static final Trait BLAST_PROTECTION = defendTrait(Protection.against(Protection.BLAST_PROTECTION, BLAST_PROTECTION_PER_LEVEL));

    private static final float MELEE_PROTECTION_PER_LEVEL = 2.0F;

    /**
     * Cobalt plating/maille. {@code ModifierIds.meleeProtection}: {@link Protection} 2 against
     * direct {@code #forgeweave:melee_protection} blows. The clone's +5% use-item speed per level
     * rides a Tinkers'-only attribute with no vanilla counterpart and is not ported.
     */
    public static final Trait MELEE_PROTECTION = defendTrait(
            Protection.against(Protection.MELEE_PROTECTION, MELEE_PROTECTION_PER_LEVEL).directOnly());

    private static final float CONSECRATED_PER_LEVEL = 1.25F;

    /** Silver plating/maille. {@code ModifierIds.consecrated}: {@link Protection} 1.25 against undead attackers. */
    public static final Trait CONSECRATED = defendTrait(
            Protection.of(CONSECRATED_PER_LEVEL).attacker(Protection.UNDEAD_ATTACKER));

    /**
     * Copper plating/maille. {@code DepthProtectionModule} (baseline 64, neutral range 32, 1.25 per
     * level): protection scales with how far below Y=64 the wearer stands, up to 2x at Y=-64 and
     * below, is nothing up to Y=96, and turns into a penalty above that, down to -1x at Y=160.
     */
    public static final Trait DEPTH_PROTECTION = new Trait() {
        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            if (Protection.CAN_PROTECT.test(defense.source())) {
                blow.addProtection(depthMultiplier((float) defense.defender().getY()) * DEPTH_PROTECTION_PER_LEVEL);
            }
        }
    };

    private static final float DEPTH_PROTECTION_PER_LEVEL = 1.25F;
    private static final float DEPTH_BASELINE_HEIGHT = 64.0F;
    private static final float DEPTH_NEUTRAL_RANGE = 32.0F;

    /** {@code DepthProtectionModule#getBonusMultiplier}, verbatim. */
    public static float depthMultiplier(float y) {
        if (y < DEPTH_BASELINE_HEIGHT) {
            return Math.min((DEPTH_BASELINE_HEIGHT - y) / DEPTH_BASELINE_HEIGHT, 2.0F);
        }
        float debuffHeight = DEPTH_BASELINE_HEIGHT + DEPTH_NEUTRAL_RANGE;
        if (y > debuffHeight) {
            return Math.max((debuffHeight - y) / DEPTH_BASELINE_HEIGHT, -1.0F);
        }
        return 0.0F;
    }

    /**
     * Manyullyn plating/maille. {@code ModifierIds.warded}'s {@code AdjustDamageModule}: at full
     * health, one damage per level comes off <em>after</em> armor, never below 1 and never above
     * what the blow was. The clone's lang row says 0.5 per level; its formula
     * ({@code VALUE - LEVEL, max 1, min VALUE}) is 1, and the formula is what is ported.
     */
    public static final Trait WARDED = new Trait() {
        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            LivingEntity defender = defense.defender();
            if (defender.getHealth() >= defender.getMaxHealth()) {
                blow.addFlatReduction(WARDED_REDUCTION_PER_LEVEL);
            }
        }
    };

    private static final float WARDED_REDUCTION_PER_LEVEL = 1.0F;

    /**
     * Amethyst bronze plating/maille. {@code ModifierIds.crystalstrike}: +5% attack speed per level
     * ({@code AttributeModule}, multiply total) and, summed across worn pieces
     * ({@code ArmorLevelModule} + {@code ModifierEvents#onKnockback}), knockback taken snaps to one
     * of {@code max(4, 2^(6 - level))} directions ({@link #onArmorKnockback}). The clone's +5% bad
     * effect duration rides a Tinkers'-only attribute and is not ported.
     */
    public static final Trait CRYSTALSTRIKE = new Trait() {
        @Override
        public void armorAttributes(ResourceLocation id, EquipmentSlot slot, ItemAttributeModifiers.Builder out) {
            out.add(Attributes.ATTACK_SPEED,
                    new AttributeModifier(id, CRYSTALSTRIKE_ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    EquipmentSlotGroup.bySlot(slot));
        }
    };

    private static final float CRYSTALSTRIKE_ATTACK_SPEED = 0.05F;

    /**
     * Knightslime plating/maille. {@code OvershieldModule} ({@code ModifierProvider}: 1.25
     * protection per level, 2 overslime consumed per hit): a protectable blow spends up to two
     * overslime and gets {@code 1.25 * consumed / 2} protection. Issue #728 replaced #690's banked
     * charge with the clone's real overslime ({@link #OVERSLIME}).
     */
    public static final Trait OVERSHIELD = new Trait() {
        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            if (!Protection.CAN_PROTECT.test(defense.source())) {
                return;
            }
            ItemStack piece = defense.tool();
            int consumed = Math.min(overslime(piece), OVERSHIELD_CONSUMED_PER_HIT);
            if (consumed <= 0) {
                return;
            }
            blow.addProtection(OVERSHIELD_PER_LEVEL * consumed / OVERSHIELD_CONSUMED_PER_HIT);
            setOverslime(piece, overslime(piece) - consumed);
        }
    };

    private static final float OVERSHIELD_PER_LEVEL = 1.25F;
    private static final int OVERSHIELD_CONSUMED_PER_HIT = 2;

    /**
     * Knightslime plating/maille, next to overshield (issue #728; the clone's
     * {@code OverslimeModifier} + {@code OverslimeModule}, {@code MaterialTraitsDataProvider}'s
     * {@code addTraits(knightslime, ARMOR, overshield, overslime)}). A pool of
     * {@link #OVERSLIME_CAPACITY} points ({@code OVERSLIME_STAT.add(builder, 50)}) that durability
     * loss is paid from before the piece itself ({@code DurabilityShieldModule#onDamageTool}), at the
     * cost of {@link #OVERSLIME_ARMOR_PENALTY} armor ({@code ToolStats.ARMOR.add(builder, -0.5f)})
     * unless an {@link #OVERSLIME_FRIEND} part is on the piece. Refilled with slime at the station
     * ({@code modifier.OverslimeRefill}). Stored as {@code forgeweave:overslime}.
     *
     * <p>Armor only for now (the issue): a tool would get the pool for free through
     * {@code ToolItem#damageItem}'s trait chain, but there reinforced rolls before it, where the
     * clone's priority 150 puts overslime first -- revisit if a tool material ever grants it.
     */
    public static final Trait OVERSLIME = new Trait() {
        @Override
        public int durabilityDamage(ItemStack stack, RandomSource random, int originalAmount, int amount) {
            int shield = Math.min(overslime(stack), amount);
            if (shield > 0) {
                setOverslime(stack, overslime(stack) - shield);
            }
            return amount - shield;
        }
    };

    /**
     * Marker: {@code ModifierIds.overslimeFriend}, the clone's tooltip-less tag modifier on the
     * skyslime and enderslime vines' ARMOR rows (blue slime vine and chorus maille here) that waives
     * {@link #OVERSLIME}'s armor penalty.
     */
    public static final Trait OVERSLIME_FRIEND = new Trait() {};

    /** {@code OverslimeModifier#addToolStats}: 50 capacity per overslime trait. */
    public static final int OVERSLIME_CAPACITY = 50;
    /** {@code OverslimeModifier#addToolStats}'s ARMOR row: -0.5 armor without an overslime friend. */
    public static final float OVERSLIME_ARMOR_PENALTY = 0.5F;
    /** {@code OverslimeModifier#getDurabilityRGB}: the light blue the overslime bar always draws in. */
    public static final int OVERSLIME_BAR_COLOR = 0x00A0FF;

    private static final ResourceLocation OVERSLIME_ID = id("overslime");
    private static final ResourceLocation OVERSLIME_FRIEND_ID = id("overslime_friend");
    /** M6 dedupe batch (issue #876): {@link #VINEWARDEN}'s own overslime-friend id. */
    private static final ResourceLocation VINEWARDEN_ID = id("vinewarden");

    /** The piece's current overslime ({@code OverslimeModule#getAmount}); 0 when absent. */
    public static int overslime(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.OVERSLIME.get(), 0);
    }

    /** {@code OverslimeModule#getCapacity}: {@link #OVERSLIME_CAPACITY} if the stack carries the trait, else 0. */
    public static int overslimeCapacity(ItemStack stack) {
        return has(stack, OVERSLIME) ? OVERSLIME_CAPACITY : 0;
    }

    /** {@code PersistentDataCapacityBar#setAmount}: clamped to the capacity, removed at zero. */
    public static void setOverslime(ItemStack stack, int amount) {
        int clamped = Math.min(amount, overslimeCapacity(stack));
        if (clamped <= 0) {
            stack.remove(ForgeweaveDataComponents.OVERSLIME.get());
        } else {
            stack.set(ForgeweaveDataComponents.OVERSLIME.get(), clamped);
        }
    }

    /**
     * What {@link #OVERSLIME} takes off a piece's armor at assembly, given its resolved trait ids:
     * {@link #OVERSLIME_ARMOR_PENALTY} once (the trait de-duplicates across parts), or nothing with
     * an {@link #OVERSLIME_FRIEND} aboard ({@code OverslimeModifier#addToolStats}'s
     * {@code has(OVERSLIME_FRIEND)} gate). Folded into {@code ArmorStats} by
     * {@code ToolAssemblyRecipes#assembleArmor} so every reader of the stat sees the net value, as
     * the clone's stat builder does.
     */
    public static float overslimeArmorPenalty(List<ResourceLocation> traitIds) {
        boolean friend = traitIds.contains(OVERSLIME_FRIEND_ID) || traitIds.contains(VINEWARDEN_ID);
        return traitIds.contains(OVERSLIME_ID) && !friend ? OVERSLIME_ARMOR_PENALTY : 0.0F;
    }

    /**
     * Slimewood general trait (issue #843, closes #180). Upstream's {@code OvergrowthModule}:
     * {@code chance.each_level(0.05)}, checked once a second, {@code CapacityBarHook#addAmount(1)}.
     * A material default trait is level 1 here (no leveled-trait framework for material grants), so
     * this is a flat 5%-per-second chance to regenerate one point of overslime -- the general-scope
     * counterpart slimewood pairs with its own {@link #OVERSLIME} grant, the first material to use
     * that combination off armor (see {@link #OVERSLIME}'s own javadoc, which anticipated this).
     */
    public static final Trait OVERGROWTH = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder.tickCount % OVERGROWTH_TICK_PERIOD != 0) {
                return;
            }
            int capacity = overslimeCapacity(stack);
            if (capacity <= 0 || overslime(stack) >= capacity) {
                return;
            }
            if (holder.getRandom().nextFloat() < OVERGROWTH_CHANCE) {
                setOverslime(stack, overslime(stack) + 1);
            }
        }
    };

    private static final int OVERGROWTH_TICK_PERIOD = 20;
    private static final float OVERGROWTH_CHANCE = 0.05F;

    /**
     * Queen's slime general trait (issue #843, closes #180). Upstream's {@code overlord.json} pairs
     * a {@code stat_copy} (10% of durability into overslime capacity) with a {@code stat_boost}
     * (-15% durability), both leveled. Forgeweave's material defaults are level 1 and there is no
     * per-trait overslime-capacity-bonus hook ({@link Trait} only sums durability and energy this
     * way, issue #830's precedent) -- adding one for this single user is not worth a new interface
     * hook, so the capacity half folds into queen's slime's own {@link #OVERSLIME} grant (a flat
     * {@link #OVERSLIME_CAPACITY} instead of a dynamic durability-scaled one) and only the durability
     * trade survives as a distinct effect. Deviation flagged in the PR body.
     */
    public static final Trait OVERLORD = new Trait() {
        @Override
        public int headDurability(int durability) {
            return Math.round(durability * OVERLORD_DURABILITY_MULTIPLIER);
        }
    };

    private static final float OVERLORD_DURABILITY_MULTIPLIER = 0.85F;

    /**
     * Necrotic bone plating/maille (issue #843, closes #180). Upstream's {@code RestoreLostHealthModule}
     * (level-1 defaults from its own builder: {@code chance.flat(0.15)}, {@code percentage.flat(0.25)},
     * {@code durability_usage.each_level(1)}, {@code effect_level.flat(1)}): a 15% chance on taking
     * damage to heal 25% of it back as Regeneration, at the cost of 1 durability. Simplified from the
     * clone's slow-regen-over-time framing to a flat instant heal (no {@code MobEffects.REGENERATION}
     * scaling curve worth porting for one level), and drops the heal sound.
     */
    public static final Trait RESTORE = new Trait() {
        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            if (blow.damage() <= 0 || defense.defender().getRandom().nextFloat() >= RESTORE_CHANCE) {
                return;
            }
            int healAmount = Math.round(RESTORE_PERCENT * blow.damage());
            if (healAmount <= 0) {
                return;
            }
            defense.defender().heal(healAmount);
            defense.tool().hurtAndBreak(RESTORE_DURABILITY_COST, defense.defender(), defense.slot());
        }
    };

    private static final float RESTORE_CHANCE = 0.15F;
    private static final float RESTORE_PERCENT = 0.25F;
    private static final int RESTORE_DURABILITY_COST = 1;

    /**
     * Hepatizon plating/maille (issue #843, closes #180). Upstream's {@code recurrent_protection.json}
     * ({@code percent.flat(0.5)}, {@code duration.each_level(100)}): taking damage grants a stacking
     * "momentum" buff that reduces future damage for 5 seconds per level. Forgeweave has no such buff
     * mob effect, so this ports the 50%-of-damage magnitude onto {@link DefendedBlow#addFlatReduction}
     * for the blow that triggered it instead of a forward-looking buff -- an instantaneous half-damage
     * ward rather than a persistent one, the same simplification shape {@link #WARDED} already uses.
     */
    public static final Trait RECURRENT_PROTECTION = new Trait() {
        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            blow.addFlatReduction(RECURRENT_PROTECTION_PERCENT * blow.damage());
        }
    };

    private static final float RECURRENT_PROTECTION_PERCENT = 0.5F;

    /**
     * Bone maille. {@code ModifierIds.piercingGuard}: the pierce counter -- a direct hit's living
     * attacker gets {@code forgeweave:pierce} (-1 armor per level) for four seconds, always, and
     * the piece pays one durability ({@code MobEffectModule.Builder#counterDurabilityUsage}).
     */
    public static final Trait PIERCING_GUARD = new Trait() {
        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            LivingEntity attacker = directAttacker(defense);
            if (attacker == null) {
                return;
            }
            attacker.addEffect(new MobEffectInstance(ForgeweaveMobEffects.PIERCE, PIERCE_TICKS, 0), defense.defender());
            defense.tool().hurtAndBreak(COUNTER_DURABILITY, defense.defender(), defense.slot());
        }
    };

    private static final int PIERCE_TICKS = 4 * 20;
    private static final int COUNTER_DURABILITY = 1;

    /**
     * Cactus maille. {@code ThornsModule} (15% chance per level, 1 + random 3 thorns damage, one
     * durability): a direct hit's attacker sometimes takes vanilla thorns damage from the wearer.
     * Distinct from spiky, cactus's general trait, which reflects a held tool's own attack damage.
     */
    public static final Trait THORNS = new Trait() {
        // #681: the thorns modifier's ThornsCounterSeam at level 1 -- one implementation for both.
        private final ThornsCounterSeam seam = new ThornsCounterSeam(THORNS_CHANCE, THORNS_CONSTANT, THORNS_RANDOM);

        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            seam.onDefend(defense, blow);
        }
    };

    private static final float THORNS_CHANCE = 0.15F;
    private static final float THORNS_CONSTANT = 1.0F;
    private static final float THORNS_RANDOM = 3.0F;

    /**
     * Chorus maille. {@code EnderclearanceModule} (25% chance per level, diameter 8 + 8 per level,
     * 16 tries): a direct hit's living attacker is sometimes teleported to a random spot nearby,
     * the way {@code TeleportHelper#randomNearbyTeleport} does it -- up to 16 random positions
     * within the diameter, the first the entity fits at wins. The ARMOR scope only: the clone's
     * same module also teleports what a chorus <em>weapon</em> hits, which is not a trait here.
     */
    public static final Trait ENDERCLEARANCE = new Trait() {
        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            LivingEntity attacker = directAttacker(defense);
            if (attacker == null || defense.level().getRandom().nextFloat() >= ENDERCLEARANCE_CHANCE) {
                return;
            }
            RandomSource random = defense.level().getRandom();
            for (int i = 0; i < ENDERCLEARANCE_TRIES; i++) {
                double x = attacker.getX() + (random.nextDouble() - 0.5) * ENDERCLEARANCE_DIAMETER;
                double y = Mth.clamp(attacker.getY() + (random.nextInt(ENDERCLEARANCE_DIAMETER) - ENDERCLEARANCE_DIAMETER / 2),
                        defense.level().getMinBuildHeight(), defense.level().getMaxBuildHeight() - 1);
                double z = attacker.getZ() + (random.nextDouble() - 0.5) * ENDERCLEARANCE_DIAMETER;
                if (attacker.randomTeleport(x, y, z, true)) {
                    return;
                }
            }
        }
    };

    private static final float ENDERCLEARANCE_CHANCE = 0.25F;
    private static final int ENDERCLEARANCE_DIAMETER = 16;
    private static final int ENDERCLEARANCE_TRIES = 16;

    /**
     * Blue slime vine maille. {@code ModifierIds.skyfall}: gravity {@code -5% - 10% per level}
     * (multiply total; -15% at the trait's level 1) and +1 safe fall distance per level -- both
     * vanilla attributes in 1.21, so no fall-event handler is needed.
     */
    public static final Trait SKYFALL = new Trait() {
        @Override
        public void armorAttributes(ResourceLocation id, EquipmentSlot slot, ItemAttributeModifiers.Builder out) {
            EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
            out.add(Attributes.GRAVITY,
                    new AttributeModifier(id, SKYFALL_GRAVITY_FLAT + SKYFALL_GRAVITY_PER_LEVEL, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), group);
            out.add(Attributes.SAFE_FALL_DISTANCE,
                    new AttributeModifier(id, SKYFALL_SAFE_FALL_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE), group);
        }
    };

    private static final float SKYFALL_GRAVITY_FLAT = -0.05F;
    private static final float SKYFALL_GRAVITY_PER_LEVEL = -0.1F;
    private static final float SKYFALL_SAFE_FALL_PER_LEVEL = 1.0F;

    /** A trait whose whole behavior is one worn-armor seam (the four protections). */
    private static Trait defendTrait(CombatSeam seam) {
        return new Trait() {
            @Override
            public void onDefend(CombatDefense defense, DefendedBlow blow) {
                seam.onDefend(defense, blow);
            }
        };
    }

    /**
     * The clone's {@code OnAttackedModifierHook#isDirectDamage} gate for counters: a living attacker
     * that struck the blow itself (no arrow, no potion), and not the wearer.
     */
    @Nullable
    private static LivingEntity directAttacker(CombatDefense defense) {
        LivingEntity attacker = defense.attacker();
        if (attacker == null || attacker == defense.defender() || !attacker.isAlive() || !defense.source().isDirect()) {
            return null;
        }
        return attacker;
    }

    /** Attribute modifiers the traits of a worn piece grant ({@link Trait#armorAttributes}); read by {@code ArmorPieceItem}. */
    public static void armorAttributes(ItemStack piece, EquipmentSlot slot, ItemAttributeModifiers.Builder out) {
        List<ResourceLocation> ids = piece.get(ForgeweaveDataComponents.TRAITS.get());
        if (ids == null) {
            return;
        }
        for (ResourceLocation id : ids) {
            Trait trait = lookup(id);
            if (trait != null) {
                trait.armorAttributes(id.withPrefix("trait/").withSuffix("/" + slot.getName()), slot, out);
            }
        }
    }

    /**
     * Registered on the game event bus in {@code Forgeweave}. Crystalstrike's knockback snap on the
     * wearer -- the clone's {@code ModifierEvents#onKnockback} + {@code RestrictAngleModule#onKnockback}:
     * the level is the number of worn pieces carrying it, and the push's horizontal direction is
     * rounded to the nearest of {@code max(4, 2^(6 - level))} compass directions.
     */
    public static void onArmorKnockback(LivingKnockBackEvent event) {
        int level = 0;
        for (ItemStack piece : event.getEntity().getArmorSlots()) {
            if (piece.getItem() instanceof ArmorPieceItem && !ToolItem.isBroken(piece) && has(piece, CRYSTALSTRIKE)) {
                level++;
            }
        }
        if (level == 0) {
            return;
        }
        double oldAngle = Mth.atan2(event.getRatioX(), event.getRatioZ());
        int directions = Math.max(4, (int) Math.pow(2, 6 - level));
        double increment = 2 * Math.PI / directions;
        double newAngle = Math.round(oldAngle / increment) * increment;
        Vec3 direction = new Vec3(event.getRatioX(), 0, event.getRatioZ()).yRot((float) (newAngle - oldAngle));
        event.setRatioX(direction.x);
        event.setRatioZ(direction.z);
    }

    /** One trait whose whole behavior is riding the combat seams -- see {@link Trait#combatSeams}. */
    private static Trait seamTrait(CombatSeam... seams) {
        List<CombatSeam> list = List.of(seams);
        return new Trait() {
            @Override
            public void combatSeams(Consumer<CombatSeam> out) {
                list.forEach(out);
            }
        };
    }

    /** Movement speed the tool's traits add while it is held, as a fraction of the holder's total. */
    public static float movementSpeedBonus(ItemStack stack) {
        float bonus = 0.0F;
        for (Trait trait : of(stack)) {
            bonus += trait.movementSpeedBonus();
        }
        return bonus;
    }

    /** Extra max durability the tool's traits carry beyond its materials and modifiers (alien). */
    public static int maxDurabilityBonus(ItemStack stack) {
        int bonus = 0;
        for (Trait trait : of(stack)) {
            bonus += trait.maxDurabilityBonus(stack);
        }
        return bonus;
    }

    /**
     * Total FE capacity every trait on {@code stack} contributes ({@link Trait#energyCapacity}),
     * summed the same way {@link #maxDurabilityBonus} is. Zero for a tool with no energy trait --
     * what keeps {@code Capabilities.EnergyStorage.ITEM} absent for it (issue #830 deliverable 1,
     * {@link EnergyBuffer#capability}).
     */
    public static int energyCapacity(ItemStack stack) {
        int capacity = 0;
        for (Trait trait : of(stack)) {
            capacity += trait.energyCapacity();
        }
        return capacity;
    }

    private static int stackLevel(ItemStack stack, DataComponentType<TraitStacks> component) {
        TraitStacks stacks = stack.get(component);
        return stacks == null ? 0 : stacks.level();
    }

    private static int stackTicksRemaining(ItemStack stack, DataComponentType<TraitStacks> component) {
        TraitStacks stacks = stack.get(component);
        return stacks == null ? 0 : stacks.ticksRemaining();
    }

    private static void decayStack(ItemStack stack, DataComponentType<TraitStacks> component) {
        TraitStacks stacks = stack.get(component);
        if (stacks == null || stacks.level() == 0) {
            return;
        }
        if (stacks.ticksRemaining() <= 1) {
            stack.remove(component);
        } else {
            stack.set(component, new TraitStacks(stacks.level(), stacks.ticksRemaining() - 1));
        }
    }

    /** M6 dedupe batch (issue #876): sharper the less worn it is. Original Forgeweave content, no upstream port. */
    public static final Trait UNYIELDING = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new DamageScalesWith(DamageScalesWith.Source.REMAINING_DURABILITY, 2.0F, 4.0F));
        }
    };

    /** M6 dedupe batch (issue #876): a fully-charged swing lands extra damage. Original Forgeweave content, no upstream port. */
    public static final Trait RADIANT_EDGE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.FULL_CHARGE, 1.0F, new FlatBonusDamage(3.0F)));
        }
    };

    /** M6 dedupe batch (issue #876): a facet of crystal absorbs a blow's shove. Original Forgeweave content, no upstream port. */
    public static final Trait VERDANT_WARD = new Trait() {
        @Override
        public float knockbackResistance() {
            return 0.15F;
        }
    };

    /** M6 dedupe batch (issue #876): a landed hit leaves the target glowing. Original Forgeweave content, no upstream port. */
    public static final Trait LUMINOUS = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.ANY, 1.0F,
                    new PotionEffectOnHitSeam(MobEffects.GLOWING, 0, 100)));
        }
    };

    /** M6 dedupe batch (issue #876): more damage the faster the wielder is moving. Original Forgeweave content, no upstream port. */
    public static final Trait STORMGLASS = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new DamageScalesWith(DamageScalesWith.Source.IMPACT_VELOCITY, 5.0F, 3.0F));
        }
    };

    /** M6 dedupe batch (issue #876): more damage against tougher targets. Original Forgeweave content, no upstream port. */
    public static final Trait BLOODGEM = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new DamageScalesWith(DamageScalesWith.Source.TARGET_MAX_HEALTH, 0.04F, 4.0F));
        }
    };

    /** M6 dedupe batch (issue #876): a flat void-forged edge. Original Forgeweave content, no upstream port. */
    public static final Trait VOIDTOUCHED = new Trait() {
        @Override
        public float attackDamageBonus(ItemStack stack) {
            return 1.0F;
        }
    };

    /** M6 dedupe batch (issue #876): shatters armored targets a little harder. Original Forgeweave content, no upstream port. */
    public static final Trait BRITTLEFORCE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.ARMORED, 1.0F, new FlatBonusDamage(2.0F)));
        }
    };

    /** M6 dedupe batch (issue #876): every hit shoves like a rockslide. Original Forgeweave content, no upstream port. */
    public static final Trait AVALANCHE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new KnockbackOnHitSeam(0.4F));
        }
    };

    /** M6 dedupe batch (issue #876): packed dense, wears slower. Original Forgeweave content, no upstream port. */
    public static final Trait LANDSLIDE = new Trait() {
        @Override
        public int maxDurabilityBonus(ItemStack stack) {
            return 25;
        }
    };

    /** M6 dedupe batch (issue #876): draws a bow noticeably faster. Original Forgeweave content, no upstream port. */
    public static final Trait SKYBORNE = new Trait() {
        @Override
        public float drawSpeedBonus() {
            return 0.08F;
        }
    };

    /** M6 dedupe batch (issue #876): a little extra spring in the step. Original Forgeweave content, no upstream port. */
    public static final Trait FEATHERFALL = new Trait() {
        @Override
        public float movementSpeedBonus() {
            return 0.03F;
        }
    };

    /** M6 dedupe batch (issue #876): light enough to swing faster. Original Forgeweave content, no upstream port. */
    public static final Trait BUOYANT = new Trait() {
        @Override
        public float attackSpeedBonus() {
            return 0.08F;
        }
    };

    /** M6 dedupe batch (issue #876): packs more mass into its durability pool. Original Forgeweave content, no upstream port. */
    public static final Trait COREBOUND = new Trait() {
        @Override
        public int maxDurabilityBonus(ItemStack stack) {
            return 40;
        }
    };

    /** M6 dedupe batch (issue #876): too heavy to be knocked far. Original Forgeweave content, no upstream port. */
    public static final Trait BALLAST = new Trait() {
        @Override
        public float knockbackResistance() {
            return 0.2F;
        }
    };

    /** M6 dedupe batch (issue #876): dense enough to slow the wielder slightly. Original Forgeweave content, no upstream port. */
    public static final Trait LEADFOOT = new Trait() {
        @Override
        public float movementSpeedBonus() {
            return -0.03F;
        }
    };

    /** M6 dedupe batch (issue #876): punishes a target already losing the fight. Original Forgeweave content, no upstream port. */
    public static final Trait OBSIDIAN_HEART = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.BELOW_WIELDER_HEALTH, 1.0F, new FlatBonusDamage(2.5F)));
        }
    };

    /** M6 dedupe batch (issue #876): a hit sometimes saps the target's strength. Original Forgeweave content, no upstream port. */
    public static final Trait VOIDREND = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.ANY, 0.25F,
                    new PotionEffectOnHitSeam(MobEffects.WEAKNESS, 0, 60)));
        }
    };

    /** M6 dedupe batch (issue #876): a heavy, shove-first strike. Original Forgeweave content, no upstream port. */
    public static final Trait SEISMIC = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new KnockbackOnHitSeam(0.6F));
        }
    };

    /** M6 dedupe batch (issue #876): opens a fight with a harder first strike. Original Forgeweave content, no upstream port. */
    public static final Trait STONEWAKE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.FULL_HEALTH, 1.0F, new FlatBonusDamage(2.0F)));
        }
    };

    /** M6 dedupe batch (issue #876): keeps a keen edge until it wears down. Original Forgeweave content, no upstream port. */
    public static final Trait KEENEDGE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new DamageScalesWith(DamageScalesWith.Source.REMAINING_DURABILITY, 1.5F, 3.0F));
        }
    };

    /** M6 dedupe batch (issue #876): a slow, unconditional trickle of self-repair. Original Forgeweave content, no upstream port. */
    public static final Trait TINSEEKER = new SelfRepairWhen(SelfRepairCondition.ALWAYS, 900);

    /** M6 dedupe batch (issue #876): a quick, disciplined swing. Original Forgeweave content, no upstream port. */
    public static final Trait STEELFAST = new Trait() {
        @Override
        public float attackSpeedBonus() {
            return 0.06F;
        }
    };

    /** M6 dedupe batch (issue #876): a brisk draw. Original Forgeweave content, no upstream port. */
    public static final Trait BRASSWIND = new Trait() {
        @Override
        public float drawSpeedBonus() {
            return 0.06F;
        }
    };

    /** M6 dedupe batch (issue #876): a charged hit sometimes sparks a burst of speed. Original Forgeweave content, no upstream port. */
    public static final Trait AMBERFLOW = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.ANY, 0.2F,
                    new PotionEffectOnHitSeam(MobEffects.MOVEMENT_SPEED, 0, 60)));
        }
    };

    /** M6 dedupe batch (issue #876): repairs a little faster after dark. Original Forgeweave content, no upstream port. */
    public static final Trait DUSKBLOOM = new SelfRepairWhen(SelfRepairCondition.NIGHT, 600);

    /** M6 dedupe batch (issue #876): striking a burning target quickens the follow-up. Original Forgeweave content, no upstream port. */
    public static final Trait EMBERWAKE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.BURNING, 1.0F,
                    new PotionEffectOnHitSeam(MobEffects.MOVEMENT_SPEED, 0, 40)));
        }
    };

    /** M6 dedupe batch (issue #876): mends faster than duskmend's own base rate at night. Original Forgeweave content, no upstream port. */
    public static final Trait SMOLDERVEIL = new SelfRepairWhen(SelfRepairCondition.NIGHT, 500);

    /** M6 dedupe batch (issue #876): a slow daylight mend, the mirror of duskmend. Original Forgeweave content, no upstream port. */
    public static final Trait ASHENBOND = new SelfRepairWhen(SelfRepairCondition.SUNLIT, 700);

    /** M6 dedupe batch (issue #876): a crystalline ward softens incoming force. Original Forgeweave content, no upstream port. */
    public static final Trait PRISMWARD = new Trait() {
        @Override
        public float knockbackResistance() {
            return 0.1F;
        }
    };

    /** M6 dedupe batch (issue #876): cracks armor a little harder than armor_breaker's base. Original Forgeweave content, no upstream port. */
    public static final Trait SHATTERMAIL = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.ARMORED, 1.0F, new FlatBonusDamage(1.5F)));
        }
    };

    /** M6 dedupe batch (issue #876): an unstable strike occasionally disorients the target. Original Forgeweave content, no upstream port. */
    public static final Trait CHAOSMARK = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.ANY, 0.15F,
                    new PotionEffectOnHitSeam(MobEffects.CONFUSION, 0, 60)));
        }
    };

    /**
     * M6 dedupe batch (issue #876): slime vine's own overslime-friend marker, split off {@code
     * overslime_friend} (chorus keeps that id) so the two materials don't share one -- functionally
     * identical to it (see {@link #overslimeArmorPenalty}'s {@code OR}), since slimevine_blue's whole
     * point in the roster is waiving the overslime armor penalty and a differently-named but
     * differently-behaving replacement would silently regress that. Original Forgeweave content, no
     * upstream port.
     */
    public static final Trait VINEWARDEN = new Trait() {};

    /** M6 dedupe batch (issue #876): a dense dark-alloy edge. Original Forgeweave content, no upstream port. */
    public static final Trait VOIDWOVEN = new Trait() {
        @Override
        public float attackDamageBonus(ItemStack stack) {
            return 1.5F;
        }
    };

    /** M6 dedupe batch (issue #876): an end-forged plate turns aside a blow. Original Forgeweave content, no upstream port. */
    public static final Trait CRYSTALLINE_WARD = new Trait() {
        @Override
        public float knockbackResistance() {
            return 0.18F;
        }
    };

    /** M6 dedupe batch (issue #876): hits harder while the wielder is still healthy. Original Forgeweave content, no upstream port. */
    public static final Trait QUARTZHEART = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new DamageScalesWith(DamageScalesWith.Source.WIELDER_HEALTH, 1.5F, 3.0F));
        }
    };

    /** M6 dedupe batch (issue #876): a second, smaller surge on a full-charge swing. Original Forgeweave content, no upstream port. */
    public static final Trait BATTEREDGE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.FULL_CHARGE, 1.0F, new FlatBonusDamage(2.5F)));
        }
    };

    /** M6 dedupe batch (issue #876): landing a hit sometimes sparks a burst of haste. Original Forgeweave content, no upstream port. */
    public static final Trait SPARKFORGE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.ANY, 0.2F,
                    new PotionEffectOnHitSeam(MobEffects.DIG_SPEED, 0, 60)));
        }
    };

    /** M6 dedupe batch (issue #876): bonus damage against a target already losing. Original Forgeweave content, no upstream port. */
    public static final Trait WARBOND = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.BELOW_WIELDER_HEALTH, 1.0F, new FlatBonusDamage(2.0F)));
        }
    };

    /** M6 dedupe batch (issue #876): a stable, oversized durability pool. Original Forgeweave content, no upstream port. */
    public static final Trait STEADFAST = new Trait() {
        @Override
        public int maxDurabilityBonus(ItemStack stack) {
            return 60;
        }
    };

    /** M6 dedupe batch (issue #876): a magnetic-coil jolt on every hit. Original Forgeweave content, no upstream port. */
    public static final Trait COILCHARGE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new KnockbackOnHitSeam(0.3F));
        }
    };

    /** M6 dedupe batch (issue #876): a very slow smoked-meat self-mend. Original Forgeweave content, no upstream port. */
    public static final Trait SMOKEHOUSE = new SelfRepairWhen(SelfRepairCondition.ALWAYS, 1000);

    /** M6 dedupe batch (issue #876): leaden weight resists being knocked back. Original Forgeweave content, no upstream port. */
    public static final Trait GRAVITIC = new Trait() {
        @Override
        public float knockbackResistance() {
            return 0.25F;
        }
    };

    /** M6 dedupe batch (issue #876): a keen magnesium-alloy edge. Original Forgeweave content, no upstream port. */
    public static final Trait ELEKTRONBOND = new Trait() {
        @Override
        public float attackDamageBonus(ItemStack stack) {
            return 1.0F;
        }
    };

    /** M6 dedupe batch (issue #876): sky stone takes a repair especially well. Original Forgeweave content, no upstream port. */
    public static final Trait STARFORGED = new Trait() {
        @Override
        public int repairBonus(int amount) {
            return amount * 10 / 100;
        }
    };

    /** M6 dedupe batch (issue #876): a bouncy slime cushions a blow. Original Forgeweave content, no upstream port. */
    public static final Trait RUBBERIZE = new Trait() {
        @Override
        public float knockbackResistance() {
            return 0.08F;
        }
    };

    /** M6 dedupe batch (issue #876): a psionic weave that mends best in daylight. Original Forgeweave content, no upstream port. */
    public static final Trait MATRIXBLOOM = new SelfRepairWhen(SelfRepairCondition.SUNLIT, 650);

    // ---------------------------------------------------------------- #876 M6 dedupe batch: every
    // material gets a distinct trait id. 49 of the new ids reuse existing ADR-0004 seams with new
    // parameters (above, alongside the ids they now stand apart from); the 10 below are the ones
    // that needed a genuinely new behavior, drawn from issue #841's gap list. Own numbers, own
    // wording -- inspiration-only per ADR-0003, no upstream port.

    private static final float WELLSPRING_CHANCE = 0.08F;
    private static final float WELLSPRING_HEAL = 1.0F;

    /** Cinderstone. #841 gap 1: heals the wielder's own health, not the tool's durability. */
    public static final Trait WELLSPRING = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && level.getRandom().nextFloat() < WELLSPRING_CHANCE) {
                breaker.heal(WELLSPRING_HEAL);
            }
        }
    };

    private static final int UNSTABLE_CORE_TICK_CHANCE = 400;
    private static final float UNSTABLE_CORE_SELF_DAMAGE = 2.0F;
    private static final float UNSTABLE_CORE_SPLASH_DAMAGE = 1.0F;
    private static final double UNSTABLE_CORE_RADIUS = 2.0;

    /**
     * Fulmenite. #841 gap 2: while the tool is actively in use, a small per-tick chance of an
     * unstable burst that hurts the wielder and anything standing close, past invulnerability
     * ({@link SecondaryDamage}) rather than a real explosion -- no block damage, no launched entities.
     */
    public static final Trait UNSTABLE_CORE = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder.getUseItem() != stack || level.getRandom().nextInt(UNSTABLE_CORE_TICK_CHANCE) != 0) {
                return;
            }
            SecondaryDamage.deal(holder, level.damageSources().magic(), UNSTABLE_CORE_SELF_DAMAGE);
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                    holder.getBoundingBox().inflate(UNSTABLE_CORE_RADIUS))) {
                if (nearby != holder) {
                    SecondaryDamage.deal(nearby, level.damageSources().magic(), UNSTABLE_CORE_SPLASH_DAMAGE);
                }
            }
        }
    };

    private static final float OVERBURDENED_CHANCE = 0.15F;
    private static final int OVERBURDENED_DURATION_TICKS = 60;

    /** Voltcinder. #841 gap 3: mining sometimes saddles the wielder with a brief digging slowdown. */
    public static final Trait OVERBURDENED = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            if (effective && level.getRandom().nextFloat() < OVERBURDENED_CHANCE) {
                breaker.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, OVERBURDENED_DURATION_TICKS, 0));
            }
        }
    };

    private static final float NOCTURNAL_EDGE_NIGHT_BONUS = 2.0F;
    private static final float NOCTURNAL_EDGE_DAY_PENALTY = -1.0F;

    /** Nightshale. #841 gap 5's combat half: stronger by night, a little weaker by day. */
    public static final Trait NOCTURNAL_EDGE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.NIGHT, 1.0F, new FlatBonusDamage(NOCTURNAL_EDGE_NIGHT_BONUS)));
            out.accept(new ConditionalSeam(HitCondition.DAY, 1.0F, new FlatBonusDamage(NOCTURNAL_EDGE_DAY_PENALTY)));
        }
    };

    private static final float OBLITERATE_CHANCE = 0.35F;

    /** Starfall stone. #841 gap 7: mined blocks sometimes drop nothing -- see {@link #onBlockBreakExperience}. */
    public static final Trait OBLITERATE = new Trait() {
        @Override
        public float dropDestroyChance() {
            return OBLITERATE_CHANCE;
        }
    };

    /** Tideiron. #841 gap 9: clears the water immediately around a block as it's mined. */
    public static final Trait TIDEBREAKER = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (level.getFluidState(neighbor).is(FluidTags.WATER)) {
                    level.setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
                }
            }
        }
    };

    private static final float MAGMAFORGE_CHANCE = 0.05F;

    /** Cinderforge. #841 gap 10: mining stone sometimes leaves molten lava in its place. */
    public static final Trait MAGMAFORGE = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && level.getRandom().nextFloat() < MAGMAFORGE_CHANCE) {
                level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
            }
        }
    };

    private static final int FALLOUT_DECAY_TICK_CHANCE = 1200;
    private static final int FALLOUT_DECAY_DURATION_TICKS = 40;
    private static final float FALLOUT_MUTATE_CHANCE = 0.03F;

    /**
     * Glowveil. #841 gap 15, partial per that gap's own note: a slow self-poison tick stands in for
     * "radioactive decay", and mining stone has a small chance of mutating an adjacent stone block
     * into deepslate rather than a bespoke mutation system.
     */
    public static final Trait FALLOUT = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (level.getRandom().nextInt(FALLOUT_DECAY_TICK_CHANCE) == 0) {
                holder.addEffect(new MobEffectInstance(MobEffects.POISON, FALLOUT_DECAY_DURATION_TICKS, 0));
            }
        }

        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            BlockPos above = pos.above();
            if (level.getBlockState(above).is(Blocks.STONE) && level.getRandom().nextFloat() < FALLOUT_MUTATE_CHANCE) {
                level.setBlockAndUpdate(above, Blocks.DEEPSLATE.defaultBlockState());
            }
        }
    };

    private static final int DAYBOUND_TICK_PERIOD = 100;
    private static final int DAYBOUND_GLOW_TICKS = 120;
    private static final float DAYBOUND_NIGHT_VISION_CHANCE = 0.3F;
    private static final int DAYBOUND_NIGHT_VISION_TICKS = 220;

    /** Daybrass. #841 gap 16: glows by day, sometimes grants night vision after dark. */
    public static final Trait DAYBOUND = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder.tickCount % DAYBOUND_TICK_PERIOD != 0) {
                return;
            }
            if (level.isDay()) {
                holder.addEffect(new MobEffectInstance(MobEffects.GLOWING, DAYBOUND_GLOW_TICKS, 0));
            } else if (level.getRandom().nextFloat() < DAYBOUND_NIGHT_VISION_CHANCE) {
                holder.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DAYBOUND_NIGHT_VISION_TICKS, 0));
            }
        }
    };

    private static final float BERSERKER_STANCE_BONUS = 4.0F;
    private static final int BERSERKER_STANCE_DURABILITY_COST = 1;

    /**
     * Truesteel. #841 gap 13, the flagship: the library has no persistent activate/deactivate toggle,
     * so this "activates" by holding crouch through the swing rather than a stateful stance with its
     * own data component -- bonus damage, paid for in extra wear, exactly while sneaking.
     */
    public static final Trait BERSERKER_STANCE = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(new ConditionalSeam(HitCondition.WIELDER_SNEAKING, 1.0F, new FlatBonusDamage(BERSERKER_STANCE_BONUS)));
        }

        @Override
        public int attackDurabilityBonus(ItemStack stack) {
            return BERSERKER_STANCE_DURABILITY_COST;
        }
    };

    // ---------------------------------------------------------------- issue #884: TAIGA-faithful
    // trait pass. The maintainer went through the M6 reference doc's §7.3 table material-by-material
    // and picked the faithful reference behavior over the post-#876 dedupe filler for eleven
    // materials (§884's own numbering); every id, description and magnitude below is Forgeweave's
    // own (ADR-0003, inspiration-only -- TAIGA is GPL-3.0, never derived from). The displaced ids
    // this batch frees up (emberwake, warbond, fertilizing, stonewake, ruthless, obsidian_heart,
    // unraveling2, unraveling3, smokehouse, plus wellspring, homeless once cinderstone itself is
    // retired -- see TrackBOre's own javadoc) stay registered but unassigned, per the issue's own
    // "registered but unassigned is acceptable" clause -- the #876 dedupe regression test
    // (MaterialTest#noTwoMaterialsShareANonExemptTraitId) only checks for duplicates, not coverage.

    private static final float EARTHMEND_CHANCE = 0.08F;
    private static final float EARTHMEND_HEAL = 1.0F;

    /**
     * Basalt. Issue #884 (1): the reference Basalt behavior, wellspring's sibling -- heals the
     * wielder for digging dirt-like blocks (dirt/grass/gravel/sand family) instead of wellspring's
     * stone family. Same chance/heal as wellspring; only the block family differs.
     */
    public static final Trait EARTHMEND = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            if (isDirtLike(state) && level.getRandom().nextFloat() < EARTHMEND_CHANCE) {
                breaker.heal(EARTHMEND_HEAL);
            }
        }
    };

    private static boolean isDirtLike(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }

    private static final int DUSKGRASP_TICKS = 100;

    /**
     * Murkiron. Issue #884 (5a): the reference Prometheum's handle darkness debuff -- one of two
     * halves the issue asked for, the other being a sneak-triggered mob-capture mechanic. The
     * capture half was found genuinely out of this batch's scope (a persisted captured-entity item,
     * a release interaction, non-boss/low-health checks and its own save-compat fixture -- a
     * mini-feature on its own next to the other ten trait changes this issue already carries) and is
     * filed as its own follow-up issue instead, named in the PR body, per the issue's own "ship the
     * debuff, file the capture" escape valve. Applied on the <b>handle</b> slot only, not general --
     * murkiron's existing {@code blighted}/{@code harrying} traits (general/head) are untouched.
     */
    public static final Trait DUSKGRASP = seamTrait(new EffectOnHit(MobEffects.DARKNESS, DUSKGRASP_TICKS, 0, 0));

    private static final float LEANHARVEST_DROP_CHANCE = 0.35F;
    private static final int LEANHARVEST_XP_BONUS = 2;

    /** Hardcinder. Issue #884 (6a): the reference Duranite's fewer-drops/more-XP mining trade-off. Replaces emberwake. */
    public static final Trait LEANHARVEST = new Trait() {
        @Override
        public float dropDestroyChance() {
            return LEANHARVEST_DROP_CHANCE;
        }

        @Override
        public int blockBreakExperience(RandomSource random, int xp) {
            return xp + LEANHARVEST_XP_BONUS;
        }
    };

    private static final int WAR_MEMORY_CAP = 20;
    private static final float WAR_MEMORY_PER_FIGHT = 0.15F;

    /**
     * Warspar. Issue #884 (8a): the reference Valyrium's adaptive damage -- the tool remembers which
     * entity types it has fought (a per-type counter in {@link WarMemory}, {@code
     * ForgeweaveDataComponents#WAR_MEMORY}) and deals growing bonus damage to that type on later
     * fights, capped at {@value #WAR_MEMORY_CAP} counted fights per type. Replaces warbond. Save-compat
     * fixture: {@code fixtures/save_compat/m884_tool_war_memory.snbt}.
     */
    public static final Trait WARMEMORY = new Trait() {
        @Override
        public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
            return Math.min(warMemoryCount(stack, target), WAR_MEMORY_CAP) * WAR_MEMORY_PER_FIGHT;
        }

        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            recordWarMemory(stack, target);
        }
    };

    private static int warMemoryCount(ItemStack stack, LivingEntity target) {
        WarMemory memory = stack.getOrDefault(ForgeweaveDataComponents.WAR_MEMORY.get(), WarMemory.EMPTY);
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        return memory.count(type);
    }

    private static void recordWarMemory(ItemStack stack, LivingEntity target) {
        WarMemory memory = stack.getOrDefault(ForgeweaveDataComponents.WAR_MEMORY.get(), WarMemory.EMPTY);
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        int current = memory.count(type);
        if (current >= WAR_MEMORY_CAP) {
            return;
        }
        stack.set(ForgeweaveDataComponents.WAR_MEMORY.get(), memory.with(type, current + 1));
    }

    private static final int HOLLOWYIELD_XP_BONUS = 3;

    /**
     * Hollowstone. Issue #884 (9a): the reference Uru's loot-to-XP swap -- mined blocks always drop
     * nothing (the highest {@link Trait#dropDestroyChance}, 1.0) but grant bonus XP instead. Replaces
     * fertilizing.
     */
    public static final Trait HOLLOWYIELD = new Trait() {
        @Override
        public float dropDestroyChance() {
            return 1.0F;
        }

        @Override
        public int blockBreakExperience(RandomSource random, int xp) {
            return xp + HOLLOWYIELD_XP_BONUS;
        }
    };

    private static final float SWIFTDIG_SPEED_BONUS = 2.0F;

    /**
     * Starfall stone. Issue #884 (11a): the reference Meteorite's other half -- faster on soft
     * (no-tool-needed) blocks, {@link #CRUMBLING}'s own {@code requiresCorrectToolForDrops} check
     * reused for the same "soft block" meaning. Kept alongside {@link #OBLITERATE} (#876's own
     * smash-drops half of this material's behavior), not a replacement for it -- starfall_stone
     * carries both.
     */
    public static final Trait SWIFTDIG = new Trait() {
        @Override
        public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
            return state.requiresCorrectToolForDrops() ? speed : speed + SWIFTDIG_SPEED_BONUS;
        }
    };

    // Voidglass's "much stronger" alien (issue #884 (12a)): triples ALIEN's pool and per-step growth,
    // duplicated rather than extracted from ALIEN's own block -- ALIEN has no existing parameterization
    // seam (its constants and AlienProgress component key are baked into one anonymous Trait), and
    // refactoring a shipped, save-compat-pinned trait to add one is a bigger, riskier diff than a
    // second self-contained copy with its own component key (ForgeweaveDataComponents#ALIEN_PROGRESS2).
    private static final int ALIEN2_POOL_POINTS = ALIEN_POOL_POINTS * 3;
    private static final int ALIEN2_DURABILITY_STEP = ALIEN_DURABILITY_STEP * 3;
    private static final float ALIEN2_SPEED_STEP = ALIEN_SPEED_STEP * 3;
    private static final float ALIEN2_ATTACK_STEP = ALIEN_ATTACK_STEP * 3;

    /**
     * Voidglass. Issue #884 (12a): {@link #ALIEN}'s upward stat drift, amplified -- the maintainer's
     * explicit "much stronger" call ("alien muito mais forte"). Same mechanic and cadence as
     * {@link #ALIEN}, {@value #ALIEN2_POOL_POINTS}-point pool (3x) and 3x per-step growth. Replaces
     * unraveling2.
     */
    public static final Trait ALIEN2 = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder instanceof FakePlayer || holder.tickCount % ALIEN_TICKS_PER_STAT != 0
                    || holder.getUseItem() == stack) {
                return;
            }
            AlienProgress progress = stack.get(ForgeweaveDataComponents.ALIEN_PROGRESS2.get());
            if (progress == null) {
                progress = new AlienProgress(rollAlien2Pool(level.getRandom()), AlienProgress.Portion.ZERO);
            }
            AlienProgress.Portion pool = progress.pool();
            AlienProgress.Portion given = progress.distributed();
            if (holder.tickCount % (ALIEN_TICKS_PER_STAT * 3) == 0) {
                if (given.attackDamage() < pool.attackDamage()) {
                    given = new AlienProgress.Portion(given.durability(), given.miningSpeed(),
                            given.attackDamage() + ALIEN2_ATTACK_STEP);
                }
            } else if (holder.tickCount % (ALIEN_TICKS_PER_STAT * 2) == 0) {
                if (given.miningSpeed() < pool.miningSpeed()) {
                    given = new AlienProgress.Portion(given.durability(),
                            given.miningSpeed() + ALIEN2_SPEED_STEP, given.attackDamage());
                }
            } else if (given.durability() < pool.durability()) {
                given = new AlienProgress.Portion(given.durability() + ALIEN2_DURABILITY_STEP,
                        given.miningSpeed(), given.attackDamage());
                stack.set(DataComponents.MAX_DAMAGE, stack.getMaxDamage() + ALIEN2_DURABILITY_STEP);
            }
            stack.set(ForgeweaveDataComponents.ALIEN_PROGRESS2.get(), new AlienProgress(pool, given));
        }

        @Override
        public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
            return speed + alien2Distributed(stack).miningSpeed();
        }

        @Override
        public float attackDamageBonus(ItemStack stack) {
            return alien2Distributed(stack).attackDamage();
        }

        @Override
        public int maxDurabilityBonus(ItemStack stack) {
            return alien2Distributed(stack).durability();
        }
    };

    private static AlienProgress.Portion rollAlien2Pool(RandomSource random) {
        int durability = 0;
        int speed = 0;
        int attack = 0;
        for (int point = 0; point < ALIEN2_POOL_POINTS; point++) {
            switch (random.nextInt(3)) {
                case 0 -> durability++;
                case 1 -> speed++;
                default -> attack++;
            }
        }
        return new AlienProgress.Portion(durability * ALIEN2_DURABILITY_STEP,
                speed * ALIEN2_SPEED_STEP, attack * ALIEN2_ATTACK_STEP);
    }

    private static AlienProgress.Portion alien2Distributed(ItemStack stack) {
        AlienProgress progress = stack.get(ForgeweaveDataComponents.ALIEN_PROGRESS2.get());
        return progress == null ? AlienProgress.Portion.ZERO : progress.distributed();
    }

    private static final float QUAKECRUMBLE_CHANCE = 0.25F;

    /**
     * Quakestone. Issue #884 (14a): the reference Triberium's mining-cracks-neighbors AoE -- distinct
     * from {@link #CASCADING}'s falling-column chase, this breaks a chance of the ordinarily-mineable
     * neighbors of whatever block was just mined, each with its own drops. Replaces stonewake.
     */
    public static final Trait QUAKECRUMBLE = new Trait() {
        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            if (!effective) {
                return;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                BlockState neighborState = level.getBlockState(neighbor);
                if (!neighborState.isAir() && neighborState.is(BlockTags.MINEABLE_WITH_PICKAXE)
                        && level.getRandom().nextFloat() < QUAKECRUMBLE_CHANCE) {
                    level.destroyBlock(neighbor, true, breaker);
                }
            }
        }
    };

    private static final float RIFTSTEP_CHANCE = 0.12F;
    private static final double RIFTSTEP_RANGE = 6.0;

    /**
     * Riftalloy. Issue #884 (17a): the reference Proxii's random short-range teleport -- a landed hit
     * sometimes teleports the target, sometimes the wielder, a coin flip either way. Replaces
     * unraveling3.
     */
    public static final Trait RIFTSTEP = new Trait() {
        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            if (level.getRandom().nextFloat() >= RIFTSTEP_CHANCE) {
                return;
            }
            randomShortTeleport(level.getRandom().nextBoolean() ? target : attacker, level.getRandom());
        }
    };

    private static void randomShortTeleport(LivingEntity entity, RandomSource random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double x = entity.getX() + (random.nextDouble() - 0.5) * 2 * RIFTSTEP_RANGE;
            double y = entity.getY() + (random.nextDouble() - 0.5) * 2 * RIFTSTEP_RANGE;
            double z = entity.getZ() + (random.nextDouble() - 0.5) * 2 * RIFTSTEP_RANGE;
            if (entity.randomTeleport(x, y, z, false)) {
                return;
            }
        }
    }

    private static final int DREADGRIP_TICKS = 60;
    private static final int DREADGRIP_SLOW_AMPLIFIER = 1;

    /**
     * Dreadalloy. Issue #884 (20a): the reference Imperomite's AI-numbing debuffs -- Slowness II and
     * Weakness I on the target, plus dropping a {@link Mob}'s current target where the API allows it
     * (the "brief no-target confusion" the issue asked for; a non-Mob {@code LivingEntity}, i.e. a
     * player, has no AI target to drop). Replaces obsidian_heart.
     */
    public static final Trait DREADGRIP = new Trait() {
        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DREADGRIP_TICKS, DREADGRIP_SLOW_AMPLIFIER));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DREADGRIP_TICKS, 0));
            if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    };

    private static final int BLOODTALLY_CAP = 200;
    private static final float BLOODTALLY_PER_KILL = 0.03F;

    /**
     * Hollowsteel. Issue #884 (22a): the reference Nihilite's permanent kill-count damage growth -- a
     * lifetime kill counter ({@code ForgeweaveDataComponents#KILL_TALLY}) that never decays, capped
     * at {@value #BLOODTALLY_CAP} counted kills (+{@value #BLOODTALLY_CAP}*{@value
     * #BLOODTALLY_PER_KILL} attack damage at the cap). Replaces ruthless. Save-compat fixture:
     * {@code fixtures/save_compat/m884_tool_kill_tally.snbt}.
     */
    public static final Trait BLOODTALLY = new Trait() {
        @Override
        public float attackDamageBonus(ItemStack stack) {
            int kills = stack.getOrDefault(ForgeweaveDataComponents.KILL_TALLY.get(), 0);
            return Math.min(kills, BLOODTALLY_CAP) * BLOODTALLY_PER_KILL;
        }

        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            if (!target.isDeadOrDying()) {
                return;
            }
            int kills = stack.getOrDefault(ForgeweaveDataComponents.KILL_TALLY.get(), 0);
            if (kills < BLOODTALLY_CAP) {
                stack.set(ForgeweaveDataComponents.KILL_TALLY.get(), kills + 1);
            }
        }
    };

    private static final float GAMEDROP_CHANCE = 0.5F;

    /**
     * Ironbrand. Issue #884 (13a, added mid-batch on maintainer request): the reference Terrax's
     * kills-drop-meat-not-XP trade -- every kill XP this tool would grant is suppressed
     * ({@link Trait#killExperience}), and about half the time a kill drops a cooked cut of meat
     * instead, {@link #BACONLICIOUS}'s drop-an-item-entity mechanism reused for a different theme
     * and trigger (a chance on every kill, not a chance on every hit/block). Replaces smokehouse.
     */
    public static final Trait GAMEDROP = new Trait() {
        @Override
        public int killExperience(RandomSource random, int xp) {
            return 0;
        }

        @Override
        public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
            if (target.isDeadOrDying() && level.getRandom().nextFloat() < GAMEDROP_CHANCE) {
                level.addFreshEntity(new ItemEntity(level, target.getX(), target.getY(), target.getZ(),
                        new ItemStack(Items.COOKED_BEEF)));
            }
        }
    };

    private static final Map<ResourceLocation, Trait> REGISTRY = Map.ofEntries(
            Map.entry(id("ecological"), ECOLOGICAL),
            Map.entry(id("cheap"), CHEAP),
            Map.entry(id("cheapskate"), CHEAPSKATE),
            Map.entry(id("crude"), CRUDE),
            Map.entry(id("crude2"), CRUDE2),
            Map.entry(id("fractured"), FRACTURED),
            Map.entry(id("magnetic"), MAGNETIC),
            Map.entry(id("magnetic2"), MAGNETIC2),
            Map.entry(id("momentum"), MOMENTUM),
            Map.entry(id("lightweight"), LIGHTWEIGHT),
            Map.entry(id("stonebound"), STONEBOUND),
            Map.entry(id("petramor"), PETRAMOR),
            Map.entry(id("insatiable"), INSATIABLE),
            Map.entry(id("coldblooded"), COLDBLOODED),
            Map.entry(id("established"), ESTABLISHED),
            // #103 metal materials: rose gold's quick, netherite's reinforced_core (netherite's
            // fireproof was retired by #447 -- every dropped tool is indestructible now).
            Map.entry(id("quick"), QUICK),
            Map.entry(id("reinforced_core"), REINFORCED_CORE),
            // #230 M3.2 stateful/special traits.
            Map.entry(id("alien"), ALIEN),
            Map.entry(id("shocking"), SHOCKING),
            Map.entry(id("slimey_green"), SLIMEY_GREEN),
            Map.entry(id("slimey_blue"), SLIMEY_BLUE),
            Map.entry(id("baconlicious"), BACONLICIOUS),
            Map.entry(id("tasty"), TASTY),
            Map.entry(id("vintage"), VINTAGE),
            // #228 M3.2 mining/durability-economy traits.
            Map.entry(id("duritos"), DURITOS),
            Map.entry(id("jagged"), JAGGED),
            Map.entry(id("aquadynamic"), AQUADYNAMIC),
            Map.entry(id("aridiculous"), ARIDICULOUS),
            Map.entry(id("crumbling"), CRUMBLING),
            Map.entry(id("unnatural"), UNNATURAL),
            Map.entry(id("dense"), DENSE),
            // Paper carries writable2 (+2) on the head and writable (+1) generally, so an all-paper
            // tool nets +3 under resolve()'s one-id-once rule -- upstream's TraitWritable pair
            // exactly (issue #344); see WRITABLE.
            Map.entry(id("writable"), WRITABLE),
            Map.entry(id("writable2"), WRITABLE2),
            Map.entry(id("squeaky"), SQUEAKY),
            Map.entry(id("autosmelt"), AUTOSMELT),
            // #229 M3.2 combat-seam trait batch; material wiring lands in later M3.2 issues.
            Map.entry(id("prickly"), PRICKLY),
            Map.entry(id("spiky"), SPIKY),
            Map.entry(id("hellish"), HELLISH),
            Map.entry(id("superheat"), SUPERHEAT),
            Map.entry(id("holy"), HOLY),
            Map.entry(id("poisonous"), POISONOUS),
            Map.entry(id("heavy"), HEAVY),
            Map.entry(id("stiff"), STIFF),
            Map.entry(id("sharp"), SHARP),
            Map.entry(id("splintering"), SPLINTERING),
            Map.entry(id("flammable"), FLAMMABLE),
            Map.entry(id("enderference"), ENDERFERENCE),
            Map.entry(id("lacerating"), LACERATING),
            // #827 M6 damage-scaling trait behavior library (ADR-0004); not yet assigned to a
            // material -- that wiring is a later M6 issue.
            Map.entry(id("pristine"), PRISTINE),
            Map.entry(id("vigorous"), VIGOROUS),
            Map.entry(id("predatory"), PREDATORY),
            Map.entry(id("colossal"), COLOSSAL),
            Map.entry(id("kinetic"), KINETIC),
            Map.entry(id("dominant"), DOMINANT),
            Map.entry(id("armor_breaker"), ARMOR_BREAKER),
            Map.entry(id("opportunist"), OPPORTUNIST),
            Map.entry(id("surging"), SURGING),
            Map.entry(id("surging2"), SURGING2),
            Map.entry(id("surging3"), SURGING3),
            Map.entry(id("ruthless"), RUTHLESS),
            Map.entry(id("escalating"), ESCALATING),
            // #829 M6 utility/economy trait behavior library (ADR-0004); not yet assigned to a
            // material -- that wiring is a later M6 issue.
            Map.entry(id("sunmend"), SUNMEND),
            Map.entry(id("duskmend"), DUSKMEND),
            Map.entry(id("cascading"), CASCADING),
            Map.entry(id("fertilizing"), FERTILIZING),
            // #830 M6 energy buffer trait behavior library (ADR-0004); not yet assigned to a
            // material -- that wiring is a later M6 issue.
            Map.entry(id("energized"), ENERGIZED),
            Map.entry(id("solar_recharge"), SOLAR_RECHARGE),
            Map.entry(id("kinetic_charge"), KINETIC_CHARGE),
            // #828 M6 on-hit effect trait behavior library (ADR-0004); not yet assigned to a
            // material -- that wiring is a later M6 issue.
            Map.entry(id("blighted"), BLIGHTED),
            Map.entry(id("enfeebling"), ENFEEBLING),
            Map.entry(id("shackling"), SHACKLING),
            Map.entry(id("revealing"), REVEALING),
            Map.entry(id("merciful"), MERCIFUL),
            Map.entry(id("quickstep"), QUICKSTEP),
            Map.entry(id("unraveling"), UNRAVELING),
            Map.entry(id("unraveling2"), UNRAVELING2),
            Map.entry(id("unraveling3"), UNRAVELING3),
            Map.entry(id("grievous"), GRIEVOUS),
            Map.entry(id("harrying"), HARRYING),
            Map.entry(id("leeching"), LEECHING),
            Map.entry(id("arcing"), ARCING),
            Map.entry(id("stormcaller"), STORMCALLER),
            // #626 T17 ammo traits; entity-side behavior lands with the material arrow.
            Map.entry(id("breakable"), BREAKABLE),
            Map.entry(id("endspeed"), ENDSPEED),
            Map.entry(id("freezing"), FREEZING),
            Map.entry(id("hovering"), HOVERING),
            Map.entry(id("splitting"), SPLITTING),
            // #680 -- the M4-5 ARMOR-scope traits.
            Map.entry(id("projectile_protection"), PROJECTILE_PROTECTION),
            Map.entry(id("depth_protection"), DEPTH_PROTECTION),
            Map.entry(id("blast_protection"), BLAST_PROTECTION),
            Map.entry(id("melee_protection"), MELEE_PROTECTION),
            Map.entry(id("warded"), WARDED),
            Map.entry(id("crystalstrike"), CRYSTALSTRIKE),
            Map.entry(id("consecrated"), CONSECRATED),
            Map.entry(id("overshield"), OVERSHIELD),
            Map.entry(id("overslime"), OVERSLIME),
            Map.entry(id("overslime_friend"), OVERSLIME_FRIEND),
            Map.entry(id("overgrowth"), OVERGROWTH),
            Map.entry(id("overlord"), OVERLORD),
            Map.entry(id("restore"), RESTORE),
            Map.entry(id("recurrent_protection"), RECURRENT_PROTECTION),
            Map.entry(id("piercing_guard"), PIERCING_GUARD),
            Map.entry(id("thorns"), THORNS),
            Map.entry(id("enderclearance"), ENDERCLEARANCE),
            Map.entry(id("skyfall"), SKYFALL),
            Map.entry(id("unyielding"), UNYIELDING),
            Map.entry(id("radiant_edge"), RADIANT_EDGE),
            Map.entry(id("verdant_ward"), VERDANT_WARD),
            Map.entry(id("luminous"), LUMINOUS),
            Map.entry(id("stormglass"), STORMGLASS),
            Map.entry(id("bloodgem"), BLOODGEM),
            Map.entry(id("voidtouched"), VOIDTOUCHED),
            Map.entry(id("brittleforce"), BRITTLEFORCE),
            Map.entry(id("obliterate"), OBLITERATE),
            Map.entry(id("avalanche"), AVALANCHE),
            Map.entry(id("landslide"), LANDSLIDE),
            Map.entry(id("skyborne"), SKYBORNE),
            Map.entry(id("featherfall"), FEATHERFALL),
            Map.entry(id("buoyant"), BUOYANT),
            Map.entry(id("corebound"), COREBOUND),
            Map.entry(id("ballast"), BALLAST),
            Map.entry(id("leadfoot"), LEADFOOT),
            Map.entry(id("obsidian_heart"), OBSIDIAN_HEART),
            Map.entry(id("voidrend"), VOIDREND),
            Map.entry(id("seismic"), SEISMIC),
            Map.entry(id("stonewake"), STONEWAKE),
            Map.entry(id("keenedge"), KEENEDGE),
            Map.entry(id("wellspring"), WELLSPRING),
            Map.entry(id("tinseeker"), TINSEEKER),
            Map.entry(id("steelfast"), STEELFAST),
            Map.entry(id("brasswind"), BRASSWIND),
            Map.entry(id("amberflow"), AMBERFLOW),
            Map.entry(id("duskbloom"), DUSKBLOOM),
            Map.entry(id("emberwake"), EMBERWAKE),
            Map.entry(id("overburdened"), OVERBURDENED),
            Map.entry(id("smolderveil"), SMOLDERVEIL),
            Map.entry(id("ashenbond"), ASHENBOND),
            Map.entry(id("fallout"), FALLOUT),
            Map.entry(id("nocturnal_edge"), NOCTURNAL_EDGE),
            Map.entry(id("prismward"), PRISMWARD),
            Map.entry(id("shattermail"), SHATTERMAIL),
            Map.entry(id("chaosmark"), CHAOSMARK),
            Map.entry(id("vinewarden"), VINEWARDEN),
            Map.entry(id("magmaforge"), MAGMAFORGE),
            Map.entry(id("voidwoven"), VOIDWOVEN),
            Map.entry(id("crystalline_ward"), CRYSTALLINE_WARD),
            Map.entry(id("quartzheart"), QUARTZHEART),
            Map.entry(id("daybound"), DAYBOUND),
            Map.entry(id("batteredge"), BATTEREDGE),
            Map.entry(id("sparkforge"), SPARKFORGE),
            Map.entry(id("unstable_core"), UNSTABLE_CORE),
            Map.entry(id("warbond"), WARBOND),
            Map.entry(id("steadfast"), STEADFAST),
            Map.entry(id("coilcharge"), COILCHARGE),
            Map.entry(id("smokehouse"), SMOKEHOUSE),
            Map.entry(id("gravitic"), GRAVITIC),
            Map.entry(id("elektronbond"), ELEKTRONBOND),
            Map.entry(id("starforged"), STARFORGED),
            Map.entry(id("rubberize"), RUBBERIZE),
            Map.entry(id("tidebreaker"), TIDEBREAKER),
            Map.entry(id("matrixbloom"), MATRIXBLOOM),
            Map.entry(id("berserker_stance"), BERSERKER_STANCE),
            // #884 TAIGA-faithful trait pass.
            Map.entry(id("earthmend"), EARTHMEND),
            Map.entry(id("duskgrasp"), DUSKGRASP),
            Map.entry(id("leanharvest"), LEANHARVEST),
            Map.entry(id("warmemory"), WARMEMORY),
            Map.entry(id("hollowyield"), HOLLOWYIELD),
            Map.entry(id("swiftdig"), SWIFTDIG),
            Map.entry(id("alien2"), ALIEN2),
            Map.entry(id("quakecrumble"), QUAKECRUMBLE),
            Map.entry(id("riftstep"), RIFTSTEP),
            Map.entry(id("dreadgrip"), DREADGRIP),
            Map.entry(id("bloodtally"), BLOODTALLY),
            Map.entry(id("gamedrop"), GAMEDROP));

    // ---------------------------------------------------------------- additive trait sources
    // (issue #832, ADR-0004 item 3): datapack definitions and KubeJS script traits.

    /** The loaded {@code trait_definition} registry, re-snapshotted on every data load/sync. */
    private static volatile Map<ResourceLocation, Trait> DATAPACK = Map.of();

    /** Traits KubeJS startup scripts registered; see {@code kubejs.ForgeweaveKubeJSPlugin}. */
    private static final Map<ResourceLocation, Trait> SCRIPTED = new ConcurrentHashMap<>();

    /**
     * The behaviour behind {@code id}: the Java roster first, then the datapack snapshot, then
     * script traits -- {@code null} for an id no source implements.
     */
    @Nullable
    public static Trait lookup(ResourceLocation id) {
        Trait trait = REGISTRY.get(id);
        if (trait == null) {
            trait = DATAPACK.get(id);
        }
        return trait == null ? SCRIPTED.get(id) : trait;
    }

    /**
     * A KubeJS startup script's trait. A built-in id is refused rather than shadowed, so a typo
     * ({@code 'forgeweave:poisonous'}) is a script error in the KubeJS console, not a silent
     * redefinition of shipped content; re-registering a script id replaces it (script reloads).
     */
    public static void registerScripted(ResourceLocation id, Trait trait) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Trait id '" + id + "' is a built-in Forgeweave trait and cannot be "
                    + "redefined from a script; pick an id in your own namespace.");
        }
        SCRIPTED.put(id, trait);
    }

    /**
     * Registered on the game event bus in {@code Forgeweave}: fires on the server after every data
     * load ({@code /reload} included) and on the client once the synced registries arrive, which
     * are exactly the two moments the {@link TraitDefinition} registry can change. A datapack id
     * colliding with a built-in one is logged and ignored -- the built-in wins, per the issue.
     */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        Map<ResourceLocation, Trait> loaded = new LinkedHashMap<>();
        event.getRegistryAccess().registry(TraitDefinition.REGISTRY).ifPresent(registry -> registry.entrySet()
                .forEach(entry -> {
                    ResourceLocation id = entry.getKey().location();
                    if (REGISTRY.containsKey(id)) {
                        LOGGER.warn("Datapack trait definition '{}' names a built-in Forgeweave trait; the built-in "
                                + "behavior wins and the definition is ignored (issue #832).", id);
                    } else {
                        loaded.put(id, entry.getValue().trait());
                    }
                }));
        DATAPACK = Map.copyOf(loaded);
        WARNED_UNKNOWN.clear();
        if (!loaded.isEmpty()) {
            LOGGER.info("Loaded {} datapack trait definitions: {}", loaded.size(), loaded.keySet());
        }
    }

    // ---------------------------------------------------------------- extra-info lines (parity audit
    // T26, issue #457) -- upstream 1.12's AbstractTrait#getExtraInfo. Traits are modifiers upstream,
    // so their lines come out of the same TooltipBuilder#addModifierInfo call the modifier ones do
    // and land in the Tool Station's tool info panel.

    private static final ResourceLocation CRUDE_ID = id("crude");
    private static final ResourceLocation CRUDE2_ID = id("crude2");
    private static final ResourceLocation HELLISH_ID = id("hellish");
    private static final ResourceLocation HOLY_ID = id("holy");
    private static final ResourceLocation JAGGED_ID = id("jagged");
    private static final ResourceLocation LIGHTWEIGHT_ID = id("lightweight");
    private static final ResourceLocation STONEBOUND_ID = id("stonebound");
    private static final ResourceLocation SUPERHEAT_ID = id("superheat");
    private static final ResourceLocation ALIEN_ID = id("alien");

    /**
     * Which trait ids produce {@code .extra} lines, so a lang-coverage test can guard those keys the
     * way {@code ModifierLangCoverageTest} guards the modifier ones. Alien is absent on purpose: its
     * three lines reuse the {@code gui.forgeweave.stat.*} keys rather than one of its own, exactly as
     * upstream reuses {@code HeadMaterialStats#formatDurability} and friends.
     */
    public static Set<ResourceLocation> extraInfoIds() {
        return Set.of(CRUDE_ID, CRUDE2_ID, HELLISH_ID, HOLY_ID, JAGGED_ID, LIGHTWEIGHT_ID,
                STONEBOUND_ID, SUPERHEAT_ID);
    }

    /**
     * What one trait on this tool is currently worth -- upstream's eight {@code getExtraInfo}
     * implementations, ported one for one:
     *
     * <ul>
     *   <li><b>crude / crude2</b> ({@code TraitCrude:38-44}): the bonus fraction against unarmored.
     *   <li><b>hellish</b> ({@code TraitHellish:29-34}) and <b>holy</b> ({@code TraitHoly:41-45}):
     *       their flat bonus damage.
     *   <li><b>jagged</b> ({@code TraitJagged:37-42}) and <b>stonebound</b>
     *       ({@code TraitStonebound:38-43}): the shared {@link #wearCurve}, which is what makes these
     *       two lines worth having -- they are the only trait numbers that move as the tool wears.
     *   <li><b>lightweight</b> ({@code TraitLightweight:53-58}) and <b>superheat</b>
     *       ({@code TraitSuperheat:31-36}): their fixed bonus fractions.
     *   <li><b>alien</b> ({@code TraitAlien:84-91}): the durability, mining speed and attack it has
     *       distributed so far, as three stat lines.
     * </ul>
     *
     * <p>Deviation from 1.12, recorded: upstream's crude is one id whose level stacks, so its line
     * reports the combined bonus; Forgeweave ships {@code crude} and {@code crude2} as separate ids
     * (issue #231), so an all-flint tool shows two rows of 5% and 10% rather than one of 15%. The
     * numbers are the same, the presentation follows the id split that was already there.
     *
     * @param tool the assembled stack, read by the three traits whose value is not a constant
     */
    public static List<Component> extraInfo(ResourceLocation id, ItemStack tool) {
        String key = "trait." + id.getNamespace() + "." + id.getPath() + ".extra";
        if (CRUDE_ID.equals(id) || CRUDE2_ID.equals(id)) {
            float bonus = CRUDE_FRACTION_PER_LEVEL * (CRUDE2_ID.equals(id) ? 2 : 1);
            return List.of(Component.translatable(key, StationText.formatPercent(bonus)));
        }
        if (HELLISH_ID.equals(id)) {
            return List.of(Component.translatable(key, StationText.formatNumber(HELLISH_BONUS_DAMAGE)));
        }
        if (HOLY_ID.equals(id)) {
            return List.of(Component.translatable(key, StationText.formatNumber(HOLY_BONUS_DAMAGE)));
        }
        if (JAGGED_ID.equals(id) || STONEBOUND_ID.equals(id)) {
            return List.of(Component.translatable(key, StationText.formatNumber(wearCurve(tool))));
        }
        if (LIGHTWEIGHT_ID.equals(id)) {
            return List.of(Component.translatable(key, StationText.formatPercent(LIGHTWEIGHT_BONUS)));
        }
        if (SUPERHEAT_ID.equals(id)) {
            return List.of(Component.translatable(key, StationText.formatPercent(SUPERHEAT_BONUS_FRACTION)));
        }
        if (ALIEN_ID.equals(id)) {
            AlienProgress.Portion given = alienDistributed(tool);
            return List.of(
                    Component.translatable("gui.forgeweave.stat.durability",
                            StationText.formatNumber(given.durability())),
                    Component.translatable("gui.forgeweave.stat.mining_speed",
                            StationText.formatNumber(given.miningSpeed())),
                    Component.translatable("gui.forgeweave.stat.attack_damage",
                            StationText.formatNumber(given.attackDamage())));
        }
        return List.of();
    }


    /**
     * The assembled tool's durability after the <b>head</b> material's head-scoped traits adjusted it
     * ({@link Trait#headDurability}), applied in order. Called from {@code ToolStats#compute} with
     * {@code head.traits().forPart(HEAD)}; an id no version of Forgeweave implements simply leaves the
     * durability alone, same as every other hook.
     */
    public static int headDurability(List<ResourceLocation> traitIds, int durability) {
        for (ResourceLocation id : traitIds) {
            Trait trait = lookup(id);
            if (trait != null) {
                durability = trait.headDurability(durability);
            }
        }
        return durability;
    }

    /**
     * The trait ids an assembled tool gets from its three materials, each material contributing the
     * traits it scopes to the part it was used as ({@link Material.Traits#forPart}), de-duplicated
     * head-first (see class javadoc).
     */
    public static List<ResourceLocation> resolve(Material head, @Nullable Material binding, Material handle) {
        return Stream.of(
                head.traits().forPart(PartItem.Kind.HEAD),
                // Nullable since issue #155: battlesign, frying pan and dagger have no EXTRA part at
                // all, so there is no binding material to take traits from (see ToolMaterials).
                binding == null ? List.<ResourceLocation>of() : binding.traits().forPart(PartItem.Kind.EXTRA),
                handle.traits().forPart(PartItem.Kind.HANDLE))
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    /** The traits of an assembled tool, in the order {@link #resolve} stored them. */
    /**
     * Whether {@code stack}'s trait list carries {@code trait} -- what the material arrow's
     * entity-side behaviors and {@code BowItem}'s shot adjustments key on (#653), upstream's
     * {@code TinkerUtil.hasTrait} membership check.
     */
    public static boolean has(ItemStack stack, Trait trait) {
        return of(stack).contains(trait);
    }

    public static List<Trait> of(ItemStack stack) {
        List<ResourceLocation> ids = stack.get(ForgeweaveDataComponents.TRAITS.get());
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Trait> traits = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Trait trait = lookup(id);
            if (trait != null) {
                traits.add(trait);
            } else if (WARNED_UNKNOWN.add(id)) {
                LOGGER.warn("Unknown trait '{}' on {}; ignoring it. Materials may only name traits this "
                        + "version of Forgeweave implements (ADR-0002).", id, stack.getItem());
            }
        }
        return traits;
    }

    /** Flat attack damage the tool's traits add, folded into its one attack damage modifier. */
    public static float attackDamageBonus(ItemStack stack) {
        float bonus = 0.0F;
        for (Trait trait : of(stack)) {
            bonus += trait.attackDamageBonus(stack);
        }
        return bonus;
    }

    /** Extra durability the tool's traits add to one repair item's worth of repair. */
    public static int repairBonus(ItemStack stack, int amount) {
        int bonus = 0;
        for (Trait trait : of(stack)) {
            bonus += trait.repairBonus(amount);
        }
        return bonus;
    }

    /** Called from {@code ToolItem#inventoryTick}, which has already ruled out clients and Broken tools. */
    public static void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        for (Trait trait : of(stack)) {
            trait.inventoryTick(stack, level, holder);
        }
        // Magnetic (issue #297 parity fix): one pull at the combined level's range, not one pull per
        // leveled trait instance -- see MAGNETIC's javadoc. Issue #459 parity fix: only while the
        // 30-tick after-use window MAGNETIC/MAGNETIC2's afterBlockBreak/afterHit opened is still
        // counting down, not on every tick the tool is merely carried. Issue #603 parity fix:
        // upstream's MagneticPotion#isReady only pulls when its duration is even (duration & 1 ==
        // 0); the window's ticksRemaining plays that same role here.
        int magneticLevel = magneticLevel(stack);
        if (magneticLevel > 0) {
            int ticksRemaining = stackTicksRemaining(stack, ForgeweaveDataComponents.MAGNETIC_STACKS.get());
            if (ticksRemaining > 0 && (ticksRemaining & 1) == 0) {
                pullMagneticItems(level, holder, MAGNETIC_BASE_RANGE + magneticLevel * MAGNETIC_RANGE_PER_LEVEL);
            }
            decayStack(stack, ForgeweaveDataComponents.MAGNETIC_STACKS.get());
        }
    }

    /**
     * Materials' traits as a consumer of the shared per-hit pipeline (ADR-0005 decision 3) -- the
     * first {@link CombatSeams} provider, registered in {@code Forgeweave}. Upstream runs its
     * {@code ITrait#damage} hook inside {@code ToolHelper#attackEntity} before armor is applied and
     * feeds each trait the untouched original damage, which is what {@link CombatSeam#preHit}'s
     * {@code originalDamage} is; the pipeline has already ruled out clients, non-Forgeweave weapons
     * and Broken tools by the time this runs.
     *
     * <p>One seam for the whole trait list rather than one per trait, so the traits keep running in
     * the order {@link #of} established and a hit allocates nothing extra.
     *
     * <p>{@link Trait#afterHit} deliberately stays where it is instead of riding
     * {@link CombatSeam#onHit}: {@code ToolItem#postHurtEnemy} calls it right <em>before</em> reading
     * {@link #attackDurabilityBonus} (issue #297 parity fix, matching upstream
     * {@code ToolHelper#attackEntity}'s afterHit-then-reduceDurabilityOnHit order) -- insatiable's
     * durability cost is the stack size <em>after</em> the hit that just grew it, so a hit crossing a
     * stack multiple of 3 pays its own extra cost. The damage event driving on-hit fires earlier than
     * {@code postHurtEnemy}, so moving the call would quietly change what a first hit costs. Combat
     * innates and modifiers carry no such ordering tie and attach to the seam.
     */
    public static final CombatSeam COMBAT_SEAM = new CombatSeam() {
        @Override
        public float preHit(CombatHit hit, float originalDamage, float damage) {
            // #228 squeaky: upstream TraitSqueaky#damage returns 0f unconditionally, trumping every
            // other bonus -- so the check runs before, not after, the additive traits. Melee only
            // (M3.5 #396): an arrow's damage is not the bow's attack stat, and in 1.12 a launcher's
            // traits never see the arrow at all, so a sponge-limbed bow still shoots arrows that hurt.
            if (!hit.isProjectile() && zeroesAttackDamage(hit.weapon())) {
                return 0.0F;
            }
            return damage + bonusDamageAgainst(hit.weapon(), hit.target(), originalDamage);
        }

        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            // #230: traits that need the full hit record (shocking's attack-strength-scaled charge)
            // ride the seam's own on-hit moment; see Trait#onCombatHit for the split from afterHit.
            for (Trait trait : of(hit.weapon())) {
                trait.onCombatHit(hit, damageDealt);
            }
        }

        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            // #680: a worn piece's traits, in the order the piece stored them (SCOPE.md D8).
            for (Trait trait : of(defense.tool())) {
                trait.onDefend(defense, blow);
            }
        }
    };

    /**
     * The traits' provider for {@link CombatSeams} (registered in {@code Forgeweave}, in the slot
     * {@link #COMBAT_SEAM} alone used to fill): the aggregate seam first -- keeping #230's
     * onCombatHit routing and #228's squeaky zeroing exactly where they were -- then each trait's
     * own seams ({@link Trait#combatSeams}) in the order {@link #of} established. Issue #229's
     * combat traits are whole {@link CombatSeam}s (on-hit effects, defensive hooks) rather than
     * another fold into the aggregate, so they ride the pipeline directly.
     */
    public static void collectCombatSeams(ItemStack weapon, Consumer<CombatSeam> out) {
        out.accept(COMBAT_SEAM);
        for (Trait trait : of(weapon)) {
            trait.combatSeams(out);
        }
    }

    /** Knockback resistance the tool's traits grant while it is held (heavy, issue #229). */
    public static float knockbackResistance(ItemStack stack) {
        float resistance = 0.0F;
        for (Trait trait : of(stack)) {
            resistance += trait.knockbackResistance();
        }
        return resistance;
    }

    /** Extra damage this weapon's traits deal to {@code target}, on top of its own attack damage. */
    public static float bonusDamageAgainst(ItemStack weapon, LivingEntity target, float damage) {
        float bonus = 0.0F;
        for (Trait trait : of(weapon)) {
            bonus += trait.bonusDamageAgainst(weapon, target, damage);
        }
        return bonus;
    }

    /** This block's destroy speed after every trait on {@code stack} has adjusted it in order. */
    public static float miningSpeed(ItemStack stack, boolean effective, float speed) {
        float result = speed;
        for (Trait trait : of(stack)) {
            result = trait.miningSpeed(stack, effective, speed, result);
        }
        return result;
    }

    /** Called from {@code ToolItem#mineBlock} once a block is actually destroyed, server side only. */
    public static void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
            LivingEntity breaker, boolean effective) {
        for (Trait trait : of(stack)) {
            trait.afterBlockBreak(stack, level, state, pos, breaker, effective);
        }
    }

    /**
     * The first non-{@link InteractionResult#PASS} answer any trait on {@code stack} gives to a
     * right-click on a block ({@link Trait#useOnBlock}, issue #829's {@code fertilize_on_use}),
     * called from {@code ToolItem#useOn}'s fallthrough. Unlike {@link #afterBlockBreak}, only one
     * trait's interaction can consume the click, so the first hit wins rather than every trait
     * running.
     */
    public static InteractionResult useOnBlock(ItemStack stack, UseOnContext context) {
        for (Trait trait : of(stack)) {
            InteractionResult result = trait.useOnBlock(stack, context);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }

    /** Fraction the tool's traits add to a launcher's draw speed (M3.5 #396, {@link Trait#drawSpeedBonus}). */
    public static float drawSpeedBonus(ItemStack stack) {
        float bonus = 0.0F;
        for (Trait trait : of(stack)) {
            bonus += trait.drawSpeedBonus();
        }
        return bonus;
    }

    /** Flat attack speed the tool's traits add, as a fraction of its base attack speed. */
    public static float attackSpeedBonus(ItemStack stack) {
        float bonus = 0.0F;
        for (Trait trait : of(stack)) {
            bonus += trait.attackSpeedBonus();
        }
        return bonus;
    }

    /** Called from {@code ToolItem#postHurtEnemy} after this tool lands a hit, server side only. */
    public static void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
        for (Trait trait : of(stack)) {
            trait.afterHit(stack, level, attacker, target);
        }
    }

    /** Extra durability this hit costs the tool, on top of {@code ToolItem#attackDurabilityCost}. */
    public static int attackDurabilityBonus(ItemStack stack) {
        int bonus = 0;
        for (Trait trait : of(stack)) {
            bonus += trait.attackDurabilityBonus(stack);
        }
        return bonus;
    }

    // -- #228 M3.2 mining/durability-economy aggregators, one per new Trait hook.

    /**
     * This durability loss after every trait on {@code stack} has adjusted it in order
     * ({@link Trait#durabilityDamage}), floored at zero so a cost-negating roll (duritos) can never
     * turn a loss into a heal. Called from {@code ToolItem#damageItem}, the single durability choke
     * point (its class javadoc).
     */
    public static int durabilityDamage(ItemStack stack, RandomSource random, int amount) {
        int result = amount;
        for (Trait trait : of(stack)) {
            result = trait.durabilityDamage(stack, random, amount, result);
        }
        return Math.max(0, result);
    }

    /**
     * {@link Trait#breakSpeed}'s driver: the break-speed traits that need the player or the block
     * (aquadynamic, aridiculous, crumbling, unnatural), chained over NeoForge's
     * {@code PlayerEvent.BreakSpeed} -- the same event upstream 1.12 handles per trait, and the only
     * seam that sees the breaking player ({@code Item#getDestroySpeed}, where
     * {@link Trait#miningSpeed} runs, does not). Registered on the game event bus in
     * {@code Forgeweave}, same idiom as {@link #onExperienceDrop}.
     */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ToolItem) || ToolItem.isBroken(stack)) {
            return;
        }
        float original = event.getOriginalSpeed();
        float speed = event.getNewSpeed();
        for (Trait trait : of(stack)) {
            speed = trait.breakSpeed(stack, player, event.getState(), original, speed);
        }
        if (speed != event.getNewSpeed()) {
            event.setNewSpeed(speed);
        }
    }

    /** Whether any trait on {@code stack} grants vanilla Silk Touch at assembly (squeaky). */
    public static boolean grantsSilkTouch(ItemStack stack) {
        for (Trait trait : of(stack)) {
            if (trait.grantsSilkTouch()) {
                return true;
            }
        }
        return false;
    }

    /** Whether any trait on {@code stack} forces its attack damage to zero (squeaky). */
    public static boolean zeroesAttackDamage(ItemStack stack) {
        for (Trait trait : of(stack)) {
            if (trait.zeroesAttackDamage()) {
                return true;
            }
        }
        return false;
    }

    /** Whether any trait on {@code stack} smelts what it mines (autosmelt; Searing's modifier twin). */
    public static boolean autoSmelts(ItemStack stack) {
        for (Trait trait : of(stack)) {
            if (trait.autoSmelt()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code forgeweave:established}'s kill-XP bonus (see its javadoc). Registered on the game event
     * bus in {@code Forgeweave}, same pattern as {@link #onIncomingDamage}.
     */
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof ToolItem) || ToolItem.isBroken(weapon)) {
            return;
        }
        int xp = event.getDroppedExperience();
        int updated = xp;
        for (Trait trait : of(weapon)) {
            updated = trait.killExperience(player.getRandom(), updated);
        }
        if (updated != xp) {
            event.setDroppedExperience(updated);
        }
    }

    /**
     * {@code forgeweave:established}'s block-break XP bonus (issue #494/T63; see its javadoc).
     * Registered on the game event bus in {@code Forgeweave}, same idiom as {@link #onExperienceDrop};
     * rides {@link BlockDropsEvent}, the same seam {@code modifier.ForgeweaveModifiers#onBlockDrops}
     * uses for its own tool-triggered XP/drop adjustments (issue #108).
     */
    public static void onBlockBreakExperience(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) {
            return;
        }
        ItemStack tool = event.getTool();
        if (!(tool.getItem() instanceof ToolItem) || ToolItem.isBroken(tool)) {
            return;
        }
        int xp = event.getDroppedExperience();
        int updated = xp;
        float destroyChance = 0.0F;
        for (Trait trait : of(tool)) {
            updated = trait.blockBreakExperience(player.getRandom(), updated);
            destroyChance = Math.max(destroyChance, trait.dropDestroyChance());
        }
        if (updated != xp) {
            event.setDroppedExperience(updated);
        }
        if (destroyChance > 0.0F) {
            // #876 M6 dedupe batch, obliterate (#841 gap 7): a mined block's own drops sometimes
            // vanish outright, same drop-list mutation ForgeweaveModifiers#onBlockDrops uses.
            float chance = destroyChance;
            event.getDrops().removeIf(drop -> player.getRandom().nextFloat() < chance);
        }
    }

    /**
     * {@code forgeweave:enderference}'s teleport block (see {@link #ENDERFERENCE}): an
     * enderman/shulker teleport by a marked entity is cancelled outright, upstream
     * {@code TraitEnderference#onEnderTeleport}. Registered on the game event bus in
     * {@code Forgeweave}, same idiom as {@link #onExperienceDrop}; the trait itself
     * stays on the combat seams -- this is the mark being read, not combat behavior attaching to a
     * new event.
     */
    public static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (event.getEntityLiving().hasEffect(ForgeweaveMobEffects.ENDERFERENCE)) {
            event.setCanceled(true);
        }
    }

    /**
     * The chorus-fruit half of {@link #onEnderTeleport}: 1.12's one {@code EnderTeleportEvent}
     * covered both paths, NeoForge splits them. (Ender-pearl throws are a player-only teleport no
     * trait can mark in practice and are left alone.)
     */
    public static void onChorusFruitTeleport(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntityLiving().hasEffect(ForgeweaveMobEffects.ENDERFERENCE)) {
            event.setCanceled(true);
        }
    }

    /**
     * {@code forgeweave:grievous}'s mark (issue #828, M6 on-hit effect library, {@code
     * reduce_target_healing}): while {@link ForgeweaveMobEffects#REDUCED_HEALING} is live, shave the
     * mark's own amplifier -- read back as a 0-100 percent, {@link ReduceTargetHealing}'s javadoc --
     * off every heal the marked entity would otherwise receive. Same "the seam leaves a mark, a
     * listener reads it" idiom as {@link #onEnderTeleport}.
     */
    public static void onLivingHeal(LivingHealEvent event) {
        MobEffectInstance mark = event.getEntity().getEffect(ForgeweaveMobEffects.REDUCED_HEALING);
        if (mark == null) {
            return;
        }
        float reduced = event.getAmount() * (1.0F - mark.getAmplifier() / 100.0F);
        if (reduced != event.getAmount()) {
            event.setAmount(Math.max(0.0F, reduced));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ForgeweaveTraits() {}
}
