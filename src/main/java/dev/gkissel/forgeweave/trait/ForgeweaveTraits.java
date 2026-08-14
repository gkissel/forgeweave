package dev.gkissel.forgeweave.trait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.AbsorbFireWhileBlocking;
import dev.gkissel.forgeweave.combat.BleedEffect;
import dev.gkissel.forgeweave.combat.BlockingDamageReduction;
import dev.gkissel.forgeweave.combat.BonusDamageFraction;
import dev.gkissel.forgeweave.combat.BonusDamageVsSeam;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.ConditionalSeam;
import dev.gkissel.forgeweave.combat.FlatBonusDamage;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.combat.ForgeweaveMobEffects;
import dev.gkissel.forgeweave.combat.GaussianArmorPiercingHit;
import dev.gkissel.forgeweave.combat.HitCondition;
import dev.gkissel.forgeweave.combat.IgniteAttackerSeam;
import dev.gkissel.forgeweave.combat.Lacerate;
import dev.gkissel.forgeweave.combat.PotionEffectOnHitSeam;
import dev.gkissel.forgeweave.combat.StackingHitBonus;
import dev.gkissel.forgeweave.combat.ThornsReflectSeam;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
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

    /**
     * Wood. Upstream {@code TraitEcological}: server side, a 1-in-{@code 20 * 40} chance per tick to
     * regenerate one durability, skipped while the holder is actively using the tool. Upstream
     * routes this through {@code ToolHelper#healTool} -&gt; {@code #damageTool}, which returns early on
     * a broken tool; the {@code ToolItem} seam applies that same guard to every trait.
     *
     * <p>Healing an already-undamaged tool needs no guard of its own: 1.21's
     * {@code ItemStack#setDamageValue} clamps to {@code [0, maxDamage]}, exactly as 1.12's
     * {@code Item#setDamage} clamped at 0 before upstream's own heal path could go negative.
     */
    public static final Trait ECOLOGICAL = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder.getUseItem() == stack) {
                return;
            }
            if (level.getRandom().nextInt(20 * ECOLOGICAL_PERIOD_SECONDS) == 0) {
                stack.setDamageValue(stack.getDamageValue() - 1);
            }
        }
    };

    /** Upstream {@code TraitEcological#chance}: one durability roughly every 40 seconds. */
    private static final int ECOLOGICAL_PERIOD_SECONDS = 40;

    /**
     * Stone. Two upstream traits in one id, because stone grants both and M1's material schema gave a
     * material exactly one. Issue #94 lifted that limit; splitting this back into upstream's separate
     * {@code cheap} + head-scoped {@code cheapskate} ids is a trait change (new id, new lang keys),
     * not a schema one, so it waits for the milestone that revisits stone's traits:
     *
     * <ul>
     *   <li>{@code TraitCheap#onToolHeal}: {@code newAmount + amount * 5 / 100}, i.e. 5% more
     *       durability per repair, integer-truncated exactly as upstream truncates it.
     *   <li>{@code TraitCheapskate#onToolBuilding} (upstream assigns it to the head part only:
     *       {@code stone.addTrait(cheapskate, HEAD)}): {@code max(1, durability * 80 / 100)} on the
     *       assembled tool, i.e. a 20% durability penalty. Head-only upstream, head-only here --
     *       hence {@link Trait#headDurability} rather than a hook every part could trigger.
     * </ul>
     */
    public static final Trait CHEAP = new Trait() {
        @Override
        public int repairBonus(int amount) {
            return amount * 5 / 100;
        }

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

    private static Trait crude(int level) {
        return new Trait() {
            @Override
            public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
                return target.getArmorValue() > 0 ? 0.0F : damage * 0.05F * level;
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
    private static final float MAGNETIC_STRENGTH = 0.035F;
    private static final int MAGNETIC_MAX_PULLED = 200;

    /** Upstream {@code MagneticPotion#performEffect}: {@code range = 1.8 + amplifier * 0.3}. */
    private static final double MAGNETIC_BASE_RANGE = 1.8;
    private static final double MAGNETIC_RANGE_PER_LEVEL = 0.3;

    /**
     * Iron. Upstream {@code TraitMagnetic}'s {@code MagneticPotion#performEffect}: pulls every item
     * drop within {@code 1.8 + level * 0.3} blocks toward the holder at a constant 0.07 blocks/tick,
     * at most 200 items, applied every other tick ({@code isReady}: {@code duration & 1 == 0}).
     *
     * <p>Upstream reaches this through a hidden potion effect re-applied from {@code afterBlockBreak}
     * and {@code onHit} every 30 ticks; Forgeweave has no potion-effect plumbing to port that
     * through, so this runs the same pull directly from {@link ForgeweaveTraits#inventoryTick} every
     * tick the tool is carried, at half strength (0.035) rather than gating on tick parity -- the same
     * average pull rate without depending on when the tool entered the world. Recorded in the PR.
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
        };
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
                item.setDeltaMovement(item.getDeltaMovement().add(delta.normalize().scale(MAGNETIC_STRENGTH)));
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
            // stack.getDamageValue() is the durability already lost, i.e. upstream's
            // (maxDurability - currentDurability): getMaxDamage() - getDamageValue() would be
            // durability remaining, the opposite of what the formula wants.
            int missing = stack.getDamageValue();
            return (float) (speed + Math.log(missing / 72.0 + 1.0) * 2.0);
        }
    };

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

    /**
     * Copper. Upstream {@code TraitEstablished}'s kill-XP bonus ({@code onXpDrop}/{@code getUpdateXP}):
     * 0 XP has a 3% chance of becoming 1, otherwise {@code round(xp * 1.25 + random * 0.25) + 1}.
     *
     * <p>Upstream also grants bonus XP on ordinary block breaking ({@code onBlockBreak}, a flat
     * 30%/3% chance of +1 via {@code BlockEvent.BreakEvent#getExpToDrop}/{@code #setExpToDrop}); this
     * NeoForge version's {@code BlockEvent.BreakEvent} carries no XP field any more -- block XP drops
     * are resolved through loot tables with no per-tool interception point -- so only the kill-XP half
     * is ported. Recorded in the PR.
     */
    public static final Trait ESTABLISHED = new Trait() {
        @Override
        public int killExperience(RandomSource random, int xp) {
            if (xp == 0) {
                return random.nextFloat() < 0.03F ? 1 : 0;
            }
            return 1 + Math.round(xp * 1.25F + random.nextFloat() * 0.25F);
        }
    };

    // -- #103 metal materials: rose gold's quick, netherite's fireproof + reinforced_core. Maintainer
    // decision recorded on issue #103 (2026-08-10): rose gold gets quick; netherite gets both traits
    // plus a netherite-ingot application recipe for the existing extra_slot modifier (see the
    // modifier_recipe/extra_slot_netherite.json shipped alongside this class).

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
     * Netherite. Vanilla netherite items survive fire and lava ({@code Item.Properties#fireResistant},
     * checked by {@code ItemEntity#fireImmune}), but that flag is per-{@code Item}, not per-stack --
     * every Forgeweave pickaxe is one shared {@code Item} instance regardless of material, so it can't
     * be set on {@code ToolItem} itself without making every pickaxe fire-immune. {@link
     * #onEntityInvulnerabilityCheck} is the per-stack equivalent: it grants the same immunity only to a
     * dropped {@code ItemEntity} carrying a tool with this trait. No upstream trait to port -- netherite
     * has no 1.12 counterpart; the mechanism is a maintainer decision recorded on issue #103.
     */
    public static final Trait FIREPROOF = new Trait() {};

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
     * Electrum. Upstream {@code TraitShocking}: a 0-100 charge built three ways -- {@code +15 *
     * attackStrength} per landed hit ({@code onHit}), {@code +15} per block broken
     * ({@code afterBlockBreak}), and {@code +2} per block moved while the tool is held, sampled every
     * 5 ticks with each sample's distance capped at 5 ({@code onUpdate}). A hit swung while fully
     * charged discharges it: 5 bonus lightning-type damage dealt as a secondary blow past the
     * target's invulnerability window (upstream's {@code attackEntitySecondary} with an
     * {@code EntityDamageSource("lightningBolt", ...)}) plus Speed VI for 2.5s on the attacker; a
     * block break that fills the charge discharges immediately into Haste III for 2.5s instead. The
     * tool shows an enchantment glint while fully charged (upstream's {@code setEnchantEffect}).
     *
     * <p>Deviations, recorded in the PR: the hit half rides {@link Trait#onCombatHit} (ADR-0005's
     * seam) with the {@link CombatHit}'s captured attack-strength scale; movement is sampled on the
     * holder's own {@code tickCount} rather than world time (world time is constant across one
     * test-staged tick, holder ticks aren't); charge is clamped at 100 on write so the serialized
     * range is honest ({@link ShockingCharge}); upstream's custom charge/discharge sounds and
     * heart-electro particles have no Forgeweave asset and are dropped.
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
                target.invulnerableTime = 0;
                target.hurt(hit.level().damageSources().lightningBolt(), SHOCKING_DISCHARGE_DAMAGE);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 5));
                setShockingCharge(stack, charge.discharged());
            } else if (attacker instanceof Player) {
                // Upstream TraitShocking#onHit's else-if EntityPlayer gate (issue #297 parity fix): a
                // non-player attacker (a mob wielding the tool) never builds charge from a hit.
                setShockingCharge(stack, charge.plus(SHOCKING_CHARGE_PER_HIT * hit.attackStrengthScale()));
            }
        }

        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            ShockingCharge charged = shockingCharge(stack, breaker).plus(SHOCKING_CHARGE_PER_BREAK);
            if (charged.isFull()) {
                // Upstream discharges a mining-filled charge on the spot, into haste rather than damage.
                breaker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 2));
                setShockingCharge(stack, charged.discharged());
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
            setShockingCharge(stack, new ShockingCharge(next, holder.getX(), holder.getY(), holder.getZ()));
        }
    };

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
     * the blue variant its own {@code EntityBlueSlime}; Forgeweave ships no blue slime entity
     * (docs/SCOPE.md M3.2 non-goals defer it to the world-content milestone), so <b>both ids spawn
     * the vanilla slime</b> for now -- flagged for maintainer review in the PR, and kept as two ids
     * so the blue entity can slot in without touching material JSON.
     */
    public static final Trait SLIMEY_GREEN = slimey();

    /** See {@link #SLIMEY_GREEN}: upstream's blue slime, vanilla slime until the entity exists. */
    public static final Trait SLIMEY_BLUE = slimey();

    private static Trait slimey() {
        return new Trait() {
            @Override
            public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                    LivingEntity breaker, boolean effective) {
                if (effective && rollsSlimeyProc(level.getRandom())) {
                    spawnTraitSlime(level, breaker, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            }

            @Override
            public void afterHit(ItemStack stack, ServerLevel level, LivingEntity attacker, LivingEntity target) {
                if (target.isDeadOrDying() && rollsSlimeyProc(level.getRandom())) {
                    spawnTraitSlime(level, attacker, target.getX(), target.getY(), target.getZ());
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
     * Slimey's spawn path (upstream {@code TraitSlimey#spawnSlime}): a size-1 slime at the given
     * spot, remembering {@code owner} as its attacker. Public so a GameTest can drive it
     * deterministically, past the roll above.
     */
    public static void spawnTraitSlime(ServerLevel level, LivingEntity owner, double x, double y, double z) {
        Slime slime = EntityType.SLIME.create(level);
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
            return (float) (Math.log(stack.getDamageValue() / 72.0 + 1.0) * 2.0);
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
     */
    public static final Trait WRITABLE = new Trait() {
        @Override
        public int bonusSlots() {
            return 1;
        }
    };

    /**
     * Paper's head-scoped half: upstream {@code TinkerTraits.writable2 = new TraitWritable(2)},
     * a +2 of its own next to {@link #WRITABLE}'s +1 -- see that javadoc for the whole pair.
     */
    public static final Trait WRITABLE2 = new Trait() {
        @Override
        public int bonusSlots() {
            return 2;
        }
    };

    /**
     * Sponge. Upstream {@code TraitSqueaky}: always-on Silk Touch ({@code applyEffect}'s
     * {@code ToolBuilder#addEnchantment}, here the assembly-time grant behind
     * {@link Trait#grantsSilkTouch}) and a hard-zero hit ({@code damage} returns {@code 0f}
     * unconditionally, here {@link Trait#zeroesAttackDamage}). Upstream's squeak-toy sound on hit has
     * no Forgeweave sound asset and its {@code canApplyTogether} luck/fortune guards have no modifier
     * compat surface to land on yet -- both recorded in the PR.
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
    };

    /**
     * Firewood. Upstream {@code TraitAutosmelt#blockHarvestDrops}: mined blocks drop their
     * furnace-smelted result. The smelting itself is the M2 Searing modifier's
     * ({@code ForgeweaveModifiers#onBlockDrops} -&gt; {@code smelt}), shared rather than duplicated
     * (issue #228); this trait only opts the tool in through {@link Trait#autoSmelt}. Upstream's
     * fortune-multiplies-smelted-drops config rider and its silk-touch {@code canApplyTogether} guard
     * follow Searing's (absent) behavior -- recorded in the PR.
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

    /** Lead. Upstream {@code TraitPoisonous}: Poison I for ~5 seconds on every landed hit. */
    public static final Trait POISONOUS = seamTrait(new PotionEffectOnHitSeam(MobEffects.POISON, 0, POISONOUS_TICKS));

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

    private static int stackLevel(ItemStack stack, DataComponentType<TraitStacks> component) {
        TraitStacks stacks = stack.get(component);
        return stacks == null ? 0 : stacks.level();
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

    private static final Map<ResourceLocation, Trait> REGISTRY = Map.ofEntries(
            Map.entry(id("ecological"), ECOLOGICAL),
            Map.entry(id("cheap"), CHEAP),
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
            // #103 metal materials: rose gold's quick, netherite's fireproof + reinforced_core.
            Map.entry(id("quick"), QUICK),
            Map.entry(id("fireproof"), FIREPROOF),
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
            Map.entry(id("lacerating"), LACERATING));


    /**
     * The assembled tool's durability after the <b>head</b> material's head-scoped traits adjusted it
     * ({@link Trait#headDurability}), applied in order. Called from {@code ToolStats#compute} with
     * {@code head.traits().forPart(HEAD)}; an id no version of Forgeweave implements simply leaves the
     * durability alone, same as every other hook.
     */
    public static int headDurability(List<ResourceLocation> traitIds, int durability) {
        for (ResourceLocation id : traitIds) {
            Trait trait = REGISTRY.get(id);
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
    public static List<Trait> of(ItemStack stack) {
        List<ResourceLocation> ids = stack.get(ForgeweaveDataComponents.TRAITS.get());
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Trait> traits = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Trait trait = REGISTRY.get(id);
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
        // leveled trait instance -- see MAGNETIC's javadoc.
        int magneticLevel = magneticLevel(stack);
        if (magneticLevel > 0) {
            pullMagneticItems(level, holder, MAGNETIC_BASE_RANGE + magneticLevel * MAGNETIC_RANGE_PER_LEVEL);
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
            // other bonus -- so the check runs before, not after, the additive traits.
            if (zeroesAttackDamage(hit.weapon())) {
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
     * {@code forgeweave:fireproof} (see {@link #FIREPROOF}'s javadoc): fired whenever any entity's
     * invulnerability to a damage source is checked, on both sides -- the one seam generic enough to
     * cover an {@link ItemEntity} without a per-{@code Item} flag. Registered on the game event bus in
     * {@code Forgeweave}, same idiom as {@link #onIncomingDamage}.
     */
    public static void onEntityInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (event.isInvulnerable() || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.getItem() instanceof ToolItem && of(stack).contains(FIREPROOF)) {
            event.setInvulnerable(true);
        }
    }

    /**
     * {@code forgeweave:enderference}'s teleport block (see {@link #ENDERFERENCE}): an
     * enderman/shulker teleport by a marked entity is cancelled outright, upstream
     * {@code TraitEnderference#onEnderTeleport}. Registered on the game event bus in
     * {@code Forgeweave}, same idiom as {@link #onEntityInvulnerabilityCheck}; the trait itself
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ForgeweaveTraits() {}
}
