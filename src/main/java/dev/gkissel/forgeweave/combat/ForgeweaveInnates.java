package dev.gkissel.forgeweave.combat;

import java.util.Optional;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
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
     * Upstream {@code BattleSign#reflectProjectiles}: only counts while looking at the projectile
     * ({@code -look · motion > 0.1}), and returns it at its own speed plus a little.
     */
    private static final int DEFLECT_HOLD_TICKS = 72000;
    private static final double DEFLECT_MIN_FACING = 0.1;
    private static final double DEFLECT_SPEED_BONUS = 0.2;

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

    /**
     * Broadsword. Upstream's 1.12 innate was sword-blocking, which modern Minecraft no longer has;
     * the maintainer's replacement (2026-08-12) is a short parry window opened with the use button.
     */
    public static final Innate PARRY = parry();

    /** Longsword: upstream's charged leap, ported constant for constant. */
    public static final Innate CHARGED_LEAP = new Innate("charged_leap", null, new ChargedLeap());

    /** Rapier: the maintainer's redesign of upstream's hybrid-damage double hit. */
    public static final CurrentHealthStrike VITAL_THRUST_SEAM = new CurrentHealthStrike(VITAL_THRUST_FRACTION);
    public static final Innate VITAL_THRUST = new Innate("vital_thrust", VITAL_THRUST_SEAM, null);

    /** Battlesign: upstream's blocking stance that returns projectiles to their sender. */
    public static final Innate DEFLECT = deflect();

    /** Frying pan: upstream's doubled knockback. */
    public static final HeavyKnockback HEAVY_SWING_SEAM = new HeavyKnockback(HEAVY_KNOCKBACK);
    public static final Innate HEAVY_SWING = new Innate("heavy_swing", HEAVY_SWING_SEAM, null);

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
        return new Innate("parry", behavior, behavior);
    }

    private static Innate deflect() {
        Deflect behavior = new Deflect(DEFLECT_HOLD_TICKS, DEFLECT_MIN_FACING, DEFLECT_SPEED_BONUS);
        return new Innate("deflect", behavior, behavior);
    }

    // ------------------------------------------------------------------ M1's retrofit (issue #164)

    /** Maintainer decision, issue #164 (2026-08-12): 1.0 flat armor-ignoring damage. */
    private static final float PIERCE_DAMAGE = 1.0F;
    /** Maintainer decision, issue #164 (2026-08-12): Slowness I for 1.5s (30 ticks). */
    private static final int FLATTEN_SLOWNESS_DURATION_TICKS = 30;
    /** Maintainer decision, issue #164 (2026-08-12): 20% bonus damage vs a blocking target. */
    private static final float SUNDER_BONUS_DAMAGE_FRACTION = 0.2F;

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
        // #158 -- the cleaver's innate beheading. Named here but deliberately carried as no
        // {@link Innate} seam at all: its levels are summed with the applied beheading modifier's
        // into one roll by {@code Beheading}'s own provider, so a seam here would roll it twice.
        if (stack.is(ForgeweaveItems.TOOL_CLEAVER.get())) {
            return Optional.of(id("beheading"));
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
            // Reached only while the tool is actively used, and the use lasts exactly the window, so
            // being here at all means the parry is open (CombatSeams#defenseOf).
            if (!isMelee(defense.source())) {
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
     * <p>The innate proper is the reflect: a projectile stopped while facing it is sent back where it
     * came from at its own speed plus a little, with the defender as its new owner, and the sign pays
     * durability for it. Ported from {@code BattleSign#reflectProjectiles}.
     */
    public record Deflect(int holdTicks, double minFacing, double speedBonus) implements CombatSeam, ToolUseAction {

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
            // No isBlocking() check: upstream's own gate is isActiveItemStackBlocking, which is
            // "holding the use button on a BLOCK-animation item" with no warm-up -- exactly what
            // CombatSeams' active-item gate already established before this seam was reached.
            // Vanilla's isBlocking() additionally demands five ticks held, which would silently make
            // the first quarter-second of a raised sign not a sign at all.
            if (!defense.source().is(DamageTypeTags.IS_PROJECTILE)
                    || !(defense.source().getDirectEntity() instanceof Projectile projectile)) {
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
                    EquipmentSlot.MAINHAND);
            return 0.0F;
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

    private ForgeweaveInnates() {}
}
