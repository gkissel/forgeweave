package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.api.trait.Trait;

/**
 * Firing this arrow sometimes does not spend it (issue #1114's {@code save_ammo}).
 *
 * <p>The other fletching-side effect, and a discrete event rather than a standing bonus: the roll
 * either happens or it does not, so {@code BowItem#consumeAmmo} announces a save through
 * {@link TraitFeedback} the way every other proc in the mod does since issue #1115. That is what
 * makes a 25% chance legible -- a quarter of shots costing nothing is invisible if nothing says so
 * and obvious if something does.
 *
 * @param chance 0..1 probability the shot is free, rolled once per arrow fired
 */
public record SaveAmmo(float chance) implements Trait {

    @Override
    public float ammoSaveChance() {
        return chance;
    }
}
