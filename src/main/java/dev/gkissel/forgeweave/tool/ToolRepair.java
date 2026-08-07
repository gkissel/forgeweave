package dev.gkissel.forgeweave.tool;

/**
 * How much durability one repair item restores at the Tool Station. Ported from upstream 1.12's
 * {@code TinkersItem#calculateRepairAmount} + {@code #calculateRepair}
 * (tinkers-1.12 {@code library/tinkering/TinkersItem.java}, pinned commit in NOTICE.md).
 *
 * <p>Upstream's two methods collapse for Forgeweave's M1 tools:
 *
 * <pre>
 * calculateRepairAmount: durability += headStats.durability * match.amount / 144
 *                        durability *= 1 + (materialsMatched - 1) / 9
 * calculateRepair:       increase = amount * min(10, actualDurability / originalDurability)
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
 *   <li>{@code materialsMatched}: a Forgeweave tool has exactly one repairable part (the head), so
 *       the multi-material bonus is always {@code 1 + 0/9 = 1}.
 *   <li>{@code actualDurability / originalDurability}: only Modifiers (M2, docs/SCOPE.md) change a
 *       tool's durability after assembly, so the factor is 1 here.
 *   <li>{@code modifierPenalty}: no Modifiers in M1, so no penalty.
 * </ul>
 *
 * <p>What survives is the repair-value-from-head-durability term, the "at least 1/64 of the tool"
 * floor, and the diminishing returns on repeated repairs.
 */
public final class ToolRepair {

    /**
     * Durability restored by a single repair item.
     *
     * @param headDurability the head {@code Material}'s {@code head.durability} stat
     * @param maxDurability the tool's total durability pool ({@code ToolStats.Stats#durability})
     * @param repairCount how many times this tool has already been repaired
     */
    public static int repairIncrement(int headDurability, int maxDurability, int repairCount) {
        float increase = Math.max(headDurability, maxDurability / 64f);
        // Integer division on repairCount is upstream's, not a typo: two repairs cost one percent.
        float diminishingReturns = Math.max(0.5f, (100 - repairCount / 2) / 100f);
        return (int) Math.ceil(increase * diminishingReturns);
    }

    private ToolRepair() {}
}
