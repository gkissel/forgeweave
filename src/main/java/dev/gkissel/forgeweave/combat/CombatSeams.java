package dev.gkissel.forgeweave.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * The per-hit event pipeline (ADR-0005 decision 3): the one place a blow struck with a Forgeweave
 * tool reaches Forgeweave code, and therefore the one place combat innates and combat modifiers
 * attach. Everything about a tool's combat <em>feel</em> stays vanilla -- cooldown, crits and
 * knockback are the game's (ADR-0005 decision 1) -- so this pipeline only observes and adjusts;
 * it never re-implements an attack.
 *
 * <h2>The three moments</h2>
 *
 * <table>
 *   <tr><th>Hook</th><th>NeoForge event</th><th>Fires</th></tr>
 *   <tr><td>{@link CombatSeam#preHit}</td><td>{@link LivingIncomingDamageEvent}</td>
 *       <td>after invulnerability checks, before any mitigation -- upstream 1.12 runs
 *           {@code ITrait#damage} at the same point in {@code ToolHelper#attackEntity}</td></tr>
 *   <tr><td>{@link CombatSeam#onHit}</td><td>{@link LivingDamageEvent.Post}</td>
 *       <td>once the target has actually lost health, upstream's {@code ITrait#afterHit}</td></tr>
 *   <tr><td>{@link CombatSeam#postKill}</td><td>{@link LivingDeathEvent}</td>
 *       <td>once the target dies</td></tr>
 *   <tr><td>{@link CombatSeam#knockback}</td><td>{@link LivingKnockBackEvent}</td>
 *       <td>the flat {@code 0.4f} push vanilla's own {@code LivingEntity#hurt} applies to every
 *           successful hit, strictly after {@link CombatSeam#onHit} for the same blow -- upstream's
 *           {@code ITool#knockback()} multiplier (issue #465/T34), see {@link #onKnockback}</td></tr>
 * </table>
 *
 * <p>Each of the first three vanilla/NeoForge events fires exactly once per damage instance
 * ({@code LivingEntity#actuallyHurt} posts the damage events once and only for a target that was
 * not invulnerable; {@code LivingEntity#hurt} calls {@code die} once afterwards), which is what
 * makes "exactly once per hit, exactly once per kill" a property of the events rather than of
 * bookkeeping here. A killing blow therefore runs pre-hit, then on-hit, then post-kill, in that
 * order. {@link LivingKnockBackEvent} has no such guarantee -- {@code LivingEntity#knockback} fires
 * it for every knockback in the game, this tool's blow included zero or more times, or not at all --
 * so {@link #onKnockback} matches it to a specific hit itself rather than leaning on event cardinality.
 *
 * <h2>Who attaches</h2>
 *
 * <p>A {@link Provider} maps a weapon stack to the seams that apply to <em>that</em> tool: one
 * provider per source of combat behavior (materials' traits today; per-tool innates and combat
 * modifiers as M3 lands them), registered once at mod construction in {@code Forgeweave} so the
 * order they run in is visible in one place. Resolution happens per hit, off the stack's own
 * components, so a provider can hand back a seam already parameterized with the level the tool
 * carries -- which is exactly the shape ADR-0004 commits these behaviors to at M6.
 *
 * <p>ponytail: no priorities, no cancellation, no per-seam registration keys. Registration order is
 * the order, and a seam that wants to stop a hit sets its damage to zero like any other adjustment.
 * Add the machinery when a shipped behavior needs it.
 */
public final class CombatSeams {

    /**
     * Supplies the seams that apply to one weapon stack. Called on every hit, so an implementation
     * should read the stack's components and hand back seams rather than do real work itself.
     */
    @FunctionalInterface
    public interface Provider {
        void collect(ItemStack weapon, Consumer<CombatSeam> out);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    /** Registers a source of combat behavior. Call order is hook order; see the class javadoc. */
    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    /**
     * The seams that apply to {@code weapon}, in registration order, or an empty list if none do.
     * Public so a GameTest can assert what a given tool resolves to without staging a real blow.
     */
    public static List<CombatSeam> seams(ItemStack weapon) {
        List<CombatSeam> seams = new ArrayList<>();
        for (Provider provider : PROVIDERS) {
            provider.collect(weapon, seams::add);
        }
        return seams;
    }

    /**
     * Registered on the game event bus in {@code Forgeweave}. See the class javadoc's table. Runs
     * both passes over the one event: first the weapon's seams ({@link CombatSeam#preHit}), then the
     * defender's ({@link CombatSeam#incomingHit}) -- in that order, so a defensive seam sees the
     * damage the attacker's tool actually intends to land.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        weaponPass(event);
        defensePass(event);
    }

    private static void weaponPass(LivingIncomingDamageEvent event) {
        CombatHit hit = hitOf(event.getSource(), event.getEntity());
        if (hit == null) {
            return;
        }
        List<CombatSeam> seams = seams(hit.weapon());
        if (seams.isEmpty()) {
            return;
        }
        // Class javadoc, "Damage order": seams see the blow before vanilla's cooldown/crit scaling,
        // then upstream's order back out -- crit, cutoff, cooldown.
        float cooldown = hit.cooldownFactor();
        float crit = hit.critMultiplier();
        float original = event.getOriginalAmount() / (cooldown * crit);
        float damage = event.getAmount() / (cooldown * crit);
        for (CombatSeam seam : seams) {
            damage = seam.preHit(hit, original, damage);
        }
        damage = ((ToolItem) hit.weapon().getItem()).cutoffDamage(damage * crit) * cooldown;
        if (damage != event.getAmount()) {
            event.setAmount(damage);
        }
    }

    /**
     * The defensive half (issue #155). A chain that leaves nothing cancels the event rather than
     * setting the amount to zero: a parried or reflected blow must not spend the target's
     * invulnerability window or play a hurt animation, which a zero-damage hit still would.
     */
    private static void defensePass(LivingIncomingDamageEvent event) {
        // Both hands since issue #460, main hand first, upstream's own order and its own
        // "stop once something cancelled the blow" (TraitEvents' `if(!event.isCanceled())` per tool).
        for (CombatDefense defense : defenses(event.getSource(), event.getEntity())) {
            List<CombatSeam> seams = seams(defense.tool());
            if (seams.isEmpty()) {
                continue;
            }
            float original = event.getOriginalAmount();
            float damage = event.getAmount();
            for (CombatSeam seam : seams) {
                damage = seam.incomingHit(defense, original, damage);
            }
            if (damage <= 0.0F) {
                event.setCanceled(true);
                return;
            }
            if (damage != event.getAmount()) {
                event.setAmount(damage);
            }
        }
        armorPass(event);
    }

    /**
     * The worn half of the defensive pass (issue #680, M4-5; SCOPE.md D8): one {@link CombatSeam#onDefend}
     * walk over the defender's two hands (#729) and four armor slots, head to feet, each worn non-Broken
     * {@link ArmorPieceItem} resolved through the same providers a held tool is -- so materials'
     * ARMOR traits and #681's modifiers both ride it with no plumbing of their own. What the walk
     * settles pre-mitigation ({@link DefendedBlow#damage}) is applied here, exactly as the held
     * pass does; what belongs after armor ({@link DefendedBlow#protection},
     * {@link DefendedBlow#flatReduction}) waits for {@link #onDamagePre}.
     */
    private static void armorPass(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        pendingArmorBlow = null;
        if (!(defender.level() instanceof ServerLevel level)) {
            return;
        }
        DefendedBlow blow = null;
        // #729: held tools first -- the clone's EquipmentContext#iterateTools walks every equipment
        // slot, hands included, so a protection on a held sword counts like one on a worn piece.
        for (CombatDefense defense : defenses(event.getSource(), defender)) {
            List<CombatSeam> seams = seams(defense.tool());
            if (seams.isEmpty()) {
                continue;
            }
            if (blow == null) {
                blow = new DefendedBlow(event.getAmount());
            }
            for (CombatSeam seam : seams) {
                seam.onDefend(defense, blow);
            }
        }
        Entity causing = event.getSource().getEntity();
        LivingEntity attacker = causing instanceof LivingEntity living ? living : null;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack piece = defender.getItemBySlot(slot);
            if (!(piece.getItem() instanceof ArmorPieceItem) || ToolItem.isBroken(piece)) {
                continue;
            }
            List<CombatSeam> seams = seams(piece);
            if (seams.isEmpty()) {
                continue;
            }
            if (blow == null) {
                blow = new DefendedBlow(event.getAmount());
            }
            CombatDefense defense = new CombatDefense(level, piece, defender, attacker, event.getSource(), false, false);
            for (CombatSeam seam : seams) {
                seam.onDefend(defense, blow);
            }
        }
        if (blow == null) {
            return; // nothing worn: the event is untouched, byte for byte (the #680 regression)
        }
        if (blow.damage() <= 0.0F) {
            event.setCanceled(true);
            return;
        }
        if (blow.damage() != event.getAmount()) {
            event.setAmount(blow.damage());
        }
        // #831: the one thing a worn piece can only settle through the event itself -- vanilla reads
        // the container's tick count into invulnerableTime right after this event returns.
        if (blow.invulnerabilityTicks() > 0) {
            event.setInvulnerabilityTicks(blow.invulnerabilityTicks());
        }
        if (blow.protection() != 0.0F || blow.flatReduction() > 0.0F) {
            pendingArmorBlow = blow;
            pendingArmorDefender = defender;
        }
    }

    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Nullable
    private static DefendedBlow pendingArmorBlow;
    @Nullable
    private static LivingEntity pendingArmorDefender;

    /**
     * Registered on the game event bus in {@code Forgeweave}. Settles what {@link #armorPass} left
     * for after armor, at the moment the 1.20 clone settles it: {@code LivingDamageEvent.Pre} fires
     * inside {@code actuallyHurt} once vanilla has applied armor, Resistance and its own Protection
     * enchantments and before absorption -- the clone's {@code ArmorUtil#getDamageForEvent} wants
     * {@code M(A(x))} and has to invert the armor formula to get it from the earlier event; here
     * the later event hands {@code A(x)} over directly.
     *
     * <p>Protection: vanilla has already taken its Protection levels {@code v} off as
     * {@code 1 - min(v, 20) / 25} ({@code CombatRules#getDamageAfterMagicAbsorb}); the clone's
     * total is {@code v} plus every piece's value under the same 20 cap, so the damage is rescaled
     * to that total. Then warded's flat cut: {@code min(d, max(1, d - flat))}, the clone's
     * {@code AdjustDamageModule} formula.
     *
     * <p>Same one-remembered-blow idiom as {@link #pendingKnockbackHit}: the incoming event and this
     * one happen back to back on the server thread inside a single {@code LivingEntity#hurt}, and a
     * blow that never reaches {@code actuallyHurt} (shield, invulnerability) is simply overwritten
     * by the next; the identity check is only there so it can never leak onto another entity.
     */
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        DefendedBlow blow = pendingArmorBlow;
        if (blow == null || event.getEntity() != pendingArmorDefender) {
            return;
        }
        pendingArmorBlow = null;
        pendingArmorDefender = null;
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        if (blow.protection() != 0.0F && damage < Float.MAX_VALUE) {
            float vanilla = 0.0F;
            if (event.getEntity().level() instanceof ServerLevel level) {
                vanilla = EnchantmentHelper.getDamageProtection(level, event.getEntity(), event.getSource());
            }
            float before = 1.0F - Mth.clamp(vanilla, 0.0F, PROTECTION_CAP) / PROTECTION_DIVISOR;
            // The clone's ProtectionModifierHook contract: "can also go negative, up to 180% increase
            // from a modifier value of -20" (depth protection above Y=96).
            float after = 1.0F - Mth.clamp(vanilla + blow.protection(), -PROTECTION_CAP, PROTECTION_CAP) / PROTECTION_DIVISOR;
            damage = damage / before * after;
        }
        if (blow.flatReduction() > 0.0F) {
            damage = Math.min(damage, Math.max(1.0F, damage - blow.flatReduction()));
        }
        if (damage != event.getNewDamage()) {
            event.setNewDamage(damage);
        }
    }

    /** Vanilla's Protection cap and divisor ({@code CombatRules#getDamageAfterMagicAbsorb}), the clone's too. */
    private static final float PROTECTION_CAP = 20.0F;
    private static final float PROTECTION_DIVISOR = 25.0F;

    /** Registered on the game event bus in {@code Forgeweave}. See the class javadoc's table. */
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        CombatHit hit = hitOf(event.getSource(), event.getEntity());
        if (hit == null) {
            return;
        }
        // #465/T34: a seam's own onHit can call LivingEntity#knockback (KnockbackOnHitSeam, the
        // frying pan's HeavyKnockback), which fires the very LivingKnockBackEvent onKnockback listens
        // for. dispatchingOnHit marks those pushes as none of onKnockback's business while they happen.
        dispatchingOnHit = true;
        try {
            for (CombatSeam seam : seams(hit.weapon())) {
                seam.onHit(hit, event.getNewDamage());
            }
        } finally {
            dispatchingOnHit = false;
        }
        // The flat push CombatSeam#knockback scales is still ahead of us -- LivingEntity#hurt calls its
        // own this.knockback(0.4F, ...) after actuallyHurt (which posted the event that led here)
        // returns, but before hurt() itself returns. Remember this hit so onKnockback can attribute
        // that next push, consumed once it does -- which is also what keeps Player#attack's own later,
        // separate sprint/enchant-driven knockback() call (fired only after hurt() has fully returned)
        // from matching a second time; see CombatSeam#knockback's javadoc.
        pendingKnockbackHit = hit;
    }

    private static boolean dispatchingOnHit;
    @Nullable
    private static CombatHit pendingKnockbackHit;

    /**
     * Registered on the game event bus in {@code Forgeweave}. See the class javadoc's table and
     * {@link CombatSeam#knockback}. Fires for every {@code LivingEntity#knockback} call in the game,
     * not just ones a Forgeweave tool caused, so most calls exit on the first check below.
     */
    public static void onKnockback(LivingKnockBackEvent event) {
        CombatHit hit = pendingKnockbackHit;
        if (dispatchingOnHit || hit == null || event.getEntity() != hit.target()) {
            return;
        }
        pendingKnockbackHit = null; // consumed: a later, unrelated push must not match this hit again
        float strength = event.getStrength();
        for (CombatSeam seam : seams(hit.weapon())) {
            strength = seam.knockback(hit, strength);
        }
        if (strength != event.getStrength()) {
            event.setStrength(strength);
        }
    }

    /** Registered on the game event bus in {@code Forgeweave}. See the class javadoc's table. */
    public static void onDeath(LivingDeathEvent event) {
        CombatHit hit = hitOf(event.getSource(), event.getEntity());
        if (hit == null) {
            return;
        }
        for (CombatSeam seam : seams(hit.weapon())) {
            seam.postKill(hit);
        }
    }

    /**
     * The blow these seams are about, or {@code null} when this damage is none of Forgeweave's
     * business. The one gate for all three hooks, so a seam never has to repeat it: server side, a
     * Forgeweave tool as the weapon, and not a Broken one -- upstream 1.12 refuses the attack outright
     * while Broken ({@code ToolHelper#attackEntity}), and {@code ToolItem} already strips a Broken
     * tool's attack attributes.
     */
    @Nullable
    private static CombatHit hitOf(DamageSource source, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return null;
        }
        ItemStack weapon = source.getWeaponItem();
        if (weapon == null || !(weapon.getItem() instanceof ToolItem) || ToolItem.isBroken(weapon)) {
            return null;
        }
        Entity causing = source.getEntity();
        LivingEntity attacker = causing instanceof LivingEntity living ? living : null;
        // M3.5 #396: an arrow's damage source hands back the bow that fired it (1.21's
        // AbstractArrow#firedFromWeapon), so a launcher's hit-effect modifiers and traits ride the
        // arrow through this same gate. A projectile hit is not a swing, so it never inherits the
        // shooter's last melee charge -- upstream ToolHelper#attackEntity runs projectile hits with
        // applyCooldown = false, i.e. at full strength.
        boolean projectile = source.getDirectEntity() != null && source.getDirectEntity() != causing;
        if (projectile) {
            // #416: the gate above ran on the snapshot -- the launcher as it was when it fired, which
            // is what decides whether the shot was legal. What the seams get is the live stack.
            weapon = liveLauncher(attacker, weapon);
        }
        return new CombatHit(level, weapon, attacker, target, source,
                projectile ? 1.0F : attackStrengthScale(attacker), projectile ? 1.0F : critMultiplier(attacker));
    }

    /**
     * The launcher a projectile hit is resolved against (issue #416). The stack 1.21 hands back
     * through {@code DamageSource#getWeaponItem} is a <b>copy</b> of the bow taken at fire time
     * ({@code AbstractArrow#firedFromWeapon}, copied in the constructor), so a seam that writes tool
     * state -- shocking's charge, luck's growth, any future one -- wrote to that copy and left the
     * real bow untouched: an electrum bow at full charge discharged Speed VI on every arrow hit and
     * never spent the charge. Resolving the shooter's own stack here, once, keeps that a property of
     * the pipeline instead of something each state-writing trait has to remember to ask about.
     *
     * <p>Identity is the tool item plus its {@code TOOL_MATERIALS}, deliberately not the whole
     * component set: the snapshot and the live bow differ in exactly the state this exists to write
     * (charge, durability, glint). Two identically-built bows, one per hand, are indistinguishable by
     * that identity and the main hand wins -- they carry the same traits and stats, so which of them
     * banks the charge is not a difference a player can observe.
     *
     * <p>ponytail: hands only, no inventory sweep. A shooter who stowed the launcher between firing
     * and impact gets the snapshot, so the read-only hit effects (fiery, smite, knockback) still land
     * -- upstream 1.12 gives a launcher's traits no arrow at all, so the snapshot is already past
     * parity -- while the state write dies with the arrow. The ceiling: a player who unequips a fully
     * charged bow from both hands after every shot still gets one discharge per arrow in flight.
     * Closing that means teaching the pipeline which seams write state; worth the machinery when a
     * second state-writing trait can reach a launcher.
     */
    private static ItemStack liveLauncher(@Nullable LivingEntity shooter, ItemStack snapshot) {
        if (shooter == null) {
            return snapshot;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = shooter.getItemInHand(hand);
            if (held.getItem() == snapshot.getItem()
                    && Objects.equals(held.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()),
                            snapshot.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()))) {
                return held;
            }
        }
        return snapshot;
    }

    /**
     * How charged the swing that produced this blow was -- see {@link CombatHit#attackStrengthScale}.
     *
     * <p>This cannot be read at hook time: {@code Player#attack} calls {@code
     * resetAttackStrengthTicker()} <em>before</em> {@code target.hurt(...)}, so by the time any of
     * the three hooks runs the player's own attack-strength scale has already been zeroed for the
     * next swing. NeoForge's {@link AttackEntityEvent} fires at the very top of {@code Player#attack},
     * before that reset, which is the last moment the real value exists -- so {@link #onPlayerAttack}
     * records it there and this reads it back.
     *
     * <p>ponytail: one remembered attacker, not a map. The event and the damage it leads to happen
     * back-to-back on the server thread within a single {@code Player#attack} call, so a second
     * attacker can never interleave between them; matching on identity is only there so an unrelated
     * later blow (a mob's, a dispenser's) doesn't inherit a stale number and instead falls back to
     * the "not a player swing" default.
     */
    private static float attackStrengthScale(@Nullable LivingEntity attacker) {
        return attacker != null && attacker == lastAttacker ? lastAttackStrengthScale : 1.0F;
    }

    @Nullable
    private static Entity lastAttacker;
    private static float lastAttackStrengthScale = 1.0F;

    /** Registered on the game event bus in {@code Forgeweave}. See {@link #attackStrengthScale}. */
    public static void onPlayerAttack(AttackEntityEvent event) {
        lastAttacker = event.getEntity();
        lastAttackStrengthScale = event.getEntity().getAttackStrengthScale(0.5F);
        // A new swing: whatever crit the previous one rolled is not this one's.
        lastCritAttacker = null;
    }

    /**
     * The crit multiplier of the swing in flight -- see {@link CombatHit#critMultiplier}. Same shape
     * as {@link #attackStrengthScale}: {@code Player#attack} fires {@link CriticalHitEvent} after
     * {@link AttackEntityEvent} and before {@code target.hurt(...)}, on the server thread, so the
     * remembered value is always the blow about to land.
     */
    private static float critMultiplier(@Nullable LivingEntity attacker) {
        return attacker != null && attacker == lastCritAttacker ? lastCritMultiplier : 1.0F;
    }

    @Nullable
    private static Entity lastCritAttacker;
    private static float lastCritMultiplier = 1.0F;

    /** Registered on the game event bus in {@code Forgeweave}. See {@link #critMultiplier}. */
    public static void onCriticalHit(CriticalHitEvent event) {
        lastCritAttacker = event.getEntity();
        lastCritMultiplier = event.isCriticalHit() && event.getDamageMultiplier() > 0.0F ? event.getDamageMultiplier() : 1.0F;
    }

    /**
     * The blows the defender's own tools get a say in -- one per Forgeweave tool <em>held in either
     * hand</em>, main hand first, empty when there is none. Same gate as {@link #hitOf}: server side,
     * a Forgeweave tool, not Broken.
     *
     * <p>Issue #155 shipped only the tool being actively used; issue #229 added the merely-held tool
     * because upstream 1.12 runs {@code ITrait#onPlayerHurt} for one (spiky's half-strength thorns,
     * flammable's retaliation fire). Issue #460 finished the port against
     * {@code TraitEvents#playerBlockOrHurtEvent}, which had been read wrong in two ways: it collects
     * <em>every</em> tool in {@code getHeldEquipment()} rather than just the main hand, and it decides
     * block-versus-hurt with {@code player.isActiveItemStackBlocking()} -- a question about the
     * player, not about the tool. So:
     *
     * <ul>
     *   <li>a raised vanilla shield (or a battlesign in the other hand) makes every held Forgeweave
     *       tool block, which it previously did not;
     *   <li>a charging longsword no longer counts as a block: upstream's gate is the BLOCK use
     *       animation and the longsword's is BOW ({@code LongSword#getItemUseAction}), same as the
     *       frypan's and every bow's.
     * </ul>
     *
     * <p>The animation is read straight off the active stack rather than through
     * {@code LivingEntity#isBlocking()}, which additionally demands five ticks held -- upstream's
     * {@code isActiveItemStackBlocking} has no warm-up, and a warm-up here would silently make the
     * first quarter-second of a raised sign not a block at all ({@link ForgeweaveInnates.Deflect}).
     */
    private static List<CombatDefense> defenses(DamageSource source, LivingEntity defender) {
        if (!(defender.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Entity causing = source.getEntity();
        LivingEntity attacker = causing instanceof LivingEntity living ? living : null;
        ItemStack used = defender.isUsingItem() ? defender.getUseItem() : ItemStack.EMPTY;
        boolean blocking = used.getUseAnimation() == UseAnim.BLOCK;
        List<CombatDefense> defenses = new ArrayList<>(2);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = defender.getItemInHand(hand);
            if (held.getItem() instanceof ToolItem && !ToolItem.isBroken(held)) {
                defenses.add(new CombatDefense(level, held, defender, attacker, source,
                        held == used, blocking));
            }
        }
        return defenses;
    }

    private CombatSeams() {}
}
