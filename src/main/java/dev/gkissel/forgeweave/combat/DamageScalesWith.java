package dev.gkissel.forgeweave.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A hit's bonus damage is {@code coefficient} times some quantity of the blow, clamped to
 * {@code [-cap, cap]} -- ADR-0004's M6 damage-scaling library batch (issue #827) collapsing the
 * reference addons' half-dozen "damage depends on X" trait classes into the one parameterized
 * shape {@link Source} picks the quantity for.
 *
 * <p>{@link Source#REMAINING_DURABILITY} and {@link Source#WIELDER_HEALTH} read a {@code 0..1}
 * fraction; {@link Source#TARGET_MISSING_HEALTH} and {@link Source#TARGET_MAX_HEALTH} read a flat
 * health number; {@link Source#IMPACT_VELOCITY} reads the attacker's current movement speed in
 * blocks/tick. A quantity that needs the attacker (wielder health, impact velocity) reads zero
 * when the blow has none -- a projectile or a mob's own attack ({@link CombatHit#attacker}).
 *
 * <p><b>{@code jagged} was not migrated here</b> despite scaling off the same remaining-durability
 * idea ({@code ForgeweaveTraits#JAGGED}, upstream {@code TraitJagged}): its formula is the "old
 * tcon" log curve ({@code ForgeweaveTraits#wearCurve}), not a linear coefficient, and forcing it
 * into {@code coefficient * fraction} shape would change its numbers rather than merely re-express
 * them. Left as its own bespoke implementation; recorded on issue #827.
 *
 * @param source which quantity of the blow the bonus scales with
 * @param coefficient bonus damage per unit of {@code source}'s quantity, either sign
 * @param cap the largest magnitude (either direction) the bonus may reach; always non-negative
 */
public record DamageScalesWith(Source source, float coefficient, float cap) implements CombatSeam {

    /** What {@link DamageScalesWith} reads its scaling quantity from. */
    public enum Source {
        /** The weapon's own remaining durability, as a {@code 0..1} fraction of its max. */
        REMAINING_DURABILITY,
        /** The attacker's current health, as a {@code 0..1} fraction of their max. */
        WIELDER_HEALTH,
        /** How much health the target has already lost: {@code getMaxHealth() - getHealth()}. */
        TARGET_MISSING_HEALTH,
        /** The target's max health. */
        TARGET_MAX_HEALTH,
        /** The attacker's current movement speed, blocks/tick, at the moment the blow lands. */
        IMPACT_VELOCITY
    }

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return damage + Mth.clamp(coefficient * quantity(hit), -cap, cap);
    }

    private float quantity(CombatHit hit) {
        LivingEntity attacker = hit.attacker();
        return switch (source) {
            case REMAINING_DURABILITY -> remainingDurabilityFraction(hit.weapon());
            case WIELDER_HEALTH -> attacker == null ? 0.0F : attacker.getHealth() / attacker.getMaxHealth();
            case TARGET_MISSING_HEALTH -> hit.target().getMaxHealth() - hit.target().getHealth();
            case TARGET_MAX_HEALTH -> hit.target().getMaxHealth();
            case IMPACT_VELOCITY -> attacker == null ? 0.0F : (float) attacker.getDeltaMovement().length();
        };
    }

    private static float remainingDurabilityFraction(ItemStack weapon) {
        int max = weapon.getMaxDamage();
        return max <= 0 ? 0.0F : 1.0F - (float) weapon.getDamageValue() / max;
    }
}
