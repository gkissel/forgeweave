package dev.gkissel.forgeweave.combat;

/**
 * Upstream's per-tool knockback multiplier (issue #465/T34, upstream {@code ToolCore#knockback()}):
 * scales the flat push vanilla applies to every landed hit by a flat factor, the way the pinned 1.12
 * clone's hatchet ({@code 1.3f}), mattock ({@code 1.1f}), lumber axe ({@code 1.5f}) and rapier
 * ({@code 0.6f}) each override {@code ToolCore}'s {@code 1.0f} default. See {@link
 * CombatSeam#knockback} for exactly which push this scales and which it deliberately does not.
 *
 * @param multiplier the factor vanilla's flat per-hit knockback is scaled by
 */
public record KnockbackMultiplierSeam(float multiplier) implements CombatSeam {

    @Override
    public float knockback(CombatHit hit, float knockback) {
        return knockback * multiplier;
    }
}
