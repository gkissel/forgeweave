package dev.gkissel.forgeweave.block;

import java.util.List;

/**
 * The energized tank's two pieces of arithmetic (docs/SCOPE.md M8, D-M8-11, issue #972): what one
 * melt tick costs a tank, and which of several tanks on one smeltery pays for it.
 *
 * <p>Both are plain integer functions with no world and no block entity behind them, so they are
 * unit-testable on their own ({@code EnergizedHeatTest}) and the block entity keeps only the parts
 * that genuinely need a level. Every number they take is a config value read at the call site, per
 * D-M8-8: nothing numeric is fixed here.
 */
public final class EnergizedHeat {

    /**
     * What one melt tick costs a tank imitating a fuel at {@code temperature}: the issue's
     * {@code rfPerMeltTickBase x temperature / divisor}, then times {@code overdriveCost} when the
     * tank's overdrive button is pressed.
     *
     * <p>Two edges are pinned rather than left to fall out of the division. A sample with no fuel
     * temperature costs nothing, because it heats nothing -- there is no tick to pay for. And any
     * real temperature costs at least 1 FE, so a low base or a high divisor cannot make heat free;
     * free heat would quietly turn the block into an infinite fuel source, which is the one thing
     * its balance rests on not being.
     */
    public static int costPerMeltTick(int temperature, int base, int divisor, boolean overdrive, double overdriveCost) {
        if (temperature <= 0 || base <= 0 || divisor <= 0) {
            return 0;
        }
        long cost = (long) base * temperature / divisor;
        if (overdrive) {
            cost = Math.round(cost * overdriveCost);
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, cost));
    }

    /**
     * One energized tank as the selection sees it: the temperature of its fuel sample, what a melt
     * tick would cost it, and what its buffer currently holds.
     */
    public record Source(int temperature, int cost, int stored) {
        /**
         * Whether this tank can actually heat the smeltery: it has a valid sample and its buffer
         * covers a whole tick. An empty buffer means no heat at all rather than partial or slow
         * heat, which is D-M8-3's below-the-lowest-rung rule carried over unchanged.
         */
        public boolean affordable() {
            return temperature > 0 && stored >= cost;
        }
    }

    /**
     * Which tank pays, as an index into {@code sources}, or {@code -1} when none can. The hottest
     * affordable sample wins and only that tank spends anything; cooler tanks contribute nothing,
     * which is what keeps the block stackable without making it exponentially cheap.
     *
     * <p>Two cases the issue asks to pin. A hottest tank whose buffer is empty <b>falls through to
     * the next one</b> rather than zeroing the smeltery: it is not heating, so it is not the hottest
     * heat source, and a spare tank behind it is exactly what a player built it for. And a tie goes
     * to the earlier entry, which the caller orders by the smeltery scan's own wall walk, so the
     * same two tanks always resolve the same way.
     */
    public static int pick(List<Source> sources) {
        int best = -1;
        for (int i = 0; i < sources.size(); i++) {
            Source source = sources.get(i);
            if (source.affordable() && (best < 0 || source.temperature() > sources.get(best).temperature())) {
                best = i;
            }
        }
        return best;
    }

    private EnergizedHeat() {}
}
