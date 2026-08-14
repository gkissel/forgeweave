package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two tables a tool issue has to touch together -- {@link ToolAssemblyRecipes#ENTRIES} and
 * {@link ToolStationTabs#TABS} -- staying in step.
 *
 * <p>{@code ToolStationTabs#build} already makes a tab that points at the <em>wrong</em> entry
 * impossible: it looks the row up by the tool itself, so a new entry landing ahead of an existing
 * one can't silently reassign a tab the way a hardcoded index into {@code ENTRIES} could. What it
 * cannot catch is the other direction -- a tool registered in {@code ENTRIES} that nobody ever gave
 * a tab, which is invisible in every test that goes through {@code ENTRIES} and shows up only as a
 * tool a player can never build. That is what this asserts.
 */
class ToolStationTabsTest {

    @Test
    void everyAssemblableToolHasExactlyOneTab() {
        List<ToolAssemblyRecipes.Entry> tabbed = new ArrayList<>();
        for (ToolStationTabs.Tab tab : ToolStationTabs.TABS) {
            if (!tab.isRepair()) {
                tabbed.add(tab.entry());
            }
        }

        assertEquals(ToolAssemblyRecipes.ENTRIES.size(), tabbed.size(),
                "one build tab per assemblable tool -- a new ENTRIES row needs a TABS row too");
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            assertEquals(1, tabbed.stream().filter(candidate -> candidate == entry).count(),
                    () -> entry.constants().id() + " must have exactly one Tool Station tab");
        }
    }

    @Test
    void everyTabListsOnePositionPerPartSlot() {
        // Tab's own constructor enforces this; asserting it here means a bad row fails as a named
        // test rather than as an ExceptionInInitializerError from whichever class touched TABS first.
        for (ToolStationTabs.Tab tab : ToolStationTabs.TABS) {
            if (!tab.isRepair()) {
                assertEquals(tab.entry().slotCount(), tab.slots().size(),
                        () -> tab.entry().constants().id() + ": tab positions and part slots disagree");
            }
        }
        assertTrue(ToolStationTabs.TABS.get(ToolStationTabs.REPAIR).isRepair(),
                "the station opens on the repair tab, so index REPAIR must be it");
    }

    /**
     * Issue #304: mattock, kama and cleaver were hand-authored despite upstream shipping exact
     * coordinates in {@code HarvestClientProxy} (mattock, kama) and {@code MeleeClientProxy}
     * (cleaver). Pins the three tabs to those upstream-derived positions so a future edit can't
     * silently drift back to invented numbers.
     */
    @Test
    void mattockKamaAndCleaverMatchUpstreamPositions() {
        ToolStationTabs.Tab mattock = tabFor("mattock");
        assertEquals(List.of(new ToolStationTabs.Pos(22, 53), new ToolStationTabs.Pos(31, 22),
                new ToolStationTabs.Pos(51, 34)), mattock.slots(),
                "mattock: handle, axe head, shovel head -- HarvestClientProxy verbatim");

        ToolStationTabs.Tab kama = tabFor("kama");
        assertEquals(List.of(new ToolStationTabs.Pos(22, 53), new ToolStationTabs.Pos(31, 22),
                new ToolStationTabs.Pos(51, 34)), kama.slots(),
                "kama: handle, head, binding -- identical to hatchet's HarvestClientProxy row");

        ToolStationTabs.Tab cleaver = tabFor("cleaver");
        assertEquals(List.of(new ToolStationTabs.Pos(9, 64), new ToolStationTabs.Pos(25, 36),
                new ToolStationTabs.Pos(47, 30), new ToolStationTabs.Pos(33, 58)), cleaver.slots(),
                "cleaver: handle, blade, plate, second rod -- MeleeClientProxy verbatim");
    }

    private static ToolStationTabs.Tab tabFor(String toolId) {
        for (ToolStationTabs.Tab tab : ToolStationTabs.TABS) {
            if (!tab.isRepair() && tab.entry().constants().id().equals(toolId)) {
                return tab;
            }
        }
        throw new IllegalStateException("no Tool Station tab for " + toolId);
    }
}
