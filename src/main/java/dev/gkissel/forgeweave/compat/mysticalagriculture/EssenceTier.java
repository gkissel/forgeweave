package dev.gkissel.forgeweave.compat.mysticalagriculture;

import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Mystical Agriculture's essence ladder, named here as a Forgeweave enum rather than borrowed from
 * the mod (issue #999, docs/SCOPE.md D-M8-20). Holding the ladder as our own type is what lets the
 * crop roster, the tier map and the augment slot count be plain unit- and GameTest-reachable code:
 * {@code runGameTestServer} has no Mystical Agriculture on its classpath, so a class that named
 * {@code com.blakebr0} could not be loaded there at all. {@link MysticalAgricultureCompat} is the one
 * place that turns a value of this enum into the mod's own tier object.
 *
 * <p><b>The tier map.</b> A crop's essence tier follows the mining level of the material it grows,
 * which makes {@link #forOre} a total function over {@link TrackBOre.Tier} written as an exhaustive
 * switch with no default branch. That is deliberate: adding a rung to the mining ladder has to fail
 * the build here rather than silently land on prudentium.
 *
 * <p>The issue's table pairs six mining rungs with five essences, so one essence covers two rungs and
 * one pairing is a judgment call. Both are settled here:
 *
 * <ul>
 *   <li>{@code hardcinder} and {@code warspar} share <b>supremium</b>, and <b>resonite</b> -- the top
 *       rung -- takes <b>awakened supremium</b>. The issue lists resonite in the supremium row and
 *       again as "the top rung"; giving the top rung the top essence is the reading that keeps the
 *       ladder strictly increasing, and a resonite crop is the last thing a player unlocks either way.
 *   <li>{@link TrackBOre.Tier#STONE} takes <b>prudentium</b>, the issue's lowest row. Forgeweave's
 *       mining ladder has no iron rung, so {@code STONE} is the rung below the table's own lowest.
 *       No shipped ore sits there today (#884 retired cinderstone, the only stone-tier entry), so the
 *       branch is unreachable in practice and exists to keep the function total.
 * </ul>
 *
 * <p><b>Inferium is deliberately unused.</b> It is Mystical Agriculture's own starter essence, which
 * its own crops produce; no Forgeweave material is cheap enough to sit there.
 */
public enum EssenceTier {

    /** Mystical Agriculture's starter tier. No Forgeweave crop uses it -- see the class javadoc. */
    INFERIUM("inferium"),
    PRUDENTIUM("prudentium"),
    TERTIUM("tertium"),
    IMPERIUM("imperium"),
    SUPREMIUM("supremium"),
    AWAKENED_SUPREMIUM("awakened_supremium");

    /** The path of Mystical Agriculture's own registry id for this tier. */
    private final String id;

    EssenceTier(String id) {
        this.id = id;
    }

    /** The path of Mystical Agriculture's own tier id, e.g. {@code awakened_supremium}. */
    public String id() {
        return id;
    }

    /**
     * How many augment slots a piece of gear built from this tier's essence metal gets: one, or two
     * for awakened supremium. Derived from the tier rather than stored per item, which is what keeps a
     * new tool shape from needing a slot count of its own.
     */
    public int augmentSlots() {
        return this == AWAKENED_SUPREMIUM ? 2 : 1;
    }

    /**
     * The essence tier a crop for a material at {@code tier} produces. Exhaustive by construction --
     * see the class javadoc for why there is no default branch and how the two overlapping rows of the
     * issue's table were settled.
     */
    public static EssenceTier forOre(TrackBOre.Tier tier) {
        return switch (tier) {
            case STONE -> PRUDENTIUM;
            case DIAMOND -> TERTIUM;
            case NETHERITE -> IMPERIUM;
            case HARDCINDER, WARSPAR -> SUPREMIUM;
            case RESONITE -> AWAKENED_SUPREMIUM;
        };
    }
}
