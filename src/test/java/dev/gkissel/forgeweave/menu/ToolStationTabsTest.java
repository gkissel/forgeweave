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
}
