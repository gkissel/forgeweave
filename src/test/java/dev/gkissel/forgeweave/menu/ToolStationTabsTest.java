package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.tool.ToolConstants;

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

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyAssemblableToolHasExactlyOneTab() {
        List<ToolAssemblyRecipes.Entry> tabbed = new ArrayList<>();
        for (ToolStationTabs.Tab tab : ToolStationTabs.TABS) {
            // Since issue #1081 a tab can build a family: the two armor tabs list four pieces each.
            tabbed.addAll(tab.entries());
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

    /**
     * Issue #1006 retired the Armor Station issue #782 added, so the tab row is one row again: every
     * armor entry has a build tab on the tool blocks' own sidebar, beside the tools, with the repair
     * tab at its head.
     *
     * <p>Only reachability is asserted here. The Tool Station/Tool Forge split is an item tag
     * ({@code ToolAssemblyRecipes#LARGE_TOOLS}) and tags are unbound outside a running server, so
     * {@link ToolStationTabs#visible} cannot tell the two apart in a plain unit test -- {@code
     * gametest.ToolForgeGameTests#stationTabsOmitTheForgeTier} is where that half is checked, against
     * the real datapack-bound tag.
     */
    @Test
    void everyArmorEntryHasABuildTabOnTheStationSidebar() {
        List<Integer> tabs = ToolStationTabs.visible(true);

        assertTrue(tabs.contains(ToolStationTabs.REPAIR), "repair stays available");
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            if (entry.constants().category() != ToolConstants.Category.ARMOR) {
                continue;
            }
            assertTrue(tabs.stream().anyMatch(index -> ToolStationTabs.get(index).entries().contains(entry)),
                    () -> entry.constants().id() + " must have a build tab");
        }
    }

    /**
     * Issue #1081: the eight armor buttons became two, one per set, and nothing else on the sidebar
     * moved. Both halves are asserted here -- the armor count, and that every other tab is still the
     * one-tool tab it was, in the order it was in.
     */
    @Test
    void theSidebarHasTwoArmorTabsAndLeavesEveryOtherOneWhereItWas() {
        List<String> nonArmor = new ArrayList<>();
        List<ToolStationTabs.Tab> armor = new ArrayList<>();
        for (ToolStationTabs.Tab tab : ToolStationTabs.TABS) {
            if (!tab.isRepair() && tab.entry().constants().category() == ToolConstants.Category.ARMOR) {
                armor.add(tab);
                continue;
            }
            assertEquals(tab.isRepair() ? 0 : 1, tab.entries().size(),
                    "only the armor tabs build more than one item");
            nonArmor.add(tab.isRepair() ? "repair" : tab.entry().constants().id());
        }

        assertEquals(2, armor.size(), "one Armor button and one Heavy armor button, and no others");
        assertEquals(List.of("repair", "pickaxe", "shovel", "hatchet", "broadsword", "longsword", "rapier",
                        "battlesign", "frying_pan", "dagger", "warmace", "mattock", "kama", "battleaxe",
                        "scimitar", "katana", "cleaver", "hammer", "excavator", "lumberaxe", "scythe",
                        "vein_hammer", "shortbow", "longbow", "crossbow", "shuriken", "arrow"),
                nonArmor, "every non-armor tab keeps its place and its order");

        for (ToolStationTabs.Tab tab : armor) {
            assertEquals(4, tab.entries().size(), "one tab per set, four pieces on it");
        }
    }

    /**
     * Issue #1081: the light armor tab's two slots are upstream 1.20's own, from its single
     * {@code plate_armor} station layout -- plating (33, 29), maille (33, 53). The heavy tab has no
     * upstream counterpart and keeps the triangle issue #735 gave it, large plate included.
     */
    @Test
    void theArmorTabsSitAtTheirUpstreamPositions() {
        ToolStationTabs.Tab armor = tabFor("helmet");
        assertEquals(List.of(new ToolStationTabs.Pos(33, 29), new ToolStationTabs.Pos(33, 53)), armor.slots(),
                "plating then maille, StationSlotLayoutProvider#plateArmor verbatim");

        ToolStationTabs.Tab heavy = tabFor("heavy_helmet");
        assertEquals(List.of(new ToolStationTabs.Pos(33, 26), new ToolStationTabs.Pos(19, 52),
                new ToolStationTabs.Pos(47, 52)), heavy.slots(),
                "plating, maille, large plate -- unchanged since #735");
    }

    /** The plating in the first slot is what picks the piece, on either armor tab. */
    @Test
    void theArmorTabsResolveEveryPieceFromItsPlating() {
        for (String set : List.of("helmet", "heavy_helmet")) {
            ToolStationTabs.Tab tab = tabFor(set);
            for (ToolAssemblyRecipes.Entry entry : tab.entries()) {
                ItemStack plating = new ItemStack(entry.part(0));
                assertEquals(entry, tab.resolve(plating),
                        () -> entry.constants().id() + " must be what its own plating builds");
                assertTrue(tab.acceptsPart(0, plating),
                        () -> "the plating slot must take " + entry.constants().id() + "'s plating");
            }
            assertEquals(tab.entry(), tab.resolve(ItemStack.EMPTY),
                    "an empty plating slot falls back to the family's first piece");
        }
    }

    private static ToolStationTabs.Tab tabFor(String toolId) {
        for (ToolStationTabs.Tab tab : ToolStationTabs.TABS) {
            if (!tab.isRepair() && tab.entries().stream()
                    .anyMatch(entry -> entry.constants().id().equals(toolId))) {
                return tab;
            }
        }
        throw new IllegalStateException("no Tool Station tab for " + toolId);
    }
}
