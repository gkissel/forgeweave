package dev.gkissel.forgeweave.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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
 * </table>
 *
 * <p>Each of those three vanilla/NeoForge events fires exactly once per damage instance
 * ({@code LivingEntity#actuallyHurt} posts the damage events once and only for a target that was
 * not invulnerable; {@code LivingEntity#hurt} calls {@code die} once afterwards), which is what
 * makes "exactly once per hit, exactly once per kill" a property of the events rather than of
 * bookkeeping here. A killing blow therefore runs pre-hit, then on-hit, then post-kill, in that
 * order.
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

    /** Registered on the game event bus in {@code Forgeweave}. See the class javadoc's table. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        CombatHit hit = hitOf(event.getSource(), event.getEntity());
        if (hit == null) {
            return;
        }
        List<CombatSeam> seams = seams(hit.weapon());
        if (seams.isEmpty()) {
            return;
        }
        float original = event.getOriginalAmount();
        float damage = event.getAmount();
        for (CombatSeam seam : seams) {
            damage = seam.preHit(hit, original, damage);
        }
        if (damage != event.getAmount()) {
            event.setAmount(damage);
        }
    }

    /** Registered on the game event bus in {@code Forgeweave}. See the class javadoc's table. */
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        CombatHit hit = hitOf(event.getSource(), event.getEntity());
        if (hit == null) {
            return;
        }
        for (CombatSeam seam : seams(hit.weapon())) {
            seam.onHit(hit, event.getNewDamage());
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
        return new CombatHit(level, weapon, causing instanceof LivingEntity living ? living : null, target, source);
    }

    private CombatSeams() {}
}
