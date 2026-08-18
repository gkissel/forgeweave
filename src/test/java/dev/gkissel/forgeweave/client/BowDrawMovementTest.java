package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.client.BowDrawMovement.Impulse;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #420: diagonal bow-draw movement was up to ~41% faster than straight, because the
 * {@code (forward, strafe)} pair was scaled per-axis with no regard for the diagonal case's extra
 * {@code sqrt(2)} length. {@link BowDrawMovement.Impulse#normalize} is the pure fix -- pinned here
 * without touching the event/{@code LocalPlayer} plumbing around it.
 *
 * <p>Parity audit T37 (issue #468): {@code LongSword}/{@code FryPan}'s own {@code preventSlowDown}
 * calls, which {@link BowDrawMovement#drawMovementSpeed(Item)} generalized the bow-only mechanism
 * to cover. {@code @BeforeAll} bootstrap and the real registered items are
 * {@link BowItemPropertiesTest}'s precedent -- a bow/tool item self-registers on construction, so a
 * standalone {@code new BowItem(...)} throws once the vanilla item registry is frozen.
 */
class BowDrawMovementTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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

    @Test
    void shortbowKeepsItsOwnDrawSpeed() {
        float speed = BowDrawMovement.drawMovementSpeed(ForgeweaveItems.TOOL_SHORTBOW.get());
        assertEquals(0.5F, speed, 0.0F);
    }

    /**
     * Upstream's own "no speedup on charging": a {@link dev.gkissel.forgeweave.item.BowItem} always
     * answers on its own account, so the longbow still gets a (harmless, no-op) vanilla 0.2 rather
     * than {@code null} -- {@code null} is reserved for an item with no draw-movement concept at all.
     */
    @Test
    void longbowCrawlsAtVanillasOwnSlowdown() {
        float speed = BowDrawMovement.drawMovementSpeed(ForgeweaveItems.TOOL_LONGBOW.get());
        assertEquals(0.2F, speed, 0.0F);
    }

    @Test
    void longswordChargedLeapDrawsAtUpstream09() {
        float speed = BowDrawMovement.drawMovementSpeed(ForgeweaveItems.TOOL_LONGSWORD.get());
        assertEquals(0.9F, speed, 0.0F);
    }

    @Test
    void fryingPanChargedLaunchDrawsAtUpstream07() {
        float speed = BowDrawMovement.drawMovementSpeed(ForgeweaveItems.TOOL_FRYING_PAN.get());
        assertEquals(0.7F, speed, 0.0F);
    }

    /** A tool with no innate at all (the M1 pickaxe) carries no draw-movement concept: {@code null}. */
    @Test
    void toolWithNoInnateHasNoOverride() {
        assertNull(BowDrawMovement.drawMovementSpeed(ForgeweaveItems.TOOL_PICKAXE.get()));
    }

    /** The dagger's {@code Backstab} innate is blow-triggered only ({@code use() == null}): {@code null}. */
    @Test
    void toolWithNoUseActionHasNoOverride() {
        assertNull(BowDrawMovement.drawMovementSpeed(ForgeweaveItems.TOOL_DAGGER.get()));
    }

    @Test
    void plainVanillaItemHasNoOverride() {
        assertNull(BowDrawMovement.drawMovementSpeed(Items.STICK));
    }
}
