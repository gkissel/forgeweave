package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.api.trait.Trait;

/**
 * A shot from this launcher strays less, once it is drawn far enough (issue #1114's
 * {@code shot_accuracy}).
 *
 * <p>The bowstring's own side of a bow. A string carries no head stats and no armour, so before this
 * the only thing a bowstring material could grant was a draw-speed bonus, and putting one on
 * {@code string} would have moved the 1.12 bow stats every parity GameTest pins. Spread is the other
 * number a string plausibly owns and nothing else reads it, so it is free to set.
 *
 * <p>{@link #minimumDraw} is what gives it character: at {@code 1} the bonus is a reward for holding
 * the shot to full draw rather than a flat accuracy upgrade, and a snap shot gets nothing.
 *
 * @param factor what the shot's spread is multiplied by while the draw is deep enough; below 1 for
 *     a tighter shot
 * @param minimumDraw the draw, 0 to 1, the bonus starts at; {@code 1} for a fully drawn shot only
 */
public record ShotAccuracy(float factor, float minimumDraw) implements Trait {

    @Override
    public float shotInaccuracyFactor(float drawProgress) {
        return drawProgress >= minimumDraw ? factor : 1.0F;
    }
}
