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
    INFERIUM("inferium", 1),
    PRUDENTIUM("prudentium", 2),
    TERTIUM("tertium", 3),
    IMPERIUM("imperium", 4),
    SUPREMIUM("supremium", 5),
    /**
     * Value 5, not 6, and that is Mystical Agriculture's own shape rather than a rounding here:
     * {@code awakened_supremium_sword} is registered at tinkerable tier 5 with two augment slots,
     * differing from {@code supremium_sword} only in the slot count, and Mystical Agriculture
     * registers no sixth crop tier and no awakened farmland (verified against 8.0.28, see the PR).
     * So an awakened crop grows on supremium farmland and awakened gear takes every augment tier
     * supremium gear does, plus a second slot.
     */
    AWAKENED_SUPREMIUM("awakened_supremium", 5),
    /**
     * Mystical Agradditions' rung above awakened supremium. Never returned by {@link #forOre}: it
     * exists for the augment side alone, where it continues awakened's two slots rather than dropping
     * back to one. Value 5 for the same reason awakened's is -- Mystical Agradditions ships a crop
     * tier 6 but no tier-6 augments and no tier-6 gear, so 5 is already the ceiling.
     */
    INSANIUM("insanium", 5);

    /** The path of Mystical Agriculture's own registry id for this tier. */
    private final String id;

    /** Mystical Agriculture's own 1-to-5 numbering, which it uses for crop tiers and augments alike. */
    private final int value;

    EssenceTier(String id, int value) {
        this.id = id;
        this.value = value;
    }

    /** The path of Mystical Agriculture's own tier id, e.g. {@code awakened_supremium}. */
    public String id() {
        return id;
    }

    /**
     * Mystical Agriculture's own tier number, 1 to 5. It is both the crop tier this essence grows at
     * ({@code CropTier.ONE} through {@code FIVE}) and the tinkerable tier that gates which augments
     * may be installed, because Mystical Agriculture numbers the two on the same scale.
     */
    public int value() {
        return value;
    }

    /**
     * How many augment slots a piece of gear built from this tier's essence metal gets: one, or two
     * for awakened supremium. Derived from the tier rather than stored per item, which is what keeps a
     * new tool shape from needing a slot count of its own.
     */
    public int augmentSlots() {
        return ordinal() >= AWAKENED_SUPREMIUM.ordinal() ? 2 : 1;
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
