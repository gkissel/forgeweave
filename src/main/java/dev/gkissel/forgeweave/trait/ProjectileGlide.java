package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.api.trait.Trait;

/**
 * An arrow fletched with this drops less on the way to the target (issue #1114's
 * {@code projectile_glide}).
 *
 * <p>The fletching's own side of a material arrow, and the same knob {@code hovering} already turns:
 * {@code ArrowEntity#getDefaultGravity} is the one number that decides how far a shot falls over its
 * flight. Air drag would be the other half of "keeps its speed", but vanilla's 0.99 is written into
 * {@code AbstractArrow#tick} with no override, and reaching it would mean a mixin -- a flatter
 * trajectory is the half of the idea that is both reachable and the half an archer actually aims
 * with.
 *
 * <p>Multiplied into whatever gravity the arrow already has rather than replacing it, so a feather
 * fletching on a blaze-rod shaft compounds with {@code hovering} instead of fighting it.
 *
 * @param gravityFactor what the arrow's gravity is multiplied by; below 1 for a flatter flight
 */
public record ProjectileGlide(float gravityFactor) implements Trait {

    @Override
    public float projectileGravityFactor() {
        return gravityFactor;
    }
}
