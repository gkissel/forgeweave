package dev.gkissel.forgeweave.tool;

/**
 * How much durability one repair item restores at the Tool Station. Ported from upstream 1.12's
 * {@code TinkersItem#calculateRepairAmount} + {@code #calculateRepair}
 * (tinkers-1.12 {@code library/tinkering/TinkersItem.java}, pinned commit in NOTICE.md).
 *
 * <p>Upstream's two methods collapse for Forgeweave's tools, which have exactly one repairable part
 * (the head):
 *
 * <pre>
 * calculateRepairAmount: amount = sum over repair parts of
 *                                 headStats.durability * match.amount * repairModifier(part) / 144
 *                        amount *= 1 + (materialsMatched - 1) / 9
 * calculateRepair:       increase = amount * min(10, actualDurability / baseDurability)
 *                        increase = max(increase, actualDurability / 64)
 *                        increase *= modifierPenalty      // 1.00 / 0.95 / 0.90 / 0.85
 *                        increase *= max(0.5, (100 - repairCount / 2) / 100)
 *                        return ceil(increase)
 * </pre>
 *
 * <ul>
 *   <li>{@code match.amount / 144}: upstream measures repair items in material units where one
 *       ingot-equivalent is 144 (e.g. one plank, one cobblestone). Forgeweave's {@code repair_item}
 *       is a plain {@code Ingredient} with no unit system, so one matching item is one
 *       ingot-equivalent and the ratio is exactly 1 -- a repair item is worth the head material's
 *       full head durability, same as upstream.
 *   <li>{@code materialsMatched}: how many <em>distinct</em> materials the repair spent this round
 *       ({@link #repairAmount}). A tool with several repair parts built from several materials pays
 *       one item of each and gets an extra {@code 1/9} per material past the first, exactly as
 *       upstream -- see {@code ToolConstants.Entry#repairSlots()} for which slots those are and what
 *       each one's {@code repairModifier} is (issue #462).
 *   <li>{@code actualDurability / baseDurability}: Modifiers (M2, docs/SCOPE.md) that grow the
 *       durability pool -- Diamond, Emerald -- make {@code actualDurability} outgrow the material's
 *       untouched {@code baseDurability}, and the repair scales up with it so the same modifier can
 *       never punish the player with a proportionally smaller heal.
 *   <li>{@code modifierPenalty}: each occupied (non-embossment) modifier slot on the tool makes every
 *       repair item worth a little less -- flat per upstream, not per modifier's own effect.
 * </ul>
 */
public final class ToolRepair {

    /** Upstream {@code TinkersItem#calculateRepair}'s {@code min(10f, durabilityFactor)} cap. */
    private static final float MAX_DURABILITY_FACTOR = 10.0F;

    /** Upstream {@code TinkersItem#calculateRepair}'s modifier-count repair penalty table. */
    private static final float[] MODIFIER_PENALTY = {1.00F, 0.95F, 0.90F, 0.85F};

    /**
     * Upstream {@code TinkersItem#calculateRepairAmount}: what one round of repair items is worth
     * before the tool's own durability pool, modifier count and repair count are folded in.
     *
     * @param weightedHeadDurability the sum, over every repair part paid this round, of that part's
     *     material head durability times the part's {@code repairModifier}
     * @param materialsMatched how many distinct materials were paid this round -- each one past the
     *     first adds {@code 1/9} to the total
     */
    public static int repairAmount(float weightedHeadDurability, int materialsMatched) {
        return (int) (weightedHeadDurability * (1f + (materialsMatched - 1) / 9f));
    }

    /**
     * Durability restored by one round of repair items.
     *
     * @param amount {@link #repairAmount}'s result -- for a one-part repair at factor {@code 1},
     *     simply the head {@code Material}'s {@code head.durability} stat
     * @param baseDurability the tool's untouched materials-derived durability pool ({@code
     *     forgeweave:tool_stats}'s {@code ToolStats.Stats#durability}, upstream's {@code origDur})
     * @param actualDurability the tool's current, possibly modifier-grown durability pool ({@code
     *     ItemStack#getMaxDamage()}, upstream's {@code actualDur})
     * @param repairCount how many times this tool has already been repaired
     * @param occupiedModifierSlots how many of the tool's modifier slots are occupied ({@link
     *     dev.gkissel.forgeweave.modifier.ForgeweaveModifiers#occupiedSlots}), embossments excluded
     */
    public static int repairIncrement(
            int amount, int baseDurability, int actualDurability, int repairCount, int occupiedModifierSlots) {
        float durabilityFactor = Math.min(MAX_DURABILITY_FACTOR, actualDurability / (float) baseDurability);
        float increase = amount * durabilityFactor;
        increase = Math.max(increase, actualDurability / 64f);
        increase *= MODIFIER_PENALTY[Math.min(occupiedModifierSlots, MODIFIER_PENALTY.length - 1)];
        // Integer division on repairCount is upstream's, not a typo: two repairs cost one percent.
        float diminishingReturns = Math.max(0.5f, (100 - repairCount / 2) / 100f);
        return (int) Math.ceil(increase * diminishingReturns);
    }

    private ToolRepair() {}
}
