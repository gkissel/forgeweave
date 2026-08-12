package dev.gkissel.forgeweave.trait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
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
     * {@code target.getTotalArmorValue() == 0}. Flint grants level 1 only -- upstream's head-scoped
     * {@code crude2} has no Forgeweave id yet, which issue #94's schema now leaves room for; see the
     * PR/NOTICE note.
     */
    public static final Trait CRUDE = new Trait() {
        @Override
        public float bonusDamageAgainst(ItemStack stack, LivingEntity target, float damage) {
            return target.getArmorValue() > 0 ? 0.0F : damage * 0.05F;
        }
    };

    /**
     * Bone. Upstream registers it as {@code new TraitBonusDamage("fractured", 1.5f)}, whose
     * {@code applyEffect} does {@code data.attack += damage} once at build time -- a flat +1.5 on the
     * tool's own attack stat, not a conditional hit-time bonus, so it belongs on the attribute
     * modifier and shows up in the tool's stats like upstream's does.
     */
    public static final Trait FRACTURED = new Trait() {
        @Override
        public float attackDamageBonus() {
            return 1.5F;
        }
    };

    // -- M2 metal traits (issue #102). Material -> trait wiring is issue #103, not here.
    private static final float MAGNETIC_STRENGTH = 0.035F;
    private static final int MAGNETIC_MAX_PULLED = 200;

    /**
     * Iron. Upstream {@code TraitMagnetic}'s {@code MagneticPotion#performEffect}: pulls every item
     * drop within {@code 1.8 + level * 0.3} blocks toward the holder at a constant 0.07 blocks/tick,
     * at most 200 items, applied every other tick ({@code isReady}: {@code duration & 1 == 0}).
     *
     * <p>Upstream reaches this through a hidden potion effect re-applied from {@code afterBlockBreak}
     * and {@code onHit} every 30 ticks; Forgeweave has no potion-effect plumbing to port that
     * through, so this runs the same pull directly from {@link Trait#inventoryTick} every tick the
     * tool is carried, at half strength (0.035) rather than gating on tick parity -- the same average
     * pull rate without depending on when the tool entered the world. Recorded in the PR.
     *
     * <p>Iron grants both this (general) and {@link #MAGNETIC2} (head only, upstream's separately
     * identified {@code magnetic2}); on an all-iron tool both ids apply at once and their pulls add,
     * whereas upstream's single potion effect would keep only the higher amplifier. Also recorded in
     * the PR.
     */
    public static final Trait MAGNETIC = magnetic(1);

    /** Iron, head part only. Upstream's {@code magnetic2}: the same trait at level 2. */
    public static final Trait MAGNETIC2 = magnetic(2);

    private static Trait magnetic(int level) {
        double range = 1.8 + level * 0.3;
        return new Trait() {
            @Override
            public void inventoryTick(ItemStack stack, ServerLevel serverLevel, LivingEntity holder) {
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
        };
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
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, LivingEntity breaker) {
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
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, LivingEntity breaker) {
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
            Map.entry(id("reinforced_core"), REINFORCED_CORE));

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
            bonus += trait.attackDamageBonus();
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
     * {@link CombatSeam#onHit}: {@code ToolItem#postHurtEnemy} calls it right after reading
     * {@link #attackDurabilityBonus}, and insatiable (issue #102) depends on that order -- the
     * durability cost of a hit is the stack size <em>before</em> the hit grew it. The damage event
     * driving on-hit fires earlier than {@code postHurtEnemy}, so moving the call would quietly
     * change what a first hit costs. Combat innates and modifiers carry no such ordering tie and
     * attach to the seam.
     */
    public static final CombatSeam COMBAT_SEAM = new CombatSeam() {
        @Override
        public float preHit(CombatHit hit, float originalDamage, float damage) {
            return damage + bonusDamageAgainst(hit.weapon(), hit.target(), originalDamage);
        }
    };

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
    public static void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, LivingEntity breaker) {
        for (Trait trait : of(stack)) {
            trait.afterBlockBreak(stack, level, state, breaker);
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ForgeweaveTraits() {}
}
