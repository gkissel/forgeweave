package dev.gkissel.forgeweave.combat;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.common.Tags;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * Every tool's own built-in combat behavior (docs/SCOPE.md M3, maintainer directive 2026-08-12:
 * every tool carries a combat innate), and the one place they are declared. Issue #164 landed M1's
 * retrofit (pickaxe pierce, shovel flatten, hatchet sunder); issue #155 added the six Tool Station
 * weapons' and the {@link Innate} record that carries them.
 *
 * <p>Two ways in, because the two batches bind differently and both are legitimate:
 * Every tool's combat innate (maintainer directive 2026-08-12: every tool carries one) -- the M1
 * retrofit's pickaxe pierce, shovel flatten and hatchet sunder (docs/SCOPE.md M3 issue #164), plus
 * the five large harvest tools' riders (issue #157). Attached the same way materials' traits are
 * ({@code ForgeweaveTraits#COMBAT_SEAM}): one {@link CombatSeams.Provider}, registered once in
 * {@code Forgeweave}, keyed on which tool {@code Item} the stack actually is rather than on any data
 * the stack carries -- these are fixed per-tool-type behavior, not per-material like traits.
 *
 * <p>Each innate is a small parameterized behavior class, or a composition of two through
 * {@link ConditionalSeam} (ADR-0004's M6 library candidates: {@link FlatArmorPiercingDamage},
 * {@link PotionEffectOnHit}, {@link BonusDamageVsBlocking}, {@link BonusDamageFraction},
 * {@link KnockbackOnHitSeam}, {@link SweepAttackSeam}), so a future datapack-driven version of this
 * table needs only new JSON, no Java change to any of them.
 *
 * <ul>
 *   <li>M1's three are keyed on which {@code Item} the stack is ({@link #collect}), because those
 *       tools' constructors predate the record and their behavior is fixed per tool type;
 *   <li>every M3 tool <em>carries</em> its {@link Innate} on the {@code ToolItem} itself, handed in
 *       at registration, so a new tool needs no branch here at all.
 * </ul>
 *
 * <p>An innate is a name plus at most two behaviors:
 *
 * <ul>
 *   <li>a {@link CombatSeam} for the ones that fire on a blow -- dealt ({@link CombatSeam#preHit}/
 *       {@link CombatSeam#onHit}) or taken ({@link CombatSeam#incomingHit});
 *   <li>a {@link ToolUseAction} for the ones whose trigger is the right-click button.
 * </ul>
 *
 * <p>{@code Forgeweave} registers one {@link CombatSeams.Provider} for the whole class, and
 * {@code ToolItem} forwards vanilla's four item-use methods to the use action. No innate subscribes
 * to an event or subclasses a tool -- ADR-0005 decision 3. (Sunder's shield-disable half is not a
 * seam at all -- see {@code ToolItem#canDisableShield}'s javadoc.)
 *
 * <p>Every behavior below takes its magnitudes as constructor arguments and reads everything else
 * off the {@link CombatHit}/{@link CombatDefense} it is handed, which is ADR-0004's M6 shape: at M6
 * these become datapack-declared parameter sets rather than Java.
 */
public final class ForgeweaveInnates {

    /**
     * One tool's innate.
     *
     * @param id the innate's name, used for its {@code tooltip.forgeweave.innate.<id>} lang keys
     * @param seam its per-blow behavior, or {@code null} if the innate is use-triggered only
     * @param use its right-click behavior, or {@code null} if the innate is blow-triggered only
     */
    public record Innate(String id, @Nullable CombatSeam seam, @Nullable ToolUseAction use) {}

    // ------------------------------------------------------------------ magnitudes

    /** Maintainer decision 2026-08-12 on issue #155: a 0.5s window, slowness I for 1s on the attacker. */
    private static final int PARRY_WINDOW_TICKS = 10;
    private static final int PARRY_SLOW_TICKS = 20;
    private static final int PARRY_SLOW_AMPLIFIER = 0; // amplifier 0 == level I

    /**
     * Upstream {@code tools/melee/item/LongSword.java#onPlayerStoppedUsing} verbatim: 200-tick charge,
     * nothing below 5 ticks held, rise {@code min(0.02t + 0.2, 0.56)}, horizontal {@code min(0.05t,
     * 0.925)}, 0.2 exhaustion and a 3-tick cooldown.
     */
    private static final int LEAP_CHARGE_TICKS = 200;
    private static final int LEAP_MIN_TICKS = 5;
    private static final float LEAP_RISE_PER_TICK = 0.02F;
    private static final float LEAP_RISE_BASE = 0.2F;
    private static final float LEAP_RISE_CAP = 0.56F;
    private static final float LEAP_SPEED_PER_TICK = 0.05F;
    private static final float LEAP_SPEED_CAP = 0.925F;
    private static final float LEAP_EXHAUSTION = 0.2F;
    private static final int LEAP_COOLDOWN_TICKS = 3;

    /** Maintainer decision 2026-08-12 on issue #155: 5% of the target's current health, past armor. */
    private static final float VITAL_THRUST_FRACTION = 0.05F;

    /**
     * Upstream {@code tools/melee/item/Rapier.java#onItemRightClick} verbatim: 0.1 exhaustion, a
     * {@code motionY += 0.32} hop, a flat 0.5 horizontal dash and a 4-tick cooldown.
     */
    private static final float LUNGE_RISE = 0.32F;
    private static final float LUNGE_SPEED = 0.5F;
    private static final float LUNGE_EXHAUSTION = 0.1F;
    private static final int LUNGE_COOLDOWN_TICKS = 4;

    /**
     * Upstream {@code BattleSign#reflectProjectiles}: only counts while looking at the projectile
     * ({@code -look · motion > 0.1}), and returns it at its own speed plus a little.
     */
    private static final int DEFLECT_HOLD_TICKS = 72000;
    private static final double DEFLECT_MIN_FACING = 0.1;
    private static final double DEFLECT_SPEED_BONUS = 0.2;

    /**
     * Upstream {@code BattleSign#reducedDamageBlocked} (issue #302): a non-magic/non-explosion/
     * non-projectile hit taken while blocking gets an extra 30% reduction, and half of the reduced
     * amount reflects onto the attacker as thorns damage.
     */
    private static final float DEFLECT_MELEE_REDUCTION = 0.7F;
    private static final float DEFLECT_MELEE_REFLECT_FRACTION = 0.5F;

    /**
     * Upstream {@code FryPan#knockback()} returns {@code 2f} where {@code ToolCore}'s default is
     * {@code 1f} -- a doubled push.
     *
     * <p>{@value} rather than the {@code 0.4} vanilla itself uses, because ours lands <em>first</em>:
     * {@code LivingEntity#hurt} runs the damage events (and therefore this seam) before its own
     * {@code knockback(0.4, ...)}, and {@code knockback} halves whatever motion it finds before adding
     * its own push. So vanilla turns our {@code 0.8} into {@code 0.8/2 + 0.4 = 0.8} -- exactly twice
     * the {@code 0.4} an ordinary blow leaves.
     */
    private static final float HEAVY_KNOCKBACK = 0.8F;

    /**
     * Upstream {@code tools/melee/item/FryPan.java#onPlayerStoppedUsing} verbatim: the use lasts
     * {@code 5 * 20} ticks but the charge is {@code min(1, held / 30)}, so it is full after a second
     * and a half; the launch is {@code look * (0.1 + 2.5p²)} with {@code look.y/3 * strength + 0.1 +
     * 0.4p} of lift, the bonus attack is {@code 5p}, the reach is 3.2 blocks, and a full charge sets
     * the target alight for one second across the blow.
     */
    private static final int LAUNCH_USE_TICKS = 5 * 20;
    private static final float LAUNCH_FULL_CHARGE_TICKS = 30.0F;
    private static final float LAUNCH_STRENGTH_BASE = 0.1F;
    private static final float LAUNCH_STRENGTH_PER_CHARGE = 2.5F;
    private static final float LAUNCH_LIFT_BASE = 0.1F;
    private static final float LAUNCH_LIFT_PER_CHARGE = 0.4F;
    private static final float LAUNCH_BONUS_DAMAGE = 5.0F;
    private static final double LAUNCH_RANGE = 3.2;
    private static final int LAUNCH_IGNITE_TICKS = 20;

    /**
     * Maintainer decision 2026-08-12 on issue #155: {@value #BACKSTAB_MAX} within
     * {@value #BACKSTAB_FULL_DEGREES}&deg; of dead-behind, falling off linearly to
     * {@value #BACKSTAB_MIN} at the {@value #BACKSTAB_CONE_DEGREES}&deg; edge of the rear cone, and
     * nothing outside it.
     */
    private static final float BACKSTAB_FULL_DEGREES = 45.0F;
    private static final float BACKSTAB_CONE_DEGREES = 90.0F;
    private static final float BACKSTAB_MAX = 1.00F;
    private static final float BACKSTAB_MIN = 0.25F;

    // ------------------------------------------------------------------ the six

    /** The broadsword's sweep, public for the same testability reason every other seam field is. */
    public static final BroadswordSweep BROADSWORD_SWEEP_SEAM = new BroadswordSweep();

    /**
     * Broadsword. Issue #303's re-verify: an earlier version of this class (and docs/SCOPE.md) claimed
     * upstream's 1.12 innate was "sword-blocking, gone from modern Minecraft" -- the clone has no such
     * thing. {@code BroadSword#dealDamage} sweeps a full-charge, grounded, slow-moving hit onto
     * everything within 3 blocks for a flat 1 damage ({@link BroadswordSweep}); it never blocks.
     * The parry window stays -- it is a deliberate maintainer addition (2026-08-12), not a replacement
     * for anything -- and the sweep it was wrongly said to replace is restored alongside it.
     */
    public static final Innate PARRY = parry();

    /** Longsword: upstream's charged leap, ported constant for constant. */
    public static final Innate CHARGED_LEAP = new Innate("charged_leap", null, new ChargedLeap());

    /**
     * Rapier: the maintainer's redesign of upstream's hybrid-damage double hit, plus upstream's own
     * right-click {@link Lunge} (issue #300).
     */
    public static final CurrentHealthStrike VITAL_THRUST_SEAM = new CurrentHealthStrike(VITAL_THRUST_FRACTION);
    public static final Innate VITAL_THRUST = new Innate("vital_thrust", VITAL_THRUST_SEAM, new Lunge());

    /**
     * Battlesign: upstream's blocking stance that returns projectiles to their sender, plus its
     * melee-block half (issue #302) -- extra reduction and thorns retaliation on a blocked blow.
     */
    public static final Innate DEFLECT = deflect();

    /** Frying pan: upstream's doubled knockback, plus its charged launch (issue #301). */
    public static final HeavyKnockback HEAVY_SWING_SEAM = new HeavyKnockback(HEAVY_KNOCKBACK);
    public static final Innate HEAVY_SWING = new Innate("heavy_swing", HEAVY_SWING_SEAM, new ChargedLaunch());

    /** Dagger: no upstream behavior at all -- the shape is from the modern branch, this is ours. */
    public static final Backstab BACKSTAB_SEAM =
            new Backstab(BACKSTAB_FULL_DEGREES, BACKSTAB_CONE_DEGREES, BACKSTAB_MAX, BACKSTAB_MIN);
    public static final Innate BACKSTAB = new Innate("backstab", BACKSTAB_SEAM, null);

    // ------------------------------------------------------------------ the two station tools (#156)

    /**
     * Mattock: a per-hit chance of a strong knockback. Maintainer decision 2026-08-12 -- utility
     * tools carry a small combat rider too, so this has no 1.12 counterpart. See {@link HeftSeam}.
     */
    public static final Innate HEFT = new Innate("heft", HeftSeam.SEAM, null);

    /** Kama: bonus damage against an already-wounded target. See {@link ReapSeam}. */
    public static final Innate REAP = new Innate("reap", ReapSeam.SEAM, null);

    private static Innate parry() {
        Parry behavior = new Parry(PARRY_WINDOW_TICKS, PARRY_SLOW_AMPLIFIER, PARRY_SLOW_TICKS);
        return new Innate("parry", new BroadswordCombat(behavior, BROADSWORD_SWEEP_SEAM), behavior);
    }

    /**
     * The one seam an {@link Innate} carries, for a tool whose defense ({@link Parry#incomingHit}) and
     * offense ({@link BroadswordSweep#onHit}) are two independently portable behaviors rather than one.
     */
    private record BroadswordCombat(Parry parry, BroadswordSweep sweep) implements CombatSeam {
        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            sweep.onHit(hit, damageDealt);
        }

        @Override
        public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
            return parry.incomingHit(defense, originalDamage, damage);
        }
    }

    private static Innate deflect() {
        Deflect behavior = new Deflect(DEFLECT_HOLD_TICKS, DEFLECT_MIN_FACING, DEFLECT_SPEED_BONUS,
                DEFLECT_MELEE_REDUCTION, DEFLECT_MELEE_REFLECT_FRACTION);
        return new Innate("deflect", behavior, behavior);
    }

    // ------------------------------------------------------------------ M1's retrofit (issue #164)

    /** Maintainer decision, issue #164 (2026-08-12): 1.0 flat armor-ignoring damage. */
    private static final float PIERCE_DAMAGE = 1.0F;
    /** Maintainer decision, issue #164 (2026-08-12): Slowness I for 1.5s (30 ticks). */
    private static final int FLATTEN_SLOWNESS_DURATION_TICKS = 30;
    /** Maintainer decision, issue #164 (2026-08-12): 20% bonus damage vs a blocking target. */
    private static final float SUNDER_BONUS_DAMAGE_FRACTION = 0.2F;

    /** Maintainer decision, issue #157 (2026-08-12): 20% chance of Slowness II for 1s. */
    private static final float CONCUSSION_CHANCE = 0.2F;
    private static final int CONCUSSION_DURATION_TICKS = 20;
    /**
     * Upstream {@code Hammer#dealDamage} verbatim (parity audit T35, issue #466): {@code damage += 3
     * + TConstruct.random.nextInt(4)} against the undead, i.e. a flat +3 plus a {@code [0, 4)} roll --
     * +3..+6 total.
     */
    private static final float HAMMER_UNDEAD_BONUS_BASE = 3.0F;
    private static final int HAMMER_UNDEAD_BONUS_ROLL = 4;
    /**
     * Maintainer decision, issue #157: "+1 flat knockback", i.e. one Knockback-enchantment level.
     * Vanilla's {@code Player#attack} turns each level into {@code knockback(level * 0.5)}, which is
     * the same unit {@link KnockbackOnHitSeam#magnitude} takes.
     */
    private static final float KNOCKBACK_LEVEL = 0.5F;
    /** Maintainer decision, issue #157: +15% damage against a target that has lost no health. */
    private static final float TIMBER_BONUS_DAMAGE_FRACTION = 0.15F;
    /** Upstream's own scythe sweep covers the same 3x3x3 its harvest does, i.e. {@code (3 - 1) / 2}. */
    private static final float SCYTHE_SWEEP_RADIUS = 1.0F;

    /** Pickaxe. */
    public static final CombatSeam PIERCE = new FlatArmorPiercingDamage(PIERCE_DAMAGE);
    /** Shovel. */
    public static final CombatSeam FLATTEN =
            new PotionEffectOnHit(MobEffects.MOVEMENT_SLOWDOWN, 0, FLATTEN_SLOWNESS_DURATION_TICKS);
    /** Hatchet. Bonus damage only -- the shield-disable half lives on {@code ToolItem}. */
    public static final CombatSeam SUNDER = new BonusDamageVsBlocking(SUNDER_BONUS_DAMAGE_FRACTION);

    /**
     * Battleaxe -- sweeping heavy blow (maintainer decision on issue #159, 2026-08-12): a full-charge
     * hit strikes all enemies in a short arc for 50% damage, and the primary target takes slowness I
     * for 1.5 seconds.
     *
     * <p>The two numbers that decision left to implementation are the arc's reach and width. 3 blocks
     * and 120 degrees: reach is one block past a player's own entity-interaction range so a second
     * rank of mobs behind the one struck is caught, and the wedge is wide enough to hit a flanking
     * pair without becoming the ring-shaped "everything around me" the word <em>arc</em> rules out.
     * Both are constructor parameters, so a playtest re-tune is a number here.
     */
    public static final SweepingBlow SWEEPING_BLOW_SEAM =
            new SweepingBlow(0.5F, 3.0, 120.0, MobEffects.MOVEMENT_SLOWDOWN, FLATTEN_SLOWNESS_DURATION_TICKS, 0);
    public static final Innate SWEEPING_BLOW = new Innate("sweeping_blow", SWEEPING_BLOW_SEAM, null);

    /**
     * Scimitar -- lacerate (maintainer decision on issue #159, 2026-08-12): 1 damage per second for 4
     * seconds per application, stacking up to 3 concurrent bleeds. See {@link LacerateEffect} for
     * where those numbers live and how the stack is carried.
     */
    public static final Lacerate LACERATE_SEAM = new Lacerate(
            ForgeweaveMobEffects.LACERATE, LacerateEffect.DURATION_TICKS, LacerateEffect.MAX_STACKS);
    public static final Innate LACERATE = new Innate("lacerate", LACERATE_SEAM, null);

    /**
     * Katana -- the in-combat damage ramp (maintainer decision on issue #160, 2026-08-12): +10%
     * damage per landed melee hit, capped at +75%, lapsing after five seconds without landing one.
     * See {@link DamageRamp} for where the three magnitudes live and how the state is serialized.
     */
    public static final Innate DAMAGE_RAMP = new Innate("damage_ramp", DamageRamp.KATANA, null);

    // ---------------------------------------------------- the five large harvest tools (#157)

    /** Hammer: a chance to leave what it hits reeling. */
    public static final CombatSeam CONCUSSION_SEAM = new ConditionalSeam(HitCondition.ANY, CONCUSSION_CHANCE,
            new PotionEffectOnHit(MobEffects.MOVEMENT_SLOWDOWN, 1, CONCUSSION_DURATION_TICKS));
    /** Hammer: upstream's own flat +3..+6 damage against the undead (parity audit T35, issue #466). */
    public static final CombatSeam HAMMER_UNDEAD_SEAM = new ConditionalSeam(HitCondition.UNDEAD, 1.0F,
            new RandomBonusDamage(HAMMER_UNDEAD_BONUS_BASE, HAMMER_UNDEAD_BONUS_ROLL));
    /** The hammer's one {@link Innate} seam: concussion's stagger chance and the undead bonus, both. */
    public static final CombatSeam HAMMER_SEAM = new HammerCombat(CONCUSSION_SEAM, HAMMER_UNDEAD_SEAM);
    public static final Innate CONCUSSION = new Innate("concussion", HAMMER_SEAM, null);

    /**
     * The one seam the hammer's {@link Innate} carries, for concussion's chance to stagger and the
     * undead bonus damage -- two independently portable behaviors under one id, the same shape as
     * {@link BroadswordCombat}.
     */
    private record HammerCombat(CombatSeam concussion, CombatSeam undead) implements CombatSeam {
        @Override
        public float preHit(CombatHit hit, float originalDamage, float damage) {
            return undead.preHit(hit, originalDamage, concussion.preHit(hit, originalDamage, damage));
        }

        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            concussion.onHit(hit, damageDealt);
            undead.onHit(hit, damageDealt);
        }
    }

    /** Excavator: everything it hits goes one Knockback level further. */
    public static final CombatSeam FLAT_SMACK_SEAM = new KnockbackOnHitSeam(KNOCKBACK_LEVEL);
    public static final Innate FLAT_SMACK = new Innate("flat_smack", FLAT_SMACK_SEAM, null);

    /** Lumber axe: the first blow is the big one. */
    public static final CombatSeam TIMBER_SEAM = new ConditionalSeam(HitCondition.FULL_HEALTH, 1.0F,
            new BonusDamageFraction(TIMBER_BONUS_DAMAGE_FRACTION));
    public static final Innate TIMBER = new Innate("timber", TIMBER_SEAM, null);

    /**
     * Scythe: the area attack that is the point of a scythe (upstream {@code Scythe#onLeftClickEntity}).
     *
     * <p>Called "sweep" rather than the issue's "reap" only because the kama already shipped an innate
     * under that name (issue #156, a finishing-damage bonus) and an innate's id is its lang key -- two
     * different behaviors cannot share one. The behavior and its magnitude are unchanged.
     */
    public static final CombatSeam SWEEP_SEAM = new SweepAttackSeam(SCYTHE_SWEEP_RADIUS);
    public static final Innate SWEEP = new Innate("sweep", SWEEP_SEAM, null);

    /** Vein hammer: armor is what it is for. */
    public static final CombatSeam CRUSHING_BLOW_SEAM = new ConditionalSeam(HitCondition.ARMORED, 1.0F,
            new KnockbackOnHitSeam(KNOCKBACK_LEVEL));
    public static final Innate CRUSHING_BLOW = new Innate("crushing_blow", CRUSHING_BLOW_SEAM, null);

    // ------------------------------------------------------------------ lookup

    /** The innate this stack's tool carries, or {@code null} -- the M3 binding (issue #155). */
    @Nullable
    public static Innate of(ItemStack stack) {
        return stack.getItem() instanceof ToolItem tool ? tool.innate() : null;
    }

    /** Registered once in {@code Forgeweave}, alongside materials' traits. */
    public static void collect(ItemStack weapon, Consumer<CombatSeam> out) {
        if (weapon.is(ForgeweaveItems.TOOL_PICKAXE.get())) {
            out.accept(PIERCE);
        } else if (weapon.is(ForgeweaveItems.TOOL_SHOVEL.get())) {
            out.accept(FLATTEN);
        } else if (weapon.is(ForgeweaveItems.TOOL_HATCHET.get())) {
            out.accept(SUNDER);
        }
        Innate innate = of(weapon);
        if (innate != null && innate.seam() != null) {
            out.accept(innate.seam());
        }
    }

    /**
     * The innate id an assembled tool's tooltip should show ({@code ToolTooltip}), or empty for a
     * tool that has none -- the same ids {@link #collect} keys its seams by, so the tooltip can never
     * name an innate the stack doesn't actually carry.
     */
    public static Optional<ResourceLocation> innateId(ItemStack stack) {
        if (stack.is(ForgeweaveItems.TOOL_PICKAXE.get())) {
            return Optional.of(id("pierce"));
        }
        if (stack.is(ForgeweaveItems.TOOL_SHOVEL.get())) {
            return Optional.of(id("flatten"));
        }
        if (stack.is(ForgeweaveItems.TOOL_HATCHET.get())) {
            return Optional.of(id("sunder"));
        }
        // #158 -- the cleaver's innate beheading. Named here but deliberately carried as no Innate
        // seam at all: its levels are summed with the applied beheading modifier's into one roll by
        // Beheading's own provider, so a seam here would roll it twice.
        if (stack.is(ForgeweaveItems.TOOL_CLEAVER.get())) {
            return Optional.of(id("beheading"));
        }
        // #303 -- the warmace's innate is vanilla's own mace, not a seam ({@code WarmaceItem}'s "why
        // not a combat seam"), so its constructor's innate argument is always null and it would
        // otherwise show no tooltip line at all, unlike every other weapon. Same carve-out as the
        // cleaver's above.
        if (stack.is(ForgeweaveItems.TOOL_WARMACE.get())) {
            return Optional.of(id("smash"));
        }
        Innate innate = of(stack);
        return innate == null ? Optional.empty() : Optional.of(id(innate.id()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    // ------------------------------------------------------------------ behaviors

    /**
     * A flat fraction of the target's <em>current</em> health, dealt past armor as a second damage
     * instance -- which is the only way to bypass mitigation from {@link CombatSeam#onHit}, since the
     * blow this rides on has already been through armor by then. Upstream's rapier splits its hit the
     * same way ({@code Rapier#dealHybridDamage}: hit, clear the invulnerability window, hit again with
     * {@code setDamageBypassesArmor}); only the magnitude is the maintainer's.
     *
     * <p>The second instance names the attacker as its <em>causing</em> entity but has no direct
     * entity, so {@link DamageSource#getWeaponItem()} is null and {@link CombatSeams} does not see it
     * as another tool blow -- which is what keeps this from feeding itself.
     */
    public record CurrentHealthStrike(float fraction) implements CombatSeam {
        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            LivingEntity target = hit.target();
            float bonus = target.getHealth() * fraction;
            if (bonus <= 0.0F) {
                return;
            }
            // Upstream clears the same window for the same reason: without it the blow we are riding
            // on has already claimed the target's invulnerability and this one is swallowed whole.
            target.invulnerableTime = 0;
            target.hurt(armorBypassing(hit.level(), hit.attacker()), bonus);
        }
    }

    /** Extra push on top of the blow's own, in the attacker's facing direction. */
    public record HeavyKnockback(float strength) implements CombatSeam {
        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            LivingEntity attacker = hit.attacker();
            if (attacker == null) {
                return;
            }
            LivingEntity target = hit.target();
            // Vanilla's own argument convention (LivingEntity#hurt: knockback(0.4, source.getX() -
            // getX(), source.getZ() - getZ())): the vector points at the attacker and knockback
            // subtracts it, so the target is pushed away. Using the attacker's yaw instead would
            // point somewhere slightly different and partly cancel the push vanilla adds afterwards.
            target.knockback(strength, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
            // knockback() sets hasImpulse, which syncs a mob; a player's client owns its own motion
            // and only takes a velocity packet when the server marks the entity hurt.
            target.hurtMarked = true;
        }
    }

    /**
     * Bonus damage that scales with how directly behind the target the blow lands: {@code maxBonus}
     * within {@code fullDegrees} of dead-behind, falling off linearly to {@code minBonus} at
     * {@code coneDegrees}, nothing outside that. Measured on the horizontal plane only, so crouching
     * or looking up doesn't move the cone.
     */
    public record Backstab(float fullDegrees, float coneDegrees, float maxBonus, float minBonus)
            implements CombatSeam {

        @Override
        public float preHit(CombatHit hit, float originalDamage, float damage) {
            return damage + originalDamage * bonusFraction(hit.attacker(), hit.target());
        }

        /** Package-visible and pure, so the gradient is testable without staging a blow. */
        public float bonusFraction(@Nullable LivingEntity attacker, LivingEntity target) {
            if (attacker == null) {
                return 0.0F;
            }
            Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYRot());
            Vec3 approach = target.position().subtract(attacker.position());
            approach = new Vec3(approach.x, 0.0, approach.z);
            if (approach.lengthSqr() < 1.0E-6) {
                return 0.0F;
            }
            // Dead-behind means the target is walking away along the same line the blow travels.
            double cos = Mth.clamp(facing.dot(approach.normalize()), -1.0, 1.0);
            float degrees = (float) Math.toDegrees(Math.acos(cos));
            if (degrees <= fullDegrees) {
                return maxBonus;
            }
            if (degrees > coneDegrees) {
                return 0.0F;
            }
            float progress = (degrees - fullDegrees) / (coneDegrees - fullDegrees);
            return Mth.lerp(progress, maxBonus, minBonus);
        }
    }

    /**
     * The broadsword's parry: a short window opened with the use button that negates one incoming
     * melee blow and slows whoever threw it.
     *
     * <p>{@link UseAnim#NONE} deliberately, not {@code BLOCK}: {@code BLOCK} would hand the window
     * vanilla's whole shield mechanic ({@code LivingEntity#isDamageSourceBlocked}), which negates
     * <em>every</em> front-facing blow for as long as it is held and never ends the parry -- a
     * strictly stronger behavior than the one that was decided, arrived at by accident.
     */
    public record Parry(int windowTicks, int slowAmplifier, int slowTicks) implements CombatSeam, ToolUseAction {

        @Override
        public UseAnim animation() {
            return UseAnim.NONE;
        }

        @Override
        public int durationTicks() {
            return windowTicks;
        }

        @Override
        public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
            // The use lasts exactly the window, so an active use means the parry is open. using()
            // is the gate: since issue #229 the defensive pass also runs for a merely-held tool,
            // which must never parry -- and since issue #460 it also runs while some *other* item
            // blocks (a raised shield in the off hand), which is not this broadsword's parry either.
            if (!defense.using() || !isMelee(defense.source())) {
                return damage;
            }
            LivingEntity attacker = defense.attacker();
            if (attacker != null) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, slowAmplifier));
            }
            defense.defender().stopUsingItem(); // one blow per parry
            return 0.0F;
        }

        /** A blow from something swinging at us: not a projectile, not an explosion, not environmental. */
        private static boolean isMelee(DamageSource source) {
            return source.getDirectEntity() instanceof LivingEntity
                    && !source.is(DamageTypeTags.IS_PROJECTILE)
                    && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        }
    }

    /**
     * The battlesign's blocking stance. {@link UseAnim#BLOCK} is upstream's own ({@code BattleSign}
     * returns {@code EnumAction.BLOCK}), which also gives it vanilla's shield blocking -- the same
     * thing 1.12's BLOCK action gave it there.
     *
     * <p>Two halves to the innate proper, both ported from upstream and both gated on
     * {@link CombatDefense#blocking()}:
     *
     * <ul>
     *   <li>a projectile stopped while facing it is sent back where it came from at its own speed
     *       plus a little, with the defender as its new owner ({@code BattleSign#reflectProjectiles});
     *   <li>any other blockable hit (issue #302, {@code BattleSign#reducedDamageBlocked}) takes an
     *       extra {@link #meleeReduction} on top of vanilla's own shield-block halving -- which lands
     *       after this seam runs, since {@link CombatSeam#incomingHit} fires before mitigation, same
     *       as upstream's {@code LivingHurtEvent} fires before its own extra halving -- and reflects
     *       {@link #meleeReflectFraction} of the reduced amount onto the attacker as thorns damage.
     * </ul>
     *
     * <p>Both halves pay durability for what they stopped.
     */
    public record Deflect(int holdTicks, double minFacing, double speedBonus, float meleeReduction,
            float meleeReflectFraction) implements CombatSeam, ToolUseAction {

        @Override
        public UseAnim animation() {
            return UseAnim.BLOCK;
        }

        @Override
        public int durationTicks() {
            return holdTicks;
        }

        @Override
        public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
            // using() rather than blocking(): upstream's gate is BattleSign#shouldBlockDamage, which
            // demands isActiveItemStackBlocking AND getActiveItemStack().getItem() == this -- a sign
            // in the hand does nothing while a shield in the other hand is what is raised (issue
            // #460). The sign's own animation is BLOCK, so using() implies blocking() here. It is
            // still not vanilla's isBlocking(), which additionally demands five ticks held and would
            // silently make the first quarter-second of a raised sign not a sign at all. (Since issue
            // #229 the defensive pass also runs for a merely-held tool, which must never deflect.)
            if (!defense.using()) {
                return damage;
            }
            // Upstream splits these two across separate events (LivingAttackEvent for the projectile
            // half, LivingHurtEvent -- which explicitly excludes isProjectile() -- for the other), but
            // both read as "blockable" here, so the source's own tag is the split.
            if (defense.source().is(DamageTypeTags.IS_PROJECTILE)) {
                return deflectProjectile(defense, originalDamage, damage);
            }
            return reduceMeleeBlock(defense, damage);
        }

        private float deflectProjectile(CombatDefense defense, float originalDamage, float damage) {
            if (!(defense.source().getDirectEntity() instanceof Projectile projectile)) {
                return damage;
            }
            Vec3 motion = projectile.getDeltaMovement();
            if (motion.lengthSqr() < 1.0E-6) {
                return damage;
            }
            Vec3 look = defense.defender().getLookAngle();
            // Upstream's own check: caught only if we were actually looking at the incoming shot.
            if (-look.dot(motion.normalize()) < minFacing) {
                return damage;
            }

            double speed = motion.length() + speedBonus;
            projectile.setDeltaMovement(look.scale(speed));
            projectile.hasImpulse = true;
            projectile.setOwner(defense.defender());
            // Upstream spends durability equal to the damage stopped.
            defense.tool().hurtAndBreak(Math.max(1, (int) originalDamage), defense.defender(),
                    defense.slot());
            return 0.0F;
        }

        /**
         * Upstream {@code BattleSign#reducedDamageBlocked} verbatim, magnitudes and all: don't affect
         * unblockable ({@code minecraft:bypasses_invulnerability}, the same tag {@code Parry#isMelee}
         * reads as "unblockable"), magic ({@code neoforge:is_magic} -- which itself lists
         * {@code minecraft:thorns}, so a reflect landing on a second blocking battlesign never
         * re-triggers this same reduction) or explosion damage. The durability cost is upstream's own
         * odd shape: half the pre-reduction damage rounded (never below 1 -- upstream's
         * {@code amount < 2f ? 1 : round(amount / 2f)}), times one and a half when there was an
         * attacker to reflect onto.
         *
         * <p>{@code damage <= 0} bails the same way upstream's own handler does on
         * {@code event.isCanceled()}: a blow an earlier seam already zeroed (flammable's fire absorb,
         * issue #229) is nothing left to reduce, reflect or pay durability for.
         */
        private float reduceMeleeBlock(CombatDefense defense, float damage) {
            if (damage <= 0.0F
                    || defense.source().is(DamageTypeTags.IS_EXPLOSION)
                    || defense.source().is(Tags.DamageTypes.IS_MAGIC)
                    || defense.source().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return damage;
            }
            int durability = damage < 2.0F ? 1 : Math.round(damage / 2.0F);
            float reduced = damage * meleeReduction;
            LivingEntity attacker = defense.attacker();
            if (attacker != null) {
                attacker.hurt(thorns(defense.level(), defense.defender()), reduced * meleeReflectFraction);
                durability = durability * 3 / 2;
            }
            defense.tool().hurtAndBreak(durability, defense.defender(), defense.slot());
            return reduced;
        }
    }

    /**
     * The longsword's charged leap, ported from {@code LongSword#onPlayerStoppedUsing}. Not a record:
     * its numbers are the class constants above rather than per-instance parameters, because there is
     * exactly one leap and its formula is upstream's, not a magnitude the maintainer picked.
     */
    public static final class ChargedLeap implements ToolUseAction {

        @Override
        public UseAnim animation() {
            return UseAnim.BOW;
        }

        @Override
        public int durationTicks() {
            return LEAP_CHARGE_TICKS;
        }

        /**
         * Upstream {@code LongSword#onItemRightClick} verbatim: don't allow free flight while
         * elytra-flying, should use fireworks instead. {@code pass} leaves the click for the offhand,
         * the same decline {@link Lunge} uses for its own carve-out.
         */
        @Override
        @Nullable
        public InteractionResultHolder<ItemStack> onUse(ItemStack stack, Level level, Player player,
                InteractionHand hand) {
            return player.isFallFlying() ? InteractionResultHolder.pass(stack) : null;
        }

        @Override
        public void onRelease(ItemStack stack, ServerLevel level, LivingEntity user, int heldTicks) {
            if (heldTicks <= LEAP_MIN_TICKS) {
                return;
            }
            float rise = Math.min(LEAP_RISE_PER_TICK * heldTicks + LEAP_RISE_BASE, LEAP_RISE_CAP);
            float speed = Math.min(LEAP_SPEED_PER_TICK * heldTicks, LEAP_SPEED_CAP);
            // Upstream writes out -sin(yaw)cos(pitch) / cos(yaw)cos(pitch) by hand; this is the same
            // vector, and keeping the pitch term is what makes looking up leap higher and shorter.
            Vec3 look = Vec3.directionFromRotation(user.getXRot(), user.getYRot());
            user.setDeltaMovement(look.x * speed, user.getDeltaMovement().y + rise, look.z * speed);
            user.setSprinting(true);
            user.hurtMarked = true; // a player's own client is authoritative; push the new motion down.
            if (user instanceof Player player) {
                player.causeFoodExhaustion(LEAP_EXHAUSTION);
                player.getCooldowns().addCooldown(stack.getItem(), LEAP_COOLDOWN_TICKS);
            }
        }
    }

    /**
     * The rapier's fencing hop, ported from {@code Rapier#onItemRightClick}. Instant rather than held,
     * so it answers {@link ToolUseAction#onUse} and never starts a use. Not a record for the same
     * reason {@link ChargedLeap} is not: the numbers are upstream's, not a magnitude anyone picked.
     *
     * <p>Two details are upstream's own and deliberately kept:
     *
     * <ul>
     *   <li>the dash is the <em>negation</em> of the look direction ({@code sin(yaw)cos(pitch)} /
     *       {@code -cos(yaw)cos(pitch)} where the longsword's leap writes the sign the other way
     *       round), i.e. a backwards disengage. Issue #300's summary calls it a forward dash; the
     *       1.12 source it cites does not, and parity wins;
     *   <li>the pitch term, which shortens the dash the further from level the player is looking.
     * </ul>
     *
     * <p>Upstream's shield carve-out changes only the <em>answer</em>, not the lunge: it hops first and
     * then returns {@code PASS} so the offhand shield still goes up. Issue #300's summary reads it as a
     * suppression; the 1.12 source does not, and parity wins (maintainer decision 2026-08-13). Making
     * it suppress instead is moving that {@code pass} above the lunge.
     */
    public static final class Lunge implements ToolUseAction {

        @Override
        public UseAnim animation() {
            return UseAnim.NONE; // never held; present only because the interface asks for it
        }

        @Override
        public int durationTicks() {
            return 0;
        }

        @Override
        @Nullable
        public InteractionResultHolder<ItemStack> onUse(ItemStack stack, Level level, Player player,
                InteractionHand hand) {
            if (player.onGround() && !level.isClientSide()) {
                Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
                player.setDeltaMovement(-look.x * LUNGE_SPEED,
                        player.getDeltaMovement().y + LUNGE_RISE,
                        -look.z * LUNGE_SPEED);
                player.hurtMarked = true; // a player's own client is authoritative; push the motion down.
                player.causeFoodExhaustion(LUNGE_EXHAUSTION);
                player.getCooldowns().addCooldown(stack.getItem(), LUNGE_COOLDOWN_TICKS);
            }
            // The lunge has already happened either way; the offhand only decides the answer. A
            // "shield-like" offhand is upstream's vanilla-shield-or-battlesign test, read off the
            // behavior rather than a list of items: a raised-block animation is what both have in
            // common. PASS hands the click on so that shield still goes up.
            if (hand == InteractionHand.MAIN_HAND
                    && player.getOffhandItem().getUseAnimation() == UseAnim.BLOCK) {
                return InteractionResultHolder.pass(stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }

    /**
     * The frying pan's charged launch, ported from {@code FryPan#onPlayerStoppedUsing} (issue #301).
     * Held like {@link ChargedLeap} and not a record for the same reason: every number is upstream's.
     *
     * <p>Three things happen on release, in upstream's own order, to whatever the player is looking at
     * within {@value #LAUNCH_RANGE} blocks: an ordinary blow with a charge-scaled bonus on the
     * attacker's attack damage, a brief ignite across that blow at full charge only -- so what it kills
     * drops cooked, which is upstream's joke and the only place the fire shows -- and then the launch
     * itself, added on top of whatever push the blow already left.
     *
     * <p>The blow is {@link Player#attack} rather than a bare {@code hurt}: upstream's
     * {@code ToolHelper#attackEntity} is a re-implementation of 1.12's player attack (attack-strength
     * scaling, crits, knockback, cooldown reset), and vanilla's own method is that same thing here --
     * which also means the pan's other half, {@link HeavyKnockback}, rides along exactly as it does on
     * a left click. Non-player users have no attack of their own to scale, so they get nothing; only
     * players hold tools.
     */
    public static final class ChargedLaunch implements ToolUseAction {

        /** The attack-damage bonus is added and removed around the one blow, never persisted. */
        private static final ResourceLocation CHARGE_BONUS = id("charged_launch");

        @Override
        public UseAnim animation() {
            return UseAnim.BOW;
        }

        @Override
        public int durationTicks() {
            return LAUNCH_USE_TICKS;
        }

        @Override
        public void onRelease(ItemStack stack, ServerLevel level, LivingEntity user, int heldTicks) {
            if (!(user instanceof Player player)) {
                return;
            }
            Vec3 look = player.getLookAngle();
            Entity target = lookedAt(player, look);
            if (target == null) {
                return;
            }
            float progress = Math.min(1.0F, heldTicks / LAUNCH_FULL_CHARGE_TICKS);
            float strength = LAUNCH_STRENGTH_BASE + LAUNCH_STRENGTH_PER_CHARGE * progress * progress;

            // Upstream lights the target for the blow and puts it out straight after, so the kill drops
            // cooked without the target actually burning afterwards.
            boolean flamingStrike = progress >= 1.0F && !target.isOnFire();
            if (flamingStrike) {
                target.setRemainingFireTicks(LAUNCH_IGNITE_TICKS);
            }
            AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) {
                attack.addTransientModifier(new AttributeModifier(CHARGE_BONUS,
                        progress * LAUNCH_BONUS_DAMAGE, AttributeModifier.Operation.ADD_VALUE));
            }
            try {
                player.attack(target);
            } finally {
                if (attack != null) {
                    attack.removeModifier(CHARGE_BONUS);
                }
            }
            if (flamingStrike) {
                target.clearFire();
            }

            target.setDeltaMovement(target.getDeltaMovement().add(look.x * strength,
                    look.y / 3.0F * strength + LAUNCH_LIFT_BASE + LAUNCH_LIFT_PER_CHARGE * progress,
                    look.z * strength));
            target.hurtMarked = true; // a launched player's own client owns its motion until told otherwise
        }

        /**
         * The entity the player's look ray meets first within {@value #LAUNCH_RANGE} blocks, or
         * {@code null}. Upstream's {@code EntityUtil#raytraceEntity} with {@code ignoreCanBeCollidedWith},
         * which is this vanilla helper -- and the shape upstream itself reaches for once it has one
         * ({@code SlingKnockbackModule} on the 1.20 branch).
         */
        @Nullable
        private static Entity lookedAt(Player player, Vec3 look) {
            Vec3 eye = player.getEyePosition(1.0F);
            Vec3 end = eye.add(look.scale(LAUNCH_RANGE));
            AABB searched = player.getBoundingBox()
                    .expandTowards(look.scale(LAUNCH_RANGE))
                    .inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, searched,
                    candidate -> candidate.isAlive() && !candidate.isSpectator());
            return hit == null ? null : hit.getEntity();
        }
    }

    /**
     * A damage source in {@code minecraft:bypasses_armor} that credits {@code attacker} for the kill
     * without naming a direct entity -- see {@link CurrentHealthStrike} for why the second half
     * matters. {@code minecraft:generic} is the plainest of the tag's members: no fire, no magic, no
     * scaling of its own.
     */
    static DamageSource armorBypassing(ServerLevel level, @Nullable LivingEntity attacker) {
        Holder<DamageType> type = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.GENERIC);
        return new DamageSource(type, null, attacker);
    }

    /**
     * A {@code minecraft:thorns}-typed damage source crediting {@code source} without naming a direct
     * entity -- upstream's {@code DamageSource.causeThornsDamage}, which the battlesign's melee-block
     * retaliation reuses (issue #302).
     */
    static DamageSource thorns(ServerLevel level, LivingEntity source) {
        Holder<DamageType> type = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.THORNS);
        return new DamageSource(type, null, source);
    }

    private ForgeweaveInnates() {}
}
