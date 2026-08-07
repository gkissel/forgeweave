package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link ToolRepair#repairIncrement} against upstream 1.12's
 * {@code TinkersItem#calculateRepairAmount} + {@code #calculateRepair} (see {@link ToolRepair}'s
 * javadoc for how those two collapse for M1 tools). Numbers use the shipped stone material
 * (head durability 120) on the stone/wood/wood pickaxe the Tool Station GameTests build
 * (durability pool 160).
 */
class ToolRepairTest {

    @Test
    void oneRepairItemIsWorthTheHeadMaterialsDurability() {
        assertEquals(120, ToolRepair.repairIncrement(120, 160, 0));
    }

    @Test
    void repairIsAtLeastOneSixtyFourthOfTheDurabilityPool() {
        // A tiny head material on a huge durability pool: the flat head value loses to the floor.
        assertEquals(157, ToolRepair.repairIncrement(1, 10_000, 0), "expected ceil(10000 / 64)");
    }

    @Test
    void repairsLoseOnePercentageEveryTwoRepairs() {
        // (100 - repairCount / 2) / 100, integer division on repairCount: 3 -> 0.99, 10 -> 0.95.
        assertEquals(120, ToolRepair.repairIncrement(120, 160, 1));
        assertEquals(119, ToolRepair.repairIncrement(120, 160, 3));
        assertEquals(114, ToolRepair.repairIncrement(120, 160, 10));
    }

    @Test
    void diminishingReturnsBottomOutAtHalf() {
        assertEquals(60, ToolRepair.repairIncrement(120, 160, 100));
        assertEquals(60, ToolRepair.repairIncrement(120, 160, 10_000),
                "repeated repairs must never fall below half value");
    }
}
