package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.client.BowDrawMovement.Impulse;

/**
 * Issue #420: diagonal bow-draw movement was up to ~41% faster than straight, because the
 * {@code (forward, strafe)} pair was scaled per-axis with no regard for the diagonal case's extra
 * {@code sqrt(2)} length. {@link BowDrawMovement.Impulse#normalize} is the pure fix -- pinned here
 * without touching the event/{@code LocalPlayer} plumbing around it.
 */
class BowDrawMovementTest {

    @Test
    void singleAxisIsUnchanged() {
        Impulse result = Impulse.normalize(1.0F, 0.0F);
        assertEquals(1.0F, result.forward(), 0.0F);
        assertEquals(0.0F, result.strafe(), 0.0F);
    }

    @Test
    void diagonalResultingSpeedMatchesStraight() {
        float multiplier = 2.5F;
        Impulse straight = Impulse.normalize(1.0F, 0.0F);
        Impulse diagonal = Impulse.normalize(1.0F, 1.0F);

        double straightSpeed = Math.hypot(straight.forward() * multiplier, straight.strafe() * multiplier);
        double diagonalSpeed = Math.hypot(diagonal.forward() * multiplier, diagonal.strafe() * multiplier);

        assertEquals(straightSpeed, diagonalSpeed, 1.0e-6, "diagonal must not outrun straight (#420)");
    }

    @Test
    void diagonalNormalizesToEqualUnitAxes() {
        Impulse result = Impulse.normalize(1.0F, 1.0F);
        float expected = (float) (1.0 / Math.sqrt(2.0));
        assertEquals(expected, result.forward(), 1.0e-6F);
        assertEquals(expected, result.strafe(), 1.0e-6F);
    }

    @Test
    void signIsPreserved() {
        Impulse result = Impulse.normalize(-1.0F, 1.0F);
        assertEquals(-1.0, Math.signum(result.forward()), 0.0);
        assertEquals(1.0, Math.signum(result.strafe()), 0.0);
    }

    @Test
    void zeroInputIsUntouched() {
        Impulse result = Impulse.normalize(0.0F, 0.0F);
        assertEquals(0.0F, result.forward(), 0.0F);
        assertEquals(0.0F, result.strafe(), 0.0F);
    }
}
