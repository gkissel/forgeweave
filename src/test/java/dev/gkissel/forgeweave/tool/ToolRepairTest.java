package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link ToolRepair#repairIncrement} against upstream 1.12's
 * {@code TinkersItem#calculateRepairAmount} + {@code #calculateRepair} (see {@link ToolRepair}'s
 * javadoc for how those two collapse for Forgeweave's single-repairable-part tools). Numbers use
 * the shipped stone material (head durability 120) on the stone/wood/wood pickaxe the Tool Station
 * GameTests build (durability pool 160 for these numbers -- the fixtures predate the Tool Forge's
 * cheap trait retune used elsewhere).
 */
class ToolRepairTest {

    @Test
    void oneRepairItemIsWorthTheHeadMaterialsDurability() {
        // Unmodified tool: actualDurability == baseDurability, no occupied modifier slots.
        assertEquals(120, ToolRepair.repairIncrement(120, 160, 160, 0, 0));
    }

    @Test
    void repairIsAtLeastOneSixtyFourthOfTheDurabilityPool() {
        // A tiny head material on a huge durability pool: the flat head value loses to the floor.
        assertEquals(157, ToolRepair.repairIncrement(1, 10_000, 10_000, 0, 0), "expected ceil(10000 / 64)");
    }

    @Test
    void repairsLoseOnePercentageEveryTwoRepairs() {
        // (100 - repairCount / 2) / 100, integer division on repairCount: 3 -> 0.99, 10 -> 0.95.
        assertEquals(120, ToolRepair.repairIncrement(120, 160, 160, 1, 0));
        assertEquals(119, ToolRepair.repairIncrement(120, 160, 160, 3, 0));
        assertEquals(114, ToolRepair.repairIncrement(120, 160, 160, 10, 0));
    }

    @Test
    void diminishingReturnsBottomOutAtHalf() {
        assertEquals(60, ToolRepair.repairIncrement(120, 160, 160, 100, 0));
        assertEquals(60, ToolRepair.repairIncrement(120, 160, 160, 10_000, 0),
                "repeated repairs must never fall below half value");
    }

    @Test
    void durabilityGrowingModifiersScaleTheRepairProportionally() {
        // A Diamond-modified tool (durability +500, e.g. 160 -> 660) repairs proportionally faster
        // per material: increase = amount * min(10, actual/base), upstream's durabilityFactor term.
        // 120 * (660 / 160) = 495.
        assertEquals(495, ToolRepair.repairIncrement(120, 160, 660, 0, 0));
    }

    @Test
    void durabilityFactorCapsAtTen() {
        // actual/base far past 10x still only scales the repair by 10x, upstream's min(10f, ...) cap.
        assertEquals(1200, ToolRepair.repairIncrement(120, 16, 16_000, 0, 0));
    }

    @Test
    void occupiedModifierSlotsApplyUpstreamsPenaltyTable() {
        // 1/2/3+ occupied (non-embossment) modifier slots: 0.95 / 0.90 / 0.85 of the unmodified repair.
        assertEquals(114, ToolRepair.repairIncrement(120, 160, 160, 0, 1), "expected ceil(120 * 0.95)");
        assertEquals(108, ToolRepair.repairIncrement(120, 160, 160, 0, 2), "expected ceil(120 * 0.90)");
        assertEquals(102, ToolRepair.repairIncrement(120, 160, 160, 0, 3), "expected ceil(120 * 0.85)");
        assertEquals(102, ToolRepair.repairIncrement(120, 160, 160, 0, 5),
                "4+ occupied slots must not fall below the 3-slot 0.85x floor");
    }
}
