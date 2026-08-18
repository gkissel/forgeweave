package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link ToolRepair#repairIncrement} against upstream 1.12's
 * {@code TinkersItem#calculateRepairAmount} + {@code #calculateRepair} (see {@link ToolRepair}'s
 * javadoc). Numbers use the shipped stone material (head durability 120) on the stone/wood/wood pickaxe the Tool Station
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

    /**
     * Upstream {@code TinkersItem#calculateRepairAmount}: each repair part contributes its material's
     * head durability times that slot's repair modifier, and the whole sum gains {@code 1/9} per
     * distinct material past the first (parity audit T31, issue #462).
     */
    @Test
    void repairAmountWeightsEachPartAndPaysAMultiMaterialBonus() {
        assertEquals(120, ToolRepair.repairAmount(120f, 1), "one part at factor 1 is just its head durability");
        assertEquals(300, ToolRepair.repairAmount(120f * 2.5f, 1), "a hammer head repairs at 2.5x");
        // Iron-headed hammer (head durability 204) with two cobalt plates (durability 780):
        // 204 * 2.5 + 780 * 1.5 = 1680, two distinct materials -> * (1 + 1/9).
        assertEquals(1866, ToolRepair.repairAmount(204f * 2.5f + 780f * 1.5f, 2));
        // Three distinct materials: * (1 + 2/9).
        assertEquals(146, ToolRepair.repairAmount(120f, 3));
    }

    @Test
    void aRoundWithNoMatchingMaterialIsWorthNothing() {
        assertEquals(0, ToolRepair.repairAmount(0f, 0));
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
