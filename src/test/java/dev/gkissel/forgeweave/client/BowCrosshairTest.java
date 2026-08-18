package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.client.BowCrosshair.Style;
import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #484 (parity audit T53): upstream 1.12 replaces the vanilla crosshair while a Forgeweave
 * launcher is in hand -- a four-cornered SQUARE for bows, a three-tipped T for the crossbow, both
 * spreading outward as the draw runs down. Pinned here are the pure halves of
 * {@link BowCrosshair}: which style an item asks for, the charge each launcher reports, the spread
 * that charge produces, and the part placement the spread feeds.
 *
 * <p>Upstream sources (commit {@code c01173c0408352c50a2e8c5017552323ce42f5b4}):
 * {@code library/client/crosshair/Crosshair.java} (spread and the four square parts),
 * {@code CrosshairInverseT.java} (the three tips), {@code Crosshairs.java} (SQUARE/T),
 * {@code tools/ranged/item/ShortBow.java:122-131} and {@code CrossBow.java:206-221}
 * ({@code getCrosshair}/{@code getCrosshairState}).
 */
class BowCrosshairTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack crossbow(boolean loaded) {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_CROSSBOW.get());
        if (loaded) {
            CrossbowItem.setLoaded(stack, true);
        }
        return stack;
    }

    // ---- which crosshair (Crosshairs.SQUARE / Crosshairs.T) -------------------------------------

    /** {@code ShortBow#getCrosshair} and {@code LongBow} (inherited): {@code Crosshairs.SQUARE}. */
    @Test
    void bowsGetTheSquareCrosshair() {
        assertSame(Style.SQUARE, BowCrosshair.styleFor(new ItemStack(ForgeweaveItems.TOOL_SHORTBOW.get())));
        assertSame(Style.SQUARE, BowCrosshair.styleFor(new ItemStack(ForgeweaveItems.TOOL_LONGBOW.get())));
    }

    /** {@code CrossBow#getCrosshair}: {@code Crosshairs.T}. */
    @Test
    void theCrossbowGetsTheTCrosshair() {
        assertSame(Style.T, BowCrosshair.styleFor(crossbow(false)));
        assertSame(Style.T, BowCrosshair.styleFor(crossbow(true)));
    }

    /**
     * {@code ICustomCrosshairUser} is implemented by the two launchers and nothing else upstream, so
     * every other tool -- and an empty hand -- keeps the vanilla crosshair.
     */
    @Test
    void everythingElseKeepsTheVanillaCrosshair() {
        assertNull(BowCrosshair.styleFor(new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get())));
        assertNull(BowCrosshair.styleFor(ItemStack.EMPTY));
    }

    // ---- charge (getCrosshairState) -------------------------------------------------------------

    /** {@code ShortBow#getCrosshairState}: the draw progress, straight through. */
    @Test
    void aBowReportsItsDrawProgress() {
        ItemStack bow = new ItemStack(ForgeweaveItems.TOOL_SHORTBOW.get());
        assertEquals(0.0F, BowCrosshair.charge(bow, false, 0.0F), 0.0F);
        assertEquals(0.4F, BowCrosshair.charge(bow, true, 0.4F), 1.0e-6F);
    }

    /** {@code CrossBow#getCrosshairState} branch 1: a loaded crossbow is always fully accurate. */
    @Test
    void aLoadedCrossbowIsAlwaysFullyCharged() {
        assertEquals(1.0F, BowCrosshair.charge(crossbow(true), false, 0.0F), 0.0F);
        assertEquals(1.0F, BowCrosshair.charge(crossbow(true), true, 0.2F), 0.0F);
    }

    /** Branch 2: an unloaded crossbow that is not the item being used sits at zero. */
    @Test
    void anIdleUnloadedCrossbowIsUncharged() {
        assertEquals(0.0F, BowCrosshair.charge(crossbow(false), false, 0.9F), 0.0F);
    }

    /** Branch 3: mid-crank, it is the draw progress like any bow. */
    @Test
    void aCrankingCrossbowReportsItsDrawProgress() {
        assertEquals(0.35F, BowCrosshair.charge(crossbow(false), true, 0.35F), 1.0e-6F);
    }

    // ---- spread (Crosshair#render) --------------------------------------------------------------

    /** {@code Crosshair#render}: {@code spread = (1 - charge) * 25}. */
    @Test
    void spreadRunsFromTwentyFiveToZero() {
        assertEquals(25.0F, BowCrosshair.spread(0.0F), 0.0F);
        assertEquals(12.5F, BowCrosshair.spread(0.5F), 1.0e-6F);
        assertEquals(0.0F, BowCrosshair.spread(1.0F), 0.0F);
    }

    /** Charge is a progress value; upstream never feeds it out of range and neither do we. */
    @Test
    void spreadIsClampedToTheChargeRange() {
        assertEquals(25.0F, BowCrosshair.spread(-1.0F), 0.0F);
        assertEquals(0.0F, BowCrosshair.spread(2.0F), 0.0F);
    }

    // ---- part placement -------------------------------------------------------------------------

    /**
     * {@code Crosshair#drawCrosshair} + {@code drawSquareCrosshairPart}: at full charge the four
     * 8x8 quarters tile back into one 16x16 crosshair centred on the screen. Order is upstream's:
     * top-left, top-right, bottom-left, bottom-right.
     */
    @Test
    void theSquarePartsTileAtFullCharge() {
        assertArrayEquals(new float[] {92.0F, 42.0F, 100.0F, 50.0F}, BowCrosshair.squarePart(0, 100.0F, 50.0F, 0.0F), 1.0e-4F);
        assertArrayEquals(new float[] {100.0F, 42.0F, 108.0F, 50.0F}, BowCrosshair.squarePart(1, 100.0F, 50.0F, 0.0F), 1.0e-4F);
        assertArrayEquals(new float[] {92.0F, 50.0F, 100.0F, 58.0F}, BowCrosshair.squarePart(2, 100.0F, 50.0F, 0.0F), 1.0e-4F);
        assertArrayEquals(new float[] {100.0F, 50.0F, 108.0F, 58.0F}, BowCrosshair.squarePart(3, 100.0F, 50.0F, 0.0F), 1.0e-4F);
    }

    /** Spread pushes each quarter diagonally outward by exactly the spread, in its own direction. */
    @Test
    void theSquarePartsSpreadDiagonallyOutward() {
        float spread = 25.0F;
        assertArrayEquals(new float[] {67.0F, 17.0F, 75.0F, 25.0F}, BowCrosshair.squarePart(0, 100.0F, 50.0F, spread), 1.0e-4F);
        assertArrayEquals(new float[] {125.0F, 75.0F, 133.0F, 83.0F}, BowCrosshair.squarePart(3, 100.0F, 50.0F, spread), 1.0e-4F);
    }

    /**
     * {@code CrosshairInverseT#drawCrosshair}: the tips sit at up / left / right of centre, each
     * pushed out by the spread. Upstream asks for a fourth (bottom) tip and then draws nothing for
     * it -- {@code drawTipCrosshairPart} has no {@code part == 3} branch -- so there are three.
     */
    @Test
    void theTipsSitAboveAndEitherSideOfCentre() {
        float spread = 20.0F;
        assertArrayEquals(new float[] {100.0F, 30.0F}, BowCrosshair.tipCenter(0, 100.0F, 50.0F, spread), 1.0e-4F);
        assertArrayEquals(new float[] {80.0F, 50.0F}, BowCrosshair.tipCenter(1, 100.0F, 50.0F, spread), 1.0e-4F);
        assertArrayEquals(new float[] {120.0F, 50.0F}, BowCrosshair.tipCenter(2, 100.0F, 50.0F, spread), 1.0e-4F);
    }

    /** At full charge the three tips converge on the screen centre. */
    @Test
    void theTipsConvergeAtFullCharge() {
        for (int part = 0; part < 3; part++) {
            assertArrayEquals(new float[] {100.0F, 50.0F}, BowCrosshair.tipCenter(part, 100.0F, 50.0F, 0.0F), 1.0e-4F);
        }
    }
}
