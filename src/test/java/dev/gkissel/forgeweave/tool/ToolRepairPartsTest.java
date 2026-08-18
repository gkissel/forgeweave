package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.tool.ToolConstants.Entry;
import dev.gkissel.forgeweave.tool.ToolConstants.RepairPart;
import dev.gkissel.forgeweave.tool.ToolConstants.Role;

/**
 * Pins {@link Entry#repairSlots()} against upstream 1.12's {@code TinkersItem#getRepairParts()} and
 * {@code #getRepairModifierForPart(int)} plus the eleven tool classes that override them (parity
 * audit T31, issue #462). Every expectation below was read out of the pinned 1.12 clone; the slot
 * indices are Forgeweave's own part order, which is upstream's {@code PartMaterialType} order
 * index-for-index for every tool derived from it.
 */
class ToolRepairPartsTest {

    private static void assertRepairs(Entry entry, RepairPart... expected) {
        assertEquals(List.of(expected), entry.repairSlots(), entry.id() + " repair slots");
    }

    @Test
    void hammerRepairsThroughItsHeadAndBothPlates() {
        // Hammer.java:85-92 -- getRepairParts {1,2,3}, index 1 -> 2.5, others -> 2.5 * 0.6.
        assertRepairs(ToolConstants.HAMMER,
                new RepairPart(1, 2.5f), new RepairPart(2, 1.5f), new RepairPart(3, 1.5f));
    }

    @Test
    void cleaverExcavatorAndLumberaxeUseTheirOwnSecondaryFactors() {
        // Cleaver.java:67-86, Excavator.java:65-72, LumberAxe.java:125-132.
        assertRepairs(ToolConstants.CLEAVER, new RepairPart(1, 2f), new RepairPart(2, 1.5f));
        assertRepairs(ToolConstants.EXCAVATOR, new RepairPart(1, 1.75f), new RepairPart(2, 1.3125f));
        assertRepairs(ToolConstants.LUMBERAXE, new RepairPart(1, 2f), new RepairPart(2, 1.25f));
    }

    @Test
    void swordsRepairThroughTheBladeOnlyAtTheirDurabilityModifier() {
        // BroadSword.java:77-79, LongSword.java:124-126, Rapier.java:121-123 -- all keep the default
        // getRepairParts {1} and return DURABILITY_MODIFIER for it.
        assertRepairs(ToolConstants.BROADSWORD, new RepairPart(1, 1.1f));
        assertRepairs(ToolConstants.LONGSWORD, new RepairPart(1, 1.05f));
        assertRepairs(ToolConstants.RAPIER, new RepairPart(1, 0.8f));
    }

    @Test
    void scytheRepairsThroughItsBindingToo() {
        // Scythe.java:41-44,179-181 -- parts are (handle, head, binding, handle) and getRepairParts
        // is {1,2}, so upstream really does accept the tough binding's material. No modifier
        // override, so both are 1 despite DURABILITY_MODIFIER = 2.2.
        assertRepairs(ToolConstants.SCYTHE, new RepairPart(1, 1f), new RepairPart(2, 1f));
        assertEquals(Role.EXTRA, ToolConstants.SCYTHE.parts().get(2).role(),
                "the second scythe repair slot is its binding, not a head");
    }

    @Test
    void mattockAndBattleaxeRepairThroughBothHeadsAtPlainOne() {
        // Mattock.java:110-112 and BattleAxe.java:63-65 override getRepairParts but not the modifier.
        assertRepairs(ToolConstants.MATTOCK, new RepairPart(1, 1f), new RepairPart(2, 1f));
        assertRepairs(ToolConstants.BATTLEAXE, new RepairPart(1, 1f), new RepairPart(2, 1f));
    }

    @Test
    void shortbowRepairsThroughEitherLimbButLongbowOnlyThroughItsSecond() {
        // ShortBow.java:46-48 overrides getRepairParts to {0,1}; LongBow does not override it at all
        // and so keeps TinkersItem's default {1}. Ported verbatim, quirk included.
        assertRepairs(ToolConstants.SHORTBOW, new RepairPart(0, 1f), new RepairPart(1, 1f));
        assertRepairs(ToolConstants.LONGBOW, new RepairPart(1, 1f));
    }

    @Test
    void toolsWithoutAnExplicitTableRepairThroughTheirFirstHeadSlot() {
        // Upstream's default is {1} at factor 1, which is the first head slot for every tool that
        // keeps it; the tools with no 1.12 counterpart read the same way.
        assertRepairs(ToolConstants.KAMA, new RepairPart(1, 1f));
        assertRepairs(ToolConstants.BATTLESIGN, new RepairPart(1, 1f));
        assertRepairs(ToolConstants.FRYING_PAN, new RepairPart(1, 1f));
        assertRepairs(ToolConstants.SCIMITAR, new RepairPart(1, 1f));
        assertRepairs(ToolConstants.KATANA, new RepairPart(1, 1f));
        assertRepairs(ToolConstants.WARMACE, new RepairPart(1, 1f));
        // ...including the two whose head is not slot 1.
        assertRepairs(ToolConstants.DAGGER, new RepairPart(0, 1f));
        assertRepairs(ToolConstants.VEIN_HAMMER, new RepairPart(0, 1f));
        // The crossbow's default lands on its single limb, which is where upstream's {1} lands too.
        assertRepairs(ToolConstants.CROSSBOW, new RepairPart(1, 1f));
    }

    @Test
    void everyToolIsRepairableThroughAtLeastOneSlot() {
        for (Entry entry : ToolConstants.ALL) {
            assertTrue(!entry.repairSlots().isEmpty(), entry.id() + " must be repairable");
            for (RepairPart part : entry.repairSlots()) {
                assertTrue(part.slot() >= 0 && part.slot() < entry.parts().size(),
                        entry.id() + " repair slot " + part.slot() + " is out of range");
                assertTrue(part.modifier() > 0f, entry.id() + " repair factors must be positive");
            }
        }
    }
}
